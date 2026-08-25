package com.example.data.cloud

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * Uploads images to Cloudinary using signed upload (no SDK, just OkHttp).
 * Credentials come from BuildConfig (injected from .env via Secrets Gradle Plugin).
 */
object CloudinaryUploader {

    private const val TAG = "CloudinaryUploader"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Uploads a local file:// URI to Cloudinary.
     * Returns the secure HTTPS URL on success, null on failure.
     *
     * @param localUriString  A "file://" URI pointing to a local image file.
     * @param folder          Cloudinary folder to organise uploads.
     */
    suspend fun upload(
        localUriString: String,
        folder: String = "expense_tracker"
    ): String? = withContext(Dispatchers.IO) {
        if (!localUriString.startsWith("file://")) {
            return@withContext null  // Already a remote URL — nothing to do
        }

        val localPath = localUriString.removePrefix("file://")
        val file = File(localPath)
        if (!file.exists()) {
            Log.w(TAG, "Local file does not exist: $localPath")
            return@withContext null
        }

        try {
            val cloudName = BuildConfig.CLOUDINARY_CLOUD_NAME
            val apiKey    = BuildConfig.CLOUDINARY_API_KEY
            val apiSecret = BuildConfig.CLOUDINARY_API_SECRET
            val timestamp = (System.currentTimeMillis() / 1000).toString()

            // Params must be sorted alphabetically for Cloudinary signature
            val paramsToSign = "folder=$folder&timestamp=$timestamp"
            val signature    = sha1("$paramsToSign$apiSecret")

            val uploadUrl = "https://api.cloudinary.com/v1_1/$cloudName/image/upload"

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file", file.name,
                    file.asRequestBody("image/*".toMediaTypeOrNull())
                )
                .addFormDataPart("api_key",   apiKey)
                .addFormDataPart("timestamp", timestamp)
                .addFormDataPart("folder",    folder)
                .addFormDataPart("signature", signature)
                .build()

            val request = Request.Builder()
                .url(uploadUrl)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                val secureUrl = JSONObject(responseBody).optString("secure_url")
                if (secureUrl.isNotEmpty()) {
                    Log.d(TAG, "Cloudinary upload success: $secureUrl")
                    secureUrl
                } else {
                    Log.e(TAG, "No secure_url in Cloudinary response: $responseBody")
                    null
                }
            } else {
                Log.e(TAG, "Cloudinary upload failed [${response.code}]: $responseBody")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cloudinary upload error for $localUriString", e)
            null
        }
    }

    /** SHA-1 hex digest used for Cloudinary request signing. */
    private fun sha1(input: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
