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

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `shops` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `phoneNumber` TEXT,
                `address` TEXT,
                `note` TEXT,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `shop_products` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `defaultUnit` TEXT,
                `defaultPrice` REAL NOT NULL,
                `isArchived` INTEGER NOT NULL,
                `createdAt` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `shop_ledger_entries` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `uuid` TEXT NOT NULL,
                `shopId` INTEGER NOT NULL,
                `type` TEXT NOT NULL,
                `productId` INTEGER,
                `quantity` REAL,
                `unitPriceAtPurchase` REAL,
                `amount` REAL NOT NULL,
                `date` INTEGER NOT NULL,
                `note` TEXT,
                FOREIGN KEY(`shopId`) REFERENCES `shops`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT,
                FOREIGN KEY(`productId`) REFERENCES `shop_products`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_shop_ledger_entries_shopId` ON `shop_ledger_entries` (`shopId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_shop_ledger_entries_productId` ON `shop_ledger_entries` (`productId`)")
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `shops` ADD COLUMN `profilePictureUri` TEXT")
        db.execSQL("ALTER TABLE `shops` ADD COLUMN `coverImageUri` TEXT")
        db.execSQL("ALTER TABLE `shops` ADD COLUMN `email` TEXT")
        db.execSQL("ALTER TABLE `shops` ADD COLUMN `businessId` TEXT")
        db.execSQL("ALTER TABLE `shops` ADD COLUMN `category` TEXT")
        db.execSQL("ALTER TABLE `shops` ADD COLUMN `isVerified` INTEGER NOT NULL DEFAULT 0")
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
        DhaarEntry::class,
        Shop::class,
        ShopProduct::class,
        ShopLedgerEntry::class
    ],
    version = 5,
    exportSchema = false
)
@androidx.room.TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun accountDao(): AccountDao
    abstract fun budgetDao(): BudgetDao
    abstract fun billDao(): BillDao
    abstract fun contactDao(): ContactDao
    abstract fun dhaarEntryDao(): DhaarEntryDao
    abstract fun shopDao(): ShopDao
    abstract fun shopProductDao(): ShopProductDao
    abstract fun shopLedgerEntryDao(): ShopLedgerEntryDao

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
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
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

            // Backfill blank UUIDs left by MIGRATION_1_2 so sync never duplicates.
            categoryDao.backfillBlankUuids()
            // Remove duplicate categories (same name+type) keeping the oldest row.
            categoryDao.deleteDuplicateCategories()

            // Only seed defaults on a truly empty table (idempotent; never re-seed on sync).
            if (categoryDao.getCategoryCount() == 0) {
                val defaultCategories = listOf(
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
                    CategoryEntity(name = "Salary", iconName = "payments", colorHex = "#10B981", type = "INCOME", isDefault = true),
                    CategoryEntity(name = "Freelance", iconName = "work", colorHex = "#059669", type = "INCOME", isDefault = true),
                    CategoryEntity(name = "Investments", iconName = "trending_up", colorHex = "#0D9488", type = "INCOME", isDefault = true),
                    CategoryEntity(name = "Bonus", iconName = "redeem", colorHex = "#EAB308", type = "INCOME", isDefault = true),
                    CategoryEntity(name = "Rental", iconName = "apartment", colorHex = "#6366F1", type = "INCOME", isDefault = true),
                    CategoryEntity(name = "Other Income", iconName = "account_balance_wallet", colorHex = "#3B82F6", type = "INCOME", isDefault = true)
                )
                categoryDao.insertCategories(defaultCategories)
            }

            // Only seed the default account on a truly empty table (idempotent; never re-seed).
            if (accountDao.getAccountCount() == 0) {
                val defaultAccounts = listOf(
                    AccountEntity(name = "Cash Wallet", type = "CASH", openingBalance = 0.0, currency = "USD", colorHex = "#10B981", iconName = "wallet")
                )
                accountDao.insertAccounts(defaultAccounts)
            }
        }
    }
}
