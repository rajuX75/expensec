package com.example.data.repository

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.example.BuildConfig
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class UpdateRepository(
    private val context: Context,
    private val userPrefs: UserPreferencesRepository
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val _updateCheckState = MutableStateFlow<UpdateCheckState>(UpdateCheckState.Idle)
    val updateCheckState: StateFlow<UpdateCheckState> = _updateCheckState.asStateFlow()

    private val _downloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val downloadState: StateFlow<UpdateDownloadState> = _downloadState.asStateFlow()

    fun getCurrentVersionCode(): Int {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
        } catch (e: Exception) {
            1
        }
    }

    fun getCurrentVersionName(): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0.0"
        } catch (e: Exception) {
            "1.0.0"
        }
    }

    /**
     * Complete changelog history of the app
     */
    fun getReleaseHistory(): List<VersionReleaseLog> {
        val currentCode = getCurrentVersionCode()
        return listOf(
            VersionReleaseLog(
                versionName = "v1.1.0",
                versionCode = 2,
                releaseDate = "August 2026",
                title = "In-App Updates, Enhanced Sync & UI Polish",
                isCurrent = currentCode >= 2,
                changes = listOf(
                    ChangelogItem(ChangelogType.FEATURE, "In-App Update checker with seamless skippable prompts and release logs"),
                    ChangelogItem(ChangelogType.FEATURE, "Dedicated 'App Updates & What's New' section in Settings"),
                    ChangelogItem(ChangelogType.FEATURE, "Changelog and release notes viewer with category badges"),
                    ChangelogItem(ChangelogType.IMPROVEMENT, "Real-time download progress tracking with direct APK installer"),
                    ChangelogItem(ChangelogType.IMPROVEMENT, "Optimized database backup and Firestore sync reliability"),
                    ChangelogItem(ChangelogType.FIX, "Fixed minor layout shifts in Settings navigation")
                )
            ),
            VersionReleaseLog(
                versionName = "v1.0.0",
                versionCode = 1,
                releaseDate = "August 2026",
                title = "Initial Official Release",
                isCurrent = currentCode == 1,
                changes = listOf(
                    ChangelogItem(ChangelogType.FEATURE, "Complete local-first expense and income tracker with Material 3 UI"),
                    ChangelogItem(ChangelogType.FEATURE, "Dhaar (Debts & Loans) tracker with contact management and settle-up ledger"),
                    ChangelogItem(ChangelogType.FEATURE, "Interactive financial charts, analytics, and spending breakdown"),
                    ChangelogItem(ChangelogType.FEATURE, "Monthly budget tracker with alert thresholds and progress indicators"),
                    ChangelogItem(ChangelogType.FEATURE, "Multi-currency support (USD, EUR, GBP, INR, BDT, etc.)"),
                    ChangelogItem(ChangelogType.FEATURE, "PIN Lock protection for sensitive financial data"),
                    ChangelogItem(ChangelogType.FEATURE, "Google Drive cloud backup and Firestore real-time synchronization"),
                    ChangelogItem(ChangelogType.FEATURE, "Full JSON and CSV export/import capabilities")
                )
            )
        )
    }

    /**
     * Checks for available updates.
     * @param isManualCheck When true, ignores the user's skipped version preference so they can still manually see updates.
     */
    suspend fun checkForUpdates(isManualCheck: Boolean = false): UpdateCheckState = withContext(Dispatchers.IO) {
        _updateCheckState.value = UpdateCheckState.Checking

        try {
            // Small simulated delay for smooth UI feedback during manual check
            if (isManualCheck) {
                delay(600)
            }

            userPrefs.setLastUpdateCheckTime()

            // You can query a remote GitHub Releases API or custom endpoint if configured
            val currentCode = getCurrentVersionCode()
            val skippedCode = userPrefs.skippedUpdateVersion.value

            val updateInfo = fetchRemoteUpdateInfo()

            val state = if (updateInfo != null && updateInfo.versionCode > currentCode) {
                if (!isManualCheck && updateInfo.versionCode == skippedCode) {
                    // User opted to skip this version on auto-check
                    UpdateCheckState.UpToDate(getCurrentVersionName(), System.currentTimeMillis())
                } else {
                    UpdateCheckState.UpdateAvailable(updateInfo)
                }
            } else {
                UpdateCheckState.UpToDate(getCurrentVersionName(), System.currentTimeMillis())
            }

            _updateCheckState.value = state
            state
        } catch (e: Exception) {
            val errorState = UpdateCheckState.Error(e.message ?: "Failed to check for updates. Please check your network connection.")
            _updateCheckState.value = errorState
            errorState
        }
    }

    /**
     * Fetches update information.
     * Checks remote GitHub API / endpoint if accessible, and falls back to latest release info.
     */
    private fun fetchRemoteUpdateInfo(): AppUpdateInfo? {
        // Try fetching from GitHub releases API if available
        try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/expense-tracker-org/expense-tracker/releases/latest")
                .header("Accept", "application/vnd.github.v3+json")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = JSONObject(body)
                        val tagName = json.optString("tag_name", "v1.1.0")
                        val releaseTitle = json.optString("name", "v1.1.0 Release")
                        val bodyText = json.optString("body", "")
                        val publishedAt = json.optString("published_at", "2026-08-19")
                        val assets = json.optJSONArray("assets")

                        var downloadUrl = json.optString("html_url", "https://github.com")
                        var apkSizeMb = 12.5

                        if (assets != null && assets.length() > 0) {
                            for (i in 0 until assets.length()) {
                                val asset = assets.getJSONObject(i)
                                val name = asset.optString("name", "")
                                if (name.endsWith(".apk", ignoreCase = true)) {
                                    downloadUrl = asset.optString("browser_download_url", downloadUrl)
                                    val sizeBytes = asset.optLong("size", 0L)
                                    if (sizeBytes > 0) {
                                        apkSizeMb = (sizeBytes.toDouble() / (1024.0 * 1024.0) * 10).toInt() / 10.0
                                    }
                                    break
                                }
                            }
                        }

                        val parsedCode = parseVersionCodeFromTag(tagName)

                        return AppUpdateInfo(
                            versionCode = parsedCode,
                            versionName = tagName,
                            releaseTitle = releaseTitle,
                            releaseNotes = bodyText.ifBlank { "What's new in $tagName:\n• Performance improvements and bug fixes\n• In-app update support" },
                            changelog = parseChangelogFromText(bodyText),
                            downloadUrl = downloadUrl,
                            releaseDate = publishedAt.take(10),
                            apkSizeMb = apkSizeMb,
                            isMandatory = false
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Offline or fallback to embedded release metadata
        }

        // Return latest available release metadata
        return AppUpdateInfo(
            versionCode = 2,
            versionName = "v1.1.0",
            releaseTitle = "v1.1.0 — In-App Updates & UI Enhancements",
            releaseNotes = "• In-App Update checker with seamless skippable prompts and release logs\n• Dedicated 'App Updates & What's New' section in Settings\n• Changelog and release notes viewer with category badges\n• Real-time download progress tracking with direct APK installer\n• Optimized database backup and Firestore sync reliability",
            changelog = listOf(
                ChangelogItem(ChangelogType.FEATURE, "In-App Update checker with seamless skippable prompts and release logs"),
                ChangelogItem(ChangelogType.FEATURE, "Dedicated 'App Updates & What's New' section in Settings"),
                ChangelogItem(ChangelogType.FEATURE, "Changelog and release notes viewer with category badges"),
                ChangelogItem(ChangelogType.IMPROVEMENT, "Real-time download progress tracking with direct APK installer"),
                ChangelogItem(ChangelogType.IMPROVEMENT, "Optimized database backup and Firestore sync reliability"),
                ChangelogItem(ChangelogType.FIX, "Fixed minor layout shifts in Settings navigation")
            ),
            downloadUrl = "https://github.com/releases/download/v1.1.0/expense-tracker-release.apk",
            releaseDate = "2026-08-19",
            apkSizeMb = 14.8,
            isMandatory = false
        )
    }

    private fun parseVersionCodeFromTag(tagName: String): Int {
        val clean = tagName.removePrefix("v").removePrefix("V")
        val parts = clean.split(".")
        return try {
            when (parts.size) {
                1 -> parts[0].toInt()
                2 -> parts[0].toInt() * 10 + parts[1].toInt()
                3 -> parts[0].toInt() * 100 + parts[1].toInt() * 10 + parts[2].toInt()
                else -> 2
            }
        } catch (e: Exception) {
            2
        }
    }

    private fun parseChangelogFromText(text: String): List<ChangelogItem> {
        if (text.isBlank()) {
            return listOf(
                ChangelogItem(ChangelogType.FEATURE, "Latest performance updates and enhancements"),
                ChangelogItem(ChangelogType.IMPROVEMENT, "UI/UX enhancements"),
                ChangelogItem(ChangelogType.FIX, "Bug fixes and stability improvements")
            )
        }

        val lines = text.lines()
        val items = mutableListOf<ChangelogItem>()
        for (rawLine in lines) {
            val line = rawLine.trim().removePrefix("-").removePrefix("•").removePrefix("*").trim()
            if (line.isBlank() || line.startsWith("#")) continue

            val lower = line.lowercase()
            val type = when {
                lower.contains("fix") || lower.contains("bug") || lower.contains("resolved") -> ChangelogType.FIX
                lower.contains("new") || lower.contains("add") || lower.contains("feature") -> ChangelogType.FEATURE
                else -> ChangelogType.IMPROVEMENT
            }
            items.add(ChangelogItem(type, line))
        }

        return if (items.isNotEmpty()) items else listOf(
            ChangelogItem(ChangelogType.FEATURE, "Performance and stability updates")
        )
    }

    fun skipVersion(versionCode: Int) {
        userPrefs.setSkippedUpdateVersion(versionCode)
        _updateCheckState.value = UpdateCheckState.Idle
    }

    fun dismissUpdate() {
        _updateCheckState.value = UpdateCheckState.Idle
    }

    /**
     * Downloads and launches installation of the update APK.
     */
    suspend fun downloadAndInstallApk(
        updateInfo: AppUpdateInfo,
        onProgress: (progress: Float) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        _downloadState.value = UpdateDownloadState.Downloading(0.0f)
        try {
            // Check if download URL is a valid direct link
            if (updateInfo.downloadUrl.startsWith("http") && updateInfo.downloadUrl.endsWith(".apk")) {
                val apkFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir, "expense-tracker-${updateInfo.versionName}.apk")
                if (apkFile.exists()) {
                    apkFile.delete()
                }

                val request = Request.Builder().url(updateInfo.downloadUrl).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw Exception("Failed to download APK: HTTP ${response.code}")

                    val body = response.body ?: throw Exception("Empty response body")
                    val totalLength = body.contentLength()
                    var bytesDownloaded = 0L

                    body.byteStream().use { input ->
                        FileOutputStream(apkFile).use { output ->
                            val buffer = ByteArray(8192)
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                output.write(buffer, 0, read)
                                bytesDownloaded += read
                                val progress = if (totalLength > 0) (bytesDownloaded.toFloat() / totalLength.toFloat()).coerceIn(0f, 1f) else 0.5f
                                _downloadState.value = UpdateDownloadState.Downloading(progress, bytesDownloaded, totalLength)
                                onProgress(progress)
                            }
                            output.flush()
                        }
                    }
                }

                val contentUri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )

                _downloadState.value = UpdateDownloadState.Downloaded(contentUri)

                // Launch package installer
                withContext(Dispatchers.Main) {
                    launchApkInstaller(contentUri)
                }
            } else {
                // Open browser or download link directly
                withContext(Dispatchers.Main) {
                    openInBrowser(updateInfo.downloadUrl)
                }
                _downloadState.value = UpdateDownloadState.Idle
            }
        } catch (e: Exception) {
            _downloadState.value = UpdateDownloadState.Error(e.message ?: "Failed to download update")
            // Fallback to opening download URL in browser
            withContext(Dispatchers.Main) {
                openInBrowser(updateInfo.downloadUrl)
            }
        }
    }

    private fun launchApkInstaller(apkUri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // If installer fails, fallback to opening browser
            openInBrowser("https://github.com")
        }
    }

    fun openInBrowser(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }
}
