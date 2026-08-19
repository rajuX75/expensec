package com.example.data.cloud

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

data class CloudFileMetadata(
    val id: String,
    val name: String,
    val modifiedTime: Long,
    val sizeBytes: Long = 0L
)

class CloudConflictException(
    val cloudExportedAt: Long,
    val localExportedAt: Long,
    message: String = "A newer backup from ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(cloudExportedAt))} exists in the cloud."
) : Exception(message)

interface DriveClient {
    suspend fun getAppBackupMetadata(accessToken: String): CloudFileMetadata?
    suspend fun downloadAppBackup(accessToken: String, fileId: String): String
    suspend fun uploadAppBackup(accessToken: String, jsonContent: String, existingFileId: String?): CloudFileMetadata
}

class GoogleDriveRestClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) : DriveClient {

    companion object {
        const val BACKUP_FILE_NAME = "expense_backup.json"
        private const val DRIVE_API_BASE = "https://www.googleapis.com/drive/v3"
        private const val DRIVE_UPLOAD_BASE = "https://www.googleapis.com/upload/drive/v3"
    }

    override suspend fun getAppBackupMetadata(accessToken: String): CloudFileMetadata? {
        val url = "$DRIVE_API_BASE/files?spaces=appDataFolder&q=name%3D'$BACKUP_FILE_NAME'%20and%20trashed%3Dfalse&fields=files(id,name,modifiedTime,size)"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                if (response.code == 404) return null
                throw IOException("Failed to check Google Drive files: HTTP ${response.code} ${response.message}")
            }
            val body = response.body?.string() ?: return null
            val json = JSONObject(body)
            val files = json.optJSONArray("files") ?: return null
            if (files.length() == 0) return null

            val fileObj = files.getJSONObject(0)
            val id = fileObj.getString("id")
            val name = fileObj.getString("name")
            val modifiedTimeStr = fileObj.optString("modifiedTime", "")
            val size = fileObj.optLong("size", 0L)

            val parsedTime = parseIsoDate(modifiedTimeStr)
            return CloudFileMetadata(id = id, name = name, modifiedTime = parsedTime, sizeBytes = size)
        }
    }

    override suspend fun downloadAppBackup(accessToken: String, fileId: String): String {
        val url = "$DRIVE_API_BASE/files/$fileId?alt=media"
        val request = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer $accessToken")
            .get()
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Failed to download backup from Google Drive: HTTP ${response.code} ${response.message}")
            }
            return response.body?.string() ?: throw IOException("Empty response from Google Drive")
        }
    }

    override suspend fun uploadAppBackup(accessToken: String, jsonContent: String, existingFileId: String?): CloudFileMetadata {
        val jsonMediaType = "application/json; charset=UTF-8".toMediaType()

        if (existingFileId != null) {
            // Update existing file in appDataFolder
            val url = "$DRIVE_UPLOAD_BASE/files/$existingFileId?uploadType=media"
            val requestBody = jsonContent.toRequestBody(jsonMediaType)
            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .patch(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Failed to update backup on Google Drive: HTTP ${response.code} ${response.message}")
                }
                val body = response.body?.string() ?: "{}"
                val resObj = JSONObject(body)
                val id = resObj.optString("id", existingFileId)
                return CloudFileMetadata(
                    id = id,
                    name = BACKUP_FILE_NAME,
                    modifiedTime = parseIsoDate(resObj.optString("modifiedTime", ""))
                )
            }
        } else {
            // Create new file in appDataFolder using multipart upload
            val url = "$DRIVE_UPLOAD_BASE/files?uploadType=multipart"

            val metadataJson = JSONObject()
                .put("name", BACKUP_FILE_NAME)
                .put("parents", org.json.JSONArray().put("appDataFolder"))
                .toString()

            val multipartBody = MultipartBody.Builder()
                .setType("multipart/related".toMediaType())
                .addPart(metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaType()))
                .addPart(jsonContent.toRequestBody(jsonMediaType))
                .build()

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $accessToken")
                .post(multipartBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Failed to upload backup to Google Drive: HTTP ${response.code} ${response.message}")
                }
                val body = response.body?.string() ?: "{}"
                val resObj = JSONObject(body)
                val id = resObj.getString("id")
                return CloudFileMetadata(
                    id = id,
                    name = BACKUP_FILE_NAME,
                    modifiedTime = parseIsoDate(resObj.optString("modifiedTime", ""))
                )
            }
        }
    }

    private fun parseIsoDate(isoString: String): Long {
        if (isoString.isBlank()) return System.currentTimeMillis()
        return runCatching {
            val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            sdf.timeZone = TimeZone.getTimeZone("UTC")
            sdf.parse(isoString)?.time ?: System.currentTimeMillis()
        }.getOrElse {
            runCatching {
                val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                sdf.parse(isoString)?.time ?: System.currentTimeMillis()
            }.getOrDefault(System.currentTimeMillis())
        }
    }
}
