package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.cloud.CloudBackupRepository
import com.example.data.cloud.CloudBackupResult
import com.example.data.cloud.DriveAuthorizeResult
import com.example.data.cloud.GoogleAuthManager
import com.example.data.local.AppDatabase
import com.example.data.model.*
import com.example.data.repository.CurrencyInfo
import com.example.data.repository.ExpenseRepository
import com.example.data.repository.ImportExportRepository
import com.example.data.repository.UserPreferencesRepository
import com.example.ui.components.BarChartEntry
import com.example.ui.components.CategoryTrendMeta
import com.example.ui.components.ChartCategoryData
import com.example.ui.components.MonthlyCategoryTrendEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.util.*

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {

    private val database = AppDatabase.getDatabase(application, viewModelScope)
    private val repository = ExpenseRepository(
        database.transactionDao(),
        database.categoryDao(),
        database.accountDao(),
        database.budgetDao(),
        database.billDao()
    )
    val dhaarRepository = com.example.data.repository.DhaarRepository(
        contactDao = database.contactDao(),
        dhaarEntryDao = database.dhaarEntryDao(),
        transactionDao = database.transactionDao(),
        accountDao = database.accountDao()
    )
    private val userPrefs = UserPreferencesRepository(application)
    val importExportRepo = ImportExportRepository(application, database, userPrefs)
    val cloudBackupRepo = CloudBackupRepository(
        importExportRepository = importExportRepo,
        userPreferencesRepository = userPrefs
    )
    val googleAuthManager = GoogleAuthManager(application, userPrefs)
    val firestoreSyncManager = com.example.data.cloud.FirestoreSyncManager(application, database, userPrefs)
    val firebaseConfigManager = com.example.data.cloud.FirebaseConfigManager(application, viewModelScope)
    val updateRepository = com.example.data.repository.UpdateRepository(application, userPrefs, firebaseConfigManager)
    val notificationRepository = com.example.data.repository.NotificationRepository(userPrefs, firebaseConfigManager, viewModelScope)

    init {
        // BUG FIX #1: Use REPLACE instead of KEEP so stale work enqueued by an older
        // app version (possibly with an incompatible worker definition) is discarded
        // instead of crashing, and guard the whole call so a WorkManager failure can
        // never crash the app at startup.
        // Only enqueue if Cloudinary credentials have been configured — avoids an
        // unnecessary network constraint wake-up for users who have never set up Cloudinary.
        if (userPrefs.cloudinaryCloudName.value.isNotBlank()) {
            try {
                val uploadWorkRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.data.cloud.CloudinaryImageUploadWorker>()
                    .setConstraints(
                        androidx.work.Constraints.Builder()
                            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                            .build()
                    )
                    .build()
                androidx.work.WorkManager.getInstance(application).enqueueUniqueWork(
                    "CloudinaryImageUpload",
                    androidx.work.ExistingWorkPolicy.REPLACE,
                    uploadWorkRequest
                )
            } catch (e: Exception) {
                android.util.Log.e("ExpenseViewModel", "Failed to enqueue Cloudinary upload work", e)
            }
        }
    }

    // User Preferences
    val currency = userPrefs.currency
    val currencySymbol = userPrefs.currencySymbol
    val themeMode = userPrefs.themeMode
    val isPinLockEnabled = userPrefs.isPinLockEnabled
    val pinCode = userPrefs.pinCode
    val pinCodeHash = userPrefs.pinCodeHash

    // Profile Preferences
    val displayName = userPrefs.displayName
    val avatarColorHex = userPrefs.avatarColorHex
    val profilePictureUri = userPrefs.profilePictureUri

    // Cloudinary Preferences
    val cloudinaryCloudName = userPrefs.cloudinaryCloudName
    val cloudinaryApiKey = userPrefs.cloudinaryApiKey
    val cloudinaryApiSecret = userPrefs.cloudinaryApiSecret
    val cloudinaryUploadPreset = userPrefs.cloudinaryUploadPreset

    // Notification Preferences
    val dueRemindersEnabled = userPrefs.dueRemindersEnabled
    val budgetAlertsEnabled = userPrefs.budgetAlertsEnabled

    // Display & Format Preferences
    val decimalPlaces = userPrefs.decimalPlaces
    val weekStartDay = userPrefs.weekStartDay
    val dateFormatPref = userPrefs.dateFormat

    // App Behavior Preferences
    val autoCategorize = userPrefs.autoCategorize
    val defaultTransactionType = userPrefs.defaultTransactionType
    val hapticFeedback = userPrefs.hapticFeedback

    // In-App Notifications (admin-published via Firebase Realtime DB, not FCM push)
    val notifications: StateFlow<List<AppNotification>> = notificationRepository.notifications
    val unreadNotificationCount: StateFlow<Int> = notificationRepository.unreadCount
    val popupNotification: StateFlow<AppNotification?> = notificationRepository.popupNotification
    fun markNotificationAsRead(id: String) = notificationRepository.markAsRead(id)
    fun markAllNotificationsAsRead() = notificationRepository.markAllAsRead()
    fun deleteNotification(id: String) = notificationRepository.delete(id)
    fun clearAllNotifications() = notificationRepository.clearAll()
    /** Dismiss the popup for the specific notification the user just closed/acted on. */
    fun dismissNotificationPopup(notificationId: String) = notificationRepository.dismissPopup(notificationId)
    /** Auto-dismiss the popup when the user opens the Notifications inbox (popup would reappear otherwise). */
    fun dismissNotificationPopupIfShowing() = notificationRepository.dismissPopupIfShowing()
    fun openNotificationAction(url: String) = updateRepository.openInBrowser(url)

    // Firebase & Cloud State
    val firebaseUser = googleAuthManager.currentUser
    val firestoreSyncState = firestoreSyncManager.syncState
    val firestoreSyncMessage = firestoreSyncManager.syncMessage
    val lastFirestoreSyncTime = firestoreSyncManager.lastSyncTimestamp

    // Cloud Backup Preferences
    val googleAccountEmail = userPrefs.googleAccountEmail
    val googleDriveAccessToken = userPrefs.googleDriveAccessToken
    val lastCloudBackupTime = userPrefs.lastCloudBackupTime
    val lastCloudBackupStatus = userPrefs.lastCloudBackupStatus
    val lastCloudBackupError = userPrefs.lastCloudBackupError
    val autoBackupFrequency = userPrefs.autoBackupFrequency
    val autoBackupWifiOnly = userPrefs.autoBackupWifiOnly

    // Pin Lock Session State (true when unlocked during current run)
    private val _isAppUnlocked = MutableStateFlow(false)
    val isAppUnlocked = _isAppUnlocked.asStateFlow()

    // Firebase Realtime DB App Config & Remote State
    val remoteConfig = firebaseConfigManager.remoteConfig
    val remoteUpdateInfo = firebaseConfigManager.remoteUpdateInfo
    val isFirebaseConfigConnected = firebaseConfigManager.isConnected

    // Repository Flows
    val allTransactions = repository.allTransactions.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val allCategories = repository.allCategories.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val allAccounts = repository.allAccounts.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val allBudgets = repository.allBudgets.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val allBills = repository.allBills.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    // Feature Delegates
    val analyticsDelegate = AnalyticsDelegate(
        viewModelScope = viewModelScope,
        allTransactions = allTransactions,
        allCategories = allCategories,
        allAccounts = allAccounts,
        allBudgets = allBudgets
    )

    val cloudDelegate = CloudDelegate(
        application = application,
        viewModelScope = viewModelScope,
        importExportRepo = importExportRepo,
        cloudBackupRepo = cloudBackupRepo,
        googleAuthManager = googleAuthManager,
        firestoreSyncManager = firestoreSyncManager,
        userPrefs = userPrefs
    )

    val dhaarDelegate = DhaarDelegate(
        viewModelScope = viewModelScope,
        database = database,
        dhaarRepository = dhaarRepository,
        googleAuthManager = googleAuthManager,
        firestoreSyncManager = firestoreSyncManager
    )
    
    val shopBakiRepository = com.example.data.repository.ShopBakiRepository(
        shopDao = database.shopDao(),
        shopProductDao = database.shopProductDao(),
        shopLedgerEntryDao = database.shopLedgerEntryDao()
    )

    val shopBakiDelegate = ShopBakiDelegate(
        viewModelScope = viewModelScope,
        shopBakiRepository = shopBakiRepository,
        expenseRepository = repository,
        googleAuthManager = googleAuthManager,
        firestoreSyncManager = firestoreSyncManager
    )

    val updateDelegate = UpdateDelegate(
        viewModelScope = viewModelScope,
        updateRepository = updateRepository,
        userPrefs = userPrefs
    )

    val feedbackRepository = com.example.data.repository.FeedbackRepository()
    val feedbackDelegate = FeedbackDelegate(
        viewModelScope = viewModelScope,
        feedbackRepository = feedbackRepository
    )

    // Forwarding Analytics Properties & Functions
    val financialSummary: StateFlow<FinancialSummary> get() = analyticsDelegate.financialSummary
    val accountsWithBalances: StateFlow<List<AccountWithBalance>> get() = analyticsDelegate.accountsWithBalances
    val categorySpendingData: StateFlow<List<ChartCategoryData>> get() = analyticsDelegate.categorySpendingData
    val monthlyTrendsData: StateFlow<List<BarChartEntry>> get() = analyticsDelegate.monthlyTrendsData
    val monthlyCategoryTrendsData: StateFlow<Pair<List<MonthlyCategoryTrendEntry>, List<CategoryTrendMeta>>> get() = analyticsDelegate.monthlyCategoryTrendsData
    val topMerchants: StateFlow<List<MerchantSpending>> get() = analyticsDelegate.topMerchants
    val budgetStatuses: StateFlow<List<BudgetStatus>> get() = analyticsDelegate.budgetStatuses
    val analyticsPeriod: StateFlow<String> get() = analyticsDelegate.analyticsPeriod
    fun setAnalyticsPeriod(period: String) = analyticsDelegate.setAnalyticsPeriod(period)
    fun suggestCategoryForMerchant(merchant: String): CategoryEntity? = analyticsDelegate.suggestCategoryForMerchant(merchant)
    fun computeTotalBalance(accounts: List<AccountEntity>, transactions: List<TransactionEntity>): Double = analyticsDelegate.computeTotalBalance(accounts, transactions)

    // Forwarding Cloud Properties & Functions
    val isCloudSyncing: StateFlow<Boolean> get() = cloudDelegate.isCloudSyncing
    val cloudSyncMessage: StateFlow<String?> get() = cloudDelegate.cloudSyncMessage
    val cloudConflict get() = cloudDelegate.cloudConflict
    val safetyBackups: StateFlow<List<File>> get() = cloudDelegate.safetyBackups
    fun loadSafetyBackups() = cloudDelegate.loadSafetyBackups()
    suspend fun exportBackupToJson(): String = cloudDelegate.exportBackupToJson()
    suspend fun exportTransactionsToCsv(): String = cloudDelegate.exportTransactionsToCsv()
    fun parseBackupJson(jsonString: String): Result<AppBackup> = cloudDelegate.parseBackupJson(jsonString)
    fun importBackupData(backup: AppBackup, mode: ImportMode, onResult: (Result<ImportResult>) -> Unit) =
        cloudDelegate.importBackupData(backup, mode, onResult)
    fun signInGoogle(
        activityContext: Context,
        webClientId: String = "",
        onFallbackToLegacy: ((android.content.Intent) -> Unit)? = null,
        onResult: (Result<String>) -> Unit
    ) = cloudDelegate.signInGoogle(activityContext, webClientId, onFallbackToLegacy, onResult)
    fun handleLegacySignInResult(data: android.content.Intent?, onResult: (Result<String>) -> Unit) =
        cloudDelegate.handleLegacySignInResult(data, onResult)
    fun authorizeDrive(activityContext: Context, onResult: (DriveAuthorizeResult) -> Unit) =
        cloudDelegate.authorizeDrive(activityContext, onResult)
    fun signOutGoogle(onComplete: () -> Unit) = cloudDelegate.signOutGoogle(onComplete)
    fun backupToCloud(forceOverwrite: Boolean = false, onResult: (Result<CloudBackupResult>) -> Unit) =
        cloudDelegate.backupToCloud(forceOverwrite, onResult)
    fun restoreFromCloud(mode: ImportMode, onResult: (Result<ImportResult>) -> Unit) =
        cloudDelegate.restoreFromCloud(mode, onResult)
    fun fetchCloudBackupPreview(onResult: (Result<AppBackup>) -> Unit) =
        cloudDelegate.fetchCloudBackupPreview(onResult)
    fun dismissCloudConflict() = cloudDelegate.dismissCloudConflict()
    fun syncWithFirestore(onResult: (Result<Unit>) -> Unit = {}) = cloudDelegate.syncWithFirestore(onResult)
    fun setAutoBackupSettings(frequency: String, wifiOnly: Boolean) = cloudDelegate.setAutoBackupSettings(frequency, wifiOnly)

    // Forwarding Dhaar Properties & Functions
    val allContacts: StateFlow<List<Contact>> get() = dhaarDelegate.allContacts
    val allDhaarEntries: StateFlow<List<DhaarEntry>> get() = dhaarDelegate.allDhaarEntries
    val contactsWithBalances: StateFlow<List<ContactWithBalance>> get() = dhaarDelegate.contactsWithBalances
    val dhaarDashboardSummary: StateFlow<DhaarDashboardSummary> get() = dhaarDelegate.dhaarDashboardSummary
    val upcomingDhaarReminders: StateFlow<List<DhaarReminderItem>> get() = dhaarDelegate.upcomingDhaarReminders
    fun addContact(contact: Contact, onCreated: (Long) -> Unit = {}) = dhaarDelegate.addContact(contact, onCreated)
    fun updateContact(contact: Contact) = dhaarDelegate.updateContact(contact)
    fun deleteContact(contact: Contact, deleteEntries: Boolean = false, onResult: (Result<Unit>) -> Unit = {}) =
        dhaarDelegate.deleteContact(contact, deleteEntries, onResult)
    fun addDhaarEntry(entry: DhaarEntry, linkToAccount: Boolean = false, accountName: String? = null, onCreated: (Long) -> Unit = {}) =
        dhaarDelegate.addDhaarEntry(entry, linkToAccount, accountName, onCreated)
    fun updateDhaarEntry(entry: DhaarEntry) = dhaarDelegate.updateDhaarEntry(entry)
    fun deleteDhaarEntry(entry: DhaarEntry) = dhaarDelegate.deleteDhaarEntry(entry)
    fun deleteDhaarEntryById(id: Long) = dhaarDelegate.deleteDhaarEntryById(id)
    fun getEntriesForContact(contactId: Long): Flow<List<DhaarEntry>> = dhaarDelegate.getEntriesForContact(contactId)
    fun getContactById(contactId: Long): Flow<Contact?> = dhaarDelegate.getContactById(contactId)

    // Forwarding ShopBaki Properties & Functions
    val allShops: StateFlow<List<Shop>> get() = shopBakiDelegate.allShops
    val activeShopProducts: StateFlow<List<ShopProduct>> get() = shopBakiDelegate.activeProducts
    val shopsWithBalances: StateFlow<List<ShopWithBalance>> get() = shopBakiDelegate.shopsWithBalances
    
    fun addShop(shop: Shop, onCreated: (Long) -> Unit = {}) = shopBakiDelegate.addShop(shop, onCreated)
    fun updateShop(shop: Shop) = shopBakiDelegate.updateShop(shop)
    fun deleteShop(shop: Shop, onResult: (Result<Unit>) -> Unit = {}) = shopBakiDelegate.deleteShop(shop, onResult)
    
    fun addShopProduct(product: ShopProduct, onCreated: (Long) -> Unit = {}) = shopBakiDelegate.addProduct(product, onCreated)
    fun updateShopProduct(product: ShopProduct) = shopBakiDelegate.updateProduct(product)
    fun deleteShopProduct(product: ShopProduct, onResult: (Result<Unit>) -> Unit = {}) = shopBakiDelegate.deleteProduct(product, onResult)
    
    fun addShopLedgerEntry(entry: ShopLedgerEntry, onCreated: (Long) -> Unit = {}) = shopBakiDelegate.addLedgerEntry(entry, onCreated)
    fun deleteShopLedgerEntry(entry: ShopLedgerEntry) = shopBakiDelegate.deleteLedgerEntry(entry)
    fun getShopTimeline(shopId: Long): Flow<List<ShopTimelineItem>> = shopBakiDelegate.getShopTimeline(shopId)
    fun getShopById(shopId: Long): Flow<Shop?> = shopBakiDelegate.getShopById(shopId)

    // Forwarding Update Properties & Functions
    val autoCheckUpdates get() = updateDelegate.autoCheckUpdates
    val skippedUpdateVersion get() = updateDelegate.skippedUpdateVersion
    val lastUpdateCheckTime get() = updateDelegate.lastUpdateCheckTime
    val updateCheckState get() = updateDelegate.updateCheckState
    val updateDownloadState get() = updateDelegate.updateDownloadState
    val currentAppVersionName get() = updateDelegate.currentAppVersionName
    val currentAppVersionCode get() = updateDelegate.currentAppVersionCode
    val releaseHistory get() = updateDelegate.releaseHistory
    fun checkForUpdates(isManual: Boolean = false) = updateDelegate.checkForUpdates(isManual)
    fun skipUpdateVersion(versionCode: Int) = updateDelegate.skipUpdateVersion(versionCode)
    fun dismissUpdatePrompt() = updateDelegate.dismissUpdatePrompt()
    fun downloadAndInstallUpdate(updateInfo: AppUpdateInfo) = updateDelegate.downloadAndInstallUpdate(updateInfo)
    fun setAutoCheckUpdates(enabled: Boolean) = updateDelegate.setAutoCheckUpdates(enabled)

    // Forwarding Feedback Properties & Functions
    val feedbackSubmitState get() = feedbackDelegate.feedbackSubmitState
    fun submitFeedback(entry: com.example.data.model.FeedbackEntry) = feedbackDelegate.submitFeedback(entry)
    fun resetFeedbackState() = feedbackDelegate.resetFeedbackState()

    init {
        loadSafetyBackups()
        viewModelScope.launch {
            googleAuthManager.currentUser
                // BUG FIX #2: react only when the signed-in user actually changes, so a
                // late/duplicate Firebase auth-state emission cannot trigger repeated
                // or overlapping Firestore syncs.
                .distinctUntilChanged { old, new -> old?.uid == new?.uid }
                .collect { user ->
                if (user != null) {
                    val googleName = user.displayName?.trim()
                    val googleEmail = user.email?.trim()
                    val googlePhoto = user.photoUrl?.toString()

                    if (!googleName.isNullOrBlank()) {
                        if (userPrefs.displayName.value.isBlank() || userPrefs.displayName.value == "Set your name") {
                            userPrefs.setDisplayName(googleName)
                        }
                    } else if (!googleEmail.isNullOrBlank() && userPrefs.displayName.value.isBlank()) {
                        val derivedName = googleEmail.substringBefore('@')
                            .replace('.', ' ')
                            .replace('_', ' ')
                            .split(" ")
                            .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
                        userPrefs.setDisplayName(derivedName)
                    }

                    if (!googlePhoto.isNullOrBlank() && userPrefs.profilePictureUri.value.isNullOrBlank()) {
                        userPrefs.setProfilePictureUri(googlePhoto)
                    }

                    if (!googleEmail.isNullOrBlank()) {
                        userPrefs.setGoogleAccount(googleEmail)
                    }

                    // BUG FIX #2: both sync entry points are fully guarded — if
                    // Firebase/Firestore is not ready yet the app stays stable and
                    // sync simply retries on the next auth-state change.
                    try {
                        firestoreSyncManager.startRealtimeSync(user.uid, viewModelScope)
                    } catch (e: Exception) {
                        android.util.Log.e("ExpenseViewModel", "Realtime sync start failed: ${e.message}")
                    }
                    try {
                        firestoreSyncManager.syncAll(user.uid)
                    } catch (e: Exception) {
                        android.util.Log.e("ExpenseViewModel", "Auto-sync on sign-in failed: ${e.message}")
                    }
                } else {
                    try {
                        firestoreSyncManager.stopRealtimeSync()
                    } catch (e: Exception) {
                        android.util.Log.e("ExpenseViewModel", "Realtime sync stop failed: ${e.message}")
                    }
                }
            }
        }
        viewModelScope.launch {
            if (userPrefs.autoCheckUpdates.value) {
                try {
                    updateRepository.checkForUpdates(isManualCheck = false)
                } catch (e: Exception) {
                    android.util.Log.e("ExpenseViewModel", "Auto update check error: ${e.message}")
                }
            }
        }
        viewModelScope.launch {
            remoteUpdateInfo.collect { info ->
                if (info != null && info.versionCode > currentAppVersionCode) {
                    val isMandatory = info.isMandatory || (currentAppVersionCode < info.minSupportedVersionCode)
                    if (isMandatory || userPrefs.autoCheckUpdates.value) {
                        updateRepository.checkForUpdates(isManualCheck = false)
                    }
                }
            }
        }
    }

    fun unlockApp() {
        _isAppUnlocked.value = true
    }

    fun verifyPin(pin: String): Boolean {
        return userPrefs.verifyPin(pin)
    }

    // Filter & Search States
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _filterType = MutableStateFlow("ALL")
    val filterType = _filterType.asStateFlow()

    private val _filterTimeRange = MutableStateFlow("ALL")
    val filterTimeRange = _filterTimeRange.asStateFlow()

    private val _filterCategoryId = MutableStateFlow<Long?>(null)
    val filterCategoryId = _filterCategoryId.asStateFlow()

    private val _filterAccountId = MutableStateFlow<Long?>(null)
    val filterAccountId = _filterAccountId.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilterType(type: String) {
        _filterType.value = type
    }

    fun setFilterTimeRange(range: String) {
        _filterTimeRange.value = range
    }

    fun setFilterCategoryId(id: Long?) {
        _filterCategoryId.value = id
    }

    fun setFilterAccountId(id: Long?) {
        _filterAccountId.value = id
    }

    fun resetFilters() {
        _searchQuery.value = ""
        _filterType.value = "ALL"
        _filterTimeRange.value = "ALL"
        _filterCategoryId.value = null
        _filterAccountId.value = null
    }

    private data class FilterCriteria(
        val query: String,
        val type: String,
        val timeRange: String,
        val categoryId: Long?,
        val accountId: Long?
    )

    // Debounce the search query so filteredTransactions doesn't recompute on every keystroke.
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private val _debouncedSearch = _searchQuery
        .debounce(300)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    private val _filterCriteria = combine(
        _debouncedSearch,
        _filterType,
        _filterTimeRange,
        _filterCategoryId,
        _filterAccountId
    ) { query, type, timeRange, catId, accId ->
        FilterCriteria(query, type, timeRange, catId, accId)
    }

    val filteredTransactions: StateFlow<List<TransactionEntity>> = combine(
        allTransactions,
        _filterCriteria
    ) { transactions, filter ->
        val now = Calendar.getInstance()

        transactions.filter { tx ->
            val matchesQuery = filter.query.isBlank() ||
                tx.merchant.contains(filter.query, ignoreCase = true) ||
                tx.note.contains(filter.query, ignoreCase = true) ||
                tx.categoryName.contains(filter.query, ignoreCase = true) ||
                tx.tags.contains(filter.query, ignoreCase = true)

            val matchesType = filter.type == "ALL" || tx.type.name.equals(filter.type, ignoreCase = true)
            val matchesCategory = filter.categoryId == null || tx.categoryId == filter.categoryId
            val matchesAccount = filter.accountId == null || tx.accountId == filter.accountId || tx.toAccountId == filter.accountId

            val matchesTime = when (filter.timeRange) {
                "TODAY" -> {
                    val txCal = Calendar.getInstance().apply { timeInMillis = tx.date }
                    txCal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                    txCal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
                }
                "THIS_WEEK" -> {
                    val txCal = Calendar.getInstance().apply { timeInMillis = tx.date }
                    val calNow = Calendar.getInstance()
                    
                    val firstDay = when (weekStartDay.value) {
                        "MONDAY" -> Calendar.MONDAY
                        "SATURDAY" -> Calendar.SATURDAY
                        else -> Calendar.SUNDAY
                    }
                    txCal.firstDayOfWeek = firstDay
                    calNow.firstDayOfWeek = firstDay
                    
                    txCal.get(Calendar.YEAR) == calNow.get(Calendar.YEAR) &&
                    txCal.get(Calendar.WEEK_OF_YEAR) == calNow.get(Calendar.WEEK_OF_YEAR)
                }
                "THIS_MONTH" -> {
                    val txCal = Calendar.getInstance().apply { timeInMillis = tx.date }
                    txCal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                    txCal.get(Calendar.MONTH) == now.get(Calendar.MONTH)
                }
                "LAST_MONTH" -> {
                    val lastMonthCal = Calendar.getInstance().apply {
                        add(Calendar.MONTH, -1)
                    }
                    val txCal = Calendar.getInstance().apply { timeInMillis = tx.date }
                    txCal.get(Calendar.YEAR) == lastMonthCal.get(Calendar.YEAR) &&
                    txCal.get(Calendar.MONTH) == lastMonthCal.get(Calendar.MONTH)
                }
                "THIS_YEAR" -> {
                    val txCal = Calendar.getInstance().apply { timeInMillis = tx.date }
                    txCal.get(Calendar.YEAR) == now.get(Calendar.YEAR)
                }
                else -> true
            }

            matchesQuery && matchesType && matchesCategory && matchesAccount && matchesTime
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Repository Mutations
    fun addTransaction(transaction: TransactionEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            // Capture the generated row id and push the entity WITH it, otherwise the
            // realtime listener can insert a duplicate (id=0) copy of the same transaction.
            val newId = repository.insertTransaction(transaction)
            googleAuthManager.currentUserId?.let { uid ->
                firestoreSyncManager.pushTransaction(uid, transaction.copy(id = newId))
            }
        }
    }

    fun updateTransaction(transaction: TransactionEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateTransaction(transaction)
            googleAuthManager.currentUserId?.let { uid ->
                firestoreSyncManager.pushTransaction(uid, transaction)
            }
        }
    }

    fun deleteTransaction(transaction: TransactionEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteTransaction(transaction)
            googleAuthManager.currentUserId?.let { uid ->
                firestoreSyncManager.deleteTransaction(uid, transaction.uuid)
            }
        }
    }

    fun deleteTransactionById(id: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = database.transactionDao().getTransactionByIdSync(id)
            repository.deleteTransactionById(id)
            if (existing != null) {
                googleAuthManager.currentUserId?.let { uid ->
                    firestoreSyncManager.deleteTransaction(uid, existing.uuid)
                }
            }
        }
    }

    fun addCategory(category: CategoryEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertCategory(category)
            googleAuthManager.currentUserId?.let { uid ->
                firestoreSyncManager.pushCategory(uid, category)
            }
        }
    }

    fun updateCategory(category: CategoryEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateCategory(category)
            googleAuthManager.currentUserId?.let { uid ->
                firestoreSyncManager.pushCategory(uid, category)
            }
        }
    }

    fun deleteCategory(category: CategoryEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteCategory(category)
            googleAuthManager.currentUserId?.let { uid ->
                firestoreSyncManager.deleteCategory(uid, category.uuid)
            }
        }
    }

    fun addAccount(account: AccountEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertAccount(account)
            googleAuthManager.currentUserId?.let { uid ->
                firestoreSyncManager.pushAccount(uid, account)
            }
        }
    }

    fun updateAccount(account: AccountEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateAccount(account)
            googleAuthManager.currentUserId?.let { uid ->
                firestoreSyncManager.pushAccount(uid, account)
            }
        }
    }

    fun deleteAccount(account: AccountEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteAccount(account)
            googleAuthManager.currentUserId?.let { uid ->
                firestoreSyncManager.deleteAccount(uid, account.uuid)
            }
        }
    }

    fun addBudget(budget: BudgetEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertBudget(budget)
            googleAuthManager.currentUserId?.let { uid ->
                firestoreSyncManager.pushBudget(uid, budget)
            }
        }
    }

    fun updateBudget(budget: BudgetEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateBudget(budget)
            googleAuthManager.currentUserId?.let { uid ->
                firestoreSyncManager.pushBudget(uid, budget)
            }
        }
    }

    fun deleteBudget(budget: BudgetEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteBudget(budget)
            googleAuthManager.currentUserId?.let { uid ->
                firestoreSyncManager.deleteBudget(uid, budget.uuid)
            }
        }
    }

    fun addBill(bill: BillEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertBill(bill)
            googleAuthManager.currentUserId?.let { uid ->
                firestoreSyncManager.pushBill(uid, bill)
            }
        }
    }

    fun updateBill(bill: BillEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.updateBill(bill)
            googleAuthManager.currentUserId?.let { uid ->
                firestoreSyncManager.pushBill(uid, bill)
            }
        }
    }

    fun deleteBill(bill: BillEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteBill(bill)
            googleAuthManager.currentUserId?.let { uid ->
                firestoreSyncManager.deleteBill(uid, bill.uuid)
            }
        }
    }

    fun markBillAsPaid(bill: BillEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            com.example.domain.usecase.MarkBillPaidUseCase(
                repository = repository,
                firestoreSyncManager = firestoreSyncManager,
                getCurrentUserId = { googleAuthManager.currentUserId }
            ).invoke(bill, allAccounts.value, allCategories.value)
        }
    }

    fun transferFunds(
        fromAccount: AccountEntity,
        toAccount: AccountEntity,
        amount: Double,
        note: String,
        date: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            com.example.domain.usecase.TransferFundsUseCase(
                repository = repository,
                firestoreSyncManager = firestoreSyncManager,
                getCurrentUserId = { googleAuthManager.currentUserId }
            ).invoke(fromAccount, toAccount, amount, note, date)
        }
    }

    // Settings actions
    fun setCurrency(currencyInfo: CurrencyInfo) {
        userPrefs.setCurrency(currencyInfo.code, currencyInfo.symbol)
    }

    fun setThemeMode(mode: String) {
        userPrefs.setThemeMode(mode)
    }

    fun setPinLock(enabled: Boolean, pin: String = "") {
        userPrefs.setPinLock(enabled, pin)
    }

    fun setDisplayName(name: String) {
        userPrefs.setDisplayName(name)
    }

    fun setAvatarColorHex(hex: String) {
        userPrefs.setAvatarColorHex(hex)
    }

    fun setProfilePictureUri(uri: String?) {
        userPrefs.setProfilePictureUri(uri)
    }

    fun setCloudinaryConfig(
        cloudName: String,
        apiKey: String,
        apiSecret: String,
        uploadPreset: String
    ) {
        userPrefs.setCloudinaryConfig(cloudName, apiKey, apiSecret, uploadPreset)
    }

    suspend fun testCloudinaryConnection(): Result<String> {
        return com.example.data.cloud.CloudinaryUploader.testConnection(getApplication<Application>())
    }

    fun setDueRemindersEnabled(enabled: Boolean) {
        userPrefs.setDueRemindersEnabled(enabled)
    }

    fun setBudgetAlertsEnabled(enabled: Boolean) {
        userPrefs.setBudgetAlertsEnabled(enabled)
    }

    fun setDecimalPlaces(places: Int) {
        userPrefs.setDecimalPlaces(places)
    }

    fun setWeekStartDay(day: String) {
        userPrefs.setWeekStartDay(day)
    }

    fun setDateFormatPref(format: String) {
        userPrefs.setDateFormat(format)
    }

    fun setAutoCategorize(enabled: Boolean) {
        userPrefs.setAutoCategorize(enabled)
    }

    fun setDefaultTransactionType(type: String) {
        userPrefs.setDefaultTransactionType(type)
    }

    fun setHapticFeedback(enabled: Boolean) {
        userPrefs.setHapticFeedback(enabled)
    }

    fun seedDemoData() {
        viewModelScope.launch(Dispatchers.IO) {
            AppDatabase.populateInitialData(database)
        }
    }

    fun clearAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            // Cancel any pending Cloudinary upload jobs first so they don't race
            // against the wipe and attempt to push data that no longer exists.
            try {
                androidx.work.WorkManager.getInstance(getApplication())
                    .cancelUniqueWork("CloudinaryImageUpload")
            } catch (e: Exception) {
                android.util.Log.w("ExpenseViewModel", "Could not cancel upload work before clear: ${e.message}")
            }
            database.shopLedgerEntryDao().deleteAllLedgerEntries()
            database.shopProductDao().deleteAllProducts()
            database.shopDao().deleteAllShops()
            database.dhaarEntryDao().deleteAllEntries()
            database.contactDao().deleteAllContacts()
            database.transactionDao().deleteAllTransactions()
            database.billDao().deleteAllBills()
            database.budgetDao().deleteAllBudgets()
            database.categoryDao().deleteAllCategories()
            database.accountDao().deleteAllAccounts()
        }
    }
}
