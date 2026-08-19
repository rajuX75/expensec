package com.example.data.work

import android.content.Context
import androidx.work.*
import com.example.data.cloud.CloudBackupRepository
import com.example.data.cloud.CloudConflictException
import com.example.data.local.AppDatabase
import com.example.data.repository.ImportExportRepository
import com.example.data.repository.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.concurrent.TimeUnit

class BackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val WORK_NAME = "expense_auto_backup_work"

        fun schedule(context: Context, frequency: String, wifiOnly: Boolean) {
            val workManager = WorkManager.getInstance(context)

            if (frequency.equals("OFF", ignoreCase = true)) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }

            val repeatIntervalHours = when (frequency.uppercase()) {
                "DAILY" -> 24L
                "WEEKLY" -> 24L * 7L
                else -> 24L
            }

            val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(networkType)
                .setRequiresBatteryNotLow(true)
                .build()

            val periodicWorkRequest = PeriodicWorkRequestBuilder<BackupWorker>(
                repeatIntervalHours, TimeUnit.HOURS,
                15, TimeUnit.MINUTES // 15 min flex window
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.MINUTES)
                .build()

            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                periodicWorkRequest
            )
        }
    }

    override suspend fun doWork(): Result {
        val userPrefs = UserPreferencesRepository(applicationContext)

        // Check if user is signed in to Google Drive
        val accountEmail = userPrefs.googleAccountEmail.value
        // The Drive REST API needs a Drive access token, not the Firebase ID token.
        val authToken = userPrefs.googleDriveAccessToken.value
        if (accountEmail.isNullOrBlank() || authToken.isNullOrBlank()) {
            return Result.success() // Silent skip if Drive access not granted
        }

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val database = AppDatabase.getDatabase(applicationContext, scope)
        val importExportRepo = ImportExportRepository(applicationContext, database, userPrefs)
        val cloudBackupRepo = CloudBackupRepository(
            importExportRepository = importExportRepo,
            userPreferencesRepository = userPrefs
        )

        return try {
            val backupResult = cloudBackupRepo.backupToCloud(
                explicitToken = authToken,
                forceOverwrite = false
            )
            if (backupResult.isSuccess) {
                Result.success()
            } else {
                val error = backupResult.exceptionOrNull()
                if (error is CloudConflictException) {
                    // Do not retry on conflict - wait for user resolution
                    Result.success()
                } else {
                    Result.retry()
                }
            }
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
