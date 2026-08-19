package com.example.data.repository

import android.content.Context
import androidx.room.withTransaction
import com.example.data.backup.BackupSerializer
import com.example.data.local.AppDatabase
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ImportExportRepository(
    private val context: Context,
    private val database: AppDatabase,
    private val userPrefsRepo: UserPreferencesRepository
) {
    private val transactionDao = database.transactionDao()
    private val categoryDao = database.categoryDao()
    private val accountDao = database.accountDao()
    private val budgetDao = database.budgetDao()
    private val billDao = database.billDao()
    private val contactDao = database.contactDao()
    private val dhaarEntryDao = database.dhaarEntryDao()

    private val safetyBackupDir: File
        get() = File(context.filesDir, "safety_backups").apply {
            if (!exists()) mkdirs()
        }

    /**
     * Creates a full [AppBackup] snapshot from the current database and preferences state.
     */
    suspend fun createFullBackup(): AppBackup = withContext(Dispatchers.IO) {
        val accounts = accountDao.getAllAccountsSync()
        val categories = categoryDao.getAllCategoriesSync()
        val transactions = transactionDao.getAllTransactionsSync()
        val budgets = budgetDao.getAllBudgetsSync()
        val bills = billDao.getAllBillsSync()
        val contacts = contactDao.getAllContactsSync()
        val dhaarEntries = dhaarEntryDao.getAllEntriesSync()
        val userSettings = userPrefsRepo.getUserSettingsBackup()

        AppBackup(
            schemaVersion = BackupSerializer.CURRENT_SCHEMA_VERSION,
            exportedAt = System.currentTimeMillis(),
            appVersion = "1.0",
            accounts = accounts,
            categories = categories,
            transactions = transactions,
            budgets = budgets,
            bills = bills,
            contacts = contacts,
            dhaarEntries = dhaarEntries,
            userSettings = userSettings
        )
    }

    /**
     * Serializes current app state into a formatted JSON backup string.
     */
    suspend fun exportBackupToJson(): String = withContext(Dispatchers.IO) {
        val backup = createFullBackup()
        BackupSerializer.exportToJson(backup)
    }

    /**
     * Exports transactions into an extended CSV string.
     */
    suspend fun exportTransactionsToCsv(): String = withContext(Dispatchers.IO) {
        val transactions = transactionDao.getAllTransactionsSync()
        BackupSerializer.exportTransactionsToCsv(transactions)
    }

    /**
     * Validates and parses a JSON string into an [AppBackup].
     */
    fun parseBackupJson(jsonString: String): Result<AppBackup> {
        return BackupSerializer.importFromJson(jsonString)
    }

    /**
     * Creates a local safety backup file in app-private storage.
     * Retains at most the last 3 backups, auto-deleting older ones.
     */
    suspend fun createSafetyBackup(): File = withContext(Dispatchers.IO) {
        val json = exportBackupToJson()
        val fileName = "safety_backup_${System.currentTimeMillis()}.json"
        val backupFile = File(safetyBackupDir, fileName)
        backupFile.writeText(json, Charsets.UTF_8)

        // Prune older safety backups, keep at most 3
        pruneSafetyBackups(maxKept = 3)

        backupFile
    }

    /**
     * Lists existing safety backups ordered from newest to oldest.
     */
    suspend fun listSafetyBackups(): List<File> = withContext(Dispatchers.IO) {
        safetyBackupDir.listFiles { file -> file.isFile && file.name.startsWith("safety_backup_") && file.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    private fun pruneSafetyBackups(maxKept: Int = 3) {
        val files = safetyBackupDir.listFiles { file -> file.isFile && file.name.startsWith("safety_backup_") }
            ?.sortedByDescending { it.lastModified() }
            ?: return

        if (files.size > maxKept) {
            for (i in maxKept until files.size) {
                files[i].delete()
            }
        }
    }

    /**
     * Imports an [AppBackup] inside a single atomic Room transaction.
     * Supports REPLACE mode (with automatic safety backup) and MERGE mode (deduplicating by UUID).
     */
    suspend fun importBackup(backup: AppBackup, mode: ImportMode): Result<ImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            var safetyBackupPath: String? = null

            if (mode == ImportMode.REPLACE) {
                // Silently create safety backup before destructive wipe
                val safetyFile = createSafetyBackup()
                safetyBackupPath = safetyFile.absolutePath

                database.withTransaction {
                    // Wipe existing data
                    dhaarEntryDao.deleteAllEntries()
                    contactDao.deleteAllContacts()
                    transactionDao.deleteAllTransactions()
                    budgetDao.deleteAllBudgets()
                    billDao.deleteAllBills()
                    categoryDao.deleteAllCategories()
                    accountDao.deleteAllAccounts()

                    // Insert imported entities
                    accountDao.insertAccounts(backup.accounts.map { it.copy(id = 0) })
                    categoryDao.insertCategories(backup.categories.map { it.copy(id = 0) })
                    transactionDao.insertTransactions(backup.transactions.map { it.copy(id = 0) })
                    budgetDao.insertBudgets(backup.budgets.map { it.copy(id = 0) })
                    billDao.insertBills(backup.bills.map { it.copy(id = 0) })

                    // Insert contacts and map their IDs to dhaar entries
                    val oldToNewContactId = mutableMapOf<Long, Long>()
                    backup.contacts.forEach { c ->
                        val newId = contactDao.insertContact(c.copy(id = 0))
                        oldToNewContactId[c.id] = newId
                    }
                    val dhaarToInsert = backup.dhaarEntries.map { entry ->
                        val mappedContactId = oldToNewContactId[entry.contactId] ?: entry.contactId
                        entry.copy(id = 0, contactId = mappedContactId)
                    }
                    if (dhaarToInsert.isNotEmpty()) {
                        dhaarEntryDao.insertEntries(dhaarToInsert)
                    }
                }

                // Restore user preferences
                userPrefsRepo.applyUserSettingsBackup(backup.userSettings)

                ImportResult(
                    mode = ImportMode.REPLACE,
                    insertedTransactions = backup.transactions.size,
                    insertedAccounts = backup.accounts.size,
                    insertedCategories = backup.categories.size,
                    insertedBudgets = backup.budgets.size,
                    insertedBills = backup.bills.size,
                    insertedContacts = backup.contacts.size,
                    insertedDhaarEntries = backup.dhaarEntries.size,
                    safetyBackupPath = safetyBackupPath
                )
            } else {
                // MERGE Mode: Match by stable UUID
                var newTxs = 0
                var newAccs = 0
                var newCats = 0
                var newBudgets = 0
                var newBills = 0
                var newContacts = 0
                var newDhaar = 0

                database.withTransaction {
                    val existingAccounts = accountDao.getAllAccountsSync()
                    val existingAccountUuids = existingAccounts.map { it.uuid }.toSet()
                    val existingAccountNames = existingAccounts.map { it.name }.toSet()

                    val accountsToInsert = backup.accounts.filter {
                        it.uuid !in existingAccountUuids && it.name !in existingAccountNames
                    }.map { it.copy(id = 0) }
                    if (accountsToInsert.isNotEmpty()) {
                        accountDao.insertAccounts(accountsToInsert)
                        newAccs = accountsToInsert.size
                    }

                    val existingCategories = categoryDao.getAllCategoriesSync()
                    val existingCategoryUuids = existingCategories.map { it.uuid }.toSet()
                    val existingCategoryNames = existingCategories.map { it.name }.toSet()

                    val categoriesToInsert = backup.categories.filter {
                        it.uuid !in existingCategoryUuids && it.name !in existingCategoryNames
                    }.map { it.copy(id = 0) }
                    if (categoriesToInsert.isNotEmpty()) {
                        categoryDao.insertCategories(categoriesToInsert)
                        newCats = categoriesToInsert.size
                    }

                    val existingTxUuids = transactionDao.getAllTransactionsSync().map { it.uuid }.toSet()
                    val txsToInsert = backup.transactions.filter {
                        it.uuid !in existingTxUuids
                    }.map { it.copy(id = 0) }
                    if (txsToInsert.isNotEmpty()) {
                        transactionDao.insertTransactions(txsToInsert)
                        newTxs = txsToInsert.size
                    }

                    val existingBudgetUuids = budgetDao.getAllBudgetsSync().map { it.uuid }.toSet()
                    val budgetsToInsert = backup.budgets.filter {
                        it.uuid !in existingBudgetUuids
                    }.map { it.copy(id = 0) }
                    if (budgetsToInsert.isNotEmpty()) {
                        budgetDao.insertBudgets(budgetsToInsert)
                        newBudgets = budgetsToInsert.size
                    }

                    val existingBillUuids = billDao.getAllBillsSync().map { it.uuid }.toSet()
                    val billsToInsert = backup.bills.filter {
                        it.uuid !in existingBillUuids
                    }.map { it.copy(id = 0) }
                    if (billsToInsert.isNotEmpty()) {
                        billDao.insertBills(billsToInsert)
                        newBills = billsToInsert.size
                    }

                    // Contacts
                    val existingContacts = contactDao.getAllContactsSync()
                    val existingContactUuids = existingContacts.map { it.uuid }.toSet()
                    val existingContactNames = existingContacts.associate { it.name.lowercase() to it.id }.toMutableMap()
                    val contactUuidToId = existingContacts.associate { it.uuid to it.id }.toMutableMap()

                    backup.contacts.forEach { contact ->
                        if (contact.uuid !in existingContactUuids && contact.name.lowercase() !in existingContactNames) {
                            val newId = contactDao.insertContact(contact.copy(id = 0))
                            contactUuidToId[contact.uuid] = newId
                            existingContactNames[contact.name.lowercase()] = newId
                            newContacts++
                        }
                    }

                    // Dhaar Entries
                    val existingDhaarUuids = dhaarEntryDao.getAllEntriesSync().map { it.uuid }.toSet()
                    val dhaarToInsert = mutableListOf<DhaarEntry>()
                    backup.dhaarEntries.forEach { entry ->
                        if (entry.uuid !in existingDhaarUuids) {
                            val originalContact = backup.contacts.find { it.id == entry.contactId }
                            val targetContactId = if (originalContact != null) {
                                contactUuidToId[originalContact.uuid] ?: existingContactNames[originalContact.name.lowercase()]
                            } else {
                                contactDao.getAllContactsSync().firstOrNull()?.id
                            }
                            if (targetContactId != null) {
                                dhaarToInsert.add(entry.copy(id = 0, contactId = targetContactId))
                            }
                        }
                    }
                    if (dhaarToInsert.isNotEmpty()) {
                        dhaarEntryDao.insertEntries(dhaarToInsert)
                        newDhaar = dhaarToInsert.size
                    }
                }

                ImportResult(
                    mode = ImportMode.MERGE,
                    insertedTransactions = newTxs,
                    insertedAccounts = newAccs,
                    insertedCategories = newCats,
                    insertedBudgets = newBudgets,
                    insertedBills = newBills,
                    insertedContacts = newContacts,
                    insertedDhaarEntries = newDhaar
                )
            }
        }
    }
}
