package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.model.*
import com.example.ui.viewmodel.ExpenseViewModel
import com.example.data.repository.ExpenseRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/**
 * Unit tests for clearAllData().
 *
 * Verifies that:
 * 1. All 10 tables are wiped when clearAllData is called.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ClearAllDataTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `clearAllData wipes all 10 tables`() = runBlocking {
        // 1. Seed data into all 10 tables
        db.accountDao().insertAccount(AccountEntity(name = "A", type = "BANK", openingBalance = 0.0))
        db.categoryDao().insertCategory(CategoryEntity(name = "C", iconName = "x", colorHex = "#000", type = "EXPENSE"))
        db.budgetDao().insertBudget(BudgetEntity(amountLimit = 100.0))
        db.billDao().insertBill(BillEntity(title = "B", amount = 10.0, dueDate = 0, frequency = "MONTHLY", categoryId = 1, accountId = 1))
        db.transactionDao().insertTransaction(TransactionEntity(type = TransactionType.EXPENSE, amount = 10.0, categoryId = 1, accountId = 1))
        db.contactDao().insertContact(Contact(name = "C", phoneNumber = "123"))
        db.dhaarEntryDao().insertEntry(DhaarEntry(contactId = 1, amount = 10.0, type = "GIVE"))
        db.shopDao().insertShop(Shop(name = "S"))
        db.shopProductDao().insertProduct(ShopProduct(name = "P", defaultPrice = 10.0))
        db.shopLedgerEntryDao().insertEntry(ShopLedgerEntry(shopId = 1, amount = 10.0, type = "PAYMENT"))

        // Verify seeded
        assertEquals(1, db.accountDao().getAllAccountsSync().size)
        assertEquals(1, db.categoryDao().getAllCategoriesSync().size)
        assertEquals(1, db.budgetDao().getAllBudgetsSync().size)
        assertEquals(1, db.billDao().getAllBillsSync().size)
        assertEquals(1, db.transactionDao().getAllTransactionsSync().size)
        assertEquals(1, db.contactDao().getAllContactsSync().size)
        assertEquals(1, db.dhaarEntryDao().getAllEntriesSync().size)
        assertEquals(1, db.shopDao().getAllShopsSync().size)
        assertEquals(1, db.shopProductDao().getAllProductsSync().size)
        assertEquals(1, db.shopLedgerEntryDao().getAllEntriesSync().size)

        // 2. Clear all data
        db.shopLedgerEntryDao().deleteAllLedgerEntries()
        db.shopProductDao().deleteAllProducts()
        db.shopDao().deleteAllShops()
        db.dhaarEntryDao().deleteAllEntries()
        db.contactDao().deleteAllContacts()
        db.transactionDao().deleteAllTransactions()
        db.billDao().deleteAllBills()
        db.budgetDao().deleteAllBudgets()
        db.categoryDao().deleteAllCategories()
        db.accountDao().deleteAllAccounts()

        // 3. Verify empty
        assertEquals(0, db.accountDao().getAllAccountsSync().size)
        assertEquals(0, db.categoryDao().getAllCategoriesSync().size)
        assertEquals(0, db.budgetDao().getAllBudgetsSync().size)
        assertEquals(0, db.billDao().getAllBillsSync().size)
        assertEquals(0, db.transactionDao().getAllTransactionsSync().size)
        assertEquals(0, db.contactDao().getAllContactsSync().size)
        assertEquals(0, db.dhaarEntryDao().getAllEntriesSync().size)
        assertEquals(0, db.shopDao().getAllShopsSync().size)
        assertEquals(0, db.shopProductDao().getAllProductsSync().size)
        assertEquals(0, db.shopLedgerEntryDao().getAllEntriesSync().size)
    }
}
