package com.example.data.cloud

import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.model.AccountEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirestoreAccountSyncer(
    private val firestore: FirebaseFirestore,
    private val database: AppDatabase
) {
    private val tag = "FirestoreAccountSyncer"

    fun attachRealtimeListener(userId: String, scope: CoroutineScope): ListenerRegistration {
        return firestore.collection("users").document(userId)
            .collection("accounts")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null && !snapshot.metadata.hasPendingWrites()) {
                    scope.launch(Dispatchers.IO) {
                        // Apply remote deletions first so removed docs are not resurrected locally
                        for (change in snapshot.documentChanges) {
                            if (change.type == com.google.firebase.firestore.DocumentChange.Type.REMOVED) {
                                runCatching { database.accountDao().deleteAccountByUuid(change.document.id) }
                            }
                        }
                        for (doc in snapshot.documents) {
                            try {
                                val data = doc.data ?: continue
                                val uuid = doc.id
                                val existing = database.accountDao().getAccountByUuid(uuid)
                                val entity = AccountEntity(
                                    id = existing?.id ?: 0L,
                                    uuid = uuid,
                                    name = data["name"] as? String ?: "",
                                    type = data["type"] as? String ?: "BANK",
                                    openingBalance = (data["balance"] as? Number)?.toDouble() ?: 0.0,
                                    currency = data["currency"] as? String ?: "USD",
                                    colorHex = data["colorHex"] as? String ?: "#00875A",
                                    iconName = data["iconName"] as? String ?: "account_balance"
                                )
                                database.accountDao().insertAccount(entity)
                            } catch (e: Exception) {
                                Log.e(tag, "Error processing realtime account: ${e.message}")
                            }
                        }
                    }
                }
            }
    }

    suspend fun sync(userId: String) {
        val accCollection = firestore.collection("users").document(userId).collection("accounts")
        val localAccounts = database.accountDao().getAllAccountsSync()

        for (local in localAccounts) {
            val docRef = accCollection.document(local.uuid)
            val data = hashMapOf(
                "uuid" to local.uuid,
                "name" to local.name,
                "type" to local.type,
                "balance" to local.openingBalance,
                "currency" to local.currency,
                "colorHex" to local.colorHex,
                "iconName" to local.iconName,
                "updatedAt" to System.currentTimeMillis()
            )
            docRef.set(data, SetOptions.merge()).await()
        }

        val remoteDocs = accCollection.get(com.google.firebase.firestore.Source.SERVER).await().documents
        val remoteUuids = mutableSetOf<String>()
        for (doc in remoteDocs) {
            val data = doc.data ?: continue
            val uuid = doc.id
            remoteUuids.add(uuid)
            val existing = database.accountDao().getAccountByUuid(uuid)
            val entity = AccountEntity(
                id = existing?.id ?: 0L,
                uuid = uuid,
                name = data["name"] as? String ?: "",
                type = data["type"] as? String ?: "BANK",
                openingBalance = (data["balance"] as? Number)?.toDouble() ?: 0.0,
                currency = data["currency"] as? String ?: "USD",
                colorHex = data["colorHex"] as? String ?: "#00875A",
                iconName = data["iconName"] as? String ?: "account_balance"
            )
            database.accountDao().insertAccount(entity)
        }

        // Delete local items that were removed from Firestore (prevent resurrection).
        // SAFETY: never run the delete pass when the server returned zero docs
        // while local data exists -- that would wipe local data on any failed/empty fetch.
        val shouldDeleteMissingLocals = remoteUuids.isNotEmpty() || localAccounts.isEmpty()
        if (shouldDeleteMissingLocals) for (local in localAccounts) {
            if (local.uuid.isNotBlank() && local.uuid !in remoteUuids) {
                runCatching { database.accountDao().deleteAccountByUuid(local.uuid) }
            }
        }
    }

    suspend fun pushAccount(userId: String, account: AccountEntity) = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext
        try {
            val docRef = firestore.collection("users").document(userId).collection("accounts").document(account.uuid)
            val data = hashMapOf(
                "uuid" to account.uuid,
                "name" to account.name,
                "type" to account.type,
                "balance" to account.openingBalance,
                "currency" to account.currency,
                "colorHex" to account.colorHex,
                "iconName" to account.iconName,
                "updatedAt" to System.currentTimeMillis()
            )
            docRef.set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.e(tag, "Failed to push account to Firestore: ${e.message}")
        }
    }

    suspend fun deleteAccount(userId: String, uuid: String) = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext
        try {
            firestore.collection("users").document(userId).collection("accounts").document(uuid).delete().await()
        } catch (e: Exception) {
            Log.e(tag, "Failed to delete account from Firestore: ${e.message}")
        }
    }
}
