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
                if (tx.type == com.example.data.model.TransactionType.INCOME) {
                    totalIncomeMonth += tx.amount
                } else if (tx.type == com.example.data.model.TransactionType.EXPENSE) {
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
        // Pre-group transactions by account to avoid an O(n*m) nested scan.
        // Bucket each transaction into all account IDs it affects so each account
        // can be resolved in O(1) instead of a full list scan.
        data class TxBucket(var income: Double = 0.0, var expense: Double = 0.0, var transfer: Double = 0.0, var count: Int = 0)
        val buckets = mutableMapOf<Long, TxBucket>()

        transactions.forEach { tx ->
            val fromId = tx.accountId
            val toId = tx.toAccountId
            when (tx.type.name) {
                "INCOME" -> {
                    buckets.getOrPut(fromId) { TxBucket() }.also { it.income += tx.amount; it.count++ }
                }
                "EXPENSE" -> {
                    buckets.getOrPut(fromId) { TxBucket() }.also { it.expense += tx.amount; it.count++ }
                }
                "TRANSFER" -> {
                    buckets.getOrPut(fromId) { TxBucket() }.also { it.transfer += tx.amount; it.count++ }
                    if (toId != null) {
                        // Receiving side of a transfer is credited as income delta
                        buckets.getOrPut(toId) { TxBucket() }.also { it.income += tx.amount; it.count++ }
                    }
                }
            }
        }

        accounts.map { acc ->
            val bucket = buckets[acc.id]
            val delta = if (bucket != null) {
                bucket.income - bucket.expense - bucket.transfer
            } else 0.0
            val liveBalance = acc.openingBalance + delta
            val count = bucket?.count ?: 0
            AccountWithBalance(account = acc, liveBalance = liveBalance, transactionCount = count)
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
            if (tx.type != com.example.data.model.TransactionType.EXPENSE) return@filter false
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
    val monthlyTrendsData: StateFlow<List<BarChartEntry>> = allTransactions
        .map { transactions ->
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
                    if (tx.type == com.example.data.model.TransactionType.EXPENSE) {
                        expenseSum += tx.amount
                    } else if (tx.type == com.example.data.model.TransactionType.INCOME) {
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
                    if (tx.type == com.example.data.model.TransactionType.EXPENSE) {
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
        transactions.filter { it.type == com.example.data.model.TransactionType.EXPENSE && it.merchant.isNotBlank() }
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
                if (tx.type != com.example.data.model.TransactionType.EXPENSE) return@filter false
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
        return com.example.domain.usecase.SuggestCategoryUseCase().invoke(
            merchant = merchant,
            transactions = allTransactions.value,
            categories = allCategories.value
        )
    }

    fun computeTotalBalance(accounts: List<AccountEntity>, transactions: List<TransactionEntity>): Double {
        var balance = accounts.sumOf { it.openingBalance }
        transactions.forEach { tx ->
            when (tx.type.name) {
                "INCOME" -> balance += tx.amount
                "EXPENSE" -> balance -= tx.amount
                // Transfers between owned accounts don't alter net aggregate total
            }
        }
        return balance
    }
}

