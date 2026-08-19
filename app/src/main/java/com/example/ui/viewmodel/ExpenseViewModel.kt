package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.cloud.CloudBackupRepository
import com.example.data.cloud.CloudBackupResult
import com.example.data.cloud.CloudConflictException
import com.example.data.cloud.DriveAuthorizeResult
import com.example.data.cloud.GoogleAuthManager
import com.example.data.local.AppDatabase
import com.example.data.model.*
import com.example.data.repository.AvailableCurrencies
import com.example.data.repository.CurrencyInfo
import com.example.data.repository.ExpenseRepository
import com.example.data.repository.ImportExportRepository
import com.example.data.repository.UserPreferencesRepository
import com.example.data.work.BackupWorker
import com.example.ui.components.BarChartEntry
import com.example.ui.components.CategoryTrendMeta
import com.example.ui.components.ChartCategoryData
import com.example.ui.components.MonthlyCategoryTrendEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
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

    // Cloud Operation State
    private val _isCloudSyncing = MutableStateFlow(false)
    val isCloudSyncing = _isCloudSyncing.asStateFlow()

    private val _cloudSyncMessage = MutableStateFlow<String?>(null)
    val cloudSyncMessage = _cloudSyncMessage.asStateFlow()

    private val _cloudConflict = MutableStateFlow<CloudConflictException?>(null)
    val cloudConflict = _cloudConflict.asStateFlow()

    private val _safetyBackups = MutableStateFlow<List<File>>(emptyList())
    val safetyBackups = _safetyBackups.asStateFlow()

    // Update States & Preferences
    val autoCheckUpdates = userPrefs.autoCheckUpdates
    val skippedUpdateVersion = userPrefs.skippedUpdateVersion
    val lastUpdateCheckTime = userPrefs.lastUpdateCheckTime
    val updateCheckState = updateRepository.updateCheckState
    val updateDownloadState = updateRepository.downloadState
    val currentAppVersionName = updateRepository.getCurrentVersionName()
    val currentAppVersionCode = updateRepository.getCurrentVersionCode()
    val releaseHistory = updateRepository.getReleaseHistory()

    // Pin Lock Session State (true when unlocked during current run)
    private val _isAppUnlocked = MutableStateFlow(false)
    val isAppUnlocked = _isAppUnlocked.asStateFlow()

    // Firebase Realtime DB App Config & Remote State
    val remoteConfig = firebaseConfigManager.remoteConfig
    val remoteUpdateInfo = firebaseConfigManager.remoteUpdateInfo
    val isFirebaseConfigConnected = firebaseConfigManager.isConnected

    init {
        loadSafetyBackups()
        viewModelScope.launch {
            googleAuthManager.currentUser.collect { user ->
                if (user != null) {
                    // Automatically populate profile info from Google account
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

                    firestoreSyncManager.startRealtimeSync(user.uid, viewModelScope)
                    try {
                        firestoreSyncManager.syncAll(user.uid)
                    } catch (e: Exception) {
                        android.util.Log.e("ExpenseViewModel", "Auto-sync on sign-in failed: ${e.message}")
                    }
                } else {
                    firestoreSyncManager.stopRealtimeSync()
                }
            }
        }
        // Auto-check for updates from Firebase Realtime DB and GitHub
        viewModelScope.launch {
            if (userPrefs.autoCheckUpdates.value) {
                try {
                    updateRepository.checkForUpdates(isManualCheck = false)
                } catch (e: Exception) {
                    android.util.Log.e("ExpenseViewModel", "Auto update check error: ${e.message}")
                }
            }
        }
        // Live Realtime DB update listener
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

    fun loadSafetyBackups() {
        viewModelScope.launch(Dispatchers.IO) {
            _safetyBackups.value = importExportRepo.listSafetyBackups()
        }
    }

    // Data Portability Actions
    suspend fun exportBackupToJson(): String {
        return importExportRepo.exportBackupToJson()
    }

    suspend fun exportTransactionsToCsv(): String {
        return importExportRepo.exportTransactionsToCsv()
    }

    fun parseBackupJson(jsonString: String): Result<AppBackup> {
        return importExportRepo.parseBackupJson(jsonString)
    }

    fun importBackupData(
        backup: AppBackup,
        mode: ImportMode,
        onResult: (Result<ImportResult>) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = importExportRepo.importBackup(backup, mode)
            loadSafetyBackups()
            onResult(result)
        }
    }

    // Cloud Backup Actions
    fun signInGoogle(activityContext: Context, webClientId: String = "", onResult: (Result<String>) -> Unit) {
        viewModelScope.launch {
            _isCloudSyncing.value = true
            _cloudSyncMessage.value = "Signing in to Google..."
            val result = googleAuthManager.signIn(activityContext, webClientId)
            _isCloudSyncing.value = false
            _cloudSyncMessage.value = null
            onResult(result)
        }
    }

    /**
     * Obtains a Google Drive access token (drive.appdata scope). On first use this may return
     * [DriveAuthorizeResult.ConsentRequired] and the UI must launch the returned IntentSender
     * before calling this again to receive [DriveAuthorizeResult.Granted].
     */
    fun authorizeDrive(activityContext: Context, onResult: (DriveAuthorizeResult) -> Unit) {
        viewModelScope.launch {
            val result = googleAuthManager.authorizeDrive(activityContext)
            onResult(result)
        }
    }

    fun signOutGoogle(onComplete: () -> Unit) {
        viewModelScope.launch {
            googleAuthManager.signOut()
            BackupWorker.schedule(getApplication(), "OFF", true)
            onComplete()
        }
    }

    fun backupToCloud(
        forceOverwrite: Boolean = false,
        onResult: (Result<CloudBackupResult>) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isCloudSyncing.value = true
            _cloudSyncMessage.value = "Backing up to Google Drive..."
            val result = cloudBackupRepo.backupToCloud(forceOverwrite = forceOverwrite)
            _isCloudSyncing.value = false
            _cloudSyncMessage.value = null

            val error = result.exceptionOrNull()
            if (error is CloudConflictException) {
                _cloudConflict.value = error
            }

            onResult(result)
        }
    }

    fun restoreFromCloud(
        mode: ImportMode,
        onResult: (Result<ImportResult>) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            _isCloudSyncing.value = true
            _cloudSyncMessage.value = "Restoring from Google Drive..."
            val result = cloudBackupRepo.restoreFromCloud(mode = mode)
            _isCloudSyncing.value = false
            _cloudSyncMessage.value = null
            loadSafetyBackups()
            onResult(result)
        }
    }

    fun fetchCloudBackupPreview(onResult: (Result<AppBackup>) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            _isCloudSyncing.value = true
            _cloudSyncMessage.value = "Checking cloud backup..."
            val result = cloudBackupRepo.fetchBackupPreviewFromCloud()
            _isCloudSyncing.value = false
            _cloudSyncMessage.value = null
            onResult(result)
        }
    }

    fun dismissCloudConflict() {
        _cloudConflict.value = null
    }

    fun syncWithFirestore(onResult: (Result<Unit>) -> Unit = {}) {
        val uid = googleAuthManager.currentUserId
        if (uid.isNullOrBlank()) {
            onResult(Result.failure(Exception("Please sign in with Google to sync with Firebase.")))
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val result = firestoreSyncManager.syncAll(uid)
                onResult(result)
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }

    fun setAutoBackupSettings(frequency: String, wifiOnly: Boolean) {
        userPrefs.setAutoBackupSettings(frequency, wifiOnly)
        BackupWorker.schedule(getApplication(), frequency, wifiOnly)
    }

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

    // Filter & Search States
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    private val _filterType = MutableStateFlow("ALL") // ALL, EXPENSE, INCOME, TRANSFER
    val filterType = _filterType.asStateFlow()

    private val _filterTimeRange = MutableStateFlow("ALL") // ALL, TODAY, THIS_WEEK, THIS_MONTH, LAST_MONTH, THIS_YEAR
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

    // Filter helper class
    private data class FilterCriteria(
        val query: String,
        val type: String,
        val timeRange: String,
        val categoryId: Long?,
        val accountId: Long?
    )

    private val _filterCriteria = combine(
        _searchQuery,
        _filterType,
        _filterTimeRange,
        _filterCategoryId,
        _filterAccountId
    ) { query, type, timeRange, catId, accId ->
        FilterCriteria(query, type, timeRange, catId, accId)
    }

    // Filtered Transactions Flow
    val filteredTransactions: StateFlow<List<TransactionEntity>> = combine(
        allTransactions,
        _filterCriteria
    ) { transactions, filter ->
        val now = Calendar.getInstance()

        transactions.filter { tx ->
            // Query filter
            val matchesQuery = filter.query.isBlank() ||
                tx.merchant.contains(filter.query, ignoreCase = true) ||
                tx.note.contains(filter.query, ignoreCase = true) ||
                tx.categoryName.contains(filter.query, ignoreCase = true) ||
                tx.tags.contains(filter.query, ignoreCase = true)

            // Type filter
            val matchesType = filter.type == "ALL" || tx.type.equals(filter.type, ignoreCase = true)

            // Category filter
            val matchesCategory = filter.categoryId == null || tx.categoryId == filter.categoryId

            // Account filter
            val matchesAccount = filter.accountId == null || tx.accountId == filter.accountId || tx.toAccountId == filter.accountId

            // Time Range filter
            val matchesTime = when (filter.timeRange) {
                "TODAY" -> {
                    val txCal = Calendar.getInstance().apply { timeInMillis = tx.date }
                    txCal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                    txCal.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)
                }
                "THIS_WEEK" -> {
                    val txCal = Calendar.getInstance().apply { timeInMillis = tx.date }
                    txCal.get(Calendar.YEAR) == now.get(Calendar.YEAR) &&
                    txCal.get(Calendar.WEEK_OF_YEAR) == now.get(Calendar.WEEK_OF_YEAR)
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

    // Net Financial Overview Metrics
    data class FinancialSummary(
        val totalBalance: Double,
        val thisMonthIncome: Double,
        val thisMonthExpense: Double,
        val netSavings: Double,
        val savingsRate: Double
    )

    val financialSummary: StateFlow<FinancialSummary> = combine(
        allTransactions,
        allAccounts
    ) { transactions, accounts ->
        val now = Calendar.getInstance()
        val currentYear = now.get(Calendar.YEAR)
        val currentMonth = now.get(Calendar.MONTH)

        var totalIncomeMonth = 0.0
        var totalExpenseMonth = 0.0

        transactions.forEach { tx ->
            val txCal = Calendar.getInstance().apply { timeInMillis = tx.date }
            val isCurrentMonth = txCal.get(Calendar.YEAR) == currentYear && txCal.get(Calendar.MONTH) == currentMonth

            if (isCurrentMonth) {
                if (tx.type.equals("INCOME", ignoreCase = true)) {
                    totalIncomeMonth += tx.amount
                } else if (tx.type.equals("EXPENSE", ignoreCase = true)) {
                    totalExpenseMonth += tx.amount
                }
            }
        }

        // Compute total live balance across accounts
        val totalLiveBalance = computeTotalBalance(accounts, transactions)

        val netSavings = totalIncomeMonth - totalExpenseMonth
        val savingsRate = if (totalIncomeMonth > 0) ((netSavings / totalIncomeMonth) * 100).coerceAtLeast(0.0) else 0.0

        FinancialSummary(
            totalBalance = totalLiveBalance,
            thisMonthIncome = totalIncomeMonth,
            thisMonthExpense = totalExpenseMonth,
            netSavings = netSavings,
            savingsRate = savingsRate
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FinancialSummary(0.0, 0.0, 0.0, 0.0, 0.0))

    // Dynamic Account Balances (Account + Transactions)
    data class AccountWithBalance(
        val account: AccountEntity,
        val liveBalance: Double,
        val transactionCount: Int
    )

    val accountsWithBalances: StateFlow<List<AccountWithBalance>> = combine(
        allAccounts,
        allTransactions
    ) { accounts, transactions ->
        accounts.map { acc ->
            var bal = acc.balance
            var count = 0
            transactions.forEach { tx ->
                if (tx.accountId == acc.id) {
                    count++
                    when (tx.type.uppercase()) {
                        "INCOME" -> bal += tx.amount
                        "EXPENSE" -> bal -= tx.amount
                        "TRANSFER" -> bal -= tx.amount
                    }
                } else if (tx.toAccountId == acc.id && tx.type.equals("TRANSFER", ignoreCase = true)) {
                    count++
                    bal += tx.amount
                }
            }
            AccountWithBalance(account = acc, liveBalance = bal, transactionCount = count)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Category Spending Analytics (for selected period)
    private val _analyticsPeriod = MutableStateFlow("THIS_MONTH") // THIS_MONTH, LAST_MONTH, THIS_YEAR, ALL
    val analyticsPeriod = _analyticsPeriod.asStateFlow()

    fun setAnalyticsPeriod(period: String) {
        _analyticsPeriod.value = period
    }

    val categorySpendingData: StateFlow<List<ChartCategoryData>> = combine(
        allTransactions,
        _analyticsPeriod,
        allCategories
    ) { transactions, period, categories ->
        val now = Calendar.getInstance()
        val filtered = transactions.filter { tx ->
            if (!tx.type.equals("EXPENSE", ignoreCase = true)) return@filter false
            val txCal = Calendar.getInstance().apply { timeInMillis = tx.date }
            when (period) {
                "THIS_MONTH" -> txCal.get(Calendar.YEAR) == now.get(Calendar.YEAR) && txCal.get(Calendar.MONTH) == now.get(Calendar.MONTH)
                "LAST_MONTH" -> {
                    val lastM = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
                    txCal.get(Calendar.YEAR) == lastM.get(Calendar.YEAR) && txCal.get(Calendar.MONTH) == lastM.get(Calendar.MONTH)
                }
                "THIS_YEAR" -> txCal.get(Calendar.YEAR) == now.get(Calendar.YEAR)
                else -> true
            }
        }

        val totalSpent = filtered.sumOf { it.amount }
        val categoryMap = mutableMapOf<String, Double>()
        val categoryInfoMap = mutableMapOf<String, Pair<String, String>>() // Name -> (icon, color)

        filtered.forEach { tx ->
            val name = tx.categoryName.ifBlank { "Other" }
            categoryMap[name] = (categoryMap[name] ?: 0.0) + tx.amount
            if (!categoryInfoMap.containsKey(name)) {
                categoryInfoMap[name] = Pair(tx.categoryIcon, tx.categoryColorHex)
            }
        }

        categoryMap.entries.map { (name, amount) ->
            val info = categoryInfoMap[name]
            val catEntity = categories.find { it.name.equals(name, ignoreCase = true) }
            val colorHex = catEntity?.colorHex ?: info?.second ?: "#64748B"
            val iconName = catEntity?.iconName ?: info?.first ?: "category"
            val percentage = if (totalSpent > 0) ((amount / totalSpent) * 100).toFloat() else 0f

            ChartCategoryData(
                name = name,
                amount = amount,
                percentage = percentage,
                colorHex = colorHex,
                iconName = iconName
            )
        }.sortedByDescending { it.amount }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Monthly Trend Chart Data (Last 6 Months)
    val monthlyTrendsData: StateFlow<List<BarChartEntry>> = allTransactions.map { transactions ->
        val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())
        val entries = mutableListOf<BarChartEntry>()

        for (i in 5 downTo 0) {
            val cal = Calendar.getInstance().apply {
                add(Calendar.MONTH, -i)
            }
            val targetYear = cal.get(Calendar.YEAR)
            val targetMonth = cal.get(Calendar.MONTH)
            val label = monthFormat.format(cal.time)

            var expenseSum = 0.0
            var incomeSum = 0.0

            transactions.forEach { tx ->
                val txCal = Calendar.getInstance().apply { timeInMillis = tx.date }
                if (txCal.get(Calendar.YEAR) == targetYear && txCal.get(Calendar.MONTH) == targetMonth) {
                    if (tx.type.equals("EXPENSE", ignoreCase = true)) {
                        expenseSum += tx.amount
                    } else if (tx.type.equals("INCOME", ignoreCase = true)) {
                        incomeSum += tx.amount
                    }
                }
            }

            entries.add(BarChartEntry(label = label, expense = expenseSum, income = incomeSum))
        }

        entries
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Monthly Category Trends Data (Recharts style breakdown by category across last 6 months)
    val monthlyCategoryTrendsData: StateFlow<Pair<List<MonthlyCategoryTrendEntry>, List<CategoryTrendMeta>>> = combine(
        allTransactions,
        allCategories
    ) { transactions, categories ->
        val monthFormat = SimpleDateFormat("MMM", Locale.getDefault())
        val entries = mutableListOf<MonthlyCategoryTrendEntry>()
        val categoryTotals = mutableMapOf<String, Double>()
        val categoryInfo = mutableMapOf<String, Pair<String, String>>() // Name -> (icon, color)

        categories.forEach { cat ->
            categoryInfo[cat.name] = Pair(cat.iconName, cat.colorHex)
        }

        for (i in 5 downTo 0) {
            val cal = Calendar.getInstance().apply {
                add(Calendar.MONTH, -i)
            }
            val targetYear = cal.get(Calendar.YEAR)
            val targetMonth = cal.get(Calendar.MONTH)
            val label = monthFormat.format(cal.time)

            val monthCategoryMap = mutableMapOf<String, Double>()
            var monthTotal = 0.0

            transactions.forEach { tx ->
                val txCal = Calendar.getInstance().apply { timeInMillis = tx.date }
                if (txCal.get(Calendar.YEAR) == targetYear && txCal.get(Calendar.MONTH) == targetMonth) {
                    if (tx.type.equals("EXPENSE", ignoreCase = true)) {
                        val catName = tx.categoryName.ifBlank { "Other" }
                        monthCategoryMap[catName] = (monthCategoryMap[catName] ?: 0.0) + tx.amount
                        categoryTotals[catName] = (categoryTotals[catName] ?: 0.0) + tx.amount
                        monthTotal += tx.amount

                        if (!categoryInfo.containsKey(catName)) {
                            categoryInfo[catName] = Pair(tx.categoryIcon, tx.categoryColorHex)
                        }
                    }
                }
            }

            entries.add(
                MonthlyCategoryTrendEntry(
                    monthLabel = label,
                    year = targetYear,
                    month = targetMonth,
                    totalExpense = monthTotal,
                    categoryAmounts = monthCategoryMap
                )
            )
        }

        val metaList = categoryTotals.entries.map { (catName, total) ->
            val info = categoryInfo[catName]
            val catEntity = categories.find { it.name.equals(catName, ignoreCase = true) }
            CategoryTrendMeta(
                name = catName,
                colorHex = catEntity?.colorHex ?: info?.second ?: "#64748B",
                iconName = catEntity?.iconName ?: info?.first ?: "category",
                totalAcrossMonths = total
            )
        }.sortedByDescending { it.totalAcrossMonths }

        Pair(entries, metaList)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Pair(emptyList(), emptyList()))

    // Top Merchants / Vendors
    data class MerchantSpending(val merchant: String, val amount: Double, val count: Int)
    val topMerchants: StateFlow<List<MerchantSpending>> = allTransactions.map { transactions ->
        val merchantMap = mutableMapOf<String, Pair<Double, Int>>()
        transactions.filter { it.type.equals("EXPENSE", ignoreCase = true) && it.merchant.isNotBlank() }
            .forEach { tx ->
                val current = merchantMap[tx.merchant] ?: Pair(0.0, 0)
                merchantMap[tx.merchant] = Pair(current.first + tx.amount, current.second + 1)
            }
        merchantMap.entries.map { (merchant, data) ->
            MerchantSpending(merchant = merchant, amount = data.first, count = data.second)
        }.sortedByDescending { it.amount }.take(5)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Budget Status Overview
    data class BudgetStatus(
        val budget: BudgetEntity,
        val spentAmount: Double,
        val remainingAmount: Double,
        val percentage: Float,
        val isOverBudget: Boolean,
        val isNearLimit: Boolean
    )

    val budgetStatuses: StateFlow<List<BudgetStatus>> = combine(
        allBudgets,
        allTransactions
    ) { budgets, transactions ->
        val now = Calendar.getInstance()
        val currentYear = now.get(Calendar.YEAR)
        val currentMonth = now.get(Calendar.MONTH)

        budgets.map { budget ->
            val spent = transactions.filter { tx ->
                if (!tx.type.equals("EXPENSE", ignoreCase = true)) return@filter false
                val txCal = Calendar.getInstance().apply { timeInMillis = tx.date }
                val isInPeriod = txCal.get(Calendar.YEAR) == currentYear && txCal.get(Calendar.MONTH) == currentMonth

                if (!isInPeriod) return@filter false

                if (budget.categoryId == null) {
                    true // Overall budget
                } else {
                    tx.categoryId == budget.categoryId || tx.categoryName.equals(budget.categoryName, ignoreCase = true)
                }
            }.sumOf { it.amount }

            val remaining = budget.amountLimit - spent
            val percentage = if (budget.amountLimit > 0) ((spent / budget.amountLimit) * 100).toFloat() else 0f
            val isOver = spent > budget.amountLimit
            val isNear = percentage >= budget.alertThresholdPercent && !isOver

            BudgetStatus(
                budget = budget,
                spentAmount = spent,
                remainingAmount = remaining,
                percentage = percentage,
                isOverBudget = isOver,
                isNearLimit = isNear
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Auto-Categorization Helper from Merchant Name
    fun suggestCategoryForMerchant(merchant: String): CategoryEntity? {
        val clean = merchant.trim().lowercase()
        val categories = allCategories.value

        return when {
            clean.contains("food") || clean.contains("restaurant") || clean.contains("cafe") ||
            clean.contains("starbucks") || clean.contains("mcdonald") || clean.contains("burger") ||
            clean.contains("pizza") || clean.contains("ramen") || clean.contains("chipotle") -> {
                categories.find { it.name.contains("Food", ignoreCase = true) }
            }
            clean.contains("grocery") || clean.contains("market") || clean.contains("walmart") ||
            clean.contains("whole foods") || clean.contains("trader joe") || clean.contains("kroger") ||
            clean.contains("target") || clean.contains("supermarket") -> {
                categories.find { it.name.contains("Groceries", ignoreCase = true) }
            }
            clean.contains("uber") || clean.contains("lyft") || clean.contains("gas") ||
            clean.contains("shell") || clean.contains("chevron") || clean.contains("transit") ||
            clean.contains("subway") || clean.contains("train") || clean.contains("airline") -> {
                categories.find { it.name.contains("Transport", ignoreCase = true) }
            }
            clean.contains("netflix") || clean.contains("spotify") || clean.contains("youtube") ||
            clean.contains("apple") || clean.contains("hulu") || clean.contains("disney") ||
            clean.contains("patreon") || clean.contains("subscription") -> {
                categories.find { it.name.contains("Subscription", ignoreCase = true) }
            }
            clean.contains("rent") || clean.contains("apartment") || clean.contains("mortgage") ||
            clean.contains("housing") -> {
                categories.find { it.name.contains("Housing", ignoreCase = true) }
            }
            clean.contains("electric") || clean.contains("power") || clean.contains("water") ||
            clean.contains("internet") || clean.contains("wifi") || clean.contains("verizon") ||
            clean.contains("at&t") || clean.contains("t-mobile") -> {
                categories.find { it.name.contains("Utilities", ignoreCase = true) }
            }
            clean.contains("amazon") || clean.contains("ebay") || clean.contains("zara") ||
            clean.contains("nike") || clean.contains("h&m") || clean.contains("clothing") ||
            clean.contains("mall") -> {
                categories.find { it.name.contains("Shopping", ignoreCase = true) }
            }
            clean.contains("pharmacy") || clean.contains("cvs") || clean.contains("walgreens") ||
            clean.contains("doctor") || clean.contains("dental") || clean.contains("hospital") -> {
                categories.find { it.name.contains("Health", ignoreCase = true) }
            }
            clean.contains("salary") || clean.contains("payroll") || clean.contains("wage") ||
            clean.contains("bonus") -> {
                categories.find { it.name.contains("Salary", ignoreCase = true) || it.name.contains("Income", ignoreCase = true) }
            }
            else -> null
        }
    }

    // Repository Mutations
    fun addTransaction(transaction: TransactionEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertTransaction(transaction)
            googleAuthManager.currentUserId?.let { uid ->
                firestoreSyncManager.pushTransaction(uid, transaction)
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
            val updated = bill.copy(isPaid = true, lastPaidDate = System.currentTimeMillis())
            repository.updateBill(updated)
            googleAuthManager.currentUserId?.let { uid ->
                firestoreSyncManager.pushBill(uid, updated)
            }

            if (bill.autoLogTransaction) {
                val accounts = allAccounts.value
                val account = accounts.find { it.id == bill.accountId } ?: accounts.firstOrNull()
                val tx = TransactionEntity(
                    type = "EXPENSE",
                    amount = bill.amount,
                    categoryId = bill.categoryId,
                    categoryName = bill.categoryName,
                    categoryIcon = "bolt",
                    categoryColorHex = "#06B6D4",
                    accountId = account?.id ?: 1,
                    accountName = account?.name ?: "Main Account",
                    date = System.currentTimeMillis(),
                    note = "Paid Bill: ${bill.title}",
                    merchant = bill.title,
                    paymentMethod = "Auto Bill Pay",
                    tags = "BillPay, ${bill.frequency}"
                )
                repository.insertTransaction(tx)
                googleAuthManager.currentUserId?.let { uid ->
                    firestoreSyncManager.pushTransaction(uid, tx)
                }
            }
        }
    }

    // Transfer Funds between accounts
    fun transferFunds(
        fromAccount: AccountEntity,
        toAccount: AccountEntity,
        amount: Double,
        note: String,
        date: Long = System.currentTimeMillis()
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val transferTx = TransactionEntity(
                type = "TRANSFER",
                amount = amount,
                accountId = fromAccount.id,
                accountName = fromAccount.name,
                toAccountId = toAccount.id,
                toAccountName = toAccount.name,
                categoryName = "Transfer",
                categoryIcon = "swap_horiz",
                categoryColorHex = "#3B82F6",
                date = date,
                note = note.ifBlank { "Transfer from ${fromAccount.name} to ${toAccount.name}" },
                merchant = "Internal Transfer",
                paymentMethod = "Transfer",
                tags = "Transfer"
            )
            repository.insertTransaction(transferTx)
            googleAuthManager.currentUserId?.let { uid ->
                firestoreSyncManager.pushTransaction(uid, transferTx)
            }
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

    // Profile settings
    fun setDisplayName(name: String) {
        userPrefs.setDisplayName(name)
    }

    fun setAvatarColorHex(hex: String) {
        userPrefs.setAvatarColorHex(hex)
    }

    fun setProfilePictureUri(uri: String?) {
        userPrefs.setProfilePictureUri(uri)
    }

    // Notification settings
    fun setDueRemindersEnabled(enabled: Boolean) {
        userPrefs.setDueRemindersEnabled(enabled)
    }

    fun setBudgetAlertsEnabled(enabled: Boolean) {
        userPrefs.setBudgetAlertsEnabled(enabled)
    }

    // Display & Format settings
    fun setDecimalPlaces(places: Int) {
        userPrefs.setDecimalPlaces(places)
    }

    fun setWeekStartDay(day: String) {
        userPrefs.setWeekStartDay(day)
    }

    fun setDateFormatPref(format: String) {
        userPrefs.setDateFormat(format)
    }

    // App Behavior settings
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
            // Delete in FK-safe order: children before parents
            database.dhaarEntryDao().deleteAllEntries()
            database.contactDao().deleteAllContacts()
            database.transactionDao().deleteAllTransactions()
            database.billDao().deleteAllBills()
            database.budgetDao().deleteAllBudgets()
            database.categoryDao().deleteAllCategories()
            database.accountDao().deleteAllAccounts()
        }
    }

    // Dena-Pawna (Dhaar) StateFlows
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

    private fun computeTotalBalance(accounts: List<AccountEntity>, transactions: List<TransactionEntity>): Double {
        var balance = accounts.sumOf { it.balance }
        transactions.forEach { tx ->
            when (tx.type.uppercase()) {
                "INCOME" -> balance += tx.amount
                "EXPENSE" -> balance -= tx.amount
                // Transfers between owned accounts don't alter net aggregate total
            }
        }
        return balance
    }

    // --- Update Actions ---
    fun checkForUpdates(isManual: Boolean = false) {
        viewModelScope.launch {
            updateRepository.checkForUpdates(isManualCheck = isManual)
        }
    }

    fun skipUpdateVersion(versionCode: Int) {
        updateRepository.skipVersion(versionCode)
    }

    fun dismissUpdatePrompt() {
        updateRepository.dismissUpdate()
    }

    fun downloadAndInstallUpdate(updateInfo: AppUpdateInfo) {
        viewModelScope.launch {
            updateRepository.downloadAndInstallApk(updateInfo)
        }
    }

    fun setAutoCheckUpdates(enabled: Boolean) {
        userPrefs.setAutoCheckUpdates(enabled)
    }
}
