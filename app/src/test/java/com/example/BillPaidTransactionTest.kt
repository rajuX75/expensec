package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.AccountEntity
import com.example.data.model.BillEntity
import com.example.data.model.CategoryEntity
import com.example.data.model.TransactionType
import com.example.data.repository.ExpenseRepository
import com.example.domain.usecase.MarkBillPaidUseCase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Integration tests for [MarkBillPaidUseCase] using an in-memory Room database.
 * No Mockito required — Robolectric already in the test dependency set.
 *
 * Verifies:
 * 1. Bill is marked paid in the DB.
 * 2. An EXPENSE transaction is created with the correct type and amount.
 * 3. Category icon/color sourced from CategoryEntity, not hardcoded strings.
 * 4. No transaction inserted when autoLogTransaction = false.
 * 5. Falls back to the first account when bill.accountId has no match.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BillPaidTransactionTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: ExpenseRepository
    private lateinit var useCase: MarkBillPaidUseCase

    private val account = AccountEntity(
        id = 10, name = "Cash Wallet", type = "CASH", openingBalance = 500.0
    )
    private val category = CategoryEntity(
        id = 42, name = "Entertainment",
        iconName = "movie", colorHex = "#8B5CF6", type = "EXPENSE"
    )
    private val bill = BillEntity(
        id = 1, uuid = "bill-uuid", title = "Netflix", amount = 15.99,
        dueDate = System.currentTimeMillis(), frequency = "MONTHLY",
        categoryId = 42, categoryName = "Entertainment",
        accountId = 10, isPaid = false, autoLogTransaction = true
    )

    @Before
    fun setUp(): Unit = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        // Seed account and category so the use-case can look them up via allAccounts.value
        db.accountDao().insertAccount(account)
        db.categoryDao().insertCategory(category)

        repo = ExpenseRepository(
            transactionDao = db.transactionDao(),
            categoryDao    = db.categoryDao(),
            accountDao     = db.accountDao(),
            budgetDao      = db.budgetDao(),
            billDao        = db.billDao()
        )

        val userPrefs = com.example.data.repository.UserPreferencesRepository(ctx)
        val syncManager = com.example.data.cloud.FirestoreSyncManager(ctx, db, userPrefs)
        useCase = MarkBillPaidUseCase(
            repository = repo,
            firestoreSyncManager = syncManager,
            getCurrentUserId = { null }
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `marks bill as paid with a lastPaidDate timestamp`(): Unit = runBlocking {
        db.billDao().insertBill(bill)
        val accounts = db.accountDao().getAllAccountsSync()
        val categories = db.categoryDao().getAllCategoriesSync()

        useCase.invoke(bill, accounts, categories)

        val allBills = db.billDao().getAllBillsSync()
        val saved = allBills.find { it.uuid == "bill-uuid" }
        assertNotNull("Bill should exist in DB", saved)
        assertTrue("isPaid should be true", saved!!.isPaid)
        assertNotNull("lastPaidDate should be set", saved.lastPaidDate)
    }

    @Test
    fun `creates EXPENSE transaction with correct type and amount`(): Unit = runBlocking {
        db.billDao().insertBill(bill)
        val accounts = db.accountDao().getAllAccountsSync()
        val categories = db.categoryDao().getAllCategoriesSync()

        useCase.invoke(bill, accounts, categories)

        val txList = db.transactionDao().getAllTransactionsSync()
        assertEquals(1, txList.size)
        val tx = txList.first()
        assertEquals(TransactionType.EXPENSE, tx.type)
        assertEquals(15.99, tx.amount, 0.001)
        assertEquals(10L, tx.accountId)
        assertEquals("Cash Wallet", tx.accountName)
    }

    @Test
    fun `uses category icon and color from CategoryEntity, not hardcoded strings`(): Unit = runBlocking {
        db.billDao().insertBill(bill)
        val accounts = db.accountDao().getAllAccountsSync()
        val categories = db.categoryDao().getAllCategoriesSync()

        useCase.invoke(bill, accounts, categories)

        val tx = db.transactionDao().getAllTransactionsSync().first()
        assertEquals("movie", tx.categoryIcon)
        assertEquals("#8B5CF6", tx.categoryColorHex)
    }

    @Test
    fun `does NOT insert transaction when autoLogTransaction is false`(): Unit = runBlocking {
        val billNoLog = bill.copy(autoLogTransaction = false)
        db.billDao().insertBill(billNoLog)
        val accounts = db.accountDao().getAllAccountsSync()
        val categories = db.categoryDao().getAllCategoriesSync()

        useCase.invoke(billNoLog, accounts, categories)

        val txList = db.transactionDao().getAllTransactionsSync()
        assertTrue("No transaction should be created", txList.isEmpty())
    }

    @Test
    fun `falls back to first account when bill accountId has no match`(): Unit = runBlocking {
        val billBadAccount = bill.copy(accountId = 999)
        db.billDao().insertBill(billBadAccount)
        val accounts = db.accountDao().getAllAccountsSync()
        val categories = db.categoryDao().getAllCategoriesSync()

        useCase.invoke(billBadAccount, accounts, categories)

        val tx = db.transactionDao().getAllTransactionsSync().first()
        // Should fall back to the first account in the DB (id=10)
        assertEquals(10L, tx.accountId)
        assertEquals("Cash Wallet", tx.accountName)
    }
}
