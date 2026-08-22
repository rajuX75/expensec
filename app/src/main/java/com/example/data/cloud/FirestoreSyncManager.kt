package com.example.data.cloud

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.model.*
import com.example.data.repository.UserPreferencesRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SyncState {
    IDLE,
    SYNCING,
    SUCCESS,
    ERROR
}

class FirestoreSyncManager(
    private val context: Context,
    private val database: AppDatabase,
    private val userPrefs: UserPreferencesRepository
) {
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val tag = "FirestoreSyncManager"

    val transactionSyncer = FirestoreTransactionSyncer(firestore, database)
    val categorySyncer = FirestoreCategorySyncer(firestore, database)
    val accountSyncer = FirestoreAccountSyncer(firestore, database)
    val budgetSyncer = FirestoreBudgetSyncer(firestore, database)
    val billSyncer = FirestoreBillSyncer(firestore, database)
    val dhaarSyncer = FirestoreDhaarSyncer(firestore, database)
    val shopBakiSyncer = FirestoreShopBakiSyncer(firestore, database)

    private val _syncState = MutableStateFlow(SyncState.IDLE)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    private val _lastSyncTimestamp = MutableStateFlow(0L)
    val lastSyncTimestamp: StateFlow<Long> = _lastSyncTimestamp.asStateFlow()

    private val activeListeners = mutableListOf<ListenerRegistration>()

    fun startRealtimeSync(userId: String, scope: CoroutineScope) {
        if (userId.isBlank()) return
        stopRealtimeSync()

        try {
            activeListeners.add(transactionSyncer.attachRealtimeListener(userId, scope))
            activeListeners.add(categorySyncer.attachRealtimeListener(userId, scope))
            activeListeners.add(accountSyncer.attachRealtimeListener(userId, scope))
            activeListeners.add(budgetSyncer.attachRealtimeListener(userId, scope))
            activeListeners.add(billSyncer.attachRealtimeListener(userId, scope))
            activeListeners.add(dhaarSyncer.attachContactsRealtimeListener(userId, scope))
            activeListeners.add(dhaarSyncer.attachEntriesRealtimeListener(userId, scope))
            activeListeners.add(shopBakiSyncer.attachShopsRealtimeListener(userId, scope))
            activeListeners.add(shopBakiSyncer.attachProductsRealtimeListener(userId, scope))
            activeListeners.add(shopBakiSyncer.attachEntriesRealtimeListener(userId, scope))
            
            // Add preferences realtime listener and set up two-way sync
            activeListeners.add(attachPreferencesRealtimeListener(userId, scope))

            Log.d(tag, "Started all realtime Firestore sync listeners for user: $userId")
        } catch (e: Exception) {
            Log.e(tag, "Failed to start realtime sync: ${e.message}")
        }
    }

    private var prefChangeListener: android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null

    private fun attachPreferencesRealtimeListener(userId: String, scope: CoroutineScope): ListenerRegistration {
        val docRef = firestore.collection("users").document(userId).collection("preferences").document("config")
        
        // Initial sync: fetch from cloud and apply if exists, else upload local defaults
        scope.launch(Dispatchers.IO) {
            try {
                val snapshot = docRef.get().kotlinx.coroutines.tasks.await()
                if (snapshot.exists()) {
                    snapshot.data?.let { userPrefs.restorePrefs(it) }
                } else {
                    docRef.set(userPrefs.getAllPrefs()).kotlinx.coroutines.tasks.await()
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to sync preferences initially", e)
            }
        }

        // Listen for local changes and push to cloud
        prefChangeListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            scope.launch(Dispatchers.IO) {
                try {
                    docRef.set(userPrefs.getAllPrefs()).kotlinx.coroutines.tasks.await()
                } catch (e: Exception) {
                    Log.e(tag, "Failed to upload preferences", e)
                }
            }
        }
        userPrefs.registerPrefChangeListener(prefChangeListener!!)

        // Listen for cloud changes (from other devices) and apply locally
        return docRef.addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            if (snapshot != null && snapshot.exists() && !snapshot.metadata.hasPendingWrites()) {
                snapshot.data?.let { userPrefs.restorePrefs(it) }
            }
        }
    }

    fun stopRealtimeSync() {
        prefChangeListener?.let { userPrefs.unregisterPrefChangeListener(it) }
        prefChangeListener = null

        for (listener in activeListeners) {
            try {
                listener.remove()
            } catch (_: Exception) {}
        }
        activeListeners.clear()
    }

    suspend fun syncAll(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        if (userId.isBlank()) {
            return@withContext Result.failure(Exception("No signed-in user found."))
        }
        _syncState.value = SyncState.SYNCING
        _syncMessage.value = "Syncing with Firestore..."

        runCatching {
            categorySyncer.sync(userId)
            transactionSyncer.sync(userId)
            accountSyncer.sync(userId)
            budgetSyncer.sync(userId)
            billSyncer.sync(userId)
            dhaarSyncer.sync(userId)
            shopBakiSyncer.sync(userId)

            val now = System.currentTimeMillis()
            _lastSyncTimestamp.value = now
            _syncState.value = SyncState.SUCCESS
            _syncMessage.value = "Synced successfully with Firebase Firestore"
            Log.d(tag, "Completed full Firestore synchronization for user: $userId")
            Unit
        }.onFailure { e ->
            Log.e(tag, "Firestore sync failed: ${e.message}", e)
            _syncState.value = SyncState.ERROR
            _syncMessage.value = e.message ?: "Failed to sync with Firebase"
        }
    }

    // Direct Mutation Helpers (delegated to entity syncers)
    suspend fun pushTransaction(userId: String, transaction: TransactionEntity) =
        transactionSyncer.pushTransaction(userId, transaction)

    suspend fun deleteTransaction(userId: String, uuid: String) =
        transactionSyncer.deleteTransaction(userId, uuid)

    suspend fun pushCategory(userId: String, category: CategoryEntity) =
        categorySyncer.pushCategory(userId, category)

    suspend fun deleteCategory(userId: String, uuid: String) =
        categorySyncer.deleteCategory(userId, uuid)

    suspend fun pushAccount(userId: String, account: AccountEntity) =
        accountSyncer.pushAccount(userId, account)

    suspend fun deleteAccount(userId: String, uuid: String) =
        accountSyncer.deleteAccount(userId, uuid)

    suspend fun pushBudget(userId: String, budget: BudgetEntity) =
        budgetSyncer.pushBudget(userId, budget)

    suspend fun deleteBudget(userId: String, uuid: String) =
        budgetSyncer.deleteBudget(userId, uuid)

    suspend fun pushBill(userId: String, bill: BillEntity) =
        billSyncer.pushBill(userId, bill)

    suspend fun deleteBill(userId: String, uuid: String) =
        billSyncer.deleteBill(userId, uuid)

    suspend fun pushContact(userId: String, contact: Contact) =
        dhaarSyncer.pushContact(userId, contact)

    suspend fun deleteContact(userId: String, uuid: String) =
        dhaarSyncer.deleteContact(userId, uuid)

    suspend fun pushDhaarEntry(userId: String, entry: DhaarEntry, contactUuid: String) =
        dhaarSyncer.pushDhaarEntry(userId, entry, contactUuid)

    suspend fun deleteDhaarEntry(userId: String, uuid: String) =
        dhaarSyncer.deleteDhaarEntry(userId, uuid)
        
    suspend fun pushShop(userId: String, shop: Shop) =
        shopBakiSyncer.pushShop(userId, shop)

    suspend fun deleteShop(userId: String, uuid: String) =
        shopBakiSyncer.deleteShop(userId, uuid)
        
    suspend fun pushShopProduct(userId: String, product: ShopProduct) =
        shopBakiSyncer.pushProduct(userId, product)

    suspend fun deleteShopProduct(userId: String, uuid: String) =
        shopBakiSyncer.deleteProduct(userId, uuid)

    suspend fun pushShopLedgerEntry(userId: String, entry: ShopLedgerEntry, shopUuid: String, productUuid: String?) =
        shopBakiSyncer.pushLedgerEntry(userId, entry, shopUuid, productUuid)

    suspend fun deleteShopLedgerEntry(userId: String, uuid: String) =
        shopBakiSyncer.deleteLedgerEntry(userId, uuid)
}
