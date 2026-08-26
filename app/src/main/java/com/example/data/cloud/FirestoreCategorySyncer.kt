package com.example.data.cloud

import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.model.CategoryEntity
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirestoreCategorySyncer(
    private val firestore: FirebaseFirestore,
    private val database: AppDatabase
) {
    private val tag = "FirestoreCategorySyncer"

    // Upsert a remote category locally without ever creating a duplicate:
    // match by uuid first, then by (name+type); reuse the existing row id so
    // OnConflictStrategy.REPLACE performs an UPDATE instead of an INSERT.
    private suspend fun com.example.data.local.CategoryDao.upsertFromRemote(
        uuid: String,
        name: String,
        iconName: String,
        colorHex: String,
        type: String,
        isDefault: Boolean
    ) {
        val byUuid = getCategoryByUuid(uuid)
        val existing = byUuid ?: getCategoryByNameAndType(name, type)
        val entity = CategoryEntity(
            id = existing?.id ?: 0L,
            uuid = uuid,
            name = name,
            iconName = iconName,
            colorHex = colorHex,
            type = type,
            isDefault = isDefault
        )
        insertCategory(entity)
    }

    fun attachRealtimeListener(userId: String, scope: CoroutineScope): ListenerRegistration {
        return firestore.collection("users").document(userId)
            .collection("categories")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null && !snapshot.metadata.hasPendingWrites()) {
                    scope.launch(Dispatchers.IO) {
                        // Apply remote deletions first so removed docs are not resurrected locally
                        for (change in snapshot.documentChanges) {
                            if (change.type == com.google.firebase.firestore.DocumentChange.Type.REMOVED) {
                                runCatching { database.categoryDao().deleteCategoryByUuid(change.document.id) }
                            }
                        }
                        for (doc in snapshot.documents) {
                            try {
                                val data = doc.data ?: continue
                                val uuid = doc.id
                                database.categoryDao().upsertFromRemote(
                                    uuid = uuid,
                                    name = data["name"] as? String ?: "",
                                    iconName = data["iconName"] as? String ?: "category",
                                    colorHex = data["colorHex"] as? String ?: "#3B82F6",
                                    type = data["type"] as? String ?: "EXPENSE",
                                    isDefault = data["isDefault"] as? Boolean ?: false
                                )
                            } catch (e: Exception) {
                                Log.e(tag, "Error processing realtime category: ${e.message}")
                            }
                        }
                    }
                }
            }
    }

    suspend fun sync(userId: String) {
        val catCollection = firestore.collection("users").document(userId).collection("categories")
        val localCategories = database.categoryDao().getAllCategoriesSync()

        for (local in localCategories) {
            val docRef = catCollection.document(local.uuid)
            val data = hashMapOf(
                "uuid" to local.uuid,
                "name" to local.name,
                "iconName" to local.iconName,
                "colorHex" to local.colorHex,
                "type" to local.type,
                "isDefault" to local.isDefault,
                "updatedAt" to System.currentTimeMillis()
            )
            docRef.set(data, SetOptions.merge()).await()
        }

        val remoteDocs = catCollection.get(com.google.firebase.firestore.Source.SERVER).await().documents
        val remoteUuids = mutableSetOf<String>()
        for (doc in remoteDocs) {
            val data = doc.data ?: continue
            val uuid = doc.id
            remoteUuids.add(uuid)
            database.categoryDao().upsertFromRemote(
                uuid = uuid,
                name = data["name"] as? String ?: "",
                iconName = data["iconName"] as? String ?: "category",
                colorHex = data["colorHex"] as? String ?: "#3B82F6",
                type = data["type"] as? String ?: "EXPENSE",
                isDefault = data["isDefault"] as? Boolean ?: false
            )
        }

        // Delete local items that were removed from Firestore (prevent resurrection).
        // SAFETY: never run the delete pass when the server returned zero docs
        // while local data exists -- that would wipe local data on any failed/empty fetch.
        val shouldDeleteMissingLocals = remoteUuids.isNotEmpty() || localCategories.isEmpty()
        if (shouldDeleteMissingLocals) for (local in localCategories) {
            if (local.uuid.isNotBlank() && local.uuid !in remoteUuids) {
                runCatching { database.categoryDao().deleteCategoryByUuid(local.uuid) }
            }
        }
    }

    suspend fun pushCategory(userId: String, category: CategoryEntity) = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext
        try {
            val docRef = firestore.collection("users").document(userId).collection("categories").document(category.uuid)
            val data = hashMapOf(
                "uuid" to category.uuid,
                "name" to category.name,
                "iconName" to category.iconName,
                "colorHex" to category.colorHex,
                "type" to category.type,
                "isDefault" to category.isDefault,
                "updatedAt" to System.currentTimeMillis()
            )
            docRef.set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.e(tag, "Failed to push category to Firestore: ${e.message}")
        }
    }

    suspend fun deleteCategory(userId: String, uuid: String) = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext
        try {
            firestore.collection("users").document(userId).collection("categories").document(uuid).delete().await()
        } catch (e: Exception) {
            Log.e(tag, "Failed to delete category from Firestore: ${e.message}")
        }
    }
}
