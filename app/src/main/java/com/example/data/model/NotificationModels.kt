package com.example.data.model

/**
 * In-app notification models.
 *
 * Notifications are published by the admin into Firebase Realtime Database
 * under the `/notifications` node (NOT via FCM push). Every field is
 * customizable from the console / REST — the app reacts to changes in
 * real time and persists local read/deleted state per device.
 */
enum class NotificationType(val label: String, val badgeColor: Long) {
    INFO("Info", 0xFF3B82F6),       // Blue
    SUCCESS("Success", 0xFF10B981), // Emerald
    WARNING("Warning", 0xFFF59E0B), // Amber
    PROMO("Promo", 0xFF8B5CF6),     // Violet
    UPDATE("Update", 0xFF0EA5E9),   // Sky
    ALERT("Alert", 0xFFEF4444);     // Red

    companion object {
        fun fromString(raw: String?): NotificationType = when (raw?.trim()?.uppercase()) {
            "SUCCESS" -> SUCCESS
            "WARNING", "WARN" -> WARNING
            "PROMO", "OFFER" -> PROMO
            "UPDATE" -> UPDATE
            "ALERT", "URGENT", "CRITICAL" -> ALERT
            else -> INFO
        }
    }
}

data class AppNotification(
    val id: String = "",
    val title: String = "",
    val message: String = "",
    val type: NotificationType = NotificationType.INFO,
    val timestamp: Long = 0L,
    val actionUrl: String? = null,
    val actionText: String? = null,
    val iconEmoji: String? = null,
    val accentColorHex: String? = null,
    /** When true, this notification pops up over the app on launch (once per device). */
    val showPopup: Boolean = false,
    /** When false, only the close button / back press can dismiss the popup. */
    val dismissible: Boolean = true,
    val active: Boolean = true,
    /** Local, per-device state — never stored in Firebase. */
    val isRead: Boolean = false
) {
    /** Accent color from RTDB (e.g. "#6366F1"), falling back to the type's badge color. */
    val accentColor: Long
        get() = accentColorHex
            ?.let { parseHexColor(it) }
            ?: type.badgeColor

    val hasAction: Boolean
        get() = !actionUrl.isNullOrBlank()

    companion object {
        fun parseHexColor(hex: String): Long? = try {
            val clean = hex.trim().removePrefix("#")
            when (clean.length) {
                6 -> ("FF$clean").toLong(16)
                8 -> clean.toLong(16)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
