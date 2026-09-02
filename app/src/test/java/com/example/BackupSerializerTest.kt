package com.example

import com.example.data.backup.BackupSerializer
import com.example.data.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BackupSerializerTest {

    private val sampleBackup = AppBackup(
        schemaVersion = 1,
        exportedAt = 1700000000000L,
        appVersion = "1.0",
        accounts = listOf(
            AccountEntity(
                id = 1,
                uuid = "acc-uuid-1",
                name = "Checking",
                type = "BANK",
                openingBalance = 2500.0,
                currency = "USD",
                colorHex = "#4CAF50",
                iconName = "account_balance"
            )
        ),
        categories = listOf(
            CategoryEntity(
                id = 1,
                uuid = "cat-uuid-1",
                name = "Groceries",
                iconName = "shopping_cart",
                colorHex = "#FF5722",
                type = "EXPENSE",
                isDefault = true
            )
        ),
        transactions = listOf(
            TransactionEntity(
                id = 1,
                uuid = "tx-uuid-1",
                type = com.example.data.model.TransactionType.EXPENSE,
                amount = 85.50,
                currency = "USD",
                categoryId = 1,
                categoryName = "Groceries",
                categoryIcon = "shopping_cart",
                categoryColorHex = "#FF5722",
                accountId = 1,
                accountName = "Checking",
                date = 1700000000000L,
                note = "Weekly grocery run",
                merchant = "Whole Foods",
                paymentMethod = "Debit Card",
                tags = "Food,Home"
            ),
            TransactionEntity(
                id = 2,
                uuid = "tx-uuid-2",
                type = com.example.data.model.TransactionType.INCOME,
                amount = 4500.0,
                currency = "USD",
                categoryId = 2,
                categoryName = "Salary",
                categoryIcon = "work",
                categoryColorHex = "#4CAF50",
                accountId = 1,
                accountName = "Checking",
                date = 1700000000000L,
                note = "Monthly salary",
                merchant = "Acme Corp",
                paymentMethod = "Direct Deposit",
                tags = "Work"
            )
        ),
        budgets = listOf(
            BudgetEntity(
                id = 1,
                uuid = "bud-uuid-1",
                categoryId = 1,
                categoryName = "Groceries",
                amountLimit = 500.0,
                period = "MONTHLY",
                startDate = 1700000000000L,
                alertThresholdPercent = 80
            )
        ),
        bills = listOf(
            BillEntity(
                id = 1,
                uuid = "bill-uuid-1",
                title = "Internet",
                amount = 70.0,
                dueDate = 1700000000000L,
                frequency = "MONTHLY",
                categoryId = 1,
                categoryName = "Utilities",
                accountId = 1,
                isPaid = false
            )
        ),
        userSettings = UserSettingsBackup(
            currency = "USD",
            currencySymbol = "$",
            themeMode = "DARK",
            isPinLockEnabled = false,
            pinCodeHash = "",
            autoBackupFrequency = "DAILY",
            autoBackupWifiOnly = true
        )
    )

    @Test
    fun testJsonSerializationRoundTrip() {
        val json = BackupSerializer.exportToJson(sampleBackup)
        assertNotNull(json)
        assertTrue(json.contains("\"schemaVersion\": 1"))
        assertTrue(json.contains("Weekly grocery run"))
        assertTrue(json.contains("Salary"))
        assertTrue(json.contains("acc-uuid-1"))

        val result = BackupSerializer.importFromJson(json)
        assertTrue(result.isSuccess)
        val deserialized = result.getOrNull()!!

        assertEquals(1, deserialized.schemaVersion)
        assertEquals(1700000000000L, deserialized.exportedAt)
        assertEquals(1, deserialized.accounts.size)
        assertEquals("Checking", deserialized.accounts[0].name)
        assertEquals(2, deserialized.transactions.size)
        assertEquals("Weekly grocery run", deserialized.transactions[0].note)
        assertEquals("Salary", deserialized.transactions[1].categoryName)
        assertEquals("USD", deserialized.userSettings.currency)
    }

    @Test
    fun testCsvExportContainsHeadersAndRows() {
        val csv = BackupSerializer.exportTransactionsToCsv(sampleBackup.transactions)

        assertNotNull(csv)
        assertTrue(csv.startsWith("UUID,ID,Type,Amount,Currency,Category,Account,ToAccount,Timestamp,FormattedDate,Merchant,Note,Tags,PaymentMethod,IsRecurring,RecurringPeriod"))
        assertTrue(csv.contains("Weekly grocery run"))
        assertTrue(csv.contains("85.50"))
        assertTrue(csv.contains("Salary"))
        assertTrue(csv.contains("4500.00"))
        assertTrue(csv.contains("Groceries"))
        assertTrue(csv.contains("Checking"))
    }

    @Test
    fun testValidateSchemaVersionRejection() {
        val json = BackupSerializer.exportToJson(sampleBackup.copy(schemaVersion = 99))
        val invalidResult = BackupSerializer.importFromJson(json, maxSupportedSchemaVersion = 1)
        assertTrue(invalidResult.isFailure)
        assertTrue(invalidResult.exceptionOrNull()?.message?.contains("newer than supported version") == true)
    }

    @Test
    fun testSummaryGeneration() {
        val summary = sampleBackup.toSummary()
        assertEquals(2, summary.transactionsCount)
        assertEquals(1, summary.accountsCount)
        assertEquals(1, summary.categoriesCount)
        assertEquals(1, summary.budgetsCount)
        assertEquals(1, summary.billsCount)
    }
}
