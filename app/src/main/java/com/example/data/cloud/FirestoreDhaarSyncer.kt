package com.example.data.cloud

import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.model.Contact
import com.example.data.model.DhaarEntry
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirestoreDhaarSyncer(
    private val firestore: FirebaseFirestore,
    private val database: AppDatabase
) {
    private val tag = "FirestoreDhaarSyncer"

    fun attachContactsRealtimeListener(userId: String, scope: CoroutineScope): ListenerRegistration {
        return firestore.collection("users").document(userId)
            .collection("contacts")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null && !snapshot.metadata.hasPendingWrites()) {
                    scope.launch(Dispatchers.IO) {
                        // Apply remote deletions first so removed docs are not resurrected locally
                        for (change in snapshot.documentChanges) {
                            if (change.type == com.google.firebase.firestore.DocumentChange.Type.REMOVED) {
                                runCatching { database.contactDao().deleteContactByUuid(change.document.id) }
                            }
                        }
                        for (doc in snapshot.documents) {
                            try {
                                val data = doc.data ?: continue
                                val uuid = doc.id
                                val existing = database.contactDao().getContactByUuid(uuid)
                                val contact = Contact(
                                    id = existing?.id ?: 0L,
                                    uuid = uuid,
                                    name = data["name"] as? String ?: "",
                                    phoneNumber = data["phoneNumber"] as? String,
                                    photoUri = data["photoUri"] as? String,
                                    createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                                )
                                // CRITICAL FIX (FK error 1811): update existing rows to preserve
                                // the local id that dhaar_entries references via foreign key.
                                if (existing != null) {
                                    database.contactDao().updateContact(contact)
                                } else {
                                    database.contactDao().insertContact(contact)
                                }
                            } catch (e: Exception) {
                                Log.e(tag, "Error processing realtime contact: ${e.message}")
                            }
                        }
                    }
                }
            }
    }

    fun attachEntriesRealtimeListener(userId: String, scope: CoroutineScope): ListenerRegistration {
        return firestore.collection("users").document(userId)
            .collection("dhaar_entries")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null && !snapshot.metadata.hasPendingWrites()) {
                    scope.launch(Dispatchers.IO) {
                        // Apply remote deletions first so removed docs are not resurrected locally
                        for (change in snapshot.documentChanges) {
                            if (change.type == com.google.firebase.firestore.DocumentChange.Type.REMOVED) {
                                runCatching { database.dhaarEntryDao().deleteEntryByUuid(change.document.id) }
                            }
                        }
                        for (doc in snapshot.documents) {
                            try {
                                val data = doc.data ?: continue
                                val uuid = doc.id
                                val contactUuid = data["contactUuid"] as? String ?: ""
                                // Retry lookup — contacts listener may not have inserted yet
                                var localContact = database.contactDao().getContactByUuid(contactUuid)
                                if (localContact == null && contactUuid.isNotBlank()) {
                                    for (attempt in 1..3) {
                                        delay(500L)
                                        localContact = database.contactDao().getContactByUuid(contactUuid)
                                        if (localContact != null) break
                                    }
                                }
                                if (localContact == null) {
                                    Log.w(tag, "Skipping dhaar entry $uuid: contact $contactUuid not found locally after retries")
                                    continue
                                }

                                val existing = database.dhaarEntryDao().getEntryByUuid(uuid)
                                val entry = DhaarEntry(
                                    id = existing?.id ?: 0L,
                                    uuid = uuid,
                                    contactId = localContact.id,
                                    type = data["type"] as? String ?: "GIVEN",
                                    amount = (data["amount"] as? Number)?.toDouble() ?: 0.0,
                                    currencyCode = data["currencyCode"] as? String ?: "USD",
                                    date = (data["date"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                                    dueDate = (data["dueDate"] as? Number)?.toLong(),
                                    note = data["note"] as? String ?: "",
                                    tagPhotoUri = data["tagPhotoUri"] as? String,
                                    linkedAccountId = (data["linkedAccountId"] as? Number)?.toLong(),
                                    isSettlementGive = data["isSettlementGive"] as? Boolean
                                )
                                database.dhaarEntryDao().insertEntry(entry)
                            } catch (e: Exception) {
                                Log.e(tag, "Error processing realtime dhaar entry: ${e.message}")
                            }
                        }
                    }
                }
            }
    }

    suspend fun sync(userId: String) {
        val contactsCollection = firestore.collection("users").document(userId).collection("contacts")
        val entriesCollection = firestore.collection("users").document(userId).collection("dhaar_entries")

        // --- Sync Contacts first (so entries can resolve contactUuid → local id) ---
        val localContacts = database.contactDao().getAllContactsSync()
        for (contact in localContacts) {
            val docRef = contactsCollection.document(contact.uuid)
            val data = hashMapOf(
                "uuid" to contact.uuid,
                "name" to contact.name,
                "phoneNumber" to contact.phoneNumber,
                "photoUri" to contact.photoUri,
                "createdAt" to contact.createdAt,
                "updatedAt" to System.currentTimeMillis()
            )
            docRef.set(data, SetOptions.merge()).await()
        }

        val remoteContacts = contactsCollection.get().await().documents
        val remoteContactUuids = mutableSetOf<String>()
        for (doc in remoteContacts) {
            val data = doc.data ?: continue
            val uuid = doc.id
            remoteContactUuids.add(uuid)
            val existing = database.contactDao().getContactByUuid(uuid)
            val contact = Contact(
                id = existing?.id ?: 0L,
                uuid = uuid,
                name = data["name"] as? String ?: "",
                phoneNumber = data["phoneNumber"] as? String,
                photoUri = data["photoUri"] as? String,
                createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
            // CRITICAL FIX (FK error 1811): use UPDATE for existing rows instead of REPLACE.
            if (existing != null) {
                database.contactDao().updateContact(contact)
            } else {
                database.contactDao().insertContact(contact)
            }
        }

        // Delete local contacts that were removed from Firestore (prevent resurrection)
        // Only delete contacts without local dhaar entries to avoid FK constraint errors
        for (local in localContacts) {
            if (local.uuid.isNotBlank() && local.uuid !in remoteContactUuids) {
                val entryCount = database.dhaarEntryDao().getEntryCountForContact(local.id)
                if (entryCount == 0) {
                    runCatching { database.contactDao().deleteContactByUuid(local.uuid) }
                }
            }
        }

        // --- Then sync Dhaar Entries (contacts are now guaranteed to be in Room) ---
        val localEntries = database.dhaarEntryDao().getAllEntriesSync()
        for (entry in localEntries) {
            val contact = database.contactDao().getContactByIdSync(entry.contactId)
            val contactUuid = contact?.uuid ?: ""
            val docRef = entriesCollection.document(entry.uuid)
            val data = hashMapOf(
                "uuid" to entry.uuid,
                "contactUuid" to contactUuid,
                "type" to entry.type,
                "amount" to entry.amount,
                "currencyCode" to entry.currencyCode,
                "date" to entry.date,
                "dueDate" to entry.dueDate,
                "note" to entry.note,
                "tagPhotoUri" to entry.tagPhotoUri,
                "linkedAccountId" to entry.linkedAccountId,
                "isSettlementGive" to entry.isSettlementGive,
                "updatedAt" to System.currentTimeMillis()
            )
            docRef.set(data, SetOptions.merge()).await()
        }

        val remoteEntries = entriesCollection.get().await().documents
        val remoteEntryUuids = mutableSetOf<String>()
        for (doc in remoteEntries) {
            val data = doc.data ?: continue
            val uuid = doc.id
            remoteEntryUuids.add(uuid)
            val contactUuid = data["contactUuid"] as? String ?: ""
            val localContact = database.contactDao().getContactByUuid(contactUuid) ?: continue

            val existing = database.dhaarEntryDao().getEntryByUuid(uuid)
            val entry = DhaarEntry(
                id = existing?.id ?: 0L,
                uuid = uuid,
                contactId = localContact.id,
                type = data["type"] as? String ?: "GIVEN",
                amount = (data["amount"] as? Number)?.toDouble() ?: 0.0,
                currencyCode = data["currencyCode"] as? String ?: "USD",
                date = (data["date"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                dueDate = (data["dueDate"] as? Number)?.toLong(),
                note = data["note"] as? String ?: "",
                tagPhotoUri = data["tagPhotoUri"] as? String,
                linkedAccountId = (data["linkedAccountId"] as? Number)?.toLong(),
                isSettlementGive = data["isSettlementGive"] as? Boolean
            )
            database.dhaarEntryDao().insertEntry(entry)
        }

        // Delete local entries that were removed from Firestore (prevent resurrection)
        for (local in localEntries) {
            if (local.uuid.isNotBlank() && local.uuid !in remoteEntryUuids) {
                runCatching { database.dhaarEntryDao().deleteEntryByUuid(local.uuid) }
            }
        }
    }

    suspend fun pushContact(userId: String, contact: Contact) = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext
        try {
            val docRef = firestore.collection("users").document(userId).collection("contacts").document(contact.uuid)
            val data = hashMapOf(
                "uuid" to contact.uuid,
                "name" to contact.name,
                "phoneNumber" to contact.phoneNumber,
                "photoUri" to contact.photoUri,
                "createdAt" to contact.createdAt,
                "updatedAt" to System.currentTimeMillis()
            )
            docRef.set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.e(tag, "Failed to push contact to Firestore: ${e.message}")
        }
    }

    suspend fun deleteContact(userId: String, uuid: String) = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext
        try {
            firestore.collection("users").document(userId).collection("contacts").document(uuid).delete().await()
        } catch (e: Exception) {
            Log.e(tag, "Failed to delete contact from Firestore: ${e.message}")
        }
    }

    suspend fun pushDhaarEntry(userId: String, entry: DhaarEntry, contactUuid: String) = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext
        try {
            val docRef = firestore.collection("users").document(userId).collection("dhaar_entries").document(entry.uuid)
            val data = hashMapOf(
                "uuid" to entry.uuid,
                "contactUuid" to contactUuid,
                "type" to entry.type,
                "amount" to entry.amount,
                "currencyCode" to entry.currencyCode,
                "date" to entry.date,
                "dueDate" to entry.dueDate,
                "note" to entry.note,
                "tagPhotoUri" to entry.tagPhotoUri,
                "linkedAccountId" to entry.linkedAccountId,
                "isSettlementGive" to entry.isSettlementGive,
                "updatedAt" to System.currentTimeMillis()
            )
            docRef.set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.e(tag, "Failed to push dhaar entry to Firestore: ${e.message}")
        }
    }

    suspend fun deleteDhaarEntry(userId: String, uuid: String) = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext
        try {
            firestore.collection("users").document(userId).collection("dhaar_entries").document(uuid).delete().await()
        } catch (e: Exception) {
            Log.e(tag, "Failed to delete dhaar entry from Firestore: ${e.message}")
        }
    }
}
