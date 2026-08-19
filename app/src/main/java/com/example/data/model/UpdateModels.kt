package com.example.data.model

import android.net.Uri

enum class ChangelogType(val label: String, val badgeColor: Long) {
    FEATURE("New", 0xFF10B981),      // Emerald Green
    IMPROVEMENT("Improved", 0xFF3B82F6),   // Blue
    FIX("Fix", 0xFFF59E0B)               // Amber
}

data class ChangelogItem(
    val type: ChangelogType = ChangelogType.FEATURE,
    val text: String = ""
)

data class AnnouncementBanner(
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val actionUrl: String? = null,
    val actionText: String? = null,
    val type: String = "INFO", // INFO, WARNING, SUCCESS
    val active: Boolean = false,
    val dismissible: Boolean = true
)

data class AppRemoteConfig(
    val appName: String = "Expense Tracker",
    val supportEmail: String = "support@expensex.app",
    val privacyPolicyUrl: String = "https://github.com/rajuX75/expensec/blob/main/PRIVACY_POLICY.md",
    val termsUrl: String = "https://github.com/rajuX75/expensec/blob/main/TERMS.md",
    val githubRepoUrl: String = "https://github.com/rajuX75/expensec",
    val defaultCurrency: String = "USD",
    val maintenanceMode: Boolean = false,
    val maintenanceMessage: String = "",
    val announcement: AnnouncementBanner? = null
)

data class AppUpdateInfo(
    val versionCode: Int = 1,
    val versionName: String = "1.0.0",
    val minSupportedVersionCode: Int = 1,
    val releaseTitle: String = "",
    val releaseNotes: String = "",
    val changelog: List<ChangelogItem> = emptyList(),
    val downloadUrl: String = "",
    val releaseDate: String = "",
    val apkSizeMb: Double = 20.1,
    val isMandatory: Boolean = false
)

data class VersionReleaseLog(
    val versionName: String = "",
    val versionCode: Int = 1,
    val releaseDate: String = "",
    val title: String = "",
    val isCurrent: Boolean = false,
    val changes: List<ChangelogItem> = emptyList()
)

sealed class UpdateCheckState {
    object Idle : UpdateCheckState()
    object Checking : UpdateCheckState()
    data class UpdateAvailable(val info: AppUpdateInfo) : UpdateCheckState()
    data class UpToDate(val currentVersion: String, val checkedAt: Long) : UpdateCheckState()
    data class Error(val message: String) : UpdateCheckState()
}

sealed class UpdateDownloadState {
    object Idle : UpdateDownloadState()
    data class Downloading(val progress: Float, val downloadedBytes: Long = 0L, val totalBytes: Long = 0L) : UpdateDownloadState()
    data class Downloaded(val apkUri: Uri) : UpdateDownloadState()
    data class Error(val message: String) : UpdateDownloadState()
}
