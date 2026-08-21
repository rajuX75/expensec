package com.example.data.cloud

import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.model.TransactionEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirestoreTransactionSyncer(
    private val firestore: FirebaseFirestore,
    private val database: AppDatabase
) {
    private val tag = "FirestoreTransactionSyncer"

    fun attachRealtimeListener(userId: String, scope: CoroutineScope): ListenerRegistration {
        return firestore.collection("users").document(userId)
            .collection("transactions")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(tag, "Realtime transactions listen error: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null && !snapshot.metadata.hasPendingWrites()) {
                    scope.launch(Dispatchers.IO) {
                        // Apply remote deletions first so removed docs are not resurrected locally
                        for (change in snapshot.documentChanges) {
                            if (change.type == com.google.firebase.firestore.DocumentChange.Type.REMOVED) {
                                runCatching { database.transactionDao().deleteTransactionByUuid(change.document.id) }
                            }
                        }
                        for (doc in snapshot.documents) {
                            try {
                                val data = doc.data ?: continue
                                val uuid = doc.id
                                val existing = database.transactionDao().getTransactionByUuid(uuid)
                                val entity = TransactionEntity(
                                    id = existing?.id ?: 0L,
                                    uuid = uuid,
                                    type = data["type"] as? String ?: "EXPENSE",
                                    amount = (data["amount"] as? Number)?.toDouble() ?: 0.0,
                                    currency = data["currency"] as? String ?: "USD",
                                    categoryId = (data["categoryId"] as? Number)?.toLong() ?: 0L,
                                    categoryName = data["categoryName"] as? String ?: "General",
                                    categoryIcon = data["categoryIcon"] as? String ?: "category",
                                    categoryColorHex = data["categoryColorHex"] as? String ?: "#64748B",
                                    accountId = (data["accountId"] as? Number)?.toLong() ?: 1L,
                                    accountName = data["accountName"] as? String ?: "Main Account",
                                    toAccountId = (data["toAccountId"] as? Number)?.toLong(),
                                    toAccountName = data["toAccountName"] as? String,
                                    date = (data["date"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                                    note = data["note"] as? String ?: "",
                                    merchant = data["merchant"] as? String ?: "",
                                    paymentMethod = data["paymentMethod"] as? String ?: "Card",
                                    receiptUri = data["receiptUri"] as? String,
                                    tags = data["tags"] as? String ?: "",
                                    isRecurring = data["isRecurring"] as? Boolean ?: false,
                                    recurringPeriod = data["recurringPeriod"] as? String
                                )
                                database.transactionDao().insertTransaction(entity)
                            } catch (e: Exception) {
                                Log.e(tag, "Error processing realtime transaction doc: ${e.message}")
                            }
                        }
                    }
                }
            }
    }

    suspend fun sync(userId: String) {
        val transCollection = firestore.collection("users").document(userId).collection("transactions")
        val localTransactions = database.transactionDao().getAllTransactionsSync()

        // 1. Upload local transactions to Firestore
        for (local in localTransactions) {
            val docRef = transCollection.document(local.uuid)
            val data = hashMapOf(
                "uuid" to local.uuid,
                "type" to local.type,
                "amount" to local.amount,
                "currency" to local.currency,
                "categoryId" to local.categoryId,
                "categoryName" to local.categoryName,
                "categoryIcon" to local.categoryIcon,
                "categoryColorHex" to local.categoryColorHex,
                "accountId" to local.accountId,
                "accountName" to local.accountName,
                "toAccountId" to local.toAccountId,
                "toAccountName" to local.toAccountName,
                "date" to local.date,
                "note" to local.note,
                "merchant" to local.merchant,
                "paymentMethod" to local.paymentMethod,
                "receiptUri" to local.receiptUri,
                "tags" to local.tags,
                "isRecurring" to local.isRecurring,
                "recurringPeriod" to local.recurringPeriod,
                "updatedAt" to System.currentTimeMillis()
            )
            docRef.set(data, SetOptions.merge()).await()
        }

        // 2. Fetch remote transactions and merge to Room
        val remoteDocs = transCollection.get(com.google.firebase.firestore.Source.SERVER).await().documents
        val remoteUuids = mutableSetOf<String>()
        for (doc in remoteDocs) {
            val data = doc.data ?: continue
            val uuid = doc.id
            remoteUuids.add(uuid)
            val existing = database.transactionDao().getTransactionByUuid(uuid)
            val entity = TransactionEntity(
                id = existing?.id ?: 0L,
                uuid = uuid,
                type = data["type"] as? String ?: "EXPENSE",
                amount = (data["amount"] as? Number)?.toDouble() ?: 0.0,
                currency = data["currency"] as? String ?: "USD",
                categoryId = (data["categoryId"] as? Number)?.toLong() ?: 0L,
                categoryName = data["categoryName"] as? String ?: "General",
                categoryIcon = data["categoryIcon"] as? String ?: "category",
                categoryColorHex = data["categoryColorHex"] as? String ?: "#64748B",
                accountId = (data["accountId"] as? Number)?.toLong() ?: 1L,
                accountName = data["accountName"] as? String ?: "Main Account",
                toAccountId = (data["toAccountId"] as? Number)?.toLong(),
                toAccountName = data["toAccountName"] as? String,
                date = (data["date"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                note = data["note"] as? String ?: "",
                merchant = data["merchant"] as? String ?: "",
                paymentMethod = data["paymentMethod"] as? String ?: "Card",
                receiptUri = data["receiptUri"] as? String,
                tags = data["tags"] as? String ?: "",
                isRecurring = data["isRecurring"] as? Boolean ?: false,
                recurringPeriod = data["recurringPeriod"] as? String
            )
            database.transactionDao().insertTransaction(entity)
        }

        // 3. Delete local items that were removed from Firestore (prevent resurrection).
        // SAFETY: never run the delete pass when the server returned zero docs while local
        // data exists -- that would wipe all local data on any failed/empty fetch.
        val shouldDeleteMissingLocals = remoteUuids.isNotEmpty() || localTransactions.isEmpty()
        if (shouldDeleteMissingLocals) for (local in localTransactions) {
            if (local.uuid.isNotBlank() && local.uuid !in remoteUuids) {
                runCatching { database.transactionDao().deleteTransactionByUuid(local.uuid) }
            }
        }
    }

    suspend fun pushTransaction(userId: String, transaction: TransactionEntity) = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext
        try {
            val docRef = firestore.collection("users").document(userId).collection("transactions").document(transaction.uuid)
            val data = hashMapOf(
                "uuid" to transaction.uuid,
                "type" to transaction.type,
                "amount" to transaction.amount,
                "currency" to transaction.currency,
                "categoryId" to transaction.categoryId,
                "categoryName" to transaction.categoryName,
                "categoryIcon" to transaction.categoryIcon,
                "categoryColorHex" to transaction.categoryColorHex,
                "accountId" to transaction.accountId,
                "accountName" to transaction.accountName,
                "toAccountId" to transaction.toAccountId,
                "toAccountName" to transaction.toAccountName,
                "date" to transaction.date,
                "note" to transaction.note,
                "merchant" to transaction.merchant,
                "paymentMethod" to transaction.paymentMethod,
                "receiptUri" to transaction.receiptUri,
                "tags" to transaction.tags,
                "isRecurring" to transaction.isRecurring,
                "recurringPeriod" to transaction.recurringPeriod,
                "updatedAt" to System.currentTimeMillis()
            )
            docRef.set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.e(tag, "Failed to push transaction to Firestore: ${e.message}")
        }
    }

    suspend fun deleteTransaction(userId: String, uuid: String) = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext
        try {
            firestore.collection("users").document(userId).collection("transactions").document(uuid).delete().await()
        } catch (e: Exception) {
            Log.e(tag, "Failed to delete transaction from Firestore: ${e.message}")
        }
    }
}
