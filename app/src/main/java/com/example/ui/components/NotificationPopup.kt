package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.AppNotification

/**
 * Modern redesigned launch popup for admin-published notifications.
 *
 * Design highlights:
 *  - Full-width card with large gradient hero banner driven by the accent color
 *  - Frosted-glass emoji pill with a soft glow ring
 *  - Type badge with subtle tint
 *  - Floating ✕ pill button in the top-right corner
 *  - Spring-animated scale + fade-in entrance
 *  - Full-width primary action button; secondary "dismiss" text link below
 *
 * Dismissal paths:
 *  - BACK press (if admin marks it dismissible)
 *  - Floating ✕ button (always)
 *  - Tap outside (only when dismissible)
 */
@Composable
fun NotificationPopupDialog(
    notification: AppNotification,
    onDismiss: () -> Unit,
    onActionClick: (String) -> Unit
) {
    val accent = Color(notification.accentColor)

    // ── Entrance animation ─────────────────────────────────────────────────
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val scale by animateFloatAsState(
        targetValue = if (visible) 1f else 0.88f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "popup_scale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "popup_alpha"
    )

    Dialog(
        onDismissRequest = { if (notification.dismissible) onDismiss() },
        properties = DialogProperties(
            dismissOnBackPress = notification.dismissible,
            dismissOnClickOutside = notification.dismissible,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .scale(scale)
                .alpha(alpha)
        ) {
            // ── Main card ──────────────────────────────────────────────────
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 24.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    // ── Gradient hero banner ───────────────────────────────
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .background(
                                brush = Brush.verticalGradient(
                                    colors = listOf(
                                        accent.copy(alpha = 0.80f),
                                        accent.copy(alpha = 0.30f)
                                    )
                                )
                            )
                    ) {
                        // Soft ambient glow circle behind the icon
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .align(Alignment.Center)
                                .background(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.18f),
                                            Color.Transparent
                                        )
                                    ),
                                    shape = CircleShape
                                )
                        )

                        // Icon / emoji pill
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.22f),
                            modifier = Modifier
                                .size(72.dp)
                                .align(Alignment.Center)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (!notification.iconEmoji.isNullOrBlank()) {
                                    Text(
                                        text = notification.iconEmoji,
                                        fontSize = 34.sp,
                                        textAlign = TextAlign.Center
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Notifications,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(36.dp)
                                    )
                                }
                            }
                        }

                        // Type badge — bottom-start of the banner
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = Color.White.copy(alpha = 0.22f),
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(start = 20.dp, bottom = 14.dp)
                        ) {
                            Text(
                                text = notification.type.label.uppercase(),
                                color = Color.White,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.2.sp
                                ),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }

                    // ── Body ───────────────────────────────────────────────
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(top = 22.dp, bottom = 20.dp)
                    ) {
                        Text(
                            text = notification.title,
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )

                        if (notification.message.isNotBlank()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = notification.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 22.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // ── Action button(s) ───────────────────────────────
                        if (notification.hasAction) {
                            Button(
                                onClick = { notification.actionUrl?.let(onActionClick) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = accent)
                            ) {
                                Text(
                                    text = notification.actionText?.takeIf { it.isNotBlank() } ?: "Open",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = if (notification.hasAction) "Maybe later" else "Got it",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // ── Floating close button ──────────────────────────────────────
            // Sits in the top-right corner, overlapping the card edge
            Surface(
                onClick = onDismiss,
                shape = CircleShape,
                color = Color.Black.copy(alpha = 0.32f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 10.dp)
                    .size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
