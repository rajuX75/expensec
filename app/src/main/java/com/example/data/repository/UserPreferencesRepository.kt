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

    // BUG FIX #6: stale/corrupt preferences left by a previous app version can store
    // a value under a key with a different type than this version expects (e.g. an Int
    // where a String is now read). SharedPreferences then throws ClassCastException
    // during property initialisation and the app crashes on launch — the "must clear
    // data after update" symptom. These safe readers return (and re-write) the
    // default instead, self-healing the corrupted entry.
    private fun safeString(key: String, default: String): String = try {
        prefs.getString(key, default) ?: default
    } catch (e: Exception) {
        try { prefs.edit().remove(key).apply() } catch (_: Exception) {}
        default
    }

    private fun safeStringOrNull(key: String): String? = try {
        prefs.getString(key, null)
    } catch (e: Exception) {
        try { prefs.edit().remove(key).apply() } catch (_: Exception) {}
        null
    }

    private fun safeBoolean(key: String, default: Boolean): Boolean = try {
        prefs.getBoolean(key, default)
    } catch (e: Exception) {
        try { prefs.edit().remove(key).apply() } catch (_: Exception) {}
        default
    }

    private fun safeInt(key: String, default: Int): Int = try {
        prefs.getInt(key, default)
    } catch (e: Exception) {
        try { prefs.edit().remove(key).apply() } catch (_: Exception) {}
        default
    }

    private fun safeLong(key: String, default: Long): Long = try {
        prefs.getLong(key, default)
    } catch (e: Exception) {
        try { prefs.edit().remove(key).apply() } catch (_: Exception) {}
        default
    }

    private val _currency = MutableStateFlow(
        safeString("selected_currency", "USD")
    )
    val currency: StateFlow<String> = _currency.asStateFlow()

    private val _currencySymbol = MutableStateFlow(
        safeString("selected_currency_symbol", "$")
    )
    val currencySymbol: StateFlow<String> = _currencySymbol.asStateFlow()

    private val _themeMode = MutableStateFlow(
        safeString("theme_mode", "SYSTEM") // SYSTEM, LIGHT, DARK
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _isPinLockEnabled = MutableStateFlow(
        safeBoolean("pin_lock_enabled", false)
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
        safeStringOrNull("google_account_email")
    )
    val googleAccountEmail: StateFlow<String?> = _googleAccountEmail.asStateFlow()

    // Firebase Google ID token (used only for Firebase Auth hand-off)
    private val _googleAuthToken = MutableStateFlow<String?>(
        safeStringOrNull("google_auth_token")
    )
    val googleAuthToken: StateFlow<String?> = _googleAuthToken.asStateFlow()

    // Google Drive OAuth access token (the token that the Drive REST API actually accepts)
    private val _googleDriveAccessToken = MutableStateFlow<String?>(
        safeStringOrNull("google_drive_access_token")
    )
    val googleDriveAccessToken: StateFlow<String?> = _googleDriveAccessToken.asStateFlow()

    private val _lastCloudBackupTime = MutableStateFlow(
        safeLong("last_cloud_backup_time", 0L)
    )
    val lastCloudBackupTime: StateFlow<Long> = _lastCloudBackupTime.asStateFlow()

    private val _lastCloudBackupStatus = MutableStateFlow(
        safeString("last_cloud_backup_status", "NEVER") // SUCCESS, FAILED, NEVER
    )
    val lastCloudBackupStatus: StateFlow<String> = _lastCloudBackupStatus.asStateFlow()

    private val _lastCloudBackupError = MutableStateFlow<String?>(
        safeStringOrNull("last_cloud_backup_error")
    )
    val lastCloudBackupError: StateFlow<String?> = _lastCloudBackupError.asStateFlow()

    private val _autoBackupFrequency = MutableStateFlow(
        safeString("auto_backup_frequency", "OFF") // OFF, DAILY, WEEKLY
    )
    val autoBackupFrequency: StateFlow<String> = _autoBackupFrequency.asStateFlow()

    private val _autoBackupWifiOnly = MutableStateFlow(
        safeBoolean("auto_backup_wifi_only", true)
    )
    val autoBackupWifiOnly: StateFlow<Boolean> = _autoBackupWifiOnly.asStateFlow()

    // Profile Preferences
    private val _displayName = MutableStateFlow(
        safeString("display_name", "")
    )
    val displayName: StateFlow<String> = _displayName.asStateFlow()

    private val _avatarColorHex = MutableStateFlow(
        safeString("avatar_color_hex", "#6366F1")
    )
    val avatarColorHex: StateFlow<String> = _avatarColorHex.asStateFlow()

    private val _profilePictureUri = MutableStateFlow<String?>(
        safeStringOrNull("profile_picture_uri")
    )
    val profilePictureUri: StateFlow<String?> = _profilePictureUri.asStateFlow()

    // Cloudinary Preferences
    private val _cloudinaryCloudName = MutableStateFlow(
        safeString("cloudinary_cloud_name", "")
    )
    val cloudinaryCloudName: StateFlow<String> = _cloudinaryCloudName.asStateFlow()

    private val _cloudinaryApiKey = MutableStateFlow(
        safeString("cloudinary_api_key", "")
    )
    val cloudinaryApiKey: StateFlow<String> = _cloudinaryApiKey.asStateFlow()

    private val _cloudinaryApiSecret = MutableStateFlow(
        safeString("cloudinary_api_secret", "")
    )
    val cloudinaryApiSecret: StateFlow<String> = _cloudinaryApiSecret.asStateFlow()

    private val _cloudinaryUploadPreset = MutableStateFlow(
        safeString("cloudinary_upload_preset", "")
    )
    val cloudinaryUploadPreset: StateFlow<String> = _cloudinaryUploadPreset.asStateFlow()

    // Notification Preferences
    private val _dueRemindersEnabled = MutableStateFlow(
        safeBoolean("due_reminders_enabled", true)
    )
    val dueRemindersEnabled: StateFlow<Boolean> = _dueRemindersEnabled.asStateFlow()

    private val _budgetAlertsEnabled = MutableStateFlow(
        safeBoolean("budget_alerts_enabled", true)
    )
    val budgetAlertsEnabled: StateFlow<Boolean> = _budgetAlertsEnabled.asStateFlow()

    // Display & Format Preferences
    private val _decimalPlaces = MutableStateFlow(
        safeInt("decimal_places", 2)
    )
    val decimalPlaces: StateFlow<Int> = _decimalPlaces.asStateFlow()

    private val _weekStartDay = MutableStateFlow(
        safeString("week_start_day", "MONDAY")
    )
    val weekStartDay: StateFlow<String> = _weekStartDay.asStateFlow()

    private val _dateFormat = MutableStateFlow(
        safeString("date_format", "MMM dd, yyyy")
    )
    val dateFormat: StateFlow<String> = _dateFormat.asStateFlow()

    // App Behavior Preferences
    private val _autoCategorize = MutableStateFlow(
        safeBoolean("auto_categorize", true)
    )
    val autoCategorize: StateFlow<Boolean> = _autoCategorize.asStateFlow()

    private val _defaultTransactionType = MutableStateFlow(
        safeString("default_transaction_type", "EXPENSE")
    )
    val defaultTransactionType: StateFlow<String> = _defaultTransactionType.asStateFlow()

    private val _hapticFeedback = MutableStateFlow(
        safeBoolean("haptic_feedback", true)
    )
    val hapticFeedback: StateFlow<Boolean> = _hapticFeedback.asStateFlow()

    // Update Preferences
    private val _autoCheckUpdates = MutableStateFlow(
        safeBoolean("auto_check_updates", true)
    )
    val autoCheckUpdates: StateFlow<Boolean> = _autoCheckUpdates.asStateFlow()

    private val _skippedUpdateVersion = MutableStateFlow(
        safeInt("skipped_update_version", 0)
    )
    val skippedUpdateVersion: StateFlow<Int> = _skippedUpdateVersion.asStateFlow()

    private val _lastUpdateCheckTime = MutableStateFlow(
        safeLong("last_update_check_time", 0L)
    )
    val lastUpdateCheckTime: StateFlow<Long> = _lastUpdateCheckTime.asStateFlow()

    // In-App Notification Preferences (per-device local state for RTDB notifications)
    private val _readNotificationIds = MutableStateFlow(safeStringSet("read_notification_ids"))
    val readNotificationIds: StateFlow<Set<String>> = _readNotificationIds.asStateFlow()

    private val _deletedNotificationIds = MutableStateFlow(safeStringSet("deleted_notification_ids"))
    val deletedNotificationIds: StateFlow<Set<String>> = _deletedNotificationIds.asStateFlow()

    private val _lastSeenPopupNotificationId = MutableStateFlow<String?>(
        safeStringOrNull("last_seen_popup_notification_id")
    )
    val lastSeenPopupNotificationId: StateFlow<String?> = _lastSeenPopupNotificationId.asStateFlow()

    private fun getOrMigratePinHash(): String {
        val existingHash = safeString("pin_code_hash", "")
        if (existingHash.isNotBlank()) return existingHash
        val legacyPin = safeString("pin_code", "")
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

    // --- Cloudinary Configuration setter ---
    fun setCloudinaryConfig(
        cloudName: String,
        apiKey: String,
        apiSecret: String,
        uploadPreset: String
    ) {
        prefs.edit()
            .putString("cloudinary_cloud_name", cloudName.trim())
            .putString("cloudinary_api_key", apiKey.trim())
            .putString("cloudinary_api_secret", apiSecret.trim())
            .putString("cloudinary_upload_preset", uploadPreset.trim())
            .apply()
        _cloudinaryCloudName.value = cloudName.trim()
        _cloudinaryApiKey.value = apiKey.trim()
        _cloudinaryApiSecret.value = apiSecret.trim()
        _cloudinaryUploadPreset.value = uploadPreset.trim()
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

    // --- In-App Notification setters ---
    private fun safeStringSet(key: String): Set<String> = try {
        prefs.getStringSet(key, emptySet())?.toSet() ?: emptySet()
    } catch (e: Exception) {
        try { prefs.edit().remove(key).apply() } catch (_: Exception) {}
        emptySet()
    }

    fun addReadNotificationId(id: String) {
        if (id.isBlank()) return
        val updated = _readNotificationIds.value + id
        prefs.edit().putStringSet("read_notification_ids", updated).apply()
        _readNotificationIds.value = updated
    }

    fun addReadNotificationIds(ids: Set<String>) {
        val valid = ids.filter { it.isNotBlank() }
        if (valid.isEmpty()) return
        val updated = _readNotificationIds.value + valid
        prefs.edit().putStringSet("read_notification_ids", updated).apply()
        _readNotificationIds.value = updated
    }

    fun addDeletedNotificationId(id: String) {
        if (id.isBlank()) return
        val updated = _deletedNotificationIds.value + id
        prefs.edit().putStringSet("deleted_notification_ids", updated).apply()
        _deletedNotificationIds.value = updated
    }

    fun addDeletedNotificationIds(ids: Set<String>) {
        val valid = ids.filter { it.isNotBlank() }
        if (valid.isEmpty()) return
        val updated = _deletedNotificationIds.value + valid
        prefs.edit().putStringSet("deleted_notification_ids", updated).apply()
        _deletedNotificationIds.value = updated
    }

    fun setLastSeenPopupNotificationId(id: String?) {
        prefs.edit().putString("last_seen_popup_notification_id", id).apply()
        _lastSeenPopupNotificationId.value = id
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
            // BUG FIX #6: safe readers here too — a malformed cloud restore payload
            // must refresh the StateFlows with defaults instead of crashing.
            _currency.value = safeString("selected_currency", "USD")
            _currencySymbol.value = safeString("selected_currency_symbol", "$")
            _themeMode.value = safeString("theme_mode", "SYSTEM")
            _decimalPlaces.value = safeInt("decimal_places", 2)
            _weekStartDay.value = safeString("week_start_day", "MONDAY")
            _dateFormat.value = safeString("date_format", "MMM dd, yyyy")
            _autoCategorize.value = safeBoolean("auto_categorize", true)
            _defaultTransactionType.value = safeString("default_transaction_type", "EXPENSE")
            _hapticFeedback.value = safeBoolean("haptic_feedback", true)
            _displayName.value = safeString("display_name", "")
            _avatarColorHex.value = safeString("avatar_color_hex", "#6366F1")
            _profilePictureUri.value = safeStringOrNull("profile_picture_uri")
            _cloudinaryCloudName.value = safeString("cloudinary_cloud_name", "")
            _cloudinaryApiKey.value = safeString("cloudinary_api_key", "")
            _cloudinaryApiSecret.value = safeString("cloudinary_api_secret", "")
            _cloudinaryUploadPreset.value = safeString("cloudinary_upload_preset", "")
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
