package com.example.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.cloud.SyncState
import com.example.ui.theme.ExpenseGreen
import com.example.ui.theme.ExpenseRed
import com.example.ui.viewmodel.ExpenseViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun FirebaseSyncCard(
    viewModel: ExpenseViewModel,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val firebaseUser by viewModel.firebaseUser.collectAsState()
    val syncState by viewModel.firestoreSyncState.collectAsState()
    val syncMessage by viewModel.firestoreSyncMessage.collectAsState()
    val lastSyncTime by viewModel.lastFirestoreSyncTime.collectAsState()
    val googleAccountEmail by viewModel.googleAccountEmail.collectAsState()

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("firebase_sync_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Storage,
                        contentDescription = "Firebase Firestore Database",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Firebase Cloud Database",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Real-time sync across devices",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Sync Status Badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        syncState == SyncState.SYNCING -> MaterialTheme.colorScheme.secondaryContainer
                        firebaseUser != null -> ExpenseGreen.copy(alpha = 0.15f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        syncState == SyncState.SYNCING -> MaterialTheme.colorScheme.primary
                                        firebaseUser != null -> ExpenseGreen
                                        else -> Color.Gray
                                    }
                                )
                        )
                        Text(
                            text = when {
                                syncState == SyncState.SYNCING -> "Syncing"
                                firebaseUser != null -> "Connected"
                                else -> "Offline"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                syncState == SyncState.SYNCING -> MaterialTheme.colorScheme.primary
                                firebaseUser != null -> ExpenseGreen
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }

            HorizontalDivider()

            // Account & Connection details
            if (firebaseUser != null || !googleAccountEmail.isNullOrBlank()) {
                val email = firebaseUser?.email ?: googleAccountEmail ?: ""
                val userId = firebaseUser?.uid ?: "Cloud Synced"

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = email,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = "User ID: $userId",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Last Synced: ${dateFormat.format(Date(lastSyncTime))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.syncWithFirestore { result ->
                                result.onSuccess {
                                    onShowMessage("Firebase Database synced successfully!")
                                }.onFailure { err ->
                                    onShowMessage("Firebase sync error: ${err.message}")
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("firebase_sync_now_button"),
                        enabled = syncState != SyncState.SYNCING
                    ) {
                        if (syncState == SyncState.SYNCING) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Syncing...")
                        } else {
                            Icon(
                                imageVector = Icons.Default.Sync,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sync Now")
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.signOutGoogle {
                                onShowMessage("Signed out from Firebase")
                            }
                        },
                        modifier = Modifier.testTag("firebase_sign_out_button")
                    ) {
                        Text("Sign Out")
                    }
                }
            } else {
                // Not logged in
                Text(
                    text = "Sign in with Google to automatically back up and synchronize all your transactions, categories, budgets, and debt ledger to Firebase Firestore.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = {
                        viewModel.signInGoogle(context) { result ->
                            result.onSuccess { email ->
                                onShowMessage("Connected Firebase account: $email")
                            }.onFailure { err ->
                                onShowMessage("Sign-in error: ${err.message}")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("firebase_sign_in_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Login,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Connect Google & Firebase")
                }
            }

            AnimatedVisibility(visible = !syncMessage.isNullOrBlank() && syncState == SyncState.ERROR) {
                Surface(
                    color = ExpenseRed.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = ExpenseRed,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = syncMessage ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = ExpenseRed
                        )
                    }
                }
            }
        }
    }
}
