package com.example.data.cloud

import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.model.BillEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirestoreBillSyncer(
    private val firestore: FirebaseFirestore,
    private val database: AppDatabase
) {
    private val tag = "FirestoreBillSyncer"

    fun attachRealtimeListener(userId: String, scope: CoroutineScope): ListenerRegistration {
        return firestore.collection("users").document(userId)
            .collection("bills")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null && !snapshot.metadata.hasPendingWrites()) {
                    scope.launch(Dispatchers.IO) {
                        // Apply remote deletions first so removed docs are not resurrected locally
                        for (change in snapshot.documentChanges) {
                            if (change.type == com.google.firebase.firestore.DocumentChange.Type.REMOVED) {
                                runCatching { database.billDao().deleteBillByUuid(change.document.id) }
                            }
                        }
                        for (doc in snapshot.documents) {
                            try {
                                val data = doc.data ?: continue
                                val uuid = doc.id
                                val existing = database.billDao().getBillByUuid(uuid)
                                val entity = BillEntity(
                                    id = existing?.id ?: 0L,
                                    uuid = uuid,
                                    title = data["title"] as? String ?: "",
                                    amount = (data["amount"] as? Number)?.toDouble() ?: 0.0,
                                    dueDate = (data["dueDate"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                                    frequency = data["frequency"] as? String ?: "MONTHLY",
                                    categoryId = (data["categoryId"] as? Number)?.toLong() ?: 0L,
                                    categoryName = data["categoryName"] as? String ?: "Utilities",
                                    accountId = (data["accountId"] as? Number)?.toLong(),
                                    isPaid = data["isPaid"] as? Boolean ?: false,
                                    lastPaidDate = (data["lastPaidDate"] as? Number)?.toLong(),
                                    autoLogTransaction = data["autoLogTransaction"] as? Boolean ?: true
                                )
                                database.billDao().insertBill(entity)
                            } catch (e: Exception) {
                                Log.e(tag, "Error processing realtime bill: ${e.message}")
                            }
                        }
                    }
                }
            }
    }

    suspend fun sync(userId: String) {
        val billCollection = firestore.collection("users").document(userId).collection("bills")
        val localBills = database.billDao().getAllBillsSync()

        for (local in localBills) {
            val docRef = billCollection.document(local.uuid)
            val data = hashMapOf(
                "uuid" to local.uuid,
                "title" to local.title,
                "amount" to local.amount,
                "dueDate" to local.dueDate,
                "frequency" to local.frequency,
                "categoryId" to local.categoryId,
                "categoryName" to local.categoryName,
                "accountId" to local.accountId,
                "isPaid" to local.isPaid,
                "lastPaidDate" to local.lastPaidDate,
                "autoLogTransaction" to local.autoLogTransaction,
                "updatedAt" to System.currentTimeMillis()
            )
            docRef.set(data, SetOptions.merge()).await()
        }

        val remoteDocs = billCollection.get().await().documents
        val remoteUuids = mutableSetOf<String>()
        for (doc in remoteDocs) {
            val data = doc.data ?: continue
            val uuid = doc.id
            remoteUuids.add(uuid)
            val existing = database.billDao().getBillByUuid(uuid)
            val entity = BillEntity(
                id = existing?.id ?: 0L,
                uuid = uuid,
                title = data["title"] as? String ?: "",
                amount = (data["amount"] as? Number)?.toDouble() ?: 0.0,
                dueDate = (data["dueDate"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                frequency = data["frequency"] as? String ?: "MONTHLY",
                categoryId = (data["categoryId"] as? Number)?.toLong() ?: 0L,
                categoryName = data["categoryName"] as? String ?: "Utilities",
                accountId = (data["accountId"] as? Number)?.toLong(),
                isPaid = data["isPaid"] as? Boolean ?: false,
                lastPaidDate = (data["lastPaidDate"] as? Number)?.toLong(),
                autoLogTransaction = data["autoLogTransaction"] as? Boolean ?: true
            )
            database.billDao().insertBill(entity)
        }

        // Delete local items that were removed from Firestore (prevent resurrection)
        for (local in localBills) {
            if (local.uuid.isNotBlank() && local.uuid !in remoteUuids) {
                runCatching { database.billDao().deleteBillByUuid(local.uuid) }
            }
        }
    }

    suspend fun pushBill(userId: String, bill: BillEntity) = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext
        try {
            val docRef = firestore.collection("users").document(userId).collection("bills").document(bill.uuid)
            val data = hashMapOf(
                "uuid" to bill.uuid,
                "title" to bill.title,
                "amount" to bill.amount,
                "dueDate" to bill.dueDate,
                "frequency" to bill.frequency,
                "categoryId" to bill.categoryId,
                "categoryName" to bill.categoryName,
                "accountId" to bill.accountId,
                "isPaid" to bill.isPaid,
                "lastPaidDate" to bill.lastPaidDate,
                "autoLogTransaction" to bill.autoLogTransaction,
                "updatedAt" to System.currentTimeMillis()
            )
            docRef.set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.e(tag, "Failed to push bill to Firestore: ${e.message}")
        }
    }

    suspend fun deleteBill(userId: String, uuid: String) = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext
        try {
            firestore.collection("users").document(userId).collection("bills").document(uuid).delete().await()
        } catch (e: Exception) {
            Log.e(tag, "Failed to delete bill from Firestore: ${e.message}")
        }
    }
}
