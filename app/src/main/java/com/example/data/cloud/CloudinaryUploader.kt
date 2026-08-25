package com.example.data.cloud

import android.content.Context
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

data class CloudinaryConfig(
    val cloudName: String = "",
    val apiKey: String = "",
    val apiSecret: String = "",
    val uploadPreset: String = ""
) {
    fun isConfigured(): Boolean {
        val validCloud = cloudName.isNotBlank() && cloudName != "your_cloud_name"
        val hasSigned = apiKey.isNotBlank() && apiKey != "your_api_key" &&
                apiSecret.isNotBlank() && apiSecret != "your_api_secret"
        val hasUnsigned = uploadPreset.isNotBlank() && uploadPreset != "your_upload_preset"
        return validCloud && (hasSigned || hasUnsigned)
    }
}

/**
 * Uploads images to Cloudinary using signed upload (or unsigned preset) via REST API.
 *
 * Config resolution priority:
 * 1. User runtime preferences stored in App Settings (expense_user_prefs)
 * 2. Environment variables injected at build time (ENV_CLOUDINARY_*)
 * 3. Secrets Gradle plugin fields (CLOUDINARY_*)
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
     * Resolves Cloudinary configuration.
     */
    fun getConfig(context: Context? = null): CloudinaryConfig {
        var prefCloud = ""
        var prefKey = ""
        var prefSecret = ""
        var prefPreset = ""

        if (context != null) {
            try {
                val prefs = context.getSharedPreferences("expense_user_prefs", Context.MODE_PRIVATE)
                prefCloud = prefs.getString("cloudinary_cloud_name", "") ?: ""
                prefKey = prefs.getString("cloudinary_api_key", "") ?: ""
                prefSecret = prefs.getString("cloudinary_api_secret", "") ?: ""
                prefPreset = prefs.getString("cloudinary_upload_preset", "") ?: ""
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read cloudinary prefs: ${e.message}")
            }
        }

        val cloudName = when {
            prefCloud.isNotBlank() -> prefCloud
            BuildConfig.ENV_CLOUDINARY_CLOUD_NAME.isNotBlank() && BuildConfig.ENV_CLOUDINARY_CLOUD_NAME != "your_cloud_name" -> BuildConfig.ENV_CLOUDINARY_CLOUD_NAME
            BuildConfig.CLOUDINARY_CLOUD_NAME.isNotBlank() && BuildConfig.CLOUDINARY_CLOUD_NAME != "your_cloud_name" -> BuildConfig.CLOUDINARY_CLOUD_NAME
            else -> ""
        }

        val apiKey = when {
            prefKey.isNotBlank() -> prefKey
            BuildConfig.ENV_CLOUDINARY_API_KEY.isNotBlank() && BuildConfig.ENV_CLOUDINARY_API_KEY != "your_api_key" -> BuildConfig.ENV_CLOUDINARY_API_KEY
            BuildConfig.CLOUDINARY_API_KEY.isNotBlank() && BuildConfig.CLOUDINARY_API_KEY != "your_api_key" -> BuildConfig.CLOUDINARY_API_KEY
            else -> ""
        }

        val apiSecret = when {
            prefSecret.isNotBlank() -> prefSecret
            BuildConfig.ENV_CLOUDINARY_API_SECRET.isNotBlank() && BuildConfig.ENV_CLOUDINARY_API_SECRET != "your_api_secret" -> BuildConfig.ENV_CLOUDINARY_API_SECRET
            BuildConfig.CLOUDINARY_API_SECRET.isNotBlank() && BuildConfig.CLOUDINARY_API_SECRET != "your_api_secret" -> BuildConfig.CLOUDINARY_API_SECRET
            else -> ""
        }

        val uploadPreset = prefPreset.trim()

        return CloudinaryConfig(
            cloudName = cloudName.trim(),
            apiKey = apiKey.trim(),
            apiSecret = apiSecret.trim(),
            uploadPreset = uploadPreset
        )
    }

    fun isConfigured(context: Context? = null): Boolean {
        return getConfig(context).isConfigured()
    }

    /**
     * Uploads a local file:// URI to Cloudinary.
     * Returns the secure HTTPS URL on success, null on failure.
     *
     * @param context         Optional Android context for reading runtime preferences.
     * @param localUriString  A "file://" URI pointing to a local image file.
     * @param folder          Cloudinary folder to organise uploads.
     */
    suspend fun upload(
        context: Context? = null,
        localUriString: String,
        folder: String = "expense_tracker"
    ): String? = withContext(Dispatchers.IO) {
        if (!localUriString.startsWith("file://")) {
            // Already a remote URL (http/https)
            if (localUriString.startsWith("http://") || localUriString.startsWith("https://")) {
                return@withContext localUriString
            }
        }

        val config = getConfig(context)
        if (!config.isConfigured()) {
            Log.w(
                TAG,
                "Cloudinary is not configured (cloud: '${config.cloudName}', hasKey: ${config.apiKey.isNotBlank()}). Skipping direct upload."
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
            val timestamp = (System.currentTimeMillis() / 1000).toString()
            val uploadUrl = "https://api.cloudinary.com/v1_1/${config.cloudName}/image/upload"

            val bodyBuilder = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart(
                    "file", file.name,
                    file.asRequestBody("image/*".toMediaTypeOrNull())
                )

            val isSigned = config.apiKey.isNotBlank() && config.apiKey != "your_api_key" &&
                    config.apiSecret.isNotBlank() && config.apiSecret != "your_api_secret"

            if (isSigned) {
                // Params must be sorted alphabetically for Cloudinary signature
                val paramsToSign = "folder=$folder&timestamp=$timestamp"
                val signature = sha1("$paramsToSign${config.apiSecret}")
                bodyBuilder
                    .addFormDataPart("api_key", config.apiKey)
                    .addFormDataPart("timestamp", timestamp)
                    .addFormDataPart("folder", folder)
                    .addFormDataPart("signature", signature)
            } else if (config.uploadPreset.isNotBlank()) {
                bodyBuilder.addFormDataPart("upload_preset", config.uploadPreset)
                if (folder.isNotBlank()) {
                    bodyBuilder.addFormDataPart("folder", folder)
                }
            }

            val request = Request.Builder()
                .url(uploadUrl)
                .post(bodyBuilder.build())
                .build()

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

    /** Overload for compatibility without context parameter */
    suspend fun upload(
        localUriString: String,
        folder: String = "expense_tracker"
    ): String? = upload(null, localUriString, folder)

    /** Helper to test Cloudinary connectivity from the app settings. */
    suspend fun testConnection(context: Context? = null): Result<String> = withContext(Dispatchers.IO) {
        val config = getConfig(context)
        if (!config.isConfigured()) {
            return@withContext Result.failure(Exception("Cloudinary credentials are not configured or still placeholders."))
        }
        try {
            val pingUrl = "https://api.cloudinary.com/v1_1/${config.cloudName}/ping"
            val request = Request.Builder().url(pingUrl).get().build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success("Connected to Cloudinary account: ${config.cloudName}")
                } else {
                    Result.failure(Exception("Cloudinary returned HTTP ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** SHA-1 hex digest used for Cloudinary request signing. */
    private fun sha1(input: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
