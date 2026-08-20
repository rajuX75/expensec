package com.example.ui.components

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

object ImageStorageHelper {

    /**
     * Copies an image from a transient Uri (e.g. from gallery picker)
     * into the app's internal persistent directory.
     * Returns the permanent local file Uri string (file://...) or null if copy fails.
     */
    suspend fun saveImageLocally(
        context: Context,
        sourceUri: Uri,
        folderName: String = "photos"
    ): String? = withContext(Dispatchers.IO) {
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
                return@withContext Uri.fromFile(destFile).toString()
            }
            null
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to original Uri string if copy fails
            sourceUri.toString()
        }
    }
}
