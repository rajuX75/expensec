package com.example

import app.cash.turbine.test
import com.example.data.model.AccountEntity
import com.example.data.model.TransactionEntity
import com.example.data.model.TransactionType
import com.example.ui.viewmodel.AnalyticsDelegate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Skill #3 (testing-setup): StateFlow behavior tests for [AnalyticsDelegate]
 * using Turbine — verifies the reactive financial summary recomputes when the
 * underlying transaction stream changes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsDelegateFlowTest {

    @Test
    fun `financialSummary recomputes when transactions change`() = runTest {
        val transactions = MutableStateFlow<List<TransactionEntity>>(emptyList())
        val accounts = MutableStateFlow(listOf(AccountEntity(name = "Cash", type = "CASH", openingBalance = 500.0)))
        val delegate = AnalyticsDelegate(
            viewModelScope = backgroundScope,
            allTransactions = transactions,
            allCategories = MutableStateFlow(emptyList()),
            allAccounts = accounts,
            allBudgets = MutableStateFlow(emptyList())
        )

        delegate.financialSummary.test {
            var initial = awaitItem()
            while (initial.totalBalance == 0.0) initial = awaitItem()
            assertEquals(500.0, initial.totalBalance, 0.001)

            transactions.value = listOf(
                TransactionEntity(type = TransactionType.INCOME, amount = 200.0),
                TransactionEntity(type = TransactionType.EXPENSE, amount = 50.0)
            )

            // Find the updated emission (skip any intermediate recomputation)
            var updated = awaitItem()
            while (updated.totalBalance == 500.0) updated = awaitItem()
            assertEquals(650.0, updated.totalBalance, 0.001)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
