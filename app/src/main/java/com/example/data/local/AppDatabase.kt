package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE transactions ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE categories ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE accounts ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE budgets ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE bills ADD COLUMN uuid TEXT NOT NULL DEFAULT ''")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `contacts` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `phoneNumber` TEXT,
                `photoUri` TEXT,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `dhaar_entries` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `contactId` INTEGER NOT NULL,
                `type` TEXT NOT NULL,
                `amount` REAL NOT NULL,
                `currencyCode` TEXT NOT NULL,
                `date` INTEGER NOT NULL,
                `dueDate` INTEGER,
                `note` TEXT NOT NULL,
                `tagPhotoUri` TEXT,
                `linkedAccountId` INTEGER,
                `isSettlementGive` INTEGER,
                FOREIGN KEY(`contactId`) REFERENCES `contacts`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_dhaar_entries_contactId` ON `dhaar_entries` (`contactId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_dhaar_entries_dueDate` ON `dhaar_entries` (`dueDate`)")
    }
}

@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        AccountEntity::class,
        BudgetEntity::class,
        BillEntity::class,
        Contact::class,
        DhaarEntry::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun accountDao(): AccountDao
    abstract fun budgetDao(): BudgetDao
    abstract fun billDao(): BillDao
    abstract fun contactDao(): ContactDao
    abstract fun dhaarEntryDao(): DhaarEntryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, scope: CoroutineScope): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "expense_tracker_db"
                )
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .addCallback(DatabaseCallback(scope))
                .build()
                INSTANCE = instance
                instance
            }
        }

        private class DatabaseCallback(
            private val scope: CoroutineScope
        ) : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                INSTANCE?.let { database ->
                    scope.launch(Dispatchers.IO) {
                        populateInitialData(database)
                    }
                }
            }
        }

        suspend fun populateInitialData(database: AppDatabase) {
            val categoryDao = database.categoryDao()
            val accountDao = database.accountDao()
            val budgetDao = database.budgetDao()
            val transactionDao = database.transactionDao()
            val billDao = database.billDao()

            // Prepopulate Categories
            val defaultCategories = listOf(
                // Expenses
                CategoryEntity(name = "Food & Dining", iconName = "restaurant", colorHex = "#EF4444", type = "EXPENSE", isDefault = true),
                CategoryEntity(name = "Groceries", iconName = "shopping_cart", colorHex = "#F59E0B", type = "EXPENSE", isDefault = true),
                CategoryEntity(name = "Transportation", iconName = "directions_car", colorHex = "#3B82F6", type = "EXPENSE", isDefault = true),
                CategoryEntity(name = "Shopping", iconName = "shopping_bag", colorHex = "#EC4899", type = "EXPENSE", isDefault = true),
                CategoryEntity(name = "Housing & Rent", iconName = "home", colorHex = "#8B5CF6", type = "EXPENSE", isDefault = true),
                CategoryEntity(name = "Utilities", iconName = "bolt", colorHex = "#06B6D4", type = "EXPENSE", isDefault = true),
                CategoryEntity(name = "Entertainment", iconName = "movie", colorHex = "#10B981", type = "EXPENSE", isDefault = true),
                CategoryEntity(name = "Health & Medical", iconName = "local_hospital", colorHex = "#F43F5E", type = "EXPENSE", isDefault = true),
                CategoryEntity(name = "Travel", iconName = "flight", colorHex = "#6366F1", type = "EXPENSE", isDefault = true),
                CategoryEntity(name = "Subscriptions", iconName = "subscriptions", colorHex = "#84CC16", type = "EXPENSE", isDefault = true),
                CategoryEntity(name = "Education", iconName = "school", colorHex = "#14B8A6", type = "EXPENSE", isDefault = true),
                CategoryEntity(name = "Personal Care", iconName = "spa", colorHex = "#D946EF", type = "EXPENSE", isDefault = true),
                CategoryEntity(name = "Miscellaneous", iconName = "category", colorHex = "#64748B", type = "EXPENSE", isDefault = true),

                // Income
                CategoryEntity(name = "Salary", iconName = "payments", colorHex = "#10B981", type = "INCOME", isDefault = true),
                CategoryEntity(name = "Freelance", iconName = "work", colorHex = "#059669", type = "INCOME", isDefault = true),
                CategoryEntity(name = "Investments", iconName = "trending_up", colorHex = "#0D9488", type = "INCOME", isDefault = true),
                CategoryEntity(name = "Bonus", iconName = "redeem", colorHex = "#EAB308", type = "INCOME", isDefault = true),
                CategoryEntity(name = "Rental", iconName = "apartment", colorHex = "#6366F1", type = "INCOME", isDefault = true),
                CategoryEntity(name = "Other Income", iconName = "account_balance_wallet", colorHex = "#3B82F6", type = "INCOME", isDefault = true)
            )
            categoryDao.insertCategories(defaultCategories)

            // Prepopulate Accounts
            val defaultAccounts = listOf(
                AccountEntity(name = "Main Bank Checking", type = "BANK", balance = 3450.00, currency = "USD", colorHex = "#00875A", iconName = "account_balance"),
                AccountEntity(name = "Cash Wallet", type = "CASH", balance = 180.00, currency = "USD", colorHex = "#10B981", iconName = "wallet"),
                AccountEntity(name = "Credit Card (Visa)", type = "CREDIT", balance = -420.50, currency = "USD", colorHex = "#3B82F6", iconName = "credit_card"),
                AccountEntity(name = "High-Yield Savings", type = "SAVINGS", balance = 8200.00, currency = "USD", colorHex = "#8B5CF6", iconName = "savings")
            )
            accountDao.insertAccounts(defaultAccounts)

            // Prepopulate Budgets
            val defaultBudgets = listOf(
                BudgetEntity(categoryId = null, categoryName = "Overall Monthly Budget", amountLimit = 2500.0, period = "MONTHLY", alertThresholdPercent = 85),
                BudgetEntity(categoryId = 1, categoryName = "Food & Dining", amountLimit = 450.0, period = "MONTHLY", alertThresholdPercent = 80),
                BudgetEntity(categoryId = 2, categoryName = "Groceries", amountLimit = 400.0, period = "MONTHLY", alertThresholdPercent = 80),
                BudgetEntity(categoryId = 3, categoryName = "Transportation", amountLimit = 200.0, period = "MONTHLY", alertThresholdPercent = 80),
                BudgetEntity(categoryId = 4, categoryName = "Shopping", amountLimit = 300.0, period = "MONTHLY", alertThresholdPercent = 75)
            )
            budgetDao.insertBudgets(defaultBudgets)

            // Prepopulate Sample Transactions for a realistic start
            val now = System.currentTimeMillis()
            val dayMs = 24 * 60 * 60 * 1000L
            val sampleTransactions = listOf(
                TransactionEntity(
                    type = "INCOME",
                    amount = 3200.00,
                    categoryName = "Salary",
                    categoryIcon = "payments",
                    categoryColorHex = "#10B981",
                    accountName = "Main Bank Checking",
                    accountId = 1,
                    date = now - (dayMs * 12),
                    note = "Monthly Bi-weekly Paycheck",
                    merchant = "Acme Corp",
                    paymentMethod = "Direct Deposit",
                    tags = "Salary, Work"
                ),
                TransactionEntity(
                    type = "EXPENSE",
                    amount = 950.00,
                    categoryName = "Housing & Rent",
                    categoryIcon = "home",
                    categoryColorHex = "#8B5CF6",
                    accountName = "Main Bank Checking",
                    accountId = 1,
                    date = now - (dayMs * 10),
                    note = "Monthly Apartment Rent Share",
                    merchant = "Skyline Apartments",
                    paymentMethod = "Bank Transfer",
                    tags = "Rent, Fixed"
                ),
                TransactionEntity(
                    type = "EXPENSE",
                    amount = 84.50,
                    categoryName = "Groceries",
                    categoryIcon = "shopping_cart",
                    categoryColorHex = "#F59E0B",
                    accountName = "Credit Card (Visa)",
                    accountId = 3,
                    date = now - (dayMs * 5),
                    note = "Weekly organic groceries & produce",
                    merchant = "Whole Foods Market",
                    paymentMethod = "Credit Card",
                    tags = "Food, Healthy"
                ),
                TransactionEntity(
                    type = "EXPENSE",
                    amount = 38.20,
                    categoryName = "Food & Dining",
                    categoryIcon = "restaurant",
                    categoryColorHex = "#EF4444",
                    accountName = "Credit Card (Visa)",
                    accountId = 3,
                    date = now - (dayMs * 3),
                    note = "Dinner ramen bowl with friends",
                    merchant = "Ippudo Ramen",
                    paymentMethod = "Credit Card",
                    tags = "DiningOut, Social"
                ),
                TransactionEntity(
                    type = "EXPENSE",
                    amount = 14.99,
                    categoryName = "Subscriptions",
                    categoryIcon = "subscriptions",
                    categoryColorHex = "#84CC16",
                    accountName = "Main Bank Checking",
                    accountId = 1,
                    date = now - (dayMs * 2),
                    note = "Monthly Premium Streaming",
                    merchant = "Spotify",
                    paymentMethod = "Auto Debit",
                    tags = "Subscription, Music",
                    isRecurring = true,
                    recurringPeriod = "MONTHLY"
                ),
                TransactionEntity(
                    type = "EXPENSE",
                    amount = 26.40,
                    categoryName = "Transportation",
                    categoryIcon = "directions_car",
                    categoryColorHex = "#3B82F6",
                    accountName = "Credit Card (Visa)",
                    accountId = 3,
                    date = now - (dayMs * 1),
                    note = "Ride downtown for meeting",
                    merchant = "Uber",
                    paymentMethod = "Credit Card",
                    tags = "Commute, Work"
                ),
                TransactionEntity(
                    type = "EXPENSE",
                    amount = 5.75,
                    categoryName = "Food & Dining",
                    categoryIcon = "restaurant",
                    categoryColorHex = "#EF4444",
                    accountName = "Cash Wallet",
                    accountId = 2,
                    date = now - (1000 * 60 * 180),
                    note = "Oat milk latte",
                    merchant = "Blue Bottle Coffee",
                    paymentMethod = "Cash",
                    tags = "Coffee"
                )
            )
            transactionDao.insertTransactions(sampleTransactions)

            // Prepopulate Bills
            val cal = Calendar.getInstance()
            cal.add(Calendar.DAY_OF_MONTH, 3)
            val bill1Due = cal.timeInMillis
            cal.add(Calendar.DAY_OF_MONTH, 7)
            val bill2Due = cal.timeInMillis

            val defaultBills = listOf(
                BillEntity(
                    title = "Electric & Power Utility",
                    amount = 78.40,
                    dueDate = bill1Due,
                    frequency = "MONTHLY",
                    categoryName = "Utilities",
                    accountId = 1,
                    isPaid = false
                ),
                BillEntity(
                    title = "High Speed Fiber Internet",
                    amount = 59.99,
                    dueDate = bill2Due,
                    frequency = "MONTHLY",
                    categoryName = "Utilities",
                    accountId = 1,
                    isPaid = false
                )
            )
            billDao.insertBills(defaultBills)

            // Prepopulate Sample Contacts & Dhaar Entries
            val contactDao = database.contactDao()
            val dhaarEntryDao = database.dhaarEntryDao()

            val contact1Id = contactDao.insertContact(
                Contact(
                    name = "Raju Ahmed",
                    phoneNumber = "+880 1712-345678",
                    createdAt = now - (dayMs * 20)
                )
            )
            val contact2Id = contactDao.insertContact(
                Contact(
                    name = "Karim Hossain",
                    phoneNumber = "+880 1819-876543",
                    createdAt = now - (dayMs * 15)
                )
            )
            val contact3Id = contactDao.insertContact(
                Contact(
                    name = "Tanvir Hasan",
                    phoneNumber = "+880 1911-223344",
                    createdAt = now - (dayMs * 10)
                )
            )

            // Raju owes you 1,500 (Gave 2,000, Settled 500)
            dhaarEntryDao.insertEntry(
                DhaarEntry(
                    contactId = contact1Id,
                    type = "GIVEN",
                    amount = 2000.0,
                    currencyCode = "BDT",
                    date = now - (dayMs * 18),
                    dueDate = now + (dayMs * 5),
                    note = "Laptop repair loan"
                )
            )
            dhaarEntryDao.insertEntry(
                DhaarEntry(
                    contactId = contact1Id,
                    type = "SETTLEMENT",
                    amount = 500.0,
                    currencyCode = "BDT",
                    date = now - (dayMs * 7),
                    note = "Partial repayment via bKash",
                    isSettlementGive = false
                )
            )

            // You owe Karim 800 (Received 1,200, Settled 400)
            dhaarEntryDao.insertEntry(
                DhaarEntry(
                    contactId = contact2Id,
                    type = "RECEIVED",
                    amount = 1200.0,
                    currencyCode = "BDT",
                    date = now - (dayMs * 14),
                    dueDate = now + (dayMs * 2),
                    note = "Dinner split share"
                )
            )
            dhaarEntryDao.insertEntry(
                DhaarEntry(
                    contactId = contact2Id,
                    type = "SETTLEMENT",
                    amount = 400.0,
                    currencyCode = "BDT",
                    date = now - (dayMs * 4),
                    note = "Sent via Cash",
                    isSettlementGive = true
                )
            )

            // Tanvir is fully settled (Gave 1000, Settled 1000)
            dhaarEntryDao.insertEntry(
                DhaarEntry(
                    contactId = contact3Id,
                    type = "GIVEN",
                    amount = 1000.0,
                    currencyCode = "BDT",
                    date = now - (dayMs * 9),
                    note = "Concert ticket advance"
                )
            )
            dhaarEntryDao.insertEntry(
                DhaarEntry(
                    contactId = contact3Id,
                    type = "SETTLEMENT",
                    amount = 1000.0,
                    currencyCode = "BDT",
                    date = now - (dayMs * 1),
                    note = "Full repayment settled",
                    isSettlementGive = false
                )
            )
        }
    }
}
