package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AppBackup
import com.example.data.model.ImportMode
import com.example.ui.theme.ExpenseGreen
import com.example.ui.theme.ExpenseRed
import com.example.ui.viewmodel.ExpenseViewModel
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportDataScreen(
    viewModel: ExpenseViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val safetyBackups by viewModel.safetyBackups.collectAsState()

    var isReadingFile by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var parsedBackup by remember { mutableStateOf<AppBackup?>(null) }
    var selectedImportMode by remember { mutableStateOf(ImportMode.MERGE) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showConfirmationDialog by remember { mutableStateOf(false) }
    var safetyBackupToRestore by remember { mutableStateOf<File?>(null) }

    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }

    // SAF Document Picker for JSON
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isReadingFile = true
            errorMessage = null
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val reader = BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8))
                    val jsonContent = reader.readText()
                    val parseResult = viewModel.parseBackupJson(jsonContent)

                    if (parseResult.isSuccess) {
                        parsedBackup = parseResult.getOrNull()
                        showConfirmationDialog = true
                    } else {
                        errorMessage = parseResult.exceptionOrNull()?.localizedMessage
                            ?: "Failed to parse the backup file. Ensure it is a valid Expense Tracker JSON file."
                    }
                } ?: run {
                    errorMessage = "Unable to read selected file."
                }
            } catch (e: Exception) {
                errorMessage = "Error reading backup: ${e.localizedMessage ?: e.message}"
            } finally {
                isReadingFile = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import Data", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("import_back_button")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(padding)
                .verticalScroll(rememberScrollState())
                .padding(
                    start = 16.dp,
                    top = padding.calculateTopPadding() + 16.dp,
                    end = 16.dp,
                    bottom = padding.calculateBottomPadding() + 16.dp
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Main Import Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileUpload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Restore from Local File",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Select a previously exported .JSON backup file to restore your transactions, accounts, categories, and settings.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            openDocumentLauncher.launch(arrayOf("application/json", "text/json", "*/*"))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("select_backup_file_button"),
                        enabled = !isReadingFile && !isImporting,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Select Backup File (.JSON)")
                    }
                }
            }

            // Error Display Card if any
            if (errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = "Error",
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = errorMessage ?: "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            // Loading indicator
            if (isReadingFile || isImporting) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = if (isReadingFile) "Reading backup file..." else "Applying backup data...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Safety Backups History
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Security,
                                contentDescription = null,
                                tint = ExpenseGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Auto Safety Snapshots",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Last 3 kept",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Safety backups are automatically created before any replace-mode import to prevent data loss.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    if (safetyBackups.isEmpty()) {
                        Text(
                            text = "No safety backups recorded yet.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        safetyBackups.forEach { file ->
                            val fileDate = Date(file.lastModified())
                            val dateStr = dateFormat.format(fileDate)
                            val sizeKb = (file.length() / 1024).coerceAtLeast(1)

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Snapshot ($dateStr)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = "$sizeKb KB",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    FilledTonalButton(
                                        onClick = {
                                            safetyBackupToRestore = file
                                            try {
                                                val content = file.readText(Charsets.UTF_8)
                                                val parsed = viewModel.parseBackupJson(content)
                                                if (parsed.isSuccess) {
                                                    parsedBackup = parsed.getOrNull()
                                                    showConfirmationDialog = true
                                                }
                                            } catch (e: Exception) {
                                                errorMessage = "Failed to load safety snapshot: ${e.message}"
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("Restore", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirmation & Mode Dialog
    if (showConfirmationDialog && parsedBackup != null) {
        val backup = parsedBackup!!
        val exportedDateStr = dateFormat.format(Date(backup.exportedAt))

        AlertDialog(
            onDismissRequest = { showConfirmationDialog = false },
            icon = {
                Icon(
                    Icons.Default.CloudSync,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text(
                    text = "Confirm Data Import",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "This backup file was exported on $exportedDateStr (Schema v${backup.schemaVersion}).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Stats Grid
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
                        text = "Choose how you want to import this data:",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    // Option 1: Merge Mode
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedImportMode = ImportMode.MERGE }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = selectedImportMode == ImportMode.MERGE,
                            onClick = { selectedImportMode = ImportMode.MERGE },
                            modifier = Modifier.testTag("mode_merge_radio")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Merge with current data", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                            Text("Inserts new records by UUID without modifying existing ones.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    // Option 2: Replace Mode
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedImportMode = ImportMode.REPLACE }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = selectedImportMode == ImportMode.REPLACE,
                            onClick = { selectedImportMode = ImportMode.REPLACE },
                            modifier = Modifier.testTag("mode_replace_radio")
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Replace all current data", fontWeight = FontWeight.SemiBold, color = ExpenseRed, style = MaterialTheme.typography.bodyMedium)
                            Text("Wipes tables and restores exact state (auto-saves safety snapshot first).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmationDialog = false
                        isImporting = true
                        viewModel.importBackupData(backup, selectedImportMode) { result ->
                            isImporting = false
                            if (result.isSuccess) {
                                val res = result.getOrNull()
                                val msg = if (res?.mode == ImportMode.REPLACE) {
                                    "Database replaced successfully (${res.insertedTransactions} transactions restored)!"
                                } else {
                                    "Data merged successfully (${res?.insertedTransactions ?: 0} new transactions added)!"
                                }
                                errorMessage = null
                                parsedBackup = null
                                onNavigateBack()
                            } else {
                                errorMessage = "Import failed: ${result.exceptionOrNull()?.localizedMessage ?: "Unknown error"}"
                            }
                        }
                    },
                    colors = if (selectedImportMode == ImportMode.REPLACE) {
                        ButtonDefaults.buttonColors(containerColor = ExpenseRed)
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                    modifier = Modifier.testTag("confirm_import_button")
                ) {
                    Text(if (selectedImportMode == ImportMode.REPLACE) "Replace & Import" else "Merge & Import")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmationDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
