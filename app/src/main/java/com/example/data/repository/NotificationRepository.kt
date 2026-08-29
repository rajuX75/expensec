package com.example.data.repository

import com.example.data.cloud.FirebaseConfigManager
import com.example.data.model.AppNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Merges admin-published notifications from Firebase Realtime Database with
 * this device's local read / deleted state (SharedPreferences via
 * [UserPreferencesRepository]) and exposes the reactive streams the UI needs:
 *  - the full notification list for the Notifications screen,
 *  - the unread badge count for the top-bar bell icon,
 *  - the next popup notification to show on app launch.
 */
class NotificationRepository(
    private val userPrefs: UserPreferencesRepository,
    firebaseConfigManager: FirebaseConfigManager,
    scope: CoroutineScope
) {
    /** All active, non-deleted notifications, newest first, with local read state applied. */
    val notifications: StateFlow<List<AppNotification>> = combine(
        firebaseConfigManager.notifications,
        userPrefs.readNotificationIds,
        userPrefs.deletedNotificationIds
    ) { remote, readIds, deletedIds ->
        remote
            .asSequence()
            .filter { it.active }
            .filter { it.id.isNotBlank() && it.id !in deletedIds }
            .map { it.copy(isRead = it.id in readIds) }
            .sortedByDescending { it.timestamp }
            .toList()
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), emptyList())

    /** Badge count shown on the bell icon. */
    val unreadCount: StateFlow<Int> = notifications
        .map { list -> list.count { !it.isRead } }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), 0)

    /** The popup is shown at most once per app session, even if not dismissed. */
    private val popupDismissedThisSession = MutableStateFlow(false)

    /**
     * The newest popup-enabled notification this device has never seen.
     * `null` when nothing should pop up.
     */
    val popupNotification: StateFlow<AppNotification?> = combine(
        notifications,
        userPrefs.lastSeenPopupNotificationId,
        popupDismissedThisSession
    ) { list, lastSeenId, dismissed ->
        if (dismissed) null
        else list.firstOrNull { it.showPopup && it.id != lastSeenId }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), null)

    fun markAsRead(id: String) = userPrefs.addReadNotificationId(id)

    fun markAllAsRead() = userPrefs.addReadNotificationIds(
        notifications.value.map { it.id }.toSet()
    )

    /** Hides a notification on this device only (admin copy in RTDB is untouched). */
    fun delete(id: String) = userPrefs.addDeletedNotificationId(id)

    /** Marks everything read and hides every current notification on this device. */
    fun clearAll() {
        val ids = notifications.value.map { it.id }.toSet()
        userPrefs.addReadNotificationIds(ids)
        userPrefs.addDeletedNotificationIds(ids)
    }

    /** Dismiss the launch popup: mark it read and remember it so it never pops again. */
    fun dismissPopup() {
        popupNotification.value?.let {
            userPrefs.addReadNotificationId(it.id)
            userPrefs.setLastSeenPopupNotificationId(it.id)
        }
        popupDismissedThisSession.value = true
    }
}
