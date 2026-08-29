package com.example.data.repository

import android.util.Log
import com.example.data.model.FeedbackEntry
import com.example.data.model.FeedbackSubmitState
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class FeedbackRepository {
    private val TAG = "FeedbackRepository"
    private val REST_DATABASE_URL = "https://expenstracke-default-rtdb.firebaseio.com"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    suspend fun submitFeedback(entry: FeedbackEntry): FeedbackSubmitState = withContext(Dispatchers.IO) {
        try {
            // Convert to map for JSON/Firebase
            val feedbackMap = mapOf(
                "id" to entry.id,
                "type" to entry.type.name,
                "message" to entry.message,
                "appVersion" to entry.appVersion,
                "appVersionCode" to entry.appVersionCode,
                "deviceModel" to entry.deviceModel,
                "androidVersion" to entry.androidVersion,
                "timestamp" to entry.timestamp,
                "userId" to entry.userId,
                "email" to entry.email
            )

            // Try saving to Firebase Realtime Database using REST API
            // Using REST API avoids needing coroutine extensions for Task
            val jsonObject = JSONObject(feedbackMap)
            val requestBody = jsonObject.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            
            val request = Request.Builder()
                .url("$REST_DATABASE_URL/feedback/${entry.id}.json")
                .put(requestBody)
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                FeedbackSubmitState.Success
            } else {
                FeedbackSubmitState.Error("Failed to submit feedback: HTTP ${response.code}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error submitting feedback", e)
            FeedbackSubmitState.Error(e.message ?: "Unknown error")
        }
    }
}
