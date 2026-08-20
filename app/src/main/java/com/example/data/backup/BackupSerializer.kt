package com.example.data.backup

import com.example.data.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

object BackupSerializer {

    const val CURRENT_SCHEMA_VERSION = 2

    /**
     * Serializes an [AppBackup] model into a formatted JSON string.
     */
    fun exportToJson(backup: AppBackup): String {
        val root = JSONObject()
        root.put("schemaVersion", backup.schemaVersion)
        root.put("exportedAt", backup.exportedAt)
        root.put("appVersion", backup.appVersion)

        // User Settings
        val settingsObj = JSONObject()
        settingsObj.put("currency", backup.userSettings.currency)
        settingsObj.put("currencySymbol", backup.userSettings.currencySymbol)
        settingsObj.put("themeMode", backup.userSettings.themeMode)
        settingsObj.put("isPinLockEnabled", backup.userSettings.isPinLockEnabled)
        settingsObj.put("pinCodeHash", backup.userSettings.pinCodeHash)
        settingsObj.put("autoBackupFrequency", backup.userSettings.autoBackupFrequency)
        settingsObj.put("autoBackupWifiOnly", backup.userSettings.autoBackupWifiOnly)
        root.put("userSettings", settingsObj)

        // Accounts
        val accountsArr = JSONArray()
        backup.accounts.forEach { acc ->
            val obj = JSONObject()
            obj.put("id", acc.id)
            obj.put("uuid", acc.uuid)
            obj.put("name", acc.name)
            obj.put("type", acc.type)
            obj.put("balance", acc.balance)
            obj.put("currency", acc.currency)
            obj.put("colorHex", acc.colorHex)
            obj.put("iconName", acc.iconName)
            accountsArr.put(obj)
        }
        root.put("accounts", accountsArr)

        // Categories
        val categoriesArr = JSONArray()
        backup.categories.forEach { cat ->
            val obj = JSONObject()
            obj.put("id", cat.id)
            obj.put("uuid", cat.uuid)
            obj.put("name", cat.name)
            obj.put("iconName", cat.iconName)
            obj.put("colorHex", cat.colorHex)
            obj.put("type", cat.type)
            obj.put("isDefault", cat.isDefault)
            categoriesArr.put(obj)
        }
        root.put("categories", categoriesArr)

        // Transactions
        val transactionsArr = JSONArray()
        backup.transactions.forEach { tx ->
            val obj = JSONObject()
            obj.put("id", tx.id)
            obj.put("uuid", tx.uuid)
            obj.put("type", tx.type)
            obj.put("amount", tx.amount)
            obj.put("currency", tx.currency)
            obj.put("categoryId", tx.categoryId)
            obj.put("categoryName", tx.categoryName)
            obj.put("categoryIcon", tx.categoryIcon)
            obj.put("categoryColorHex", tx.categoryColorHex)
            obj.put("accountId", tx.accountId)
            obj.put("accountName", tx.accountName)
            if (tx.toAccountId != null) obj.put("toAccountId", tx.toAccountId)
            if (tx.toAccountName != null) obj.put("toAccountName", tx.toAccountName)
            obj.put("date", tx.date)
            obj.put("note", tx.note)
            obj.put("merchant", tx.merchant)
            obj.put("paymentMethod", tx.paymentMethod)
            if (tx.receiptUri != null) obj.put("receiptUri", tx.receiptUri)
            obj.put("tags", tx.tags)
            obj.put("isRecurring", tx.isRecurring)
            if (tx.recurringPeriod != null) obj.put("recurringPeriod", tx.recurringPeriod)
            transactionsArr.put(obj)
        }
        root.put("transactions", transactionsArr)

        // Budgets
        val budgetsArr = JSONArray()
        backup.budgets.forEach { b ->
            val obj = JSONObject()
            obj.put("id", b.id)
            obj.put("uuid", b.uuid)
            if (b.categoryId != null) obj.put("categoryId", b.categoryId)
            if (b.categoryName != null) obj.put("categoryName", b.categoryName)
            obj.put("amountLimit", b.amountLimit)
            obj.put("period", b.period)
            obj.put("startDate", b.startDate)
            obj.put("alertThresholdPercent", b.alertThresholdPercent)
            budgetsArr.put(obj)
        }
        root.put("budgets", budgetsArr)

        // Bills
        val billsArr = JSONArray()
        backup.bills.forEach { bill ->
            val obj = JSONObject()
            obj.put("id", bill.id)
            obj.put("uuid", bill.uuid)
            obj.put("title", bill.title)
            obj.put("amount", bill.amount)
            obj.put("dueDate", bill.dueDate)
            obj.put("frequency", bill.frequency)
            obj.put("categoryId", bill.categoryId)
            obj.put("categoryName", bill.categoryName)
            if (bill.accountId != null) obj.put("accountId", bill.accountId)
            obj.put("isPaid", bill.isPaid)
            if (bill.lastPaidDate != null) obj.put("lastPaidDate", bill.lastPaidDate)
            obj.put("autoLogTransaction", bill.autoLogTransaction)
            billsArr.put(obj)
        }
        root.put("bills", billsArr)

        // Contacts (Dena-Pawna)
        val contactsArr = JSONArray()
        backup.contacts.forEach { contact ->
            val obj = JSONObject()
            obj.put("id", contact.id)
            obj.put("uuid", contact.uuid)
            obj.put("name", contact.name)
            if (contact.phoneNumber != null) obj.put("phoneNumber", contact.phoneNumber)
            if (contact.photoUri != null) obj.put("photoUri", contact.photoUri)
            obj.put("createdAt", contact.createdAt)
            contactsArr.put(obj)
        }
        root.put("contacts", contactsArr)

        // Dhaar Entries (Dena-Pawna)
        val dhaarArr = JSONArray()
        backup.dhaarEntries.forEach { entry ->
            val obj = JSONObject()
            obj.put("id", entry.id)
            obj.put("uuid", entry.uuid)
            obj.put("contactId", entry.contactId)
            obj.put("type", entry.type)
            obj.put("amount", entry.amount)
            obj.put("currencyCode", entry.currencyCode)
            obj.put("date", entry.date)
            if (entry.dueDate != null) obj.put("dueDate", entry.dueDate)
            obj.put("note", entry.note)
            if (entry.tagPhotoUri != null) obj.put("tagPhotoUri", entry.tagPhotoUri)
            if (entry.linkedAccountId != null) obj.put("linkedAccountId", entry.linkedAccountId)
            if (entry.isSettlementGive != null) obj.put("isSettlementGive", entry.isSettlementGive)
            dhaarArr.put(obj)
        }
        root.put("dhaarEntries", dhaarArr)

        return root.toString(2)
    }

    /**
     * Parses a JSON string into an [AppBackup] instance with schema validation.
     */
    fun importFromJson(jsonString: String, maxSupportedSchemaVersion: Int = CURRENT_SCHEMA_VERSION): Result<AppBackup> {
        return runCatching {
            val root = JSONObject(jsonString)

            val schemaVersion = root.optInt("schemaVersion", 1)
            if (schemaVersion > maxSupportedSchemaVersion) {
                throw IllegalArgumentException(
                    "Backup file schema version ($schemaVersion) is newer than supported version ($maxSupportedSchemaVersion). Please update the application to import this file."
                )
            }

            val exportedAt = root.optLong("exportedAt", System.currentTimeMillis())
            val appVersion = root.optString("appVersion", "1.0")

            // Settings
            val settingsObj = root.optJSONObject("userSettings")
            val userSettings = if (settingsObj != null) {
                UserSettingsBackup(
                    currency = settingsObj.optString("currency", "USD"),
                    currencySymbol = settingsObj.optString("currencySymbol", "$"),
                    themeMode = settingsObj.optString("themeMode", "SYSTEM"),
                    isPinLockEnabled = settingsObj.optBoolean("isPinLockEnabled", false),
                    pinCodeHash = settingsObj.optString("pinCodeHash", ""),
                    autoBackupFrequency = settingsObj.optString("autoBackupFrequency", "OFF"),
                    autoBackupWifiOnly = settingsObj.optBoolean("autoBackupWifiOnly", true)
                )
            } else {
                UserSettingsBackup()
            }

            // Accounts
            val accountsList = mutableListOf<AccountEntity>()
            val accountsArr = root.optJSONArray("accounts")
            if (accountsArr != null) {
                for (i in 0 until accountsArr.length()) {
                    val obj = accountsArr.getJSONObject(i)
                    accountsList.add(
                        AccountEntity(
                            id = obj.optLong("id", 0),
                            uuid = obj.optString("uuid").ifBlank { UUID.randomUUID().toString() },
                            name = obj.optString("name", "Account"),
                            type = obj.optString("type", "BANK"),
                            balance = obj.optDouble("balance", 0.0),
                            currency = obj.optString("currency", "USD"),
                            colorHex = obj.optString("colorHex", "#00875A"),
                            iconName = obj.optString("iconName", "account_balance")
                        )
                    )
                }
            }

            // Categories
            val categoriesList = mutableListOf<CategoryEntity>()
            val categoriesArr = root.optJSONArray("categories")
            if (categoriesArr != null) {
                for (i in 0 until categoriesArr.length()) {
                    val obj = categoriesArr.getJSONObject(i)
                    categoriesList.add(
                        CategoryEntity(
                            id = obj.optLong("id", 0),
                            uuid = obj.optString("uuid").ifBlank { UUID.randomUUID().toString() },
                            name = obj.optString("name", "Category"),
                            iconName = obj.optString("iconName", "category"),
                            colorHex = obj.optString("colorHex", "#64748B"),
                            type = obj.optString("type", "EXPENSE"),
                            isDefault = obj.optBoolean("isDefault", false)
                        )
                    )
                }
            }

            // Transactions
            val transactionsList = mutableListOf<TransactionEntity>()
            val transactionsArr = root.optJSONArray("transactions")
            if (transactionsArr != null) {
                for (i in 0 until transactionsArr.length()) {
                    val obj = transactionsArr.getJSONObject(i)
                    transactionsList.add(
                        TransactionEntity(
                            id = obj.optLong("id", 0),
                            uuid = obj.optString("uuid").ifBlank { UUID.randomUUID().toString() },
                            type = obj.optString("type", "EXPENSE"),
                            amount = obj.optDouble("amount", 0.0),
                            currency = obj.optString("currency", "USD"),
                            categoryId = obj.optLong("categoryId", 0),
                            categoryName = obj.optString("categoryName", "General"),
                            categoryIcon = obj.optString("categoryIcon", "category"),
                            categoryColorHex = obj.optString("categoryColorHex", "#64748B"),
                            accountId = obj.optLong("accountId", 1),
                            accountName = obj.optString("accountName", "Main Account"),
                            toAccountId = if (obj.has("toAccountId") && !obj.isNull("toAccountId")) obj.optLong("toAccountId") else null,
                            toAccountName = if (obj.has("toAccountName") && !obj.isNull("toAccountName")) obj.optString("toAccountName") else null,
                            date = obj.optLong("date", System.currentTimeMillis()),
                            note = obj.optString("note", ""),
                            merchant = obj.optString("merchant", ""),
                            paymentMethod = obj.optString("paymentMethod", "Card"),
                            receiptUri = if (obj.has("receiptUri") && !obj.isNull("receiptUri")) obj.optString("receiptUri") else null,
                            tags = obj.optString("tags", ""),
                            isRecurring = obj.optBoolean("isRecurring", false),
                            recurringPeriod = if (obj.has("recurringPeriod") && !obj.isNull("recurringPeriod")) obj.optString("recurringPeriod") else null
                        )
                    )
                }
            }

            // Budgets
            val budgetsList = mutableListOf<BudgetEntity>()
            val budgetsArr = root.optJSONArray("budgets")
            if (budgetsArr != null) {
                for (i in 0 until budgetsArr.length()) {
                    val obj = budgetsArr.getJSONObject(i)
                    budgetsList.add(
                        BudgetEntity(
                            id = obj.optLong("id", 0),
                            uuid = obj.optString("uuid").ifBlank { UUID.randomUUID().toString() },
                            categoryId = if (obj.has("categoryId") && !obj.isNull("categoryId")) obj.optLong("categoryId") else null,
                            categoryName = if (obj.has("categoryName") && !obj.isNull("categoryName")) obj.optString("categoryName") else null,
                            amountLimit = obj.optDouble("amountLimit", 0.0),
                            period = obj.optString("period", "MONTHLY"),
                            startDate = obj.optLong("startDate", System.currentTimeMillis()),
                            alertThresholdPercent = obj.optInt("alertThresholdPercent", 80)
                        )
                    )
                }
            }

            // Bills
            val billsList = mutableListOf<BillEntity>()
            val billsArr = root.optJSONArray("bills")
            if (billsArr != null) {
                for (i in 0 until billsArr.length()) {
                    val obj = billsArr.getJSONObject(i)
                    billsList.add(
                        BillEntity(
                            id = obj.optLong("id", 0),
                            uuid = obj.optString("uuid").ifBlank { UUID.randomUUID().toString() },
                            title = obj.optString("title", "Bill"),
                            amount = obj.optDouble("amount", 0.0),
                            dueDate = obj.optLong("dueDate", System.currentTimeMillis()),
                            frequency = obj.optString("frequency", "MONTHLY"),
                            categoryId = obj.optLong("categoryId", 0),
                            categoryName = obj.optString("categoryName", "Utilities"),
                            accountId = if (obj.has("accountId") && !obj.isNull("accountId")) obj.optLong("accountId") else null,
                            isPaid = obj.optBoolean("isPaid", false),
                            lastPaidDate = if (obj.has("lastPaidDate") && !obj.isNull("lastPaidDate")) obj.optLong("lastPaidDate") else null,
                            autoLogTransaction = obj.optBoolean("autoLogTransaction", true)
                        )
                    )
                }
            }

            // Contacts (Dena-Pawna)
            val contactsList = mutableListOf<Contact>()
            val contactsArr = root.optJSONArray("contacts")
            if (contactsArr != null) {
                for (i in 0 until contactsArr.length()) {
                    val obj = contactsArr.getJSONObject(i)
                    contactsList.add(
                        Contact(
                            id = obj.optLong("id", 0),
                            uuid = obj.optString("uuid").ifBlank { UUID.randomUUID().toString() },
                            name = obj.optString("name", "Contact"),
                            phoneNumber = if (obj.has("phoneNumber") && !obj.isNull("phoneNumber")) obj.optString("phoneNumber") else null,
                            photoUri = if (obj.has("photoUri") && !obj.isNull("photoUri")) obj.optString("photoUri") else null,
                            createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                        )
                    )
                }
            }

            // Dhaar Entries (Dena-Pawna)
            val dhaarList = mutableListOf<DhaarEntry>()
            val dhaarArr = root.optJSONArray("dhaarEntries")
            if (dhaarArr != null) {
                for (i in 0 until dhaarArr.length()) {
                    val obj = dhaarArr.getJSONObject(i)
                    dhaarList.add(
                        DhaarEntry(
                            id = obj.optLong("id", 0),
                            uuid = obj.optString("uuid").ifBlank { UUID.randomUUID().toString() },
                            contactId = obj.optLong("contactId", 0),
                            type = obj.optString("type", "GIVEN"),
                            amount = obj.optDouble("amount", 0.0),
                            currencyCode = obj.optString("currencyCode", "USD"),
                            date = obj.optLong("date", System.currentTimeMillis()),
                            dueDate = if (obj.has("dueDate") && !obj.isNull("dueDate")) obj.optLong("dueDate") else null,
                            note = obj.optString("note", ""),
                            tagPhotoUri = if (obj.has("tagPhotoUri") && !obj.isNull("tagPhotoUri")) obj.optString("tagPhotoUri") else null,
                            linkedAccountId = if (obj.has("linkedAccountId") && !obj.isNull("linkedAccountId")) obj.optLong("linkedAccountId") else null,
                            isSettlementGive = if (obj.has("isSettlementGive") && !obj.isNull("isSettlementGive")) obj.optBoolean("isSettlementGive") else null
                        )
                    )
                }
            }

            AppBackup(
                schemaVersion = schemaVersion,
                exportedAt = exportedAt,
                appVersion = appVersion,
                accounts = accountsList,
                categories = categoriesList,
                transactions = transactionsList,
                budgets = budgetsList,
                bills = billsList,
                contacts = contactsList,
                dhaarEntries = dhaarList,
                userSettings = userSettings
            )
        }
    }

    /**
     * Exports transactions including EXPENSE, INCOME, and TRANSFER to a spreadsheet-compatible CSV string.
     * Delegates to [CsvSerializer].
     */
    fun exportTransactionsToCsv(transactions: List<TransactionEntity>): String =
        CsvSerializer.exportTransactionsToCsv(transactions)
}
