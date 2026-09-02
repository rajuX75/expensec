package com.example

import com.example.data.model.AccountEntity
import com.example.data.model.TransactionEntity
import com.example.data.model.TransactionType
import com.example.ui.viewmodel.AnalyticsDelegate
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Unit tests for [AnalyticsDelegate.computeTotalBalance].
 *
 * computeTotalBalance formula:
 *   sum(account.openingBalance) + sum(INCOME.amount) - sum(EXPENSE.amount)
 * TRANSFER transactions must not affect the aggregate balance.
 */
class AnalyticsDelegateTest {

    private val delegate = AnalyticsDelegate(
        viewModelScope = kotlinx.coroutines.MainScope(),
        allTransactions = MutableStateFlow(emptyList()),
        allCategories = MutableStateFlow(emptyList()),
        allAccounts = MutableStateFlow(emptyList()),
        allBudgets = MutableStateFlow(emptyList())
    )

    private fun account(openingBalance: Double) = AccountEntity(
        name = "Test", type = "BANK", openingBalance = openingBalance
    )

    private fun tx(type: TransactionType, amount: Double) = TransactionEntity(
        type = type, amount = amount
    )

    @Test
    fun `computeTotalBalance sums opening balances when no transactions`() {
        val accounts = listOf(account(1000.0), account(500.0))
        val result = delegate.computeTotalBalance(accounts, emptyList())
        assertEquals(1500.0, result, 0.001)
    }

    @Test
    fun `computeTotalBalance adds income and subtracts expense`() {
        val accounts = listOf(account(1000.0))
        val transactions = listOf(
            tx(TransactionType.INCOME, 300.0),
            tx(TransactionType.EXPENSE, 150.0)
        )
        val result = delegate.computeTotalBalance(accounts, transactions)
        assertEquals(1150.0, result, 0.001)
    }

    @Test
    fun `computeTotalBalance ignores TRANSFER transactions`() {
        val accounts = listOf(account(500.0), account(200.0))
        val transactions = listOf(
            tx(TransactionType.TRANSFER, 100.0)  // moves money between accounts, net = 0
        )
        val result = delegate.computeTotalBalance(accounts, transactions)
        // TRANSFER should not change net balance
        assertEquals(700.0, result, 0.001)
    }

    @Test
    fun `computeTotalBalance returns zero for empty input`() {
        val result = delegate.computeTotalBalance(emptyList(), emptyList())
        assertEquals(0.0, result, 0.001)
    }

    @Test
    fun `computeTotalBalance handles multiple accounts and mixed transactions`() {
        val accounts = listOf(account(2000.0), account(800.0))
        val transactions = listOf(
            tx(TransactionType.INCOME, 500.0),
            tx(TransactionType.EXPENSE, 200.0),
            tx(TransactionType.TRANSFER, 300.0),  // ignored
            tx(TransactionType.EXPENSE, 100.0)
        )
        // 2000 + 800 + 500 - 200 - 100 = 3000
        val result = delegate.computeTotalBalance(accounts, transactions)
        assertEquals(3000.0, result, 0.001)
    }
}
