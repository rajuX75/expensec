package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object ImageStorageHelper {

    private const val TAG = "ImageStorageHelper"

    /** Longest edge of the compressed image we store/upload. */
    private const val MAX_DIMENSION = 1280

    /** JPEG quality for the compressed copy. */
    private const val JPEG_QUALITY = 82

    /**
     * Copies an image locally (compressed to a sane size), then attempts to
     * upload it to Cloudinary. Returns the Cloudinary download URL on success.
     * If upload fails, returns the persistent local file Uri and queues the
     * [com.example.data.cloud.CloudinaryImageUploadWorker] to retry when online.
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

            // Decode + downscale + fix EXIF rotation instead of a blind byte copy,
            // so we never store/upload multi-MB full-resolution camera images.
            val compressed = compressImage(context, sourceUri)
            if (compressed != null) {
                FileOutputStream(destFile).use { out ->
                    compressed.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                }
                localUri = Uri.fromFile(destFile).toString()
            } else {
                // Fallback: plain byte copy if the bitmap could not be decoded.
                context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                    FileOutputStream(destFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
                localUri = Uri.fromFile(destFile).toString()
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to save image locally", e)
            localUri = sourceUri.toString() // Fallback to original
        }

        // Attempt to upload to Cloudinary for permanent cross-device syncing
        if (localUri.startsWith("file://")) {
            val cloudUrl = com.example.data.cloud.CloudinaryUploader.upload(context, localUri, folderName)
            if (cloudUrl != null) {
                return@withContext cloudUrl
            } else {
                // Upload failed (offline or Cloudinary not configured).
                // Queue the worker to upload it later when connected.
                enqueueUploadWorker(context)
            }
        }

        localUri
    }

    /**
     * Enqueues the one-time image-upload worker with a network constraint.
     * Uses APPEND_OR_REPLACE so multiple pending images don't spawn a queue
     * of duplicate workers.
     */
    fun enqueueUploadWorker(context: Context) {
        try {
            val uploadWorkRequest =
                androidx.work.OneTimeWorkRequestBuilder<com.example.data.cloud.CloudinaryImageUploadWorker>()
                    .setConstraints(
                        androidx.work.Constraints.Builder()
                            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                            .build()
                    )
                    .setBackoffCriteria(
                        androidx.work.BackoffPolicy.EXPONENTIAL,
                        30,
                        java.util.concurrent.TimeUnit.SECONDS
                    )
                    .build()
            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(
                "CloudinaryImageUpload",
                androidx.work.ExistingWorkPolicy.APPEND_OR_REPLACE,
                uploadWorkRequest
            )
        } catch (workEx: Exception) {
            android.util.Log.w(TAG, "Could not enqueue image upload worker: ${workEx.message}")
        }
    }

    /**
     * Decodes [sourceUri], downscales it to at most [MAX_DIMENSION] px on the
     * longest edge, and applies EXIF rotation. Returns null on decode failure.
     */
    private fun compressImage(context: Context, sourceUri: Uri): Bitmap? {
        return try {
            // First pass: bounds only
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(sourceUri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            // Second pass: sampled decode
            val sample = calculateInSampleSize(bounds.outWidth, bounds.outHeight, MAX_DIMENSION)
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            val decoded = context.contentResolver.openInputStream(sourceUri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            } ?: return null

            applyExifRotation(context, sourceUri, decoded)
        } catch (e: Exception) {
            android.util.Log.e(TAG, "compressImage failed", e)
            null
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDim: Int): Int {
        var inSampleSize = 1
        var w = width
        var h = height
        while (w / 2 >= maxDim || h / 2 >= maxDim) {
            w /= 2
            h /= 2
            inSampleSize *= 2
        }
        return inSampleSize
    }

    private fun applyExifRotation(context: Context, sourceUri: Uri, bitmap: Bitmap): Bitmap {
        return try {
            val rotation = context.contentResolver.openInputStream(sourceUri)?.use { input ->
                val exif = ExifInterface(input)
                when (
                    exif.getAttributeInt(
                        ExifInterface.TAG_ORIENTATION,
                        ExifInterface.ORIENTATION_NORMAL
                    )
                ) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            } ?: 0f

            if (rotation == 0f) bitmap
            else {
                val matrix = android.graphics.Matrix().apply { postRotate(rotation) }
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            }
        } catch (e: Exception) {
            bitmap
        }
    }
}
