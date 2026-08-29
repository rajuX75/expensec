package com.example.data.model

import java.util.UUID

enum class FeedbackType(val label: String) {
    FEATURE_REQUEST("Feature Request"),
    BUG_REPORT("Bug Report"),
    CRASH_LOG("Crash Log"),
    GENERAL("General")
}

data class FeedbackEntry(
    val id: String = UUID.randomUUID().toString(),
    val type: FeedbackType = FeedbackType.GENERAL,
    val message: String = "",
    val appVersion: String = "",
    val appVersionCode: Int = 0,
    val deviceModel: String = "",
    val androidVersion: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val userId: String = "anonymous",
    val email: String? = null
)

sealed class FeedbackSubmitState {
    object Idle : FeedbackSubmitState()
    object Submitting : FeedbackSubmitState()
    object Success : FeedbackSubmitState()
    data class Error(val message: String) : FeedbackSubmitState()
}
