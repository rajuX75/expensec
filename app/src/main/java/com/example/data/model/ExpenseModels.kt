package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

enum class TransactionType {
    EXPENSE,
    INCOME,
    TRANSFER
}

enum class AccountType {
    BANK,
    CASH,
    CREDIT,
    SAVINGS,
    WALLET
}

enum class BudgetPeriod {
    MONTHLY,
    WEEKLY
}

enum class BillFrequency {
    ONETIME,
    WEEKLY,
    MONTHLY,
    YEARLY
}

@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val type: TransactionType, // EXPENSE, INCOME, TRANSFER
    val amount: Double,
    val currency: String = "USD",
    val categoryId: Long = 0,
    val categoryName: String = "General",
    val categoryIcon: String = "category",
    val categoryColorHex: String = "#64748B",
    val accountId: Long = 1,
    val accountName: String = "Main Account",
    val toAccountId: Long? = null,
    val toAccountName: String? = null,
    val date: Long = System.currentTimeMillis(),
    val note: String = "",
    val merchant: String = "",
    val paymentMethod: String = "Card",
    val receiptUri: String? = null,
    val tags: String = "",
    val isRecurring: Boolean = false,
    val recurringPeriod: String? = null
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val name: String,
    val iconName: String,
    val colorHex: String,
    val type: String, // EXPENSE, INCOME
    val isDefault: Boolean = false
)

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val name: String,
    val type: String, // BANK, CASH, CREDIT, SAVINGS, WALLET
    /** 
     * This represents the *opening* or initial balance of the account. 
     * The true current balance is computed dynamically as: openingBalance + sum(incomes) - sum(expenses). 
     */
    @androidx.room.ColumnInfo(name = "balance")
    val openingBalance: Double = 0.0,
    val currency: String = "USD",
    val colorHex: String = "#00875A",
    val iconName: String = "account_balance"
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val categoryId: Long? = null, // null for overall spending limit
    val categoryName: String? = "Overall Budget",
    val amountLimit: Double,
    val period: String = "MONTHLY",
    val startDate: Long = System.currentTimeMillis(),
    val alertThresholdPercent: Int = 80
)

@Entity(tableName = "bills")
data class BillEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val title: String,
    val amount: Double,
    val dueDate: Long,
    val frequency: String = "MONTHLY", // ONETIME, WEEKLY, MONTHLY, YEARLY
    val categoryId: Long = 0,
    val categoryName: String = "Utilities",
    val accountId: Long? = null,
    val isPaid: Boolean = false,
    val lastPaidDate: Long? = null,
    val autoLogTransaction: Boolean = true
)
