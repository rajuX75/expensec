package com.example.data.model

data class UserSettingsBackup(
    val currency: String = "USD",
    val currencySymbol: String = "$",
    val themeMode: String = "SYSTEM",
    val isPinLockEnabled: Boolean = false,
    val pinCodeHash: String = "",
    val autoBackupFrequency: String = "OFF",
    val autoBackupWifiOnly: Boolean = true
)

data class AppBackup(
    val schemaVersion: Int = 2,
    val exportedAt: Long = System.currentTimeMillis(),
    val appVersion: String = "1.0",
    val accounts: List<AccountEntity> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val transactions: List<TransactionEntity> = emptyList(),
    val budgets: List<BudgetEntity> = emptyList(),
    val bills: List<BillEntity> = emptyList(),
    val contacts: List<Contact> = emptyList(),
    val dhaarEntries: List<DhaarEntry> = emptyList(),
    val userSettings: UserSettingsBackup = UserSettingsBackup()
) {
    fun toSummary(): BackupSummary = BackupSummary(
        transactionsCount = transactions.size,
        accountsCount = accounts.size,
        categoriesCount = categories.size,
        budgetsCount = budgets.size,
        billsCount = bills.size,
        contactsCount = contacts.size,
        dhaarEntriesCount = dhaarEntries.size,
        exportedAt = exportedAt,
        schemaVersion = schemaVersion
    )
}

data class BackupSummary(
    val transactionsCount: Int,
    val accountsCount: Int,
    val categoriesCount: Int,
    val budgetsCount: Int,
    val billsCount: Int,
    val contactsCount: Int = 0,
    val dhaarEntriesCount: Int = 0,
    val exportedAt: Long,
    val schemaVersion: Int
)

enum class ImportMode {
    MERGE,
    REPLACE
}

data class ImportResult(
    val mode: ImportMode,
    val insertedTransactions: Int,
    val insertedAccounts: Int,
    val insertedCategories: Int,
    val insertedBudgets: Int,
    val insertedBills: Int,
    val insertedContacts: Int = 0,
    val insertedDhaarEntries: Int = 0,
    val safetyBackupPath: String? = null
)
