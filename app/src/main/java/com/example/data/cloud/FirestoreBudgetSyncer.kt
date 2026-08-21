package com.example.data.cloud

import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.model.BudgetEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirestoreBudgetSyncer(
    private val firestore: FirebaseFirestore,
    private val database: AppDatabase
) {
    private val tag = "FirestoreBudgetSyncer"

    fun attachRealtimeListener(userId: String, scope: CoroutineScope): ListenerRegistration {
        return firestore.collection("users").document(userId)
            .collection("budgets")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null && !snapshot.metadata.hasPendingWrites()) {
                    scope.launch(Dispatchers.IO) {
                        // Apply remote deletions first so removed docs are not resurrected locally
                        for (change in snapshot.documentChanges) {
                            if (change.type == com.google.firebase.firestore.DocumentChange.Type.REMOVED) {
                                runCatching { database.budgetDao().deleteBudgetByUuid(change.document.id) }
                            }
                        }
                        for (doc in snapshot.documents) {
                            try {
                                val data = doc.data ?: continue
                                val uuid = doc.id
                                val existing = database.budgetDao().getBudgetByUuid(uuid)
                                val entity = BudgetEntity(
                                    id = existing?.id ?: 0L,
                                    uuid = uuid,
                                    categoryId = (data["categoryId"] as? Number)?.toLong(),
                                    categoryName = data["categoryName"] as? String,
                                    amountLimit = (data["amountLimit"] as? Number)?.toDouble() ?: 0.0,
                                    period = data["period"] as? String ?: "MONTHLY",
                                    startDate = (data["startDate"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                                    alertThresholdPercent = (data["alertThresholdPercent"] as? Number)?.toInt() ?: 80
                                )
                                database.budgetDao().insertBudget(entity)
                            } catch (e: Exception) {
                                Log.e(tag, "Error processing realtime budget: ${e.message}")
                            }
                        }
                    }
                }
            }
    }

    suspend fun sync(userId: String) {
        val budgetCollection = firestore.collection("users").document(userId).collection("budgets")
        val localBudgets = database.budgetDao().getAllBudgetsSync()

        for (local in localBudgets) {
            val docRef = budgetCollection.document(local.uuid)
            val data = hashMapOf(
                "uuid" to local.uuid,
                "categoryId" to local.categoryId,
                "categoryName" to local.categoryName,
                "amountLimit" to local.amountLimit,
                "period" to local.period,
                "startDate" to local.startDate,
                "alertThresholdPercent" to local.alertThresholdPercent,
                "updatedAt" to System.currentTimeMillis()
            )
            docRef.set(data, SetOptions.merge()).await()
        }

        val remoteDocs = budgetCollection.get(com.google.firebase.firestore.Source.SERVER).await().documents
        val remoteUuids = mutableSetOf<String>()
        for (doc in remoteDocs) {
            val data = doc.data ?: continue
            val uuid = doc.id
            remoteUuids.add(uuid)
            val existing = database.budgetDao().getBudgetByUuid(uuid)
            val entity = BudgetEntity(
                id = existing?.id ?: 0L,
                uuid = uuid,
                categoryId = (data["categoryId"] as? Number)?.toLong(),
                categoryName = data["categoryName"] as? String,
                amountLimit = (data["amountLimit"] as? Number)?.toDouble() ?: 0.0,
                period = data["period"] as? String ?: "MONTHLY",
                startDate = (data["startDate"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                alertThresholdPercent = (data["alertThresholdPercent"] as? Number)?.toInt() ?: 80
            )
            database.budgetDao().insertBudget(entity)
        }

        // Delete local items that were removed from Firestore (prevent resurrection).
        // SAFETY: never run the delete pass when the server returned zero docs
        // while local data exists -- that would wipe local data on any failed/empty fetch.
        val shouldDeleteMissingLocals = remoteUuids.isNotEmpty() || localBudgets.isEmpty()
        if (shouldDeleteMissingLocals) for (local in localBudgets) {
            if (local.uuid.isNotBlank() && local.uuid !in remoteUuids) {
                runCatching { database.budgetDao().deleteBudgetByUuid(local.uuid) }
            }
        }
    }

    suspend fun pushBudget(userId: String, budget: BudgetEntity) = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext
        try {
            val docRef = firestore.collection("users").document(userId).collection("budgets").document(budget.uuid)
            val data = hashMapOf(
                "uuid" to budget.uuid,
                "categoryId" to budget.categoryId,
                "categoryName" to budget.categoryName,
                "amountLimit" to budget.amountLimit,
                "period" to budget.period,
                "startDate" to budget.startDate,
                "alertThresholdPercent" to budget.alertThresholdPercent,
                "updatedAt" to System.currentTimeMillis()
            )
            docRef.set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.e(tag, "Failed to push budget to Firestore: ${e.message}")
        }
    }

    suspend fun deleteBudget(userId: String, uuid: String) = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext
        try {
            firestore.collection("users").document(userId).collection("budgets").document(uuid).delete().await()
        } catch (e: Exception) {
            Log.e(tag, "Failed to delete budget from Firestore: ${e.message}")
        }
    }
}
