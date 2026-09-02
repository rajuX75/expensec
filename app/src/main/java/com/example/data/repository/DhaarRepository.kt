package com.example.data.repository

import com.example.data.local.AccountDao
import com.example.data.local.ContactDao
import com.example.data.local.DhaarEntryDao
import com.example.data.local.TransactionDao
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class DhaarRepository(
    private val contactDao: ContactDao,
    private val dhaarEntryDao: DhaarEntryDao,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao
) {
    val allContacts: Flow<List<Contact>> = contactDao.getAllContacts()
    val allEntries: Flow<List<DhaarEntry>> = dhaarEntryDao.getAllEntries()

    /**
     * Reactively computes contact balances and metadata.
     * Net balance formula:
     * - GIVEN: +amount (they owe you / পাওনা)
     * - RECEIVED: -amount (you owe them / দেনা)
     * - SETTLEMENT:
     *   - If isSettlementGive == true (I paid them): +amount (reduces my negative debt)
     *   - If isSettlementGive == false or null (They paid me): -amount (reduces their positive debt)
     */
    val contactsWithBalances: Flow<List<ContactWithBalance>> = combine(
        allContacts,
        allEntries
    ) { contacts, entries ->
        val now = System.currentTimeMillis()
        val entriesByContact = entries.groupBy { it.contactId }

        contacts.map { contact ->
            val contactEntries = entriesByContact[contact.id] ?: emptyList()
            var givenSum = 0.0
            var receivedSum = 0.0
            var settledSum = 0.0
            var netBal = 0.0
            var hasOverdue = false

            contactEntries.forEach { entry ->
                when (entry.type.uppercase()) {
                    "GIVEN" -> {
                        givenSum += entry.amount
                        netBal += entry.amount
                    }
                    "RECEIVED" -> {
                        receivedSum += entry.amount
                        netBal -= entry.amount
                    }
                    "SETTLEMENT" -> {
                        settledSum += entry.amount
                        if (entry.isSettlementGive == true) {
                            netBal += entry.amount
                        } else {
                            netBal -= entry.amount
                        }
                    }
                }
            }

            // Determine overdue AFTER computing final balance
            // Only flag overdue if the contact still has an outstanding balance AND has past-due entries
            if (abs(netBal) > 0.01) {
                hasOverdue = contactEntries.any { entry ->
                    entry.dueDate != null && entry.dueDate > 0 && entry.dueDate < now
                }
            }

            val lastDate = contactEntries.maxOfOrNull { it.date }

            ContactWithBalance(
                contact = contact,
                netBalance = netBal,
                totalGiven = givenSum,
                totalReceived = receivedSum,
                totalSettled = settledSum,
                lastEntryDate = lastDate,
                entryCount = contactEntries.size,
                hasOverdue = hasOverdue
            )
        }.sortedWith(
            compareByDescending<ContactWithBalance> { abs(it.netBalance) }
                .thenByDescending { it.lastEntryDate ?: 0L }
        )
    }

    /**
     * Dashboard Summary across all contacts
     */
    val dashboardSummary: Flow<DhaarDashboardSummary> = contactsWithBalances.map { contactsWithBal ->
        var totalYouWillGet = 0.0
        var totalYouWillPay = 0.0
        var activeCount = 0
        var settledCount = 0

        contactsWithBal.forEach { item ->
            if (item.netBalance > 0.01) {
                totalYouWillGet += item.netBalance
                activeCount++
            } else if (item.netBalance < -0.01) {
                totalYouWillPay += abs(item.netBalance)
                activeCount++
            } else {
                if (item.entryCount > 0) {
                    settledCount++
                }
            }
        }

        DhaarDashboardSummary(
            totalYouWillGet = totalYouWillGet,
            totalYouWillPay = totalYouWillPay,
            netPosition = totalYouWillGet - totalYouWillPay,
            activeContactsCount = activeCount,
            settledContactsCount = settledCount
        )
    }

    fun getEntriesForContact(contactId: Long): Flow<List<DhaarEntry>> =
        dhaarEntryDao.getEntriesForContact(contactId)

    fun getContactById(contactId: Long): Flow<Contact?> =
        contactDao.getContactById(contactId)

    suspend fun getContactByIdSync(contactId: Long): Contact? =
        contactDao.getContactByIdSync(contactId)

    fun getUpcomingDueEntries(): Flow<List<DhaarReminderItem>> = combine(
        allContacts,
        dhaarEntryDao.getUpcomingDueEntries()
    ) { contacts, entries ->
        val now = System.currentTimeMillis()
        val contactMap = contacts.associateBy { it.id }

        entries.mapNotNull { entry ->
            val contact = contactMap[entry.contactId] ?: return@mapNotNull null
            val dueDate = entry.dueDate ?: return@mapNotNull null
            val diffMs = dueDate - now
            val daysRemaining = TimeUnit.MILLISECONDS.toDays(diffMs)
            val isOverdue = diffMs < 0

            DhaarReminderItem(
                entry = entry,
                contactName = contact.name,
                contactPhone = contact.phoneNumber,
                isOverdue = isOverdue,
                daysRemaining = daysRemaining
            )
        }
    }

    suspend fun insertContact(contact: Contact): Long = withContext(Dispatchers.IO) {
        contactDao.insertContact(contact)
    }

    suspend fun updateContact(contact: Contact) = withContext(Dispatchers.IO) {
        contactDao.updateContact(contact)
    }

    suspend fun getEntryCountForContact(contactId: Long): Int = withContext(Dispatchers.IO) {
        dhaarEntryDao.getEntryCountForContact(contactId)
    }

    suspend fun deleteContact(contact: Contact, deleteEntries: Boolean = false): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val count = dhaarEntryDao.getEntryCountForContact(contact.id)
            if (count > 0 && !deleteEntries) {
                throw IllegalStateException("Contact has $count active ledger entries.")
            }
            if (deleteEntries) {
                dhaarEntryDao.deleteEntriesForContact(contact.id)
            }
            contactDao.deleteContact(contact)
        }
    }

    suspend fun insertDhaarEntry(
        entry: DhaarEntry,
        linkToAccount: Boolean = false,
        accountName: String? = null
    ): Long = withContext(Dispatchers.IO) {
        val entryId = dhaarEntryDao.insertEntry(entry)

        // If linked to an account, record a corresponding main financial transaction
        if (linkToAccount && entry.linkedAccountId != null && entry.linkedAccountId > 0) {
            val contact = contactDao.getContactByIdSync(entry.contactId)
            val contactName = contact?.name ?: "Contact #${entry.contactId}"
            val accName = accountName ?: "Account #${entry.linkedAccountId}"

            val txTypeEnum = when (entry.type.uppercase()) {
                "GIVEN" -> com.example.data.model.TransactionType.EXPENSE
                "RECEIVED" -> com.example.data.model.TransactionType.INCOME
                "SETTLEMENT" -> {
                    if (entry.isSettlementGive == true) com.example.data.model.TransactionType.EXPENSE else com.example.data.model.TransactionType.INCOME
                }
                else -> com.example.data.model.TransactionType.EXPENSE
            }

            val notePrefix = when (entry.type.uppercase()) {
                "GIVEN" -> "Lent to $contactName"
                "RECEIVED" -> "Borrowed from $contactName"
                "SETTLEMENT" -> if (entry.isSettlementGive == true) "Settled debt to $contactName" else "Received settlement from $contactName"
                else -> "Dhaar Entry with $contactName"
            }

            val fullNote = if (entry.note.isNotBlank()) "$notePrefix: ${entry.note}" else notePrefix

            val transaction = TransactionEntity(
                type = txTypeEnum,
                amount = entry.amount,
                currency = entry.currencyCode,
                categoryId = 0,
                categoryName = "Dena-Pawna (Lending)",
                categoryIcon = "handshake",
                categoryColorHex = if (txTypeEnum == com.example.data.model.TransactionType.INCOME) "#10B981" else "#EF4444",
                accountId = entry.linkedAccountId,
                accountName = accName,
                date = entry.date,
                note = fullNote,
                merchant = contactName,
                paymentMethod = "Dhaar Ledger Link",
                receiptUri = entry.tagPhotoUri,
                tags = "Dhaar, DenaPawna, $contactName"
            )
            transactionDao.insertTransaction(transaction)
        }

        entryId
    }

    suspend fun updateDhaarEntry(entry: DhaarEntry) = withContext(Dispatchers.IO) {
        dhaarEntryDao.updateEntry(entry)
    }

    suspend fun deleteDhaarEntry(entry: DhaarEntry) = withContext(Dispatchers.IO) {
        dhaarEntryDao.deleteEntry(entry)
    }

    suspend fun deleteDhaarEntryById(id: Long) = withContext(Dispatchers.IO) {
        dhaarEntryDao.deleteEntryById(id)
    }

    /**
     * Computes net balance from a static list of entries.
     */
    fun computeNetBalance(entries: List<DhaarEntry>): Double {
        var bal = 0.0
        entries.forEach { entry ->
            when (entry.type.uppercase()) {
                "GIVEN" -> bal += entry.amount
                "RECEIVED" -> bal -= entry.amount
                "SETTLEMENT" -> {
                    if (entry.isSettlementGive == true) {
                        bal += entry.amount
                    } else {
                        bal -= entry.amount
                    }
                }
            }
        }
        return bal
    }
}
