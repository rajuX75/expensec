package com.example

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.CategoryEntity
import com.example.data.model.TransactionEntity
import com.example.data.model.TransactionType
import com.example.data.repository.ExpenseRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Skill #3 (testing-setup): Repository integration tests against an in-memory
 * Room database — verifies real DAO SQL behavior without a device.
 */
@RunWith(RobolectricTestRunner::class)
class ExpenseRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: ExpenseRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        repository = ExpenseRepository(
            db.transactionDao(), db.categoryDao(), db.accountDao(),
            db.budgetDao(), db.billDao()
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `inserted transaction appears in allTransactions flow`() = runTest {
        repository.insertTransaction(
            TransactionEntity(type = TransactionType.EXPENSE, amount = 42.5, note = "Lunch")
        )
        val all = repository.allTransactions.first()
        assertEquals(1, all.size)
        assertEquals(42.5, all[0].amount, 0.001)
    }

    @Test
    fun `deleteTransactionById removes the row`() = runTest {
        val id = repository.insertTransaction(
            TransactionEntity(type = TransactionType.INCOME, amount = 100.0)
        )
        repository.deleteTransactionById(id)
        assertTrue(repository.allTransactions.first().isEmpty())
    }

    @Test
    fun `searchTransactions matches note text`() = runTest {
        repository.insertTransaction(TransactionEntity(type = TransactionType.EXPENSE, amount = 10.0, note = "Groceries"))
        repository.insertTransaction(TransactionEntity(type = TransactionType.EXPENSE, amount = 20.0, note = "Fuel"))
        val hits = repository.searchTransactions("Groc").first()
        assertEquals(1, hits.size)
        assertEquals("Groceries", hits[0].note)
    }

    @Test
    fun `category insert and query by type round-trips`() = runTest {
        repository.insertCategory(CategoryEntity(name = "Food", type = "EXPENSE", iconName = "restaurant", colorHex = "#FF0000"))
        repository.insertCategory(CategoryEntity(name = "Salary", type = "INCOME", iconName = "attach_money", colorHex = "#00FF00"))
        val expenseCats = repository.getCategoriesByType("EXPENSE").first()
        assertEquals(1, expenseCats.size)
        assertEquals("Food", expenseCats[0].name)
    }
}
