package com.example.ui.components

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object ImageStorageHelper {

    /**
     * Copies an image locally, then attempts to upload it to Cloudinary.
     * Returns the Cloudinary download URL on success. 
     * If upload fails, returns the persistent local file Uri.
     */
    suspend fun saveImageLocally(
        context: Context,
        sourceUri: Uri,
        folderName: String = "photos"
    ): String? = withContext(Dispatchers.IO) {
        var localUri: String? = null
        try {
            val folder = File(context.filesDir, folderName).apply {
                if (!exists()) mkdirs()
            }
            val fileName = "img_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg"
            val destFile = File(folder, fileName)

            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                FileOutputStream(destFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
                localUri = Uri.fromFile(destFile).toString()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            localUri = sourceUri.toString() // Fallback to original
        }

        // Attempt to upload to Cloudinary for permanent cross-device syncing
        if (localUri != null && localUri!!.startsWith("file://")) {
            val cloudUrl = com.example.data.cloud.CloudinaryUploader.upload(localUri!!, folderName)
            if (cloudUrl != null) {
                return@withContext cloudUrl
            } else {
                // Upload failed (e.g., offline). Queue the worker to upload it later when connected.
                try {
                    val uploadWorkRequest = androidx.work.OneTimeWorkRequestBuilder<com.example.data.cloud.CloudinaryImageUploadWorker>()
                        .setConstraints(
                            androidx.work.Constraints.Builder()
                                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                                .build()
                        )
                        .build()
                    androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                        "CloudinaryImageUpload",
                        androidx.work.ExistingWorkPolicy.APPEND_OR_REPLACE,
                        uploadWorkRequest
                    )
                } catch (workEx: Exception) {
                    android.util.Log.w("ImageStorageHelper", "Could not enqueue image upload worker: ${workEx.message}")
                }
            }
        }

        localUri
    }
}
