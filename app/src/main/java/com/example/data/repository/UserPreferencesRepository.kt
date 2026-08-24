package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.data.model.UserSettingsBackup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest


class UserPreferencesRepository(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("expense_user_prefs", Context.MODE_PRIVATE)

    private val _currency = MutableStateFlow(
        prefs.getString("selected_currency", "USD") ?: "USD"
    )
    val currency: StateFlow<String> = _currency.asStateFlow()

    private val _currencySymbol = MutableStateFlow(
        prefs.getString("selected_currency_symbol", "$") ?: "$"
    )
    val currencySymbol: StateFlow<String> = _currencySymbol.asStateFlow()

    private val _themeMode = MutableStateFlow(
        prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM" // SYSTEM, LIGHT, DARK
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _isPinLockEnabled = MutableStateFlow(
        prefs.getBoolean("pin_lock_enabled", false)
    )
    val isPinLockEnabled: StateFlow<Boolean> = _isPinLockEnabled.asStateFlow()

    private val _pinCodeHash = MutableStateFlow(
        getOrMigratePinHash()
    )
    val pinCodeHash: StateFlow<String> = _pinCodeHash.asStateFlow()

    // For backwards compatibility with UI observing pinCode
    val pinCode: StateFlow<String> = _pinCodeHash.asStateFlow()

    // Cloud Backup Preferences
    private val _googleAccountEmail = MutableStateFlow<String?>(
        prefs.getString("google_account_email", null)
    )
    val googleAccountEmail: StateFlow<String?> = _googleAccountEmail.asStateFlow()

    // Firebase Google ID token (used only for Firebase Auth hand-off)
    private val _googleAuthToken = MutableStateFlow<String?>(
        prefs.getString("google_auth_token", null)
    )
    val googleAuthToken: StateFlow<String?> = _googleAuthToken.asStateFlow()

    // Google Drive OAuth access token (the token that the Drive REST API actually accepts)
    private val _googleDriveAccessToken = MutableStateFlow<String?>(
        prefs.getString("google_drive_access_token", null)
    )
    val googleDriveAccessToken: StateFlow<String?> = _googleDriveAccessToken.asStateFlow()

    private val _lastCloudBackupTime = MutableStateFlow(
        prefs.getLong("last_cloud_backup_time", 0L)
    )
    val lastCloudBackupTime: StateFlow<Long> = _lastCloudBackupTime.asStateFlow()

    private val _lastCloudBackupStatus = MutableStateFlow(
        prefs.getString("last_cloud_backup_status", "NEVER") ?: "NEVER" // SUCCESS, FAILED, NEVER
    )
    val lastCloudBackupStatus: StateFlow<String> = _lastCloudBackupStatus.asStateFlow()

    private val _lastCloudBackupError = MutableStateFlow<String?>(
        prefs.getString("last_cloud_backup_error", null)
    )
    val lastCloudBackupError: StateFlow<String?> = _lastCloudBackupError.asStateFlow()

    private val _autoBackupFrequency = MutableStateFlow(
        prefs.getString("auto_backup_frequency", "OFF") ?: "OFF" // OFF, DAILY, WEEKLY
    )
    val autoBackupFrequency: StateFlow<String> = _autoBackupFrequency.asStateFlow()

    private val _autoBackupWifiOnly = MutableStateFlow(
        prefs.getBoolean("auto_backup_wifi_only", true)
    )
    val autoBackupWifiOnly: StateFlow<Boolean> = _autoBackupWifiOnly.asStateFlow()

    // Profile Preferences
    private val _displayName = MutableStateFlow(
        prefs.getString("display_name", "") ?: ""
    )
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    private val _avatarColorHex = MutableStateFlow(
        prefs.getString("avatar_color_hex", "#6366F1") ?: "#6366F1"
    )
    val avatarColorHex: StateFlow<String> = _avatarColorHex.asStateFlow()

    private val _profilePictureUri = MutableStateFlow<String?>(
        prefs.getString("profile_picture_uri", null)
    )
    val profilePictureUri: StateFlow<String?> = _profilePictureUri.asStateFlow()

    // Notification Preferences
    private val _dueRemindersEnabled = MutableStateFlow(
        prefs.getBoolean("due_reminders_enabled", true)
    )
    val dueRemindersEnabled: StateFlow<Boolean> = _dueRemindersEnabled.asStateFlow()

    private val _budgetAlertsEnabled = MutableStateFlow(
        prefs.getBoolean("budget_alerts_enabled", true)
    )
    val budgetAlertsEnabled: StateFlow<Boolean> = _budgetAlertsEnabled.asStateFlow()

    // Display & Format Preferences
    private val _decimalPlaces = MutableStateFlow(
        prefs.getInt("decimal_places", 2)
    )
    val decimalPlaces: StateFlow<Int> = _decimalPlaces.asStateFlow()

    private val _weekStartDay = MutableStateFlow(
        prefs.getString("week_start_day", "MONDAY") ?: "MONDAY"
    )
    val weekStartDay: StateFlow<String> = _weekStartDay.asStateFlow()

    private val _dateFormat = MutableStateFlow(
        prefs.getString("date_format", "MMM dd, yyyy") ?: "MMM dd, yyyy"
    )
    val dateFormat: StateFlow<String> = _dateFormat.asStateFlow()

    // App Behavior Preferences
    private val _autoCategorize = MutableStateFlow(
        prefs.getBoolean("auto_categorize", true)
    )
    val autoCategorize: StateFlow<Boolean> = _autoCategorize.asStateFlow()

    private val _defaultTransactionType = MutableStateFlow(
        prefs.getString("default_transaction_type", "EXPENSE") ?: "EXPENSE"
    )
    val defaultTransactionType: StateFlow<String> = _defaultTransactionType.asStateFlow()

    private val _hapticFeedback = MutableStateFlow(
        prefs.getBoolean("haptic_feedback", true)
    )
    val hapticFeedback: StateFlow<Boolean> = _hapticFeedback.asStateFlow()

    // Update Preferences
    private val _autoCheckUpdates = MutableStateFlow(
        prefs.getBoolean("auto_check_updates", true)
    )
    val autoCheckUpdates: StateFlow<Boolean> = _autoCheckUpdates.asStateFlow()

    private val _skippedUpdateVersion = MutableStateFlow(
        prefs.getInt("skipped_update_version", 0)
    )
    val skippedUpdateVersion: StateFlow<Int> = _skippedUpdateVersion.asStateFlow()

    private val _lastUpdateCheckTime = MutableStateFlow(
        prefs.getLong("last_update_check_time", 0L)
    )
    val lastUpdateCheckTime: StateFlow<Long> = _lastUpdateCheckTime.asStateFlow()

    private fun getOrMigratePinHash(): String {
        val existingHash = prefs.getString("pin_code_hash", "") ?: ""
        if (existingHash.isNotBlank()) return existingHash
        val legacyPin = prefs.getString("pin_code", "") ?: ""
        if (legacyPin.isNotBlank()) {
            val hash = hashPin(legacyPin)
            prefs.edit().putString("pin_code_hash", hash).remove("pin_code").apply()
            return hash
        }
        return ""
    }

    fun hashPin(pin: String): String {
        if (pin.isBlank()) return ""
        val bytes = MessageDigest.getInstance("SHA-256").digest(pin.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun verifyPin(inputPin: String): Boolean {
        if (!_isPinLockEnabled.value) return true
        val currentHash = _pinCodeHash.value
        if (currentHash.isBlank()) return true
        return hashPin(inputPin) == currentHash || inputPin == currentHash
    }

    fun setCurrency(code: String, symbol: String) {
        prefs.edit()
            .putString("selected_currency", code)
            .putString("selected_currency_symbol", symbol)
            .apply()
        _currency.value = code
        _currencySymbol.value = symbol
    }

    fun setThemeMode(mode: String) {
        prefs.edit().putString("theme_mode", mode).apply()
        _themeMode.value = mode
    }

    fun setPinLock(enabled: Boolean, pin: String = "") {
        val hash = if (pin.isNotBlank()) hashPin(pin) else _pinCodeHash.value
        prefs.edit()
            .putBoolean("pin_lock_enabled", enabled)
            .putString("pin_code_hash", if (enabled) hash else "")
            .remove("pin_code")
            .apply()
        _isPinLockEnabled.value = enabled
        _pinCodeHash.value = if (enabled) hash else ""
    }

    fun setPinLockWithHash(enabled: Boolean, hash: String) {
        prefs.edit()
            .putBoolean("pin_lock_enabled", enabled)
            .putString("pin_code_hash", hash)
            .remove("pin_code")
            .apply()
        _isPinLockEnabled.value = enabled
        _pinCodeHash.value = hash
    }

    fun setGoogleAccount(email: String?, token: String? = null) {
        prefs.edit()
            .putString("google_account_email", email)
            .putString("google_auth_token", token)
            .apply()
        _googleAccountEmail.value = email
        _googleAuthToken.value = token
    }

    fun setDriveAccessToken(token: String?) {
        prefs.edit()
            .putString("google_drive_access_token", token)
            .apply()
        _googleDriveAccessToken.value = token
    }

    fun setCloudBackupResult(status: String, timestamp: Long = System.currentTimeMillis(), error: String? = null) {
        prefs.edit()
            .putString("last_cloud_backup_status", status)
            .putLong("last_cloud_backup_time", timestamp)
            .putString("last_cloud_backup_error", error)
            .apply()
        _lastCloudBackupStatus.value = status
        _lastCloudBackupTime.value = timestamp
        _lastCloudBackupError.value = error
    }

    fun setAutoBackupSettings(frequency: String, wifiOnly: Boolean) {
        prefs.edit()
            .putString("auto_backup_frequency", frequency)
            .putBoolean("auto_backup_wifi_only", wifiOnly)
            .apply()
        _autoBackupFrequency.value = frequency
        _autoBackupWifiOnly.value = wifiOnly
    }

    // --- Profile setters ---
    fun setDisplayName(name: String) {
        prefs.edit().putString("display_name", name).apply()
        _displayName.value = name
    }

    fun setAvatarColorHex(hex: String) {
        prefs.edit().putString("avatar_color_hex", hex).apply()
        _avatarColorHex.value = hex
    }

    fun setProfilePictureUri(uri: String?) {
        prefs.edit().putString("profile_picture_uri", uri).apply()
        _profilePictureUri.value = uri
    }

    // --- Notification setters ---
    fun setDueRemindersEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("due_reminders_enabled", enabled).apply()
        _dueRemindersEnabled.value = enabled
    }

    fun setBudgetAlertsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("budget_alerts_enabled", enabled).apply()
        _budgetAlertsEnabled.value = enabled
    }

    // --- Display & Format setters ---
    fun setDecimalPlaces(places: Int) {
        prefs.edit().putInt("decimal_places", places).apply()
        _decimalPlaces.value = places
    }

    fun setWeekStartDay(day: String) {
        prefs.edit().putString("week_start_day", day).apply()
        _weekStartDay.value = day
    }

    fun setDateFormat(format: String) {
        prefs.edit().putString("date_format", format).apply()
        _dateFormat.value = format
    }

    // --- App Behavior setters ---
    fun setAutoCategorize(enabled: Boolean) {
        prefs.edit().putBoolean("auto_categorize", enabled).apply()
        _autoCategorize.value = enabled
    }

    fun setDefaultTransactionType(type: String) {
        prefs.edit().putString("default_transaction_type", type).apply()
        _defaultTransactionType.value = type
    }

    fun setHapticFeedback(enabled: Boolean) {
        prefs.edit().putBoolean("haptic_feedback", enabled).apply()
        _hapticFeedback.value = enabled
    }

    // --- Update setters ---
    fun setAutoCheckUpdates(enabled: Boolean) {
        prefs.edit().putBoolean("auto_check_updates", enabled).apply()
        _autoCheckUpdates.value = enabled
    }

    fun setSkippedUpdateVersion(versionCode: Int) {
        prefs.edit().putInt("skipped_update_version", versionCode).apply()
        _skippedUpdateVersion.value = versionCode
    }

    fun setLastUpdateCheckTime(timestamp: Long = System.currentTimeMillis()) {
        prefs.edit().putLong("last_update_check_time", timestamp).apply()
        _lastUpdateCheckTime.value = timestamp
    }

    fun registerPrefChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterPrefChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    fun getAllPrefs(): Map<String, Any?> {
        return prefs.all
    }

    /** Guards against triggering SharedPreferences listener during cloud restore (prevents infinite loop). */
    @Volatile var isRestoringFromCloud: Boolean = false

    fun restorePrefs(map: Map<String, Any?>) {
        isRestoringFromCloud = true
        try {
            val editor = prefs.edit()
            map.forEach { (key, value) ->
                when (value) {
                    is String -> editor.putString(key, value)
                    is Boolean -> editor.putBoolean(key, value)
                    // Firestore returns all integers as Long; SharedPreferences uses Int for some keys.
                    is Long -> {
                        // Determine the correct type based on what's already stored.
                        // If the key holds an Int in prefs, write as Int; otherwise write as Long.
                        if (prefs.contains(key)) {
                            try {
                                editor.putInt(key, value.toInt())
                            } catch (_: Exception) {
                                editor.putLong(key, value)
                            }
                        } else {
                            editor.putLong(key, value)
                        }
                    }
                    is Int -> editor.putInt(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Double -> editor.putFloat(key, value.toFloat())
                }
            }
            editor.apply()

            // Update all StateFlows so the UI reacts immediately
            _currency.value = prefs.getString("selected_currency", "USD") ?: "USD"
            _currencySymbol.value = prefs.getString("selected_currency_symbol", "$") ?: "$"
            _themeMode.value = prefs.getString("theme_mode", "SYSTEM") ?: "SYSTEM"
            _decimalPlaces.value = prefs.getInt("decimal_places", 2)
            _weekStartDay.value = prefs.getString("week_start_day", "MONDAY") ?: "MONDAY"
            _dateFormat.value = prefs.getString("date_format", "MMM dd, yyyy") ?: "MMM dd, yyyy"
            _autoCategorize.value = prefs.getBoolean("auto_categorize", true)
            _defaultTransactionType.value = prefs.getString("default_transaction_type", "EXPENSE") ?: "EXPENSE"
            _hapticFeedback.value = prefs.getBoolean("haptic_feedback", true)
            _displayName.value = prefs.getString("display_name", "") ?: ""
            _avatarColorHex.value = prefs.getString("avatar_color_hex", "#6366F1") ?: "#6366F1"
            _profilePictureUri.value = prefs.getString("profile_picture_uri", null)
        } finally {
            isRestoringFromCloud = false
        }
    }

    fun getUserSettingsBackup(): UserSettingsBackup {
        return UserSettingsBackup(
            currency = _currency.value,
            currencySymbol = _currencySymbol.value,
            themeMode = _themeMode.value,
            isPinLockEnabled = _isPinLockEnabled.value,
            pinCodeHash = _pinCodeHash.value, // Hashed PIN only, no raw secret
            autoBackupFrequency = _autoBackupFrequency.value,
            autoBackupWifiOnly = _autoBackupWifiOnly.value
        )
    }

    fun applyUserSettingsBackup(settings: UserSettingsBackup) {
        setCurrency(settings.currency, settings.currencySymbol)
        setThemeMode(settings.themeMode)
        if (settings.isPinLockEnabled && settings.pinCodeHash.isNotBlank()) {
            setPinLockWithHash(true, settings.pinCodeHash)
        } else {
            setPinLock(false, "")
        }
        setAutoBackupSettings(settings.autoBackupFrequency, settings.autoBackupWifiOnly)
    }
}
