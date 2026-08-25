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
 * Uploads images to Cloudinary using signed upload (REST API via OkHttp).
 *
 * Credentials come from BuildConfig (injected from .env via Secrets Gradle Plugin).
 *
 * Docs: https://cloudinary.com/documentation/java_integration
 * Upload API: https://cloudinary.com/documentation/image_upload_api_reference
 */
object CloudinaryUploader {

    private const val TAG = "CloudinaryUploader"

    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    /**
     * True when real Cloudinary credentials are configured (i.e. the developer
     * replaced the placeholder values in .env). When false, uploads are skipped
     * with a clear error instead of failing silently.
     */
    fun isConfigured(): Boolean {
        val cloudName = BuildConfig.CLOUDINARY_CLOUD_NAME
        val apiKey = BuildConfig.CLOUDINARY_API_KEY
        val apiSecret = BuildConfig.CLOUDINARY_API_SECRET
        return !cloudName.isNullOrBlank() && cloudName != "your_cloud_name" &&
                !apiKey.isNullOrBlank() && apiKey != "your_api_key" &&
                !apiSecret.isNullOrBlank() && apiSecret != "your_api_secret"
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
            return@withContext localUriString.ifBlank { null } // Already a remote URL
        }

        if (!isConfigured()) {
            Log.e(
                TAG,
                "Cloudinary is NOT configured. Put real CLOUDINARY_CLOUD_NAME / " +
                        "CLOUDINARY_API_KEY / CLOUDINARY_API_SECRET in .env and rebuild. " +
                        "Keeping local file so it can be retried later."
            )
            return@withContext null
        }

        val localPath = localUriString.removePrefix("file://")
        val file = File(localPath)
        if (!file.exists()) {
            Log.w(TAG, "Local file does not exist: $localPath")
            return@withContext null
        }

        try {
            val cloudName = BuildConfig.CLOUDINARY_CLOUD_NAME.trim()
            val apiKey = BuildConfig.CLOUDINARY_API_KEY.trim()
            val apiSecret = BuildConfig.CLOUDINARY_API_SECRET.trim()
            val timestamp = (System.currentTimeMillis() / 1000).toString()

            // Params must be sorted alphabetically for Cloudinary signature
            val paramsToSign = "folder=$folder&timestamp=$timestamp"
            val signature = sha1("$paramsToSign$apiSecret")

            val uploadUrl = "https://api.cloudinary.com/v1_1/$cloudName/image/upload"

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file", file.name,
                    file.asRequestBody("image/*".toMediaTypeOrNull())
                )
                .addFormDataPart("api_key", apiKey)
                .addFormDataPart("timestamp", timestamp)
                .addFormDataPart("folder", folder)
                .addFormDataPart("signature", signature)
                .build()

            val request = Request.Builder()
                .url(uploadUrl)
                .post(requestBody)
                .build()

            // use {} closes the response body — fixes the connection leak that
            // eventually made every upload time out after a few images.
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string()

                if (response.isSuccessful && responseBody != null) {
                    val secureUrl = JSONObject(responseBody).optString("secure_url")
                    if (secureUrl.isNotEmpty()) {
                        Log.d(TAG, "Cloudinary upload success: $secureUrl")
                        return@withContext secureUrl
                    } else {
                        Log.e(TAG, "No secure_url in Cloudinary response: $responseBody")
                        return@withContext null
                    }
                } else {
                    Log.e(TAG, "Cloudinary upload failed [${response.code}]: $responseBody")
                    return@withContext null
                }
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
