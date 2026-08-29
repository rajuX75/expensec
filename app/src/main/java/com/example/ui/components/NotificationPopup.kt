package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AppNotification

/**
 * Launch popup for admin-published notifications.
 *
 * Dismissal paths (as required):
 *  - the system BACK press closes the popup (`dismissOnBackPress = true`),
 *  - the dedicated ✕ close button in the corner,
 *  - tapping outside (only when the admin marks the notification dismissible).
 *
 * Everything visual (title, message, emoji, accent color, action) is driven
 * by Firebase Realtime DB data, so the admin can restyle it without an update.
 */
@Composable
fun NotificationPopupDialog(
    notification: AppNotification,
    onDismiss: () -> Unit,
    onActionClick: (String) -> Unit
) {
    val accent = Color(notification.accentColor)

    Dialog(
        onDismissRequest = { if (notification.dismissible) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = true, // Back button always closes the popup
            dismissOnClickOutside = notification.dismissible,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column {
                // Accent header with icon + close button
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(accent.copy(alpha = 0.14f))
                        .padding(start = 20.dp, top = 8.dp, end = 8.dp, bottom = 16.dp)
                ) {
                    Column(modifier = Modifier.align(Alignment.CenterStart)) {
                        Surface(
                            shape = CircleShape,
                            color = accent.copy(alpha = 0.18f),
                            modifier = Modifier.size(52.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (!notification.iconEmoji.isNullOrBlank()) {
                                    Text(
                                        text = notification.iconEmoji,
                                        style = MaterialTheme.typography.headlineSmall
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = null,
                                        tint = accent
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = accent.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = notification.type.label.uppercase(),
                                color = accent,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    // Dedicated close button
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Body
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                    Text(
                        text = notification.title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (notification.message.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = notification.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Dismiss")
                        }
                        if (notification.hasAction) {
                            Spacer(modifier = Modifier.width(4.dp))
                            Button(
                                onClick = {
                                    onDismiss()
                                    notification.actionUrl?.let(onActionClick)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = accent),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = notification.actionText?.takeIf { it.isNotBlank() } ?: "Open",
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
