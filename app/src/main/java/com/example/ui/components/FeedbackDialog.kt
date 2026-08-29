package com.example.ui.components

import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.FeedbackEntry
import com.example.data.model.FeedbackSubmitState
import com.example.data.model.FeedbackType
import com.google.firebase.auth.FirebaseUser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackDialog(
    initialType: FeedbackType = FeedbackType.GENERAL,
    appVersionName: String,
    appVersionCode: Int,
    firebaseUser: FirebaseUser?,
    submitState: FeedbackSubmitState,
    onSubmit: (FeedbackEntry) -> Unit,
    onDismiss: () -> Unit,
    onResetState: () -> Unit
) {
    var selectedType by remember { mutableStateOf(initialType) }
    var message by remember { mutableStateOf("") }
    var email by remember { mutableStateOf(firebaseUser?.email ?: "") }
    var includeDeviceInfo by remember { mutableStateOf(true) }

    val context = LocalContext.current
    val isSubmitting = submitState is FeedbackSubmitState.Submitting

    LaunchedEffect(submitState) {
        if (submitState is FeedbackSubmitState.Success) {
            Toast.makeText(context, "Feedback sent! Thank you.", Toast.LENGTH_SHORT).show()
            onResetState()
            onDismiss()
        } else if (submitState is FeedbackSubmitState.Error) {
            Toast.makeText(context, (submitState as FeedbackSubmitState.Error).message, Toast.LENGTH_LONG).show()
            onResetState()
        }
    }

    Dialog(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !isSubmitting, dismissOnClickOutside = !isSubmitting)
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Icon(
                    imageVector = Icons.Default.Feedback,
                    contentDescription = "Feedback",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Send Feedback",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(16.dp))

                // Type Chips
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        TypeChip(
                            label = FeedbackType.FEATURE_REQUEST.label,
                            icon = Icons.Default.Lightbulb,
                            selected = selectedType == FeedbackType.FEATURE_REQUEST,
                            onClick = { selectedType = FeedbackType.FEATURE_REQUEST }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TypeChip(
                            label = FeedbackType.BUG_REPORT.label,
                            icon = Icons.Default.BugReport,
                            selected = selectedType == FeedbackType.BUG_REPORT,
                            onClick = { selectedType = FeedbackType.BUG_REPORT }
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        TypeChip(
                            label = FeedbackType.CRASH_LOG.label,
                            icon = Icons.Default.Warning,
                            selected = selectedType == FeedbackType.CRASH_LOG,
                            onClick = { selectedType = FeedbackType.CRASH_LOG }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        TypeChip(
                            label = FeedbackType.GENERAL.label,
                            icon = Icons.Default.ChatBubble,
                            selected = selectedType == FeedbackType.GENERAL,
                            onClick = { selectedType = FeedbackType.GENERAL }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Message Input
                OutlinedTextField(
                    value = message,
                    onValueChange = { message = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp),
                    label = { Text("Tell us more...") },
                    placeholder = { Text("What's on your mind?") },
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Email Input
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Email (Optional)") },
                    placeholder = { Text("For follow-up") },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Device Info Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = includeDeviceInfo,
                        onCheckedChange = { includeDeviceInfo = it }
                    )
                    Text(
                        text = "Include device & app version info",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            if (message.isBlank()) {
                                Toast.makeText(context, "Please enter a message", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            
                            val entry = FeedbackEntry(
                                type = selectedType,
                                message = message.trim(),
                                appVersion = if (includeDeviceInfo) appVersionName else "hidden",
                                appVersionCode = if (includeDeviceInfo) appVersionCode else 0,
                                deviceModel = if (includeDeviceInfo) Build.MODEL else "hidden",
                                androidVersion = if (includeDeviceInfo) Build.VERSION.RELEASE else "hidden",
                                userId = firebaseUser?.uid ?: "anonymous",
                                email = email.trim().takeIf { it.isNotEmpty() }
                            )
                            onSubmit(entry)
                        },
                        enabled = !isSubmitting,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Send")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TypeChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    val containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = containerColor,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal),
                color = contentColor
            )
        }
    }
}
