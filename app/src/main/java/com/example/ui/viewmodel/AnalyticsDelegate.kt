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
    val financialSummary: StateFlow<FinancialSummary> = combine(
        allTransactions,
        allAccounts
    ) { transactions, accounts ->
        val now = Calendar.getInstance()
        val currentYear = now.get(Calendar.YEAR)
        val currentMonth = now.get(Calendar.MONTH)

        var totalIncomeMonth = 0.0
        var totalExpenseMonth = 0.0

        transactions.forEach { tx ->
            val txCal = Calendar.getInstance().apply { timeInMillis = tx.date }
            val isCurrentMonth = txCal.get(Calendar.YEAR) == currentYear && txCal.get(Calendar.MONTH) == currentMonth

            if (isCurrentMonth) {
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
    val accountsWithBalances: StateFlow<List<AccountWithBalance>> = combine(
        allAccounts,
        allTransactions
    ) { accounts, transactions ->
        accounts.map { acc ->
            var bal = acc.balance
            var count = 0
            transactions.forEach { tx ->
                if (tx.accountId == acc.id) {
                    count++
                    when (tx.type.uppercase()) {
                        "INCOME" -> bal += tx.amount
                        "EXPENSE" -> bal -= tx.amount
                        "TRANSFER" -> bal -= tx.amount
                    }
                } else if (tx.toAccountId == acc.id && tx.type.equals("TRANSFER", ignoreCase = true)) {
                    count++
                    bal += tx.amount
                }
            }
            AccountWithBalance(account = acc, liveBalance = bal, transactionCount = count)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Category Spending Analytics (for selected period)
    private val _analyticsPeriod = MutableStateFlow("THIS_MONTH") // THIS_MONTH, LAST_MONTH, THIS_YEAR, ALL
    val analyticsPeriod = _analyticsPeriod.asStateFlow()

    fun setAnalyticsPeriod(period: String) {
        _analyticsPeriod.value = period
    }

    val categorySpendingData: StateFlow<List<ChartCategoryData>> = combine(
        allTransactions,
        _analyticsPeriod,
        allCategories
    ) { transactions, period, categories ->
        val now = Calendar.getInstance()
        val filtered = transactions.filter { tx ->
            if (!tx.type.equals("EXPENSE", ignoreCase = true)) return@filter false
            val txCal = Calendar.getInstance().apply { timeInMillis = tx.date }
            when (period) {
                "THIS_MONTH" -> txCal.get(Calendar.YEAR) == now.get(Calendar.YEAR) && txCal.get(Calendar.MONTH) == now.get(Calendar.MONTH)
                "LAST_MONTH" -> {
                    val lastM = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
                    txCal.get(Calendar.YEAR) == lastM.get(Calendar.YEAR) && txCal.get(Calendar.MONTH) == lastM.get(Calendar.MONTH)
                }
                "THIS_YEAR" -> txCal.get(Calendar.YEAR) == now.get(Calendar.YEAR)
                else -> true
            }
        }

        val totalSpent = filtered.sumOf { it.amount }
        val categoryMap = mutableMapOf<String, Double>()
        val categoryInfoMap = mutableMapOf<String, Pair<String, String>>() // Name -> (icon, color)

        filtered.forEach { tx ->
            val name = tx.categoryName.ifBlank { "Other" }
            categoryMap[name] = (categoryMap[name] ?: 0.0) + tx.amount
            if (!categoryInfoMap.containsKey(name)) {
                categoryInfoMap[name] = Pair(tx.categoryIcon, tx.categoryColorHex)
            }
        }

        categoryMap.entries.map { (name, amount) ->
            val info = categoryInfoMap[name]
            val catEntity = categories.find { it.name.equals(name, ignoreCase = true) }
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
    val monthlyTrendsData: StateFlow<List<BarChartEntry>> = allTransactions.map { transactions ->
        val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())
        val entries = mutableListOf<BarChartEntry>()

        for (i in 5 downTo 0) {
            val cal = Calendar.getInstance().apply {
                add(Calendar.MONTH, -i)
            }
            val targetYear = cal.get(Calendar.YEAR)
            val targetMonth = cal.get(Calendar.MONTH)
            val label = monthFormat.format(cal.time)

            var expenseSum = 0.0
            var incomeSum = 0.0

            transactions.forEach { tx ->
                val txCal = Calendar.getInstance().apply { timeInMillis = tx.date }
                if (txCal.get(Calendar.YEAR) == targetYear && txCal.get(Calendar.MONTH) == targetMonth) {
                    if (tx.type.equals("EXPENSE", ignoreCase = true)) {
                        expenseSum += tx.amount
                    } else if (tx.type.equals("INCOME", ignoreCase = true)) {
                        incomeSum += tx.amount
                    }
                }
            }

            entries.add(BarChartEntry(label = label, expense = expenseSum, income = incomeSum))
        }

        entries
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Monthly Category Trends Data (Recharts style breakdown by category across last 6 months)
    val monthlyCategoryTrendsData: StateFlow<Pair<List<MonthlyCategoryTrendEntry>, List<CategoryTrendMeta>>> = combine(
        allTransactions,
        allCategories
    ) { transactions, categories ->
        val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())
        val entries = mutableListOf<MonthlyCategoryTrendEntry>()
        val categoryTotals = mutableMapOf<String, Double>()
        val categoryInfo = mutableMapOf<String, Pair<String, String>>() // Name -> (icon, color)

        categories.forEach { cat ->
            categoryInfo[cat.name] = Pair(cat.iconName, cat.colorHex)
        }

        for (i in 5 downTo 0) {
            val cal = Calendar.getInstance().apply {
                add(Calendar.MONTH, -i)
            }
            val targetYear = cal.get(Calendar.YEAR)
            val targetMonth = cal.get(Calendar.MONTH)
            val label = monthFormat.format(cal.time)

            val monthCategoryMap = mutableMapOf<String, Double>()
            var monthTotal = 0.0

            transactions.forEach { tx ->
                val txCal = Calendar.getInstance().apply { timeInMillis = tx.date }
                if (txCal.get(Calendar.YEAR) == targetYear && txCal.get(Calendar.MONTH) == targetMonth) {
                    if (tx.type.equals("EXPENSE", ignoreCase = true)) {
                        val catName = tx.categoryName.ifBlank { "Other" }
                        monthCategoryMap[catName] = (monthCategoryMap[catName] ?: 0.0) + tx.amount
                        categoryTotals[catName] = (categoryTotals[catName] ?: 0.0) + tx.amount
                        monthTotal += tx.amount

                        if (!categoryInfo.containsKey(catName)) {
                            categoryInfo[catName] = Pair(tx.categoryIcon, tx.categoryColorHex)
                        }
                    }
                }
            }

            entries.add(
                MonthlyCategoryTrendEntry(
                    monthLabel = label,
                    year = targetYear,
                    month = targetMonth,
                    totalExpense = monthTotal,
                    categoryAmounts = monthCategoryMap
                )
            )
        }

        val metaList = categoryTotals.entries.map { (catName, total) ->
            val info = categoryInfo[catName]
            val catEntity = categories.find { it.name.equals(catName, ignoreCase = true) }
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
        val merchantMap = mutableMapOf<String, Pair<Double, Int>>()
        transactions.filter { it.type.equals("EXPENSE", ignoreCase = true) && it.merchant.isNotBlank() }
            .forEach { tx ->
                val current = merchantMap[tx.merchant] ?: Pair(0.0, 0)
                merchantMap[tx.merchant] = Pair(current.first + tx.amount, current.second + 1)
            }
        merchantMap.entries.map { (merchant, data) ->
            MerchantSpending(merchant = merchant, amount = data.first, count = data.second)
        }.sortedByDescending { it.amount }.take(5)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val budgetStatuses: StateFlow<List<BudgetStatus>> = combine(
        allBudgets,
        allTransactions
    ) { budgets, transactions ->
        val now = Calendar.getInstance()
        val currentYear = now.get(Calendar.YEAR)
        val currentMonth = now.get(Calendar.MONTH)

        budgets.map { budget ->
            val spent = transactions.filter { tx ->
                if (!tx.type.equals("EXPENSE", ignoreCase = true)) return@filter false
                val txCal = Calendar.getInstance().apply { timeInMillis = tx.date }
                val isInPeriod = txCal.get(Calendar.YEAR) == currentYear && txCal.get(Calendar.MONTH) == currentMonth

                if (!isInPeriod) return@filter false

                if (budget.categoryId == null) {
                    true // Overall budget
                } else {
                    tx.categoryId == budget.categoryId || tx.categoryName.equals(budget.categoryName, ignoreCase = true)
                }
            }.sumOf { it.amount }

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
