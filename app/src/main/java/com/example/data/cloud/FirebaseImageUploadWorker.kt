package com.example.data.cloud

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.local.AppDatabase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class FirebaseImageUploadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Log.d("ImageUploadWorker", "User not logged in, skipping upload.")
            return@withContext Result.retry()
        }

        val uid = user.uid
        val storage = FirebaseStorage.getInstance().reference

        // We use an ad-hoc scope just to get the DAOs via the database instance
        // AppDatabase.getDatabase takes a CoroutineScope which it uses for prepopulation.
        // It's safe to pass a GlobalScope or a simple custom scope here since we only need the instance.
        val database = AppDatabase.getDatabase(applicationContext, kotlinx.coroutines.GlobalScope)

        try {
            uploadShopImages(database, uid, storage)
            uploadContactImages(database, uid, storage)
            uploadDhaarImages(database, uid, storage)
            uploadTransactionImages(database, uid, storage)

            Log.d("ImageUploadWorker", "Image upload sync completed.")
            Result.success()
        } catch (e: Exception) {
            Log.e("ImageUploadWorker", "Error uploading images", e)
            Result.retry()
        }
    }

    private suspend fun uploadFile(localUriString: String, uid: String, storage: com.google.firebase.storage.StorageReference): String? {
        if (!localUriString.startsWith("file://")) return null // Already remote or empty
        
        val localPath = localUriString.removePrefix("file://")
        val file = File(localPath)
        if (!file.exists()) {
            Log.w("ImageUploadWorker", "Local file does not exist: $localPath")
            return null
        }

        return try {
            val ref = storage.child("users/$uid/migrated_photos/${UUID.randomUUID()}.jpg")
            ref.putFile(Uri.parse(localUriString)).await()
            ref.downloadUrl.await().toString()
        } catch (e: Exception) {
            Log.e("ImageUploadWorker", "Failed to upload $localUriString", e)
            null
        }
    }

    private suspend fun uploadShopImages(db: AppDatabase, uid: String, storage: com.google.firebase.storage.StorageReference) {
        val shopDao = db.shopDao()
        val shops = shopDao.getAllShopsSync()
        for (shop in shops) {
            var updated = false
            var newShop = shop
            
            if (shop.profilePictureUri?.startsWith("file://") == true) {
                val remoteUrl = uploadFile(shop.profilePictureUri, uid, storage)
                if (remoteUrl != null) {
                    newShop = newShop.copy(profilePictureUri = remoteUrl)
                    updated = true
                }
            }
            if (newShop.coverImageUri?.startsWith("file://") == true) {
                val remoteUrl = uploadFile(newShop.coverImageUri, uid, storage)
                if (remoteUrl != null) {
                    newShop = newShop.copy(coverImageUri = remoteUrl)
                    updated = true
                }
            }
            
            if (updated) {
                shopDao.insertShop(newShop) // Replaces based on PK
            }
        }
    }

    private suspend fun uploadContactImages(db: AppDatabase, uid: String, storage: com.google.firebase.storage.StorageReference) {
        val contactDao = db.contactDao()
        val contacts = contactDao.getAllContactsSync()
        for (contact in contacts) {
            if (contact.photoUri?.startsWith("file://") == true) {
                val remoteUrl = uploadFile(contact.photoUri, uid, storage)
                if (remoteUrl != null) {
                    contactDao.insertContact(contact.copy(photoUri = remoteUrl))
                }
            }
        }
    }

    private suspend fun uploadDhaarImages(db: AppDatabase, uid: String, storage: com.google.firebase.storage.StorageReference) {
        val dhaarDao = db.dhaarEntryDao()
        val entries = dhaarDao.getAllEntriesSync()
        for (entry in entries) {
            if (entry.tagPhotoUri?.startsWith("file://") == true) {
                val remoteUrl = uploadFile(entry.tagPhotoUri, uid, storage)
                if (remoteUrl != null) {
                    dhaarDao.insertEntry(entry.copy(tagPhotoUri = remoteUrl))
                }
            }
        }
    }

    private suspend fun uploadTransactionImages(db: AppDatabase, uid: String, storage: com.google.firebase.storage.StorageReference) {
        val txDao = db.transactionDao()
        val txs = txDao.getAllTransactionsSync()
        for (tx in txs) {
            if (tx.receiptUri?.startsWith("file://") == true) {
                val remoteUrl = uploadFile(tx.receiptUri, uid, storage)
                if (remoteUrl != null) {
                    txDao.insertTransaction(tx.copy(receiptUri = remoteUrl))
                }
            }
        }
    }
}
