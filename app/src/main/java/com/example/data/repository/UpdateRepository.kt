package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.core.content.FileProvider
import com.example.data.cloud.FirebaseConfigManager
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class UpdateRepository(
    private val context: Context,
    private val userPrefs: UserPreferencesRepository,
    private val firebaseConfigManager: FirebaseConfigManager
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
     * Complete changelog history of the app.
     * Uses Realtime DB changelog if available, else falls back to default release logs.
     */
    fun getReleaseHistory(): List<VersionReleaseLog> {
        val rtdbLogs = firebaseConfigManager.releaseHistory.value
        if (rtdbLogs.isNotEmpty()) {
            return rtdbLogs.map { log ->
                log.copy(isCurrent = log.versionCode == getCurrentVersionCode())
            }
        }

        return listOf(
            VersionReleaseLog(
                versionName = "v1.0.0",
                versionCode = 1,
                releaseDate = "August 2026",
                title = "Initial Official Release",
                isCurrent = true,
                changes = listOf(
                    ChangelogItem(ChangelogType.FEATURE, "Clean, local-first financial manager with 0-balance start"),
                    ChangelogItem(ChangelogType.FEATURE, "Dhaar (Debts & Loans) tracker with contact photo avatars & phone picker"),
                    ChangelogItem(ChangelogType.FEATURE, "User profile picture upload and custom contact photos"),
                    ChangelogItem(ChangelogType.FEATURE, "Real-time Firebase Firestore database synchronization"),
                    ChangelogItem(ChangelogType.FEATURE, "Firebase Realtime DB app configuration and smart update system"),
                    ChangelogItem(ChangelogType.IMPROVEMENT, "Sleek, compact search bar and refined Material 3 UI"),
                    ChangelogItem(ChangelogType.FIX, "Fixed phone contact selection crash with safe permission-free picker")
                )
            )
        )
    }

    /**
     * Checks for available updates from Firebase Realtime Database primary, with GitHub fallback.
     * @param isManualCheck When true, ignores user's skipped version preference.
     */
    suspend fun checkForUpdates(isManualCheck: Boolean = false): UpdateCheckState = withContext(Dispatchers.IO) {
        _updateCheckState.value = UpdateCheckState.Checking

        try {
            if (isManualCheck) {
                delay(500)
            }

            userPrefs.setLastUpdateCheckTime()

            val currentCode = getCurrentVersionCode()
            val currentName = getCurrentVersionName()
            val skippedCode = userPrefs.skippedUpdateVersion.value

            // 1. Try Firebase Realtime Database first
            var updateInfo = firebaseConfigManager.remoteUpdateInfo.value

            // If null from Realtime DB listener, attempt a REST fetch
            if (updateInfo == null) {
                firebaseConfigManager.fetchViaRest()
                delay(300)
                updateInfo = firebaseConfigManager.remoteUpdateInfo.value
            }

            // 2. If still null, fallback to GitHub Releases API
            if (updateInfo == null) {
                updateInfo = fetchGitHubReleaseUpdateInfo()
            }

            val state = if (updateInfo != null && updateInfo.versionCode > currentCode) {
                val isMandatory = updateInfo.isMandatory || (currentCode < updateInfo.minSupportedVersionCode)
                val effectiveInfo = updateInfo.copy(isMandatory = isMandatory)

                if (!isManualCheck && !isMandatory && effectiveInfo.versionCode == skippedCode) {
                    // User opted to skip this version on auto-check
                    UpdateCheckState.UpToDate(currentName, System.currentTimeMillis())
                } else {
                    UpdateCheckState.UpdateAvailable(effectiveInfo)
                }
            } else {
                UpdateCheckState.UpToDate(currentName, System.currentTimeMillis())
            }

            _updateCheckState.value = state
            state
        } catch (e: Exception) {
            val errorState = UpdateCheckState.Error(e.message ?: "Failed to check for updates.")
            _updateCheckState.value = errorState
            errorState
        }
    }

    /**
     * Fetches update information from GitHub repository if Firebase is unreachable.
     */
    private fun fetchGitHubReleaseUpdateInfo(): AppUpdateInfo? {
        try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/rajuX75/expensec/releases/latest")
                .header("Accept", "application/vnd.github.v3+json")
                .header("User-Agent", "ExpenseTracker-AndroidApp")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        val json = JSONObject(body)
                        val tagName = json.optString("tag_name", "")
                        val releaseTitle = json.optString("name", tagName)
                        val bodyText = json.optString("body", "")
                        val publishedAt = json.optString("published_at", "")
                        val assets = json.optJSONArray("assets")

                        var downloadUrl = "https://github.com/rajuX75/expensec/releases/latest/download/expense-tracker-release.apk"
                        var apkSizeMb = 20.1

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
                            minSupportedVersionCode = 1,
                            releaseTitle = releaseTitle,
                            releaseNotes = bodyText.ifBlank { "What's new in $tagName:\n• Performance improvements and bug fixes" },
                            changelog = parseChangelogFromText(bodyText),
                            downloadUrl = downloadUrl,
                            releaseDate = if (publishedAt.length >= 10) publishedAt.take(10) else "Latest",
                            apkSizeMb = apkSizeMb,
                            isMandatory = false
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        return null
    }

    private fun parseVersionCodeFromTag(tagName: String): Int {
        val clean = tagName.removePrefix("v").removePrefix("V").trim()
        val parts = clean.split(".")
        return try {
            when (parts.size) {
                1 -> parts[0].toIntOrNull() ?: 1
                2 -> (parts[0].toIntOrNull() ?: 0) * 10 + (parts[1].toIntOrNull() ?: 0)
                3 -> (parts[0].toIntOrNull() ?: 0) * 100 + (parts[1].toIntOrNull() ?: 0) * 10 + (parts[2].toIntOrNull() ?: 0)
                else -> 1
            }
        } catch (e: Exception) {
            1
        }
    }

    private fun parseChangelogFromText(text: String): List<ChangelogItem> {
        if (text.isBlank()) {
            return listOf(
                ChangelogItem(ChangelogType.FEATURE, "Performance and stability updates"),
                ChangelogItem(ChangelogType.IMPROVEMENT, "User experience enhancements")
            )
        }

        val lines = text.lines()
        val items = mutableListOf<ChangelogItem>()
        for (rawLine in lines) {
            val line = rawLine.trim().removePrefix("-").removePrefix("•").removePrefix("*").trim()
            if (line.isBlank() || line.startsWith("#")) continue

            val lower = line.lowercase()
            val type = when {
                lower.contains("fix") || lower.contains("bug") || lower.contains("resolved") || lower.contains("crash") -> ChangelogType.FIX
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
     * Downloads and launches installation of the update APK automatically.
     */
    suspend fun downloadAndInstallApk(
        updateInfo: AppUpdateInfo,
        onProgress: (progress: Float) -> Unit = {}
    ) = withContext(Dispatchers.IO) {
        _downloadState.value = UpdateDownloadState.Downloading(0.0f)
        try {
            val targetDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
            val apkFile = File(targetDir, "expense-tracker-${updateInfo.versionName}.apk")
            if (apkFile.exists()) {
                apkFile.delete()
            }

            val request = Request.Builder()
                .url(updateInfo.downloadUrl)
                .header("User-Agent", "ExpenseTracker-AndroidApp")
                .build()

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

            withContext(Dispatchers.Main) {
                launchApkInstaller(contentUri)
            }
        } catch (e: Exception) {
            _downloadState.value = UpdateDownloadState.Error(e.message ?: "Failed to download update")
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
            openInBrowser("https://github.com/rajuX75/expensec/releases")
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
