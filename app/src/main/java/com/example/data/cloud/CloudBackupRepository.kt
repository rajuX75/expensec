package com.example.data.cloud

import com.example.data.backup.BackupSerializer
import com.example.data.model.AppBackup
import com.example.data.model.ImportMode
import com.example.data.model.ImportResult
import com.example.data.repository.ImportExportRepository
import com.example.data.repository.UserPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CloudBackupInfo(
    val fileId: String,
    val modifiedTime: Long,
    val sizeBytes: Long,
    val backup: AppBackup? = null
)

data class CloudBackupResult(
    val fileId: String,
    val timestamp: Long,
    val bytesUploaded: Int
)

class CloudBackupRepository(
    private val driveClient: DriveClient = GoogleDriveRestClient(),
    private val importExportRepository: ImportExportRepository,
    private val userPreferencesRepository: UserPreferencesRepository
) {

    private fun getValidToken(explicitToken: String?): String {
        // IMPORTANT: the Drive REST API needs a Google *access token* for the drive.appdata
        // scope — NOT the Firebase ID token. A previously stored Firebase ID token is never a
        // valid bearer token for Drive, so we intentionally ignore the legacy slot here.
        val token = explicitToken ?: userPreferencesRepository.googleDriveAccessToken.value
        if (token.isNullOrBlank()) {
            throw IllegalStateException("Google Drive authorization required. Please sign in with Google.")
        }
        return token
    }

    /**
     * Checks if a backup exists in Google Drive's appDataFolder and parses its metadata.
     */
    suspend fun getCloudBackupInfo(explicitToken: String? = null): Result<CloudBackupInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val token = getValidToken(explicitToken)
            val metadata = driveClient.getAppBackupMetadata(token) ?: return@runCatching null
            CloudBackupInfo(
                fileId = metadata.id,
                modifiedTime = metadata.modifiedTime,
                sizeBytes = metadata.sizeBytes
            )
        }
    }

    /**
     * Downloads and parses the backup JSON from Google Drive without importing it.
     */
    suspend fun fetchBackupPreviewFromCloud(explicitToken: String? = null): Result<AppBackup> = withContext(Dispatchers.IO) {
        runCatching {
            val token = getValidToken(explicitToken)
            val metadata = driveClient.getAppBackupMetadata(token)
                ?: throw IllegalStateException("No backup file found in Google Drive appDataFolder.")

            val jsonContent = driveClient.downloadAppBackup(token, metadata.id)
            val backupResult = BackupSerializer.importFromJson(jsonContent)
            backupResult.getOrThrow()
        }
    }

    /**
     * Uploads/overwrites the database backup in Google Drive appDataFolder.
     * Compares timestamps for conflict detection unless [forceOverwrite] is true.
     */
    suspend fun backupToCloud(
        explicitToken: String? = null,
        forceOverwrite: Boolean = false
    ): Result<CloudBackupResult> = withContext(Dispatchers.IO) {
        runCatching {
            val token = getValidToken(explicitToken)

            // Step 1: Check existing cloud file and conflict detection
            val existingMetadata = driveClient.getAppBackupMetadata(token)
            val lastLocalBackupTime = userPreferencesRepository.lastCloudBackupTime.value

            if (!forceOverwrite && existingMetadata != null && existingMetadata.modifiedTime > lastLocalBackupTime && lastLocalBackupTime > 0L) {
                // Potential conflict detected: cloud has newer backup from another device
                throw CloudConflictException(
                    cloudExportedAt = existingMetadata.modifiedTime,
                    localExportedAt = lastLocalBackupTime
                )
            }

            // Step 2: Create backup payload (safe PIN hash, no plaintext secret)
            val fullBackup = importExportRepository.createFullBackup()
            val jsonContent = BackupSerializer.exportToJson(fullBackup)
            val bytesCount = jsonContent.toByteArray(Charsets.UTF_8).size

            // Step 3: Upload to Google Drive appDataFolder
            val uploadedMeta = driveClient.uploadAppBackup(
                accessToken = token,
                jsonContent = jsonContent,
                existingFileId = existingMetadata?.id
            )

            // Step 4: Record success state
            userPreferencesRepository.setCloudBackupResult(
                status = "SUCCESS",
                timestamp = uploadedMeta.modifiedTime
            )

            CloudBackupResult(
                fileId = uploadedMeta.id,
                timestamp = uploadedMeta.modifiedTime,
                bytesUploaded = bytesCount
            )
        }.onFailure { error ->
            if (error !is CloudConflictException) {
                userPreferencesRepository.setCloudBackupResult(
                    status = "FAILED",
                    error = error.localizedMessage ?: error.message
                )
            }
        }
    }

    /**
     * Restores backup from Google Drive using the transactional import logic from Part 1.
     */
    suspend fun restoreFromCloud(
        explicitToken: String? = null,
        mode: ImportMode
    ): Result<ImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val token = getValidToken(explicitToken)
            val metadata = driveClient.getAppBackupMetadata(token)
                ?: throw IllegalStateException("No backup found in Google Drive.")

            val jsonContent = driveClient.downloadAppBackup(token, metadata.id)
            val backup = BackupSerializer.importFromJson(jsonContent).getOrThrow()

            // Run import inside atomic Room transaction (with automatic safety backup if REPLACE mode)
            val importResult = importExportRepository.importBackup(backup, mode).getOrThrow()

            userPreferencesRepository.setCloudBackupResult(
                status = "SUCCESS",
                timestamp = System.currentTimeMillis()
            )

            importResult
        }.onFailure { error ->
            userPreferencesRepository.setCloudBackupResult(
                status = "FAILED",
                error = error.localizedMessage ?: error.message
            )
        }
    }
}
