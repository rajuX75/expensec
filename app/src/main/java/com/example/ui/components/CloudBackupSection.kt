package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.unit.sp
import com.example.data.cloud.DriveAuthorizeResult
import com.example.data.model.AppBackup
import com.example.data.model.ImportMode
import com.example.ui.theme.ExpenseGreen
import com.example.ui.theme.ExpenseRed
import com.example.ui.viewmodel.ExpenseViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CloudBackupSection(
    viewModel: ExpenseViewModel,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { context as? Activity }

    // Launches Google's Drive-consent screen on first authorization. After the user grants
    // access we request the token again; subsequent attempts return it without a prompt.
    val driveConsentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { _ ->
        val act = activity
        if (act == null) {
            onShowMessage("Google Drive consent finished. Reopen Settings to complete.")
        } else {
            viewModel.authorizeDrive(act) { driveResult ->
                when (driveResult) {
                    is DriveAuthorizeResult.Granted ->
                        onShowMessage("Google Drive access granted.")
                    is DriveAuthorizeResult.Failed ->
                        onShowMessage(driveResult.message)
                    is DriveAuthorizeResult.ConsentRequired ->
                        onShowMessage("Google Drive consent was cancelled.")
                }
            }
        }
    }

    val googleAccountEmail by viewModel.googleAccountEmail.collectAsState()
    val driveAccessToken by viewModel.googleDriveAccessToken.collectAsState()
    val lastCloudBackupTime by viewModel.lastCloudBackupTime.collectAsState()
    val lastCloudBackupStatus by viewModel.lastCloudBackupStatus.collectAsState()
    val lastCloudBackupError by viewModel.lastCloudBackupError.collectAsState()
    val autoBackupFrequency by viewModel.autoBackupFrequency.collectAsState()
    val autoBackupWifiOnly by viewModel.autoBackupWifiOnly.collectAsState()
    val isCloudSyncing by viewModel.isCloudSyncing.collectAsState()
    val cloudSyncMessage by viewModel.cloudSyncMessage.collectAsState()
    val cloudConflict by viewModel.cloudConflict.collectAsState()

    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var cloudBackupPreview by remember { mutableStateOf<AppBackup?>(null) }
    var selectedRestoreMode by remember { mutableStateOf(ImportMode.MERGE) }
    var isCheckingPreview by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("cloud_backup_card")
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
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
                        imageVector = Icons.Default.CloudSync,
                        contentDescription = "Google Drive Cloud Backup",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Google Drive Backup",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Private backup in hidden appDataFolder",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Status Badge
                val (badgeBg, badgeText, badgeColor) = when (lastCloudBackupStatus) {
                    "SUCCESS" -> Triple(ExpenseGreen.copy(alpha = 0.15f), "Synced", ExpenseGreen)
                    "FAILED" -> Triple(ExpenseRed.copy(alpha = 0.15f), "Failed", ExpenseRed)
                    else -> Triple(MaterialTheme.colorScheme.surfaceVariant, "Never", MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Surface(
                    color = badgeBg,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = badgeText,
                        color = badgeColor,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            HorizontalDivider()

            // Account Status & Sign In/Out
            if (googleAccountEmail.isNullOrBlank()) {
                // Not Signed In State
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Sign in with your Google Account to enable automatic cloud backups to your private Google Drive app storage.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Button(
                        onClick = {
                            viewModel.signInGoogle(context) { result ->
                                if (result.isSuccess) {
                                    authorizeDriveAfterSignIn(
                                        viewModel = viewModel,
                                        context = context,
                                        onMessage = onShowMessage,
                                        onConsentRequired = { sender, _ ->
                                            driveConsentLauncher.launch(
                                                IntentSenderRequest.Builder(sender).build()
                                            )
                                        }
                                    )
                                } else {
                                    onShowMessage("Sign-in: ${result.exceptionOrNull()?.localizedMessage ?: "Failed"}")
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("google_sign_in_button"),
                        enabled = !isCloudSyncing,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Sign in with Google Drive")
                    }
                }
            } else {
                // Signed In State
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Account info row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = ExpenseGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = googleAccountEmail ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                val lastTimeText = if (lastCloudBackupTime > 0) {
                                    "Last backup: " + dateFormat.format(Date(lastCloudBackupTime))
                                } else {
                                    "No cloud backups yet"
                                }
                                Text(
                                    text = lastTimeText,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        TextButton(
                            onClick = {
                                viewModel.signOutGoogle {
                                    onShowMessage("Signed out of Google account")
                                }
                            },
                            modifier = Modifier.testTag("google_sign_out_button")
                        ) {
                            Text("Sign out", color = MaterialTheme.colorScheme.error)
                        }
                    }

                    if (lastCloudBackupStatus == "FAILED" && !lastCloudBackupError.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Error: $lastCloudBackupError",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    // If signed in but the Drive app-data scope was never granted (e.g. a prior
                    // build only stored the Firebase ID token), offer to grant it now.
                    if (driveAccessToken.isNullOrBlank()) {
                        OutlinedButton(
                            onClick = {
                                authorizeDriveAfterSignIn(
                                    viewModel = viewModel,
                                    context = context,
                                    onMessage = onShowMessage,
                                    onConsentRequired = { sender, _ ->
                                        driveConsentLauncher.launch(
                                            IntentSenderRequest.Builder(sender).build()
                                        )
                                    }
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("grant_drive_access_button"),
                            enabled = !isCloudSyncing,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CloudDone, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Grant Google Drive access")
                        }
                    }

                    // Backup & Restore Action Buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.backupToCloud(forceOverwrite = false) { result ->
                                    if (result.isSuccess) {
                                        onShowMessage("Cloud backup completed successfully!")
                                    } else {
                                        val err = result.exceptionOrNull()
                                        if (err !is com.example.data.cloud.CloudConflictException) {
                                            onShowMessage("Cloud backup failed: ${err?.localizedMessage ?: "Unknown error"}")
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("cloud_backup_now_button"),
                            enabled = !isCloudSyncing,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Back up now", fontSize = 13.sp)
                        }

                        OutlinedButton(
                            onClick = {
                                isCheckingPreview = true
                                viewModel.fetchCloudBackupPreview { result ->
                                    isCheckingPreview = false
                                    if (result.isSuccess) {
                                        cloudBackupPreview = result.getOrNull()
                                        showRestoreConfirmDialog = true
                                    } else {
                                        onShowMessage("Failed to fetch cloud backup: ${result.exceptionOrNull()?.localizedMessage ?: "No backup found"}")
                                    }
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("cloud_restore_button"),
                            enabled = !isCloudSyncing && !isCheckingPreview,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restore cloud", fontSize = 13.sp)
                        }
                    }

                    // Auto-Backup Settings
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Auto-Backup Schedule",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )

                            // Frequency Selector
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf("OFF", "DAILY", "WEEKLY").forEach { freq ->
                                    val isSelected = autoBackupFrequency.equals(freq, ignoreCase = true)
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            viewModel.setAutoBackupSettings(freq, autoBackupWifiOnly)
                                        },
                                        label = {
                                            Text(
                                                when (freq) {
                                                    "DAILY" -> "Daily"
                                                    "WEEKLY" -> "Weekly"
                                                    else -> "Off"
                                                },
                                                fontSize = 12.sp
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            // Wi-Fi Only Toggle
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = "Wi-Fi Only",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "Only auto-backup when connected to Wi-Fi",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = autoBackupWifiOnly,
                                    onCheckedChange = { wifiOnly ->
                                        viewModel.setAutoBackupSettings(autoBackupFrequency, wifiOnly)
                                    },
                                    modifier = Modifier.testTag("auto_backup_wifi_switch")
                                )
                            }
                        }
                    }
                }
            }

            // Sync In Progress banner
            AnimatedVisibility(visible = isCloudSyncing) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = cloudSyncMessage ?: "Syncing with cloud...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }

    // Cloud Conflict Resolution Dialog
    if (cloudConflict != null) {
        val conflict = cloudConflict!!
        val cloudDateStr = dateFormat.format(Date(conflict.cloudExportedAt))

        AlertDialog(
            onDismissRequest = { viewModel.dismissCloudConflict() },
            icon = {
                Icon(
                    Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text("Cloud Backup Conflict", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    text = "A newer backup from $cloudDateStr exists in Google Drive (likely from another device).\n\nDo you want to overwrite it anyway with this device's data?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissCloudConflict()
                        viewModel.backupToCloud(forceOverwrite = true) { result ->
                            if (result.isSuccess) {
                                onShowMessage("Cloud backup overwritten successfully!")
                            } else {
                                onShowMessage("Overwrite failed: ${result.exceptionOrNull()?.localizedMessage}")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("conflict_overwrite_button")
                ) {
                    Text("Overwrite Cloud")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissCloudConflict() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Cloud Restore Confirmation Dialog
    if (showRestoreConfirmDialog && cloudBackupPreview != null) {
        val backup = cloudBackupPreview!!
        val cloudDateStr = dateFormat.format(Date(backup.exportedAt))

        AlertDialog(
            onDismissRequest = { showRestoreConfirmDialog = false },
            icon = {
                Icon(
                    Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text("Restore Cloud Backup", fontWeight = FontWeight.Bold)
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Cloud backup from $cloudDateStr (Schema v${backup.schemaVersion}) contains:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("• ${backup.transactions.size} Transactions", style = MaterialTheme.typography.bodyMedium)
                            Text("• ${backup.accounts.size} Accounts", style = MaterialTheme.typography.bodyMedium)
                            Text("• ${backup.categories.size} Categories", style = MaterialTheme.typography.bodyMedium)
                            Text("• ${backup.budgets.size} Budgets & ${backup.bills.size} Bills", style = MaterialTheme.typography.bodyMedium)
                        }
                    }

                    Text(
                        text = "Choose restore method:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedRestoreMode = ImportMode.MERGE }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = selectedRestoreMode == ImportMode.MERGE,
                            onClick = { selectedRestoreMode = ImportMode.MERGE }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Merge data", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Text("Inserts new records without overwriting existing data.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedRestoreMode = ImportMode.REPLACE }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = selectedRestoreMode == ImportMode.REPLACE,
                            onClick = { selectedRestoreMode = ImportMode.REPLACE }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Replace all data", fontWeight = FontWeight.SemiBold, color = ExpenseRed, style = MaterialTheme.typography.bodyMedium)
                            Text("Wipes tables and restores exact cloud backup (auto-saves safety snapshot first).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRestoreConfirmDialog = false
                        viewModel.restoreFromCloud(selectedRestoreMode) { result ->
                            if (result.isSuccess) {
                                val res = result.getOrNull()
                                onShowMessage("Cloud restore completed (${res?.insertedTransactions} transactions restored)!")
                            } else {
                                onShowMessage("Cloud restore failed: ${result.exceptionOrNull()?.localizedMessage}")
                            }
                        }
                    },
                    colors = if (selectedRestoreMode == ImportMode.REPLACE) {
                        ButtonDefaults.buttonColors(containerColor = ExpenseRed)
                    } else {
                        ButtonDefaults.buttonColors()
                    }
                ) {
                    Text(if (selectedRestoreMode == ImportMode.REPLACE) "Replace & Restore" else "Merge & Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * After a successful Google sign-in, request the Google Drive access token. On first use the
 * Google consent flow is triggered via the returned [android.content.IntentSender].
 */
private fun authorizeDriveAfterSignIn(
    viewModel: ExpenseViewModel,
    context: Context,
    onMessage: (String) -> Unit,
    onConsentRequired: (android.content.IntentSender, Activity) -> Unit
) {
    val activity = context as? Activity
    if (activity == null) {
        // Fall back to a plain token request; it cannot show consent but may work if the user
        // already granted the drive.appdata scope in a previous run.
        viewModel.authorizeDrive(context) { driveResult ->
            when (driveResult) {
                is DriveAuthorizeResult.Granted -> onMessage("Signed in. Google Drive access granted.")
                is DriveAuthorizeResult.Failed -> onMessage(driveResult.message)
                is DriveAuthorizeResult.ConsentRequired ->
                    onMessage("Open Settings again to finish granting Google Drive access.")
            }
        }
        return
    }

    viewModel.authorizeDrive(activity) { driveResult ->
        when (driveResult) {
            is DriveAuthorizeResult.Granted -> onMessage("Signed in. Google Drive access granted.")
            is DriveAuthorizeResult.Failed -> onMessage(driveResult.message)
            is DriveAuthorizeResult.ConsentRequired ->
                onConsentRequired(driveResult.intentSender, activity)
        }
    }
}
