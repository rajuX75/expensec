package com.example.ui.viewmodel

import com.example.data.cloud.FirestoreSyncManager
import com.example.data.cloud.GoogleAuthManager
import com.example.data.local.AppDatabase
import com.example.data.model.*
import com.example.data.repository.DhaarRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class DhaarDelegate(
    private val viewModelScope: CoroutineScope,
    private val database: AppDatabase,
    private val dhaarRepository: DhaarRepository,
    private val googleAuthManager: GoogleAuthManager,
    private val firestoreSyncManager: FirestoreSyncManager
) {
    val allContacts: StateFlow<List<Contact>> = dhaarRepository.allContacts.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val allDhaarEntries: StateFlow<List<DhaarEntry>> = dhaarRepository.allEntries.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val contactsWithBalances: StateFlow<List<ContactWithBalance>> = dhaarRepository.contactsWithBalances.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val dhaarDashboardSummary: StateFlow<DhaarDashboardSummary> = dhaarRepository.dashboardSummary.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), DhaarDashboardSummary(0.0, 0.0, 0.0, 0, 0)
    )

    val upcomingDhaarReminders: StateFlow<List<DhaarReminderItem>> = dhaarRepository.getUpcomingDueEntries().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    fun addContact(contact: Contact, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = dhaarRepository.insertContact(contact)
            googleAuthManager.currentUserId?.let { uid ->
                firestoreSyncManager.pushContact(uid, contact.copy(id = id))
            }
            withContext(Dispatchers.Main) {
                onCreated(id)
            }
        }
    }

    fun updateContact(contact: Contact) {
        viewModelScope.launch(Dispatchers.IO) {
            dhaarRepository.updateContact(contact)
            googleAuthManager.currentUserId?.let { uid ->
                firestoreSyncManager.pushContact(uid, contact)
            }
        }
    }

    fun deleteContact(contact: Contact, deleteEntries: Boolean = false, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = dhaarRepository.deleteContact(contact, deleteEntries)
            if (res.isSuccess) {
                googleAuthManager.currentUserId?.let { uid ->
                    firestoreSyncManager.deleteContact(uid, contact.uuid)
                }
            }
            withContext(Dispatchers.Main) {
                onResult(res)
            }
        }
    }

    fun addDhaarEntry(
        entry: DhaarEntry,
        linkToAccount: Boolean = false,
        accountName: String? = null,
        onCreated: (Long) -> Unit = {}
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = dhaarRepository.insertDhaarEntry(entry, linkToAccount, accountName)
            googleAuthManager.currentUserId?.let { uid ->
                val contact = database.contactDao().getContactByIdSync(entry.contactId)
                val contactUuid = contact?.uuid ?: ""
                firestoreSyncManager.pushDhaarEntry(uid, entry.copy(id = id), contactUuid)
            }
            withContext(Dispatchers.Main) {
                onCreated(id)
            }
        }
    }

    fun updateDhaarEntry(entry: DhaarEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            dhaarRepository.updateDhaarEntry(entry)
            googleAuthManager.currentUserId?.let { uid ->
                val contact = database.contactDao().getContactByIdSync(entry.contactId)
                val contactUuid = contact?.uuid ?: ""
                firestoreSyncManager.pushDhaarEntry(uid, entry, contactUuid)
            }
        }
    }

    fun deleteDhaarEntry(entry: DhaarEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            dhaarRepository.deleteDhaarEntry(entry)
            googleAuthManager.currentUserId?.let { uid ->
                firestoreSyncManager.deleteDhaarEntry(uid, entry.uuid)
            }
        }
    }

    fun deleteDhaarEntryById(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = database.dhaarEntryDao().getEntryByIdSync(id)
            dhaarRepository.deleteDhaarEntryById(id)
            if (existing != null) {
                googleAuthManager.currentUserId?.let { uid ->
                    firestoreSyncManager.deleteDhaarEntry(uid, existing.uuid)
                }
            }
        }
    }

    fun getEntriesForContact(contactId: Long): Flow<List<DhaarEntry>> =
        dhaarRepository.getEntriesForContact(contactId)

    fun getContactById(contactId: Long): Flow<Contact?> =
        dhaarRepository.getContactById(contactId)
}
