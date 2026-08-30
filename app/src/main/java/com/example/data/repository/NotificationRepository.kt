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
     * The newest popup-enabled notification this device has never read.
     * `null` when nothing should pop up (dismissed this session, or all popup
     * notifications have already been read on this device).
     *
     * Uses [readNotificationIds] (a Set) instead of a single lastSeenId so that:
     *  - Multiple concurrent showPopup notifications are all suppressed once read.
     *  - Marking a notification read in the inbox also prevents its popup.
     */
    val popupNotification: StateFlow<AppNotification?> = combine(
        notifications,
        userPrefs.readNotificationIds,
        popupDismissedThisSession
    ) { list, readIds, dismissed ->
        if (dismissed) null
        else list.firstOrNull { it.showPopup && it.id !in readIds }
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

    /**
     * Dismiss the launch popup for a specific notification.
     * The [notificationId] is passed explicitly by the UI so we never rely on
     * the StateFlow's `.value` (which starts as `null` and may momentarily be
     * `null` when the flow has no subscribers).
     */
    fun dismissPopup(notificationId: String) {
        if (notificationId.isNotBlank()) {
            userPrefs.addReadNotificationId(notificationId)
            userPrefs.setLastSeenPopupNotificationId(notificationId)
        }
        popupDismissedThisSession.value = true
    }

    /**
     * Called when the user opens the Notifications screen while a popup would
     * have been showing. The popup is suppressed by the screen check in the UI,
     * but without this call it would reappear when the user navigates back.
     * We mark it as read and remember it so it never pops up again.
     */
    fun dismissPopupIfShowing() {
        val current = popupNotification.value ?: return
        dismissPopup(current.id)
    }
}
