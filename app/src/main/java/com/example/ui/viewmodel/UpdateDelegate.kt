package com.example.ui.viewmodel

import com.example.data.model.AppUpdateInfo
import com.example.data.repository.UpdateRepository
import com.example.data.repository.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class UpdateDelegate(
    private val viewModelScope: CoroutineScope,
    private val updateRepository: UpdateRepository,
    private val userPrefs: UserPreferencesRepository
) {
    val autoCheckUpdates = userPrefs.autoCheckUpdates
    val skippedUpdateVersion = userPrefs.skippedUpdateVersion
    val lastUpdateCheckTime = userPrefs.lastUpdateCheckTime
    val updateCheckState = updateRepository.updateCheckState
    val updateDownloadState = updateRepository.downloadState
    val currentAppVersionName = updateRepository.getCurrentVersionName()
    val currentAppVersionCode = updateRepository.getCurrentVersionCode()
    
    // Reactively observe release history from Firebase via the repository
    val releaseHistory: kotlinx.coroutines.flow.StateFlow<List<com.example.data.model.VersionReleaseLog>> = 
        updateRepository.releaseHistoryFlow
            .map { rtdbLogs ->
                if (rtdbLogs.isNotEmpty()) {
                    rtdbLogs.map { log ->
                        log.copy(isCurrent = log.versionCode == currentAppVersionCode)
                    }
                } else {
                    updateRepository.getReleaseHistory() // fallback
                }
            }
            .stateIn(
                scope = viewModelScope,
                started = kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000),
                initialValue = updateRepository.getReleaseHistory()
            )

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
