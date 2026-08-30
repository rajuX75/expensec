package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.data.cloud.CloudBackupRepository
import com.example.data.cloud.CloudBackupResult
import com.example.data.cloud.CloudConflictException
import com.example.data.cloud.DriveAuthorizeResult
import com.example.data.cloud.FirestoreSyncManager
import com.example.data.cloud.GoogleAuthManager
import com.example.data.cloud.toReadableSignInError
import com.example.data.model.AppBackup
import com.example.data.model.ImportMode
import com.example.data.model.ImportResult
import com.example.data.repository.ImportExportRepository
import com.example.data.repository.UserPreferencesRepository
import com.example.data.work.BackupWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class CloudDelegate(
    private val application: Application,
    private val viewModelScope: CoroutineScope,
    private val importExportRepo: ImportExportRepository,
    private val cloudBackupRepo: CloudBackupRepository,
    private val googleAuthManager: GoogleAuthManager,
    private val firestoreSyncManager: FirestoreSyncManager,
    private val userPrefs: UserPreferencesRepository
) {
    // Cloud Operation State
    private val _isCloudSyncing = MutableStateFlow(false)
    val isCloudSyncing = _isCloudSyncing.asStateFlow()

    private val _cloudSyncMessage = MutableStateFlow<String?>(null)
    val cloudSyncMessage = _cloudSyncMessage.asStateFlow()

    private val _cloudConflict = MutableStateFlow<CloudConflictException?>(null)
    val cloudConflict = _cloudConflict.asStateFlow()

    private val _safetyBackups = MutableStateFlow<List<File>>(emptyList())
    val safetyBackups = _safetyBackups.asStateFlow()

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
    fun signInGoogle(
        activityContext: Context,
        webClientId: String = "",
        onFallbackToLegacy: ((android.content.Intent) -> Unit)? = null,
        onResult: (Result<String>) -> Unit
    ) {
        viewModelScope.launch {
            _isCloudSyncing.value = true
            _cloudSyncMessage.value = "Signing in to Google..."

            // Use signInInternal so we get the raw exception type for fallback detection.
            val result = googleAuthManager.signInInternal(activityContext, webClientId)

            if (result.isFailure && onFallbackToLegacy != null) {
                val ex = result.exceptionOrNull()
                // Credential Manager failed — check if this is a class of exception
                // that benefits from the legacy GoogleSignIn intent fallback (typical on
                // Xiaomi / MIUI devices where the Credential Manager bottom-sheet is blocked).
                val isCredentialManagerFailure = ex is GetCredentialException ||
                    ex is NoCredentialException ||
                    generateSequence(ex) { it.cause }.any {
                        it is GetCredentialException || it is NoCredentialException
                    }
                if (isCredentialManagerFailure) {
                    android.util.Log.w(
                        "CloudDelegate",
                        "Credential Manager failed (${ex?.javaClass?.simpleName}), " +
                            "falling back to legacy GoogleSignIn intent"
                    )
                    _isCloudSyncing.value = false
                    _cloudSyncMessage.value = null
                    val legacyIntent = googleAuthManager.getLegacySignInIntent(activityContext)
                    onFallbackToLegacy(legacyIntent)
                    return@launch
                }
            }

            _isCloudSyncing.value = false
            _cloudSyncMessage.value = null

            // Convert raw exception to a readable message for the UI.
            val finalResult = result.recoverCatching { t ->
                throw t.toReadableSignInError()
            }
            onResult(finalResult)
        }
    }

    /**
     * Call this with the [android.content.Intent] from [ActivityResult.data] after the
     * legacy GoogleSignIn launcher returns. Completes the Firebase sign-in and calls [onResult].
     */
    fun handleLegacySignInResult(
        data: android.content.Intent?,
        onResult: (Result<String>) -> Unit
    ) {
        viewModelScope.launch {
            _isCloudSyncing.value = true
            _cloudSyncMessage.value = "Completing sign-in..."
            val result = googleAuthManager.handleLegacySignInResult(data)
            _isCloudSyncing.value = false
            _cloudSyncMessage.value = null
            onResult(result)
        }
    }

    fun authorizeDrive(activityContext: Context, onResult: (DriveAuthorizeResult) -> Unit) {
        viewModelScope.launch {
            val result = googleAuthManager.authorizeDrive(activityContext)
            onResult(result)
        }
    }

    fun signOutGoogle(onComplete: () -> Unit) {
        viewModelScope.launch {
            googleAuthManager.signOut()
            BackupWorker.schedule(application, "OFF", true)
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
        BackupWorker.schedule(application, frequency, wifiOnly)
    }
}
