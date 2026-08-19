package com.example.data.model

import android.net.Uri

enum class ChangelogType(val label: String, val badgeColor: Long) {
    FEATURE("New", 0xFF10B981),      // Emerald Green
    IMPROVEMENT("Improved", 0xFF3B82F6),   // Blue
    FIX("Fix", 0xFFF59E0B)               // Amber
}

data class ChangelogItem(
    val type: ChangelogType,
    val text: String
)

data class AppUpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val releaseTitle: String,
    val releaseNotes: String,
    val changelog: List<ChangelogItem> = emptyList(),
    val downloadUrl: String,
    val releaseDate: String,
    val apkSizeMb: Double = 12.5,
    val isMandatory: Boolean = false
)

data class VersionReleaseLog(
    val versionName: String,
    val versionCode: Int,
    val releaseDate: String,
    val title: String,
    val isCurrent: Boolean = false,
    val changes: List<ChangelogItem>
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
