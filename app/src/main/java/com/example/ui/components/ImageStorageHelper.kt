package com.example.ui.components

import android.content.Context
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object ImageStorageHelper {

    /**
     * Copies an image locally, then attempts to upload it to Firebase Cloud Storage.
     * Returns the Firebase Storage download URL on success. 
     * If upload fails or user is not logged in, returns the persistent local file Uri.
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

        // Attempt to upload to Firebase Storage for permanent cross-device syncing
        val user = FirebaseAuth.getInstance().currentUser
        if (user != null && localUri != null && localUri!!.startsWith("file://")) {
            try {
                val storageRef = FirebaseStorage.getInstance().reference
                    .child("users/${user.uid}/$folderName/${UUID.randomUUID()}.jpg")
                storageRef.putFile(Uri.parse(localUri)).await()
                val downloadUrl = storageRef.downloadUrl.await()
                return@withContext downloadUrl.toString() // Return the cloud URL!
            } catch (e: Exception) {
                e.printStackTrace()
                // If upload fails (e.g. offline), we fall back to returning the local URI below.
                // In a production app, you might queue a background Worker to upload this later.
            }
        }

        localUri
    }
}
