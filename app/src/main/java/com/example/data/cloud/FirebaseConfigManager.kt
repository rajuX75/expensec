package com.example.data.cloud

import android.content.Context
import android.util.Log
import com.example.data.model.*
import com.google.firebase.database.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class FirebaseConfigManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "FirebaseConfigManager"
        private const val REST_DATABASE_FALLBACK_URL = "https://expenstracke-default-rtdb.firebaseio.com"

        // BUG FIX #4: process-wide guard — persistence may only be enabled once per
        // process, no matter how many manager instances are created.
        private val persistenceAttempted = java.util.concurrent.atomic.AtomicBoolean(false)
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private var database: FirebaseDatabase? = null
    private var versionRef: DatabaseReference? = null
    private var configRef: DatabaseReference? = null
    private var changelogRef: DatabaseReference? = null
    private var notificationsRef: DatabaseReference? = null

    private val _remoteConfig = MutableStateFlow(AppRemoteConfig())
    val remoteConfig: StateFlow<AppRemoteConfig> = _remoteConfig.asStateFlow()

    private val _remoteUpdateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    val remoteUpdateInfo: StateFlow<AppUpdateInfo?> = _remoteUpdateInfo.asStateFlow()

    private val _releaseHistory = MutableStateFlow<List<VersionReleaseLog>>(emptyList())
    val releaseHistory: StateFlow<List<VersionReleaseLog>> = _releaseHistory.asStateFlow()

    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    init {
        database = try {
            val instance = FirebaseDatabase.getInstance()
            // BUG FIX #4: setPersistenceEnabled(true) throws IllegalStateException if
            // called a second time in the same process (guaranteed on app updates).
            // The AtomicBoolean makes the call happen exactly once per process, and
            // any failure is logged but never leaves `database` in a broken state.
            if (persistenceAttempted.compareAndSet(false, true)) {
                try {
                    instance.setPersistenceEnabled(true)
                } catch (ise: IllegalStateException) {
                    Log.w(TAG, "Firebase persistence already enabled: ${ise.message}")
                } catch (e: Exception) {
                    Log.w(TAG, "Could not enable Firebase persistence", e)
                }
            }
            instance
        } catch (e: Exception) {
            Log.w(TAG, "Firebase Realtime Database init failed: ${e.message}. Using REST sync fallback.")
            null
        }

        if (database != null) {
            versionRef = database?.getReference("app_version")
            configRef = database?.getReference("app_config")
            changelogRef = database?.getReference("changelog")
            notificationsRef = database?.getReference("notifications")
            try {
                setupRealtimeListeners()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to attach realtime listeners: ${e.message}. Using REST fallback.")
                fetchViaRest()
            }
        } else {
            // Native SDK unusable (e.g. google-services.json not injected) — the REST
            // fallback still provides remote config / update info instead of crashing.
            fetchViaRest()
        }
    }

    private fun setupRealtimeListeners() {
        // 1. App Version & Update Info listener
        versionRef?.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val updateInfo = parseUpdateInfoFromSnapshot(snapshot)
                    if (updateInfo != null) {
                        _remoteUpdateInfo.value = updateInfo
                        _isConnected.value = true
                        Log.d(TAG, "Realtime DB: App version updated -> ${updateInfo.versionName} (${updateInfo.versionCode})")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing app_version from Realtime DB", e)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Version listener cancelled: ${error.message}")
                fetchViaRest()
            }
        })

        // 2. App Config & Remote Settings listener
        configRef?.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val config = parseAppConfigFromSnapshot(snapshot)
                    if (config != null) {
                        _remoteConfig.value = config
                        _isConnected.value = true
                        Log.d(TAG, "Realtime DB: App config updated -> maintenance=${config.maintenanceMode}")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing app_config from Realtime DB", e)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Config listener cancelled: ${error.message}")
            }
        })

        // 3. Changelog history listener
        changelogRef?.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    val logs = parseChangelogFromSnapshot(snapshot)
                    if (logs.isNotEmpty()) {
                        _releaseHistory.value = logs
                        Log.d(TAG, "Realtime DB: Loaded ${logs.size} changelog releases")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing changelog from Realtime DB", e)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Changelog listener cancelled: ${error.message}")
            }
        })

        // 4. Admin notifications inbox listener
        notificationsRef?.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                try {
                    _notifications.value = parseNotificationsFromSnapshot(snapshot)
                    _isConnected.value = true
                    Log.d(TAG, "Realtime DB: Loaded ${_notifications.value.size} notifications")
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing notifications from Realtime DB", e)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, "Notifications listener cancelled: ${error.message}")
            }
        })
    }

    /**
     * Fallback to fetch data over Firebase Realtime DB REST API (.json)
     */
    fun fetchViaRest() {
        scope.launch(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$REST_DATABASE_FALLBACK_URL/.json")
                    .header("Accept", "application/json")
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (!body.isNullOrBlank()) {
                            val rootJson = JSONObject(body)

                            // Parse app_version
                            if (rootJson.has("app_version")) {
                                val vJson = rootJson.getJSONObject("app_version")
                                val info = parseUpdateInfoFromJson(vJson)
                                _remoteUpdateInfo.value = info
                            }

                            // Parse app_config
                            if (rootJson.has("app_config")) {
                                val cJson = rootJson.getJSONObject("app_config")
                                val config = parseAppConfigFromJson(cJson)
                                _remoteConfig.value = config
                            }

                            // Parse changelog
                            if (rootJson.has("changelog")) {
                                val chJson = rootJson.getJSONObject("changelog")
                                val logs = parseChangelogFromJson(chJson)
                                if (logs.isNotEmpty()) {
                                    _releaseHistory.value = logs
                                }
                            }

                            // Parse notifications inbox
                            if (rootJson.has("notifications")) {
                                _notifications.value = parseNotificationsFromJson(rootJson.getJSONObject("notifications"))
                            }

                            _isConnected.value = true
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "REST fallback fetch error: ${e.message}")
            }
        }
    }

    private fun parseUpdateInfoFromSnapshot(snapshot: DataSnapshot): AppUpdateInfo? {
        if (!snapshot.exists()) return null
        val code = snapshot.child("versionCode").getValue(Long::class.java)?.toInt() ?: 1
        val name = snapshot.child("versionName").getValue(String::class.java) ?: "1.0.0"
        val minCode = snapshot.child("minSupportedVersionCode").getValue(Long::class.java)?.toInt() ?: 1
        val title = snapshot.child("releaseTitle").getValue(String::class.java) ?: "v$name Update"
        val notes = snapshot.child("releaseNotes").getValue(String::class.java) ?: ""
        val url = snapshot.child("downloadUrl").getValue(String::class.java)
            ?: "https://github.com/rajuX75/expensec/releases/latest/download/expense-tracker-release.apk"
        val date = snapshot.child("releaseDate").getValue(String::class.java) ?: "Latest"
        val size = snapshot.child("apkSizeMb").getValue(Double::class.java) ?: 20.1
        val mandatory = snapshot.child("isMandatory").getValue(Boolean::class.java) ?: false

        val changelogList = mutableListOf<ChangelogItem>()
        val changesSnap = snapshot.child("changes")
        if (changesSnap.exists()) {
            for (child in changesSnap.children) {
                val text = child.getValue(String::class.java) ?: continue
                val type = detectChangelogType(text)
                changelogList.add(ChangelogItem(type, text))
            }
        }

        return AppUpdateInfo(
            versionCode = code,
            versionName = name,
            minSupportedVersionCode = minCode,
            releaseTitle = title,
            releaseNotes = notes,
            changelog = changelogList,
            downloadUrl = url,
            releaseDate = date,
            apkSizeMb = size,
            isMandatory = mandatory
        )
    }

    private fun parseAppConfigFromSnapshot(snapshot: DataSnapshot): AppRemoteConfig? {
        if (!snapshot.exists()) return null
        val appName = snapshot.child("appName").getValue(String::class.java) ?: "Expense Tracker"
        val supportEmail = snapshot.child("supportEmail").getValue(String::class.java) ?: "support@expensex.app"
        val privacyUrl = snapshot.child("privacyPolicyUrl").getValue(String::class.java) ?: "https://github.com/rajuX75/expensec/blob/main/PRIVACY_POLICY.md"
        val termsUrl = snapshot.child("termsUrl").getValue(String::class.java) ?: "https://github.com/rajuX75/expensec/blob/main/TERMS.md"
        val repoUrl = snapshot.child("githubRepoUrl").getValue(String::class.java) ?: "https://github.com/rajuX75/expensec"
        val currency = snapshot.child("defaultCurrency").getValue(String::class.java) ?: "USD"
        val maintenance = snapshot.child("maintenanceMode").getValue(Boolean::class.java) ?: false
        val maintMsg = snapshot.child("maintenanceMessage").getValue(String::class.java) ?: ""

        var announcement: AnnouncementBanner? = null
        val annSnap = snapshot.child("announcement")
        if (annSnap.exists()) {
            val id = annSnap.child("id").getValue(String::class.java) ?: ""
            val title = annSnap.child("title").getValue(String::class.java) ?: ""
            val msg = annSnap.child("message").getValue(String::class.java) ?: ""
            val actionUrl = annSnap.child("actionUrl").getValue(String::class.java)
            val actionText = annSnap.child("actionText").getValue(String::class.java)
            val type = annSnap.child("type").getValue(String::class.java) ?: "INFO"
            val active = annSnap.child("active").getValue(Boolean::class.java) ?: false
            val dismissible = annSnap.child("dismissible").getValue(Boolean::class.java) ?: true

            announcement = AnnouncementBanner(id, title, msg, actionUrl, actionText, type, active, dismissible)
        }

        return AppRemoteConfig(appName, supportEmail, privacyUrl, termsUrl, repoUrl, currency, maintenance, maintMsg, announcement)
    }

    private fun parseChangelogFromSnapshot(snapshot: DataSnapshot): List<VersionReleaseLog> {
        val list = mutableListOf<VersionReleaseLog>()
        for (child in snapshot.children) {
            val code = child.child("versionCode").getValue(Long::class.java)?.toInt() ?: 1
            val name = child.child("versionName").getValue(String::class.java) ?: child.key ?: ""
            val date = child.child("releaseDate").getValue(String::class.java) ?: ""
            val title = child.child("title").getValue(String::class.java) ?: "Release $name"

            val items = mutableListOf<ChangelogItem>()
            val changesSnap = child.child("changes")
            for (c in changesSnap.children) {
                val text = c.getValue(String::class.java) ?: continue
                items.add(ChangelogItem(detectChangelogType(text), text))
            }

            list.add(VersionReleaseLog(versionName = name, versionCode = code, releaseDate = date, title = title, changes = items))
        }
        return list.sortedByDescending { it.versionCode }
    }

    private fun parseUpdateInfoFromJson(json: JSONObject): AppUpdateInfo {
        val code = json.optInt("versionCode", 1)
        val name = json.optString("versionName", "1.0.0")
        val minCode = json.optInt("minSupportedVersionCode", 1)
        val title = json.optString("releaseTitle", "v$name Update")
        val notes = json.optString("releaseNotes", "")
        val url = json.optString("downloadUrl", "https://github.com/rajuX75/expensec/releases/latest/download/expense-tracker-release.apk")
        val date = json.optString("releaseDate", "Latest")
        val size = json.optDouble("apkSizeMb", 20.1)
        val mandatory = json.optBoolean("isMandatory", false)

        val changelogList = mutableListOf<ChangelogItem>()
        val arr = json.optJSONArray("changes")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val text = arr.optString(i)
                if (text.isNotBlank()) changelogList.add(ChangelogItem(detectChangelogType(text), text))
            }
        }

        return AppUpdateInfo(code, name, minCode, title, notes, changelogList, url, date, size, mandatory)
    }

    private fun parseAppConfigFromJson(json: JSONObject): AppRemoteConfig {
        val appName = json.optString("appName", "Expense Tracker")
        val supportEmail = json.optString("supportEmail", "support@expensex.app")
        val privacyUrl = json.optString("privacyPolicyUrl", "https://github.com/rajuX75/expensec/blob/main/PRIVACY_POLICY.md")
        val termsUrl = json.optString("termsUrl", "https://github.com/rajuX75/expensec/blob/main/TERMS.md")
        val repoUrl = json.optString("githubRepoUrl", "https://github.com/rajuX75/expensec")
        val currency = json.optString("defaultCurrency", "USD")
        val maintenance = json.optBoolean("maintenanceMode", false)
        val maintMsg = json.optString("maintenanceMessage", "")

        var announcement: AnnouncementBanner? = null
        if (json.has("announcement")) {
            val aJson = json.getJSONObject("announcement")
            announcement = AnnouncementBanner(
                id = aJson.optString("id", ""),
                title = aJson.optString("title", ""),
                message = aJson.optString("message", ""),
                actionUrl = aJson.optString("actionUrl").takeIf { it.isNotBlank() },
                actionText = aJson.optString("actionText").takeIf { it.isNotBlank() },
                type = aJson.optString("type", "INFO"),
                active = aJson.optBoolean("active", false),
                dismissible = aJson.optBoolean("dismissible", true)
            )
        }

        return AppRemoteConfig(appName, supportEmail, privacyUrl, termsUrl, repoUrl, currency, maintenance, maintMsg, announcement)
    }

    private fun parseChangelogFromJson(json: JSONObject): List<VersionReleaseLog> {
        val list = mutableListOf<VersionReleaseLog>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val child = json.optJSONObject(key) ?: continue
            val code = child.optInt("versionCode", 1)
            val name = child.optString("versionName", key)
            val date = child.optString("releaseDate", "")
            val title = child.optString("title", "Release $name")

            val items = mutableListOf<ChangelogItem>()
            val changesArr = child.optJSONArray("changes")
            if (changesArr != null) {
                for (i in 0 until changesArr.length()) {
                    val text = changesArr.optString(i)
                    if (text.isNotBlank()) items.add(ChangelogItem(detectChangelogType(text), text))
                }
            }
            list.add(VersionReleaseLog(name, code, date, title, changes = items))
        }
        return list.sortedByDescending { it.versionCode }
    }

    private fun parseNotificationsFromSnapshot(snapshot: DataSnapshot): List<AppNotification> {
        val list = mutableListOf<AppNotification>()
        for (child in snapshot.children) {
            try {
                val n = parseNotificationSnapshot(child) ?: continue
                list.add(n)
            } catch (e: Exception) {
                Log.w(TAG, "Skipping malformed notification ${child.key}: ${e.message}")
            }
        }
        return list
    }

    private fun parseNotificationSnapshot(child: DataSnapshot): AppNotification? {
        val title = child.child("title").getValue(String::class.java)
            ?: child.child("message").getValue(String::class.java)
            ?: return null
        val id = child.child("id").getValue(String::class.java) ?: child.key ?: return null
        val message = child.child("message").getValue(String::class.java) ?: ""
        val type = NotificationType.fromString(child.child("type").getValue(String::class.java))
        val timestamp = child.child("timestamp").getValue(Long::class.java)
            ?: child.child("createdAt").getValue(Long::class.java)
            ?: 0L
        val actionUrl = child.child("actionUrl").getValue(String::class.java)?.takeIf { it.isNotBlank() }
        val actionText = child.child("actionText").getValue(String::class.java)?.takeIf { it.isNotBlank() }
        val iconEmoji = (child.child("iconEmoji").getValue(String::class.java)
            ?: child.child("emoji").getValue(String::class.java))?.takeIf { it.isNotBlank() }
        val accentColorHex = (child.child("accentColorHex").getValue(String::class.java)
            ?: child.child("color").getValue(String::class.java))?.takeIf { AppNotification.parseHexColor(it) != null }
        val showPopup = child.child("showPopup").getValue(Boolean::class.java) ?: false
        val dismissible = child.child("dismissible").getValue(Boolean::class.java) ?: true
        val active = child.child("active").getValue(Boolean::class.java) ?: true

        return AppNotification(id, title, message, type, timestamp, actionUrl, actionText, iconEmoji, accentColorHex, showPopup, dismissible, active)
    }

    private fun parseNotificationsFromJson(json: JSONObject): List<AppNotification> {
        val list = mutableListOf<AppNotification>()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val child = json.optJSONObject(key) ?: continue
            try {
                val title = child.optString("title", "").ifBlank { child.optString("message", "") }
                if (title.isBlank()) continue
                list.add(
                    AppNotification(
                        id = child.optString("id", "").ifBlank { key },
                        title = title,
                        message = child.optString("message", ""),
                        type = NotificationType.fromString(child.optString("type", "INFO")),
                        timestamp = child.optLong("timestamp", child.optLong("createdAt", 0L)),
                        actionUrl = child.optString("actionUrl").takeIf { it.isNotBlank() },
                        actionText = child.optString("actionText").takeIf { it.isNotBlank() },
                        iconEmoji = child.optString("iconEmoji", child.optString("emoji", "")).takeIf { it.isNotBlank() },
                        accentColorHex = child.optString("accentColorHex", child.optString("color", ""))
                            .takeIf { it.isNotBlank() && AppNotification.parseHexColor(it) != null },
                        showPopup = child.optBoolean("showPopup", false),
                        dismissible = child.optBoolean("dismissible", true),
                        active = child.optBoolean("active", true)
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Skipping malformed notification $key: ${e.message}")
            }
        }
        return list
    }

    private fun detectChangelogType(text: String): ChangelogType {
        val lower = text.lowercase()
        return when {
            lower.contains("fix") || lower.contains("bug") || lower.contains("crash") || lower.contains("resolved") -> ChangelogType.FIX
            lower.contains("new") || lower.contains("add") || lower.contains("feature") -> ChangelogType.FEATURE
            else -> ChangelogType.IMPROVEMENT
        }
    }
}
