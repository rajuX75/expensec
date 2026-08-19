package com.example.data.cloud

import android.content.Context
import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.model.*
import com.example.data.repository.UserPreferencesRepository
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await

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
            // Realtime listener for transactions
            val transListener = firestore.collection("users").document(userId)
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
            activeListeners.add(transListener)

            // Realtime listener for categories
            val catListener = firestore.collection("users").document(userId)
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
                                    val existing = database.categoryDao().getCategoryByUuid(uuid)
                                    val entity = CategoryEntity(
                                        id = existing?.id ?: 0L,
                                        uuid = uuid,
                                        name = data["name"] as? String ?: "",
                                        iconName = data["iconName"] as? String ?: "category",
                                        colorHex = data["colorHex"] as? String ?: "#3B82F6",
                                        type = data["type"] as? String ?: "EXPENSE",
                                        isDefault = data["isDefault"] as? Boolean ?: false
                                    )
                                    database.categoryDao().insertCategory(entity)
                                } catch (e: Exception) {
                                    Log.e(tag, "Error processing realtime category: ${e.message}")
                                }
                            }
                        }
                    }
                }
            activeListeners.add(catListener)

            // Realtime listener for accounts
            val accListener = firestore.collection("users").document(userId)
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
                                        balance = (data["balance"] as? Number)?.toDouble() ?: 0.0,
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
            activeListeners.add(accListener)

            // Realtime listener for budgets
            val budgetListener = firestore.collection("users").document(userId)
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
            activeListeners.add(budgetListener)

            // Realtime listener for bills
            val billListener = firestore.collection("users").document(userId)
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
            activeListeners.add(billListener)

            // Realtime listener for contacts
            val contactListener = firestore.collection("users").document(userId)
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
            activeListeners.add(contactListener)

            // Realtime listener for dhaar entries
            val entryListener = firestore.collection("users").document(userId)
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
            activeListeners.add(entryListener)

            Log.d(tag, "Started all realtime Firestore sync listeners for user: $userId")
        } catch (e: Exception) {
            Log.e(tag, "Failed to start realtime sync: ${e.message}")
        }
    }

    fun stopRealtimeSync() {
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
            syncCategoriesInternal(userId)
            syncTransactionsInternal(userId)
            syncAccountsInternal(userId)
            syncBudgetsInternal(userId)
            syncBillsInternal(userId)
            syncDhaarInternal(userId)

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

    private suspend fun syncTransactionsInternal(userId: String) {
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
        val remoteDocs = transCollection.get().await().documents
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

        // 3. Delete local items that were removed from Firestore (prevent resurrection)
        for (local in localTransactions) {
            if (local.uuid.isNotBlank() && local.uuid !in remoteUuids) {
                runCatching { database.transactionDao().deleteTransactionByUuid(local.uuid) }
            }
        }
    }

    private suspend fun syncCategoriesInternal(userId: String) {
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

        val remoteDocs = catCollection.get().await().documents
        val remoteUuids = mutableSetOf<String>()
        for (doc in remoteDocs) {
            val data = doc.data ?: continue
            val uuid = doc.id
            remoteUuids.add(uuid)
            val existing = database.categoryDao().getCategoryByUuid(uuid)
            val entity = CategoryEntity(
                id = existing?.id ?: 0L,
                uuid = uuid,
                name = data["name"] as? String ?: "",
                iconName = data["iconName"] as? String ?: "category",
                colorHex = data["colorHex"] as? String ?: "#3B82F6",
                type = data["type"] as? String ?: "EXPENSE",
                isDefault = data["isDefault"] as? Boolean ?: false
            )
            database.categoryDao().insertCategory(entity)
        }

        // Delete local items that were removed from Firestore (prevent resurrection)
        for (local in localCategories) {
            if (local.uuid.isNotBlank() && local.uuid !in remoteUuids) {
                runCatching { database.categoryDao().deleteCategoryByUuid(local.uuid) }
            }
        }
    }

    private suspend fun syncAccountsInternal(userId: String) {
        val accCollection = firestore.collection("users").document(userId).collection("accounts")
        val localAccounts = database.accountDao().getAllAccountsSync()

        for (local in localAccounts) {
            val docRef = accCollection.document(local.uuid)
            val data = hashMapOf(
                "uuid" to local.uuid,
                "name" to local.name,
                "type" to local.type,
                "balance" to local.balance,
                "currency" to local.currency,
                "colorHex" to local.colorHex,
                "iconName" to local.iconName,
                "updatedAt" to System.currentTimeMillis()
            )
            docRef.set(data, SetOptions.merge()).await()
        }

        val remoteDocs = accCollection.get().await().documents
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
                balance = (data["balance"] as? Number)?.toDouble() ?: 0.0,
                currency = data["currency"] as? String ?: "USD",
                colorHex = data["colorHex"] as? String ?: "#00875A",
                iconName = data["iconName"] as? String ?: "account_balance"
            )
            database.accountDao().insertAccount(entity)
        }

        // Delete local items that were removed from Firestore (prevent resurrection)
        for (local in localAccounts) {
            if (local.uuid.isNotBlank() && local.uuid !in remoteUuids) {
                runCatching { database.accountDao().deleteAccountByUuid(local.uuid) }
            }
        }
    }

    private suspend fun syncBudgetsInternal(userId: String) {
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

        val remoteDocs = budgetCollection.get().await().documents
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

        // Delete local items that were removed from Firestore (prevent resurrection)
        for (local in localBudgets) {
            if (local.uuid.isNotBlank() && local.uuid !in remoteUuids) {
                runCatching { database.budgetDao().deleteBudgetByUuid(local.uuid) }
            }
        }
    }

    private suspend fun syncBillsInternal(userId: String) {
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

    private suspend fun syncDhaarInternal(userId: String) {
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
            // Room's REPLACE strategy deletes the existing row first, which violates the
            // FOREIGN KEY … ON DELETE RESTRICT constraint from dhaar_entries → contacts.
            // Updating in-place preserves the original local id so all child FK references stay valid.
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

    // Direct Mutation Helpers
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

    suspend fun pushAccount(userId: String, account: AccountEntity) = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext
        try {
            val docRef = firestore.collection("users").document(userId).collection("accounts").document(account.uuid)
            val data = hashMapOf(
                "uuid" to account.uuid,
                "name" to account.name,
                "type" to account.type,
                "balance" to account.balance,
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
