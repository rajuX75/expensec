package com.example.data.cloud

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Background worker that scans the local database for any image URI that still
 * points to a local file:// (i.e. it was never uploaded — usually because the
 * device was offline or Cloudinary was not configured yet) and uploads it.
 *
 * On success the DB row is updated with the Cloudinary secure_url and the now
 * redundant local file is deleted. The local file is only deleted AFTER a
 * successful upload, so "images gone after clear data" cannot happen as long
 * as the upload actually succeeded.
 */
class CloudinaryImageUploadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        // Fail fast with a clear log instead of silently "succeeding" while
        // nothing was ever uploaded to the cloud.
        if (!CloudinaryUploader.isConfigured()) {
            Log.e(
                TAG,
                "Cloudinary credentials are placeholders — skipping upload sync. " +
                        "Set real CLOUDINARY_CLOUD_NAME / CLOUDINARY_API_KEY / " +
                        "CLOUDINARY_API_SECRET in .env and rebuild."
            )
            return@withContext Result.success()
        }

        val database = AppDatabase.getDatabase(
            applicationContext,
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
        )

        return@withContext try {
            uploadShopImages(database)
            uploadContactImages(database)
            uploadDhaarImages(database)
            uploadTransactionImages(database)

            Log.d(TAG, "Image upload sync completed.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error uploading images", e)
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }

    /** Deletes the local file only after a successful upload. */
    private fun cleanupLocalFile(localFileUri: String) {
        try {
            val path = localFileUri.removePrefix("file://")
            val f = File(path)
            if (f.exists() && f.delete()) {
                Log.d(TAG, "Deleted local copy after upload: $path")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not delete local file $localFileUri: ${e.message}")
        }
    }

    private suspend fun uploadShopImages(db: AppDatabase) {
        val shopDao = db.shopDao()
        val shops = shopDao.getAllShopsSync()
        for (shop in shops) {
            var updated = false
            var newShop = shop

            val profileLocal = shop.profilePictureUri
            if (profileLocal?.startsWith("file://") == true) {
                val remoteUrl = CloudinaryUploader.upload(profileLocal, "shops")
                if (remoteUrl != null) {
                    newShop = newShop.copy(profilePictureUri = remoteUrl)
                    updated = true
                    cleanupLocalFile(profileLocal)
                }
            }
            val coverLocal = newShop.coverImageUri
            if (coverLocal?.startsWith("file://") == true) {
                val remoteUrl = CloudinaryUploader.upload(coverLocal, "shops")
                if (remoteUrl != null) {
                    newShop = newShop.copy(coverImageUri = remoteUrl)
                    updated = true
                    cleanupLocalFile(coverLocal)
                }
            }

            if (updated) {
                shopDao.insertShop(newShop)
            }
        }
    }

    private suspend fun uploadContactImages(db: AppDatabase) {
        val contactDao = db.contactDao()
        val contacts = contactDao.getAllContactsSync()
        for (contact in contacts) {
            val local = contact.photoUri
            if (local?.startsWith("file://") == true) {
                val remoteUrl = CloudinaryUploader.upload(local, "contacts")
                if (remoteUrl != null) {
                    contactDao.insertContact(contact.copy(photoUri = remoteUrl))
                    cleanupLocalFile(local)
                }
            }
        }
    }

    private suspend fun uploadDhaarImages(db: AppDatabase) {
        val dhaarDao = db.dhaarEntryDao()
        val entries = dhaarDao.getAllEntriesSync()
        for (entry in entries) {
            val local = entry.tagPhotoUri
            if (local?.startsWith("file://") == true) {
                val remoteUrl = CloudinaryUploader.upload(local, "dhaar")
                if (remoteUrl != null) {
                    dhaarDao.insertEntry(entry.copy(tagPhotoUri = remoteUrl))
                    cleanupLocalFile(local)
                }
            }
        }
    }

    private suspend fun uploadTransactionImages(db: AppDatabase) {
        val txDao = db.transactionDao()
        val txs = txDao.getAllTransactionsSync()
        for (tx in txs) {
            val local = tx.receiptUri
            if (local?.startsWith("file://") == true) {
                val remoteUrl = CloudinaryUploader.upload(local, "receipts")
                if (remoteUrl != null) {
                    txDao.insertTransaction(tx.copy(receiptUri = remoteUrl))
                    cleanupLocalFile(local)
                }
            }
        }
    }

    companion object {
        private const val TAG = "CloudinaryWorker"
    }
}
