package com.example.ui.viewmodel

import com.example.data.model.AccountEntity
import com.example.data.model.BudgetEntity
import com.example.data.model.CategoryEntity
import com.example.data.model.TransactionEntity
import com.example.ui.components.BarChartEntry
import com.example.ui.components.CategoryTrendMeta
import com.example.ui.components.ChartCategoryData
import com.example.ui.components.MonthlyCategoryTrendEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*

// Net Financial Overview Metrics
data class FinancialSummary(
    val totalBalance: Double,
    val thisMonthIncome: Double,
    val thisMonthExpense: Double,
    val netSavings: Double,
    val savingsRate: Double
)

// Dynamic Account Balances (Account + Transactions)
data class AccountWithBalance(
    val account: AccountEntity,
    val liveBalance: Double,
    val transactionCount: Int
)

// Top Merchants / Vendors
data class MerchantSpending(
    val merchant: String,
    val amount: Double,
    val count: Int
)

// Budget Status Overview
data class BudgetStatus(
    val budget: BudgetEntity,
    val spentAmount: Double,
    val remainingAmount: Double,
    val percentage: Float,
    val isOverBudget: Boolean,
    val isNearLimit: Boolean
)

class AnalyticsDelegate(
    private val viewModelScope: CoroutineScope,
    private val allTransactions: StateFlow<List<TransactionEntity>>,
    private val allCategories: StateFlow<List<CategoryEntity>>,
    private val allAccounts: StateFlow<List<AccountEntity>>,
    private val allBudgets: StateFlow<List<BudgetEntity>>
) {
    // Net Financial Overview Metrics
    // OPTIMIZATION: Use month boundary timestamps for fast range checks (O(1) per transaction with 0 Calendar allocations in loop)
    val financialSummary: StateFlow<FinancialSummary> = combine(
        allTransactions,
        allAccounts
    ) { transactions, accounts ->
        val now = Calendar.getInstance()
        val startOfMonth = (now.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val startOfNextMonth = (now.clone() as Calendar).apply {
            add(Calendar.MONTH, 1)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        var totalIncomeMonth = 0.0
        var totalExpenseMonth = 0.0

        transactions.forEach { tx ->
            if (tx.date >= startOfMonth && tx.date < startOfNextMonth) {
                if (tx.type.equals("INCOME", ignoreCase = true)) {
                    totalIncomeMonth += tx.amount
                } else if (tx.type.equals("EXPENSE", ignoreCase = true)) {
                    totalExpenseMonth += tx.amount
                }
            }
        }

        // Compute total live balance across accounts
        val totalLiveBalance = computeTotalBalance(accounts, transactions)

        val netSavings = totalIncomeMonth - totalExpenseMonth
        val savingsRate = if (totalIncomeMonth > 0) ((netSavings / totalIncomeMonth) * 100).coerceAtLeast(0.0) else 0.0

        FinancialSummary(
            totalBalance = totalLiveBalance,
            thisMonthIncome = totalIncomeMonth,
            thisMonthExpense = totalExpenseMonth,
            netSavings = netSavings,
            savingsRate = savingsRate
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FinancialSummary(0.0, 0.0, 0.0, 0.0, 0.0))

    // Dynamic Account Balances (Account + Transactions)
    // OPTIMIZATION: Single O(N) pass to accumulate transaction deltas and counts, reducing complexity from O(M * N) to O(N + M)
    val accountsWithBalances: StateFlow<List<AccountWithBalance>> = combine(
        allAccounts,
        allTransactions
    ) { accounts, transactions ->
        val balanceDeltas = HashMap<Long, Double>()
        val txCounts = HashMap<Long, Int>()

        transactions.forEach { tx ->
            when (tx.type.uppercase()) {
                "INCOME" -> {
                    balanceDeltas[tx.accountId] = (balanceDeltas[tx.accountId] ?: 0.0) + tx.amount
                    txCounts[tx.accountId] = (txCounts[tx.accountId] ?: 0) + 1
                }
                "EXPENSE" -> {
                    balanceDeltas[tx.accountId] = (balanceDeltas[tx.accountId] ?: 0.0) - tx.amount
                    txCounts[tx.accountId] = (txCounts[tx.accountId] ?: 0) + 1
                }
                "TRANSFER" -> {
                    balanceDeltas[tx.accountId] = (balanceDeltas[tx.accountId] ?: 0.0) - tx.amount
                    txCounts[tx.accountId] = (txCounts[tx.accountId] ?: 0) + 1

                    tx.toAccountId?.let { toId ->
                        balanceDeltas[toId] = (balanceDeltas[toId] ?: 0.0) + tx.amount
                        txCounts[toId] = (txCounts[toId] ?: 0) + 1
                    }
                }
            }
        }

        accounts.map { acc ->
            AccountWithBalance(
                account = acc,
                liveBalance = acc.balance + (balanceDeltas[acc.id] ?: 0.0),
                transactionCount = txCounts[acc.id] ?: 0
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Category Spending Analytics (for selected period)
    private val _analyticsPeriod = MutableStateFlow("THIS_MONTH") // THIS_MONTH, LAST_MONTH, THIS_YEAR, ALL
    val analyticsPeriod = _analyticsPeriod.asStateFlow()

    fun setAnalyticsPeriod(period: String) {
        _analyticsPeriod.value = period
    }

    // OPTIMIZATION: Pre-calculate period boundary timestamps once; use HashMap category lookups for O(1) metadata fetching
    val categorySpendingData: StateFlow<List<ChartCategoryData>> = combine(
        allTransactions,
        _analyticsPeriod,
        allCategories
    ) { transactions, period, categories ->
        val now = Calendar.getInstance()
        val (startMs, endMs) = when (period) {
            "THIS_MONTH" -> {
                val start = (now.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val end = (now.clone() as Calendar).apply {
                    add(Calendar.MONTH, 1); set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                start to end
            }
            "LAST_MONTH" -> {
                val start = (now.clone() as Calendar).apply {
                    add(Calendar.MONTH, -1); set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val end = (now.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                start to end
            }
            "THIS_YEAR" -> {
                val start = (now.clone() as Calendar).apply {
                    set(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                val end = (now.clone() as Calendar).apply {
                    add(Calendar.YEAR, 1); set(Calendar.DAY_OF_YEAR, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                start to end
            }
            else -> Long.MIN_VALUE to Long.MAX_VALUE
        }

        val filtered = transactions.filter { tx ->
            tx.type.equals("EXPENSE", ignoreCase = true) && tx.date >= startMs && tx.date < endMs
        }

        val totalSpent = filtered.sumOf { it.amount }
        val categoryMap = HashMap<String, Double>()
        val categoryInfoMap = HashMap<String, Pair<String, String>>() // Name -> (icon, color)

        filtered.forEach { tx ->
            val name = tx.categoryName.ifBlank { "Other" }
            categoryMap[name] = (categoryMap[name] ?: 0.0) + tx.amount
            if (!categoryInfoMap.containsKey(name)) {
                categoryInfoMap[name] = Pair(tx.categoryIcon, tx.categoryColorHex)
            }
        }

        val catMapByName = categories.associateBy { it.name.lowercase() }

        categoryMap.entries.map { (name, amount) ->
            val info = categoryInfoMap[name]
            val catEntity = catMapByName[name.lowercase()]
            val colorHex = catEntity?.colorHex ?: info?.second ?: "#64748B"
            val iconName = catEntity?.iconName ?: info?.first ?: "category"
            val percentage = if (totalSpent > 0) ((amount / totalSpent) * 100).toFloat() else 0f

            ChartCategoryData(
                name = name,
                amount = amount,
                percentage = percentage,
                colorHex = colorHex,
                iconName = iconName
            )
        }.sortedByDescending { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Monthly Trend Chart Data (Last 6 Months)
    // OPTIMIZATION: Pre-calculate 6-month timestamp boundaries; single O(N) pass over transactions with zero Calendar allocations in loop
    val monthlyTrendsData: StateFlow<List<BarChartEntry>> = allTransactions.map { transactions ->
        val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())
        val entries = mutableListOf<BarChartEntry>()

        class TargetMonth(val label: String, val startMs: Long, val endMs: Long)
        val months = Array(6) { idx ->
            val i = 5 - idx
            val calStart = Calendar.getInstance().apply {
                add(Calendar.MONTH, -i)
                set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val calEnd = (calStart.clone() as Calendar).apply {
                add(Calendar.MONTH, 1)
            }
            TargetMonth(
                label = monthFormat.format(calStart.time),
                startMs = calStart.timeInMillis,
                endMs = calEnd.timeInMillis
            )
        }

        val expenseSums = DoubleArray(6)
        val incomeSums = DoubleArray(6)

        transactions.forEach { tx ->
            val txDate = tx.date
            val isExpense = tx.type.equals("EXPENSE", ignoreCase = true)
            val isIncome = if (!isExpense) tx.type.equals("INCOME", ignoreCase = true) else false

            if (isExpense || isIncome) {
                for (idx in 0 until 6) {
                    val m = months[idx]
                    if (txDate >= m.startMs && txDate < m.endMs) {
                        if (isExpense) expenseSums[idx] += tx.amount
                        else incomeSums[idx] += tx.amount
                        break
                    }
                }
            }
        }

        for (idx in 0 until 6) {
            entries.add(BarChartEntry(label = months[idx].label, expense = expenseSums[idx], income = incomeSums[idx]))
        }

        entries
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Monthly Category Trends Data (Recharts style breakdown by category across last 6 months)
    // OPTIMIZATION: Single-pass bucket aggregation with 0 Calendar allocations in transaction loop & O(1) category metadata map
    val monthlyCategoryTrendsData: StateFlow<Pair<List<MonthlyCategoryTrendEntry>, List<CategoryTrendMeta>>> = combine(
        allTransactions,
        allCategories
    ) { transactions, categories ->
        val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())
        val entries = mutableListOf<MonthlyCategoryTrendEntry>()
        val categoryTotals = HashMap<String, Double>()
        val categoryInfo = HashMap<String, Pair<String, String>>() // Name -> (icon, color)

        categories.forEach { cat ->
            categoryInfo[cat.name] = Pair(cat.iconName, cat.colorHex)
        }

        class TargetMonth(val label: String, val year: Int, val month: Int, val startMs: Long, val endMs: Long)
        val months = Array(6) { idx ->
            val i = 5 - idx
            val calStart = Calendar.getInstance().apply {
                add(Calendar.MONTH, -i)
                set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val calEnd = (calStart.clone() as Calendar).apply {
                add(Calendar.MONTH, 1)
            }
            TargetMonth(
                label = monthFormat.format(calStart.time),
                year = calStart.get(Calendar.YEAR),
                month = calStart.get(Calendar.MONTH),
                startMs = calStart.timeInMillis,
                endMs = calEnd.timeInMillis
            )
        }

        val monthCategoryMaps = Array(6) { HashMap<String, Double>() }
        val monthTotals = DoubleArray(6)

        transactions.forEach { tx ->
            if (tx.type.equals("EXPENSE", ignoreCase = true)) {
                val txDate = tx.date
                for (idx in 0 until 6) {
                    val m = months[idx]
                    if (txDate >= m.startMs && txDate < m.endMs) {
                        val catName = tx.categoryName.ifBlank { "Other" }
                        val currentCatAmount = monthCategoryMaps[idx][catName] ?: 0.0
                        monthCategoryMaps[idx][catName] = currentCatAmount + tx.amount
                        categoryTotals[catName] = (categoryTotals[catName] ?: 0.0) + tx.amount
                        monthTotals[idx] += tx.amount

                        if (!categoryInfo.containsKey(catName)) {
                            categoryInfo[catName] = Pair(tx.categoryIcon, tx.categoryColorHex)
                        }
                        break
                    }
                }
            }
        }

        for (idx in 0 until 6) {
            val m = months[idx]
            entries.add(
                MonthlyCategoryTrendEntry(
                    monthLabel = m.label,
                    year = m.year,
                    month = m.month,
                    totalExpense = monthTotals[idx],
                    categoryAmounts = monthCategoryMaps[idx]
                )
            )
        }

        val catMapByName = categories.associateBy { it.name.lowercase() }

        val metaList = categoryTotals.entries.map { (catName, total) ->
            val info = categoryInfo[catName]
            val catEntity = catMapByName[catName.lowercase()]
            CategoryTrendMeta(
                name = catName,
                colorHex = catEntity?.colorHex ?: info?.second ?: "#64748B",
                iconName = catEntity?.iconName ?: info?.first ?: "category",
                totalAcrossMonths = total
            )
        }.sortedByDescending { it.totalAcrossMonths }

        Pair(entries, metaList)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Pair(emptyList(), emptyList()))

    val topMerchants: StateFlow<List<MerchantSpending>> = allTransactions.map { transactions ->
        val merchantMap = HashMap<String, Pair<Double, Int>>()
        transactions.filter { it.type.equals("EXPENSE", ignoreCase = true) && it.merchant.isNotBlank() }
            .forEach { tx ->
                val current = merchantMap[tx.merchant] ?: Pair(0.0, 0)
                merchantMap[tx.merchant] = Pair(current.first + tx.amount, current.second + 1)
            }
        merchantMap.entries.map { (merchant, data) ->
            MerchantSpending(merchant = merchant, amount = data.first, count = data.second)
        }.sortedByDescending { it.amount }.take(5)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // OPTIMIZATION: Pre-filter transactions for current month once in O(N); calculate budget statuses in O(B) without Calendar object allocations in loop
    val budgetStatuses: StateFlow<List<BudgetStatus>> = combine(
        allBudgets,
        allTransactions
    ) { budgets, transactions ->
        val now = Calendar.getInstance()
        val startOfMonth = (now.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val startOfNextMonth = (now.clone() as Calendar).apply {
            add(Calendar.MONTH, 1); set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val spentByCatId = HashMap<Long, Double>()
        val spentByCatName = HashMap<String, Double>()
        var overallSpent = 0.0

        transactions.forEach { tx ->
            if (tx.type.equals("EXPENSE", ignoreCase = true) && tx.date >= startOfMonth && tx.date < startOfNextMonth) {
                overallSpent += tx.amount
                spentByCatId[tx.categoryId] = (spentByCatId[tx.categoryId] ?: 0.0) + tx.amount
                val nameLower = tx.categoryName.lowercase()
                spentByCatName[nameLower] = (spentByCatName[nameLower] ?: 0.0) + tx.amount
            }
        }

        budgets.map { budget ->
            val spent = if (budget.categoryId == null) {
                overallSpent
            } else {
                spentByCatId[budget.categoryId] ?: spentByCatName[budget.categoryName?.lowercase() ?: ""] ?: 0.0
            }

            val remaining = budget.amountLimit - spent
            val percentage = if (budget.amountLimit > 0) ((spent / budget.amountLimit) * 100).toFloat() else 0f
            val isOver = spent > budget.amountLimit
            val isNear = percentage >= budget.alertThresholdPercent && !isOver

            BudgetStatus(
                budget = budget,
                spentAmount = spent,
                remainingAmount = remaining,
                percentage = percentage,
                isOverBudget = isOver,
                isNearLimit = isNear
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Auto-Categorization Helper from Merchant Name
    fun suggestCategoryForMerchant(merchant: String): CategoryEntity? {
        val clean = merchant.trim().lowercase()
        val categories = allCategories.value

        return when {
            clean.contains("food") || clean.contains("restaurant") || clean.contains("cafe") ||
            clean.contains("starbucks") || clean.contains("mcdonald") || clean.contains("burger") ||
            clean.contains("pizza") || clean.contains("ramen") || clean.contains("chipotle") -> {
                categories.find { it.name.contains("Food", ignoreCase = true) }
            }
            clean.contains("grocery") || clean.contains("market") || clean.contains("walmart") ||
            clean.contains("whole foods") || clean.contains("trader joe") || clean.contains("kroger") ||
            clean.contains("target") || clean.contains("supermarket") -> {
                categories.find { it.name.contains("Groceries", ignoreCase = true) }
            }
            clean.contains("uber") || clean.contains("lyft") || clean.contains("gas") ||
            clean.contains("shell") || clean.contains("chevron") || clean.contains("transit") ||
            clean.contains("subway") || clean.contains("train") || clean.contains("airline") -> {
                categories.find { it.name.contains("Transport", ignoreCase = true) }
            }
            clean.contains("netflix") || clean.contains("spotify") || clean.contains("youtube") ||
            clean.contains("apple") || clean.contains("hulu") || clean.contains("disney") ||
            clean.contains("patreon") || clean.contains("subscription") -> {
                categories.find { it.name.contains("Subscription", ignoreCase = true) }
            }
            clean.contains("rent") || clean.contains("apartment") || clean.contains("mortgage") ||
            clean.contains("housing") -> {
                categories.find { it.name.contains("Housing", ignoreCase = true) }
            }
            clean.contains("electric") || clean.contains("power") || clean.contains("water") ||
            clean.contains("internet") || clean.contains("wifi") || clean.contains("verizon") ||
            clean.contains("at&t") || clean.contains("t-mobile") -> {
                categories.find { it.name.contains("Utilities", ignoreCase = true) }
            }
            clean.contains("amazon") || clean.contains("ebay") || clean.contains("zara") ||
            clean.contains("nike") || clean.contains("h&m") || clean.contains("clothing") ||
            clean.contains("mall") -> {
                categories.find { it.name.contains("Shopping", ignoreCase = true) }
            }
            clean.contains("pharmacy") || clean.contains("cvs") || clean.contains("walgreens") ||
            clean.contains("doctor") || clean.contains("dental") || clean.contains("hospital") -> {
                categories.find { it.name.contains("Health", ignoreCase = true) }
            }
            clean.contains("salary") || clean.contains("payroll") || clean.contains("wage") ||
            clean.contains("bonus") -> {
                categories.find { it.name.contains("Salary", ignoreCase = true) || it.name.contains("Income", ignoreCase = true) }
            }
            else -> null
        }
    }

    fun computeTotalBalance(accounts: List<AccountEntity>, transactions: List<TransactionEntity>): Double {
        var balance = accounts.sumOf { it.balance }
        transactions.forEach { tx ->
            when (tx.type.uppercase()) {
                "INCOME" -> balance += tx.amount
                "EXPENSE" -> balance -= tx.amount
                // Transfers between owned accounts don't alter net aggregate total
            }
        }
        return balance
    }
}
