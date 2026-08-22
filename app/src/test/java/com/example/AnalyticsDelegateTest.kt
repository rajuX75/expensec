package com.example

import com.example.data.model.AccountEntity
import com.example.data.model.BudgetEntity
import com.example.data.model.CategoryEntity
import com.example.data.model.TransactionEntity
import com.example.ui.viewmodel.AnalyticsDelegate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsDelegateTest {

    @Test
    fun testAnalyticsDelegateCalculations() = runTest {
        val acc1 = AccountEntity(id = 1, name = "Checking", type = "BANK", balance = 1000.0)
        val acc2 = AccountEntity(id = 2, name = "Savings", type = "SAVINGS", balance = 500.0)

        val catFood = CategoryEntity(id = 10, name = "Food", iconName = "fastfood", colorHex = "#FF0000", type = "EXPENSE")
        val catSalary = CategoryEntity(id = 20, name = "Salary", iconName = "work", colorHex = "#00FF00", type = "INCOME")

        val now = Calendar.getInstance()
        val currentTxDate = now.timeInMillis

        val tx1 = TransactionEntity(id = 1, type = "INCOME", amount = 2000.0, accountId = 1, categoryId = 20, categoryName = "Salary", date = currentTxDate)
        val tx2 = TransactionEntity(id = 2, type = "EXPENSE", amount = 150.0, accountId = 1, categoryId = 10, categoryName = "Food", date = currentTxDate)
        val tx3 = TransactionEntity(id = 3, type = "TRANSFER", amount = 300.0, accountId = 1, toAccountId = 2, categoryName = "Transfer", date = currentTxDate)

        val budgetFood = BudgetEntity(id = 100, categoryId = 10, categoryName = "Food", amountLimit = 500.0)

        val txFlow = MutableStateFlow(listOf(tx1, tx2, tx3))
        val catFlow = MutableStateFlow(listOf(catFood, catSalary))
        val accFlow = MutableStateFlow(listOf(acc1, acc2))
        val budgetFlow = MutableStateFlow(listOf(budgetFood))

        val delegate = AnalyticsDelegate(
            viewModelScope = backgroundScope,
            allTransactions = txFlow,
            allCategories = catFlow,
            allAccounts = accFlow,
            allBudgets = budgetFlow
        )

        // Verify accountsWithBalances
        val balances = delegate.accountsWithBalances.first { it.isNotEmpty() }
        val acc1Bal = balances.find { it.account.id == 1L }
        val acc2Bal = balances.find { it.account.id == 2L }

        // acc1 initial 1000 + 2000 (INCOME) - 150 (EXPENSE) - 300 (TRANSFER) = 2550
        assertEquals(2550.0, acc1Bal?.liveBalance ?: 0.0, 0.01)
        assertEquals(3, acc1Bal?.transactionCount)

        // acc2 initial 500 + 300 (TRANSFER) = 800
        assertEquals(800.0, acc2Bal?.liveBalance ?: 0.0, 0.01)
        assertEquals(1, acc2Bal?.transactionCount)

        // Verify financialSummary
        val summary = delegate.financialSummary.first { it.totalBalance != 0.0 }
        assertEquals(2000.0, summary.thisMonthIncome, 0.01)
        assertEquals(150.0, summary.thisMonthExpense, 0.01)
        assertEquals(1850.0, summary.netSavings, 0.01)
        assertEquals(3350.0, summary.totalBalance, 0.01)

        // Verify budgetStatuses
        val budgets = delegate.budgetStatuses.first { it.isNotEmpty() }
        assertEquals(1, budgets.size)
        assertEquals(150.0, budgets[0].spentAmount, 0.01)
        assertEquals(350.0, budgets[0].remainingAmount, 0.01)
        assertEquals(30f, budgets[0].percentage, 0.01f)

        // Verify categorySpendingData
        val categoryData = delegate.categorySpendingData.first { it.isNotEmpty() }
        assertEquals(1, categoryData.size)
        assertEquals("Food", categoryData[0].name)
        assertEquals(150.0, categoryData[0].amount, 0.01)

        // Verify monthlyTrendsData
        val monthlyTrends = delegate.monthlyTrendsData.first { it.isNotEmpty() }
        assertEquals(6, monthlyTrends.size)
        val currentMonthEntry = monthlyTrends.last()
        assertEquals(150.0, currentMonthEntry.expense, 0.01)
        assertEquals(2000.0, currentMonthEntry.income, 0.01)
    }
}
