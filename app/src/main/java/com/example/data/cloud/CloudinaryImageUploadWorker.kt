package com.example.data.cloud

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CloudinaryImageUploadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val database = AppDatabase.getDatabase(applicationContext, kotlinx.coroutines.GlobalScope)

        try {
            uploadShopImages(database)
            uploadContactImages(database)
            uploadDhaarImages(database)
            uploadTransactionImages(database)

            Log.d("CloudinaryWorker", "Image upload sync completed.")
            Result.success()
        } catch (e: Exception) {
            Log.e("CloudinaryWorker", "Error uploading images", e)
            Result.retry()
        }
    }

    private suspend fun uploadShopImages(db: AppDatabase) {
        val shopDao = db.shopDao()
        val shops = shopDao.getAllShopsSync()
        for (shop in shops) {
            var updated = false
            var newShop = shop
            
            if (shop.profilePictureUri?.startsWith("file://") == true) {
                val remoteUrl = CloudinaryUploader.upload(shop.profilePictureUri, "shops")
                if (remoteUrl != null) {
                    newShop = newShop.copy(profilePictureUri = remoteUrl)
                    updated = true
                }
            }
            if (newShop.coverImageUri?.startsWith("file://") == true) {
                val remoteUrl = CloudinaryUploader.upload(newShop.coverImageUri, "shops")
                if (remoteUrl != null) {
                    newShop = newShop.copy(coverImageUri = remoteUrl)
                    updated = true
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
            if (contact.photoUri?.startsWith("file://") == true) {
                val remoteUrl = CloudinaryUploader.upload(contact.photoUri, "contacts")
                if (remoteUrl != null) {
                    contactDao.insertContact(contact.copy(photoUri = remoteUrl))
                }
            }
        }
    }

    private suspend fun uploadDhaarImages(db: AppDatabase) {
        val dhaarDao = db.dhaarEntryDao()
        val entries = dhaarDao.getAllEntriesSync()
        for (entry in entries) {
            if (entry.tagPhotoUri?.startsWith("file://") == true) {
                val remoteUrl = CloudinaryUploader.upload(entry.tagPhotoUri, "dhaar")
                if (remoteUrl != null) {
                    dhaarDao.insertEntry(entry.copy(tagPhotoUri = remoteUrl))
                }
            }
        }
    }

    private suspend fun uploadTransactionImages(db: AppDatabase) {
        val txDao = db.transactionDao()
        val txs = txDao.getAllTransactionsSync()
        for (tx in txs) {
            if (tx.receiptUri?.startsWith("file://") == true) {
                val remoteUrl = CloudinaryUploader.upload(tx.receiptUri, "receipts")
                if (remoteUrl != null) {
                    txDao.insertTransaction(tx.copy(receiptUri = remoteUrl))
                }
            }
        }
    }
}
