package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.repository.AvailableCurrencies
import com.example.data.repository.CurrencyInfo
import com.example.ui.viewmodel.ExpenseViewModel

@Composable
fun SettingsScreen(
    viewModel: ExpenseViewModel,
    onNavigateToExport: () -> Unit = {},
    onNavigateToImport: () -> Unit = {},
    onNavigateToProfile: () -> Unit = {}
) {
    val context = LocalContext.current
    val currentCurrency by viewModel.currency.collectAsState()
    val currentSymbol by viewModel.currencySymbol.collectAsState()
    val currentTheme by viewModel.themeMode.collectAsState()
    val isPinLockEnabled by viewModel.isPinLockEnabled.collectAsState()

    // Profile
    val displayName by viewModel.displayName.collectAsState()
    val avatarColorHex by viewModel.avatarColorHex.collectAsState()
    val profilePictureUri by viewModel.profilePictureUri.collectAsState()
    val googleEmail by viewModel.googleAccountEmail.collectAsState()
    val firebaseUser by viewModel.firebaseUser.collectAsState()

    // Notifications
    val dueRemindersEnabled by viewModel.dueRemindersEnabled.collectAsState()
    val budgetAlertsEnabled by viewModel.budgetAlertsEnabled.collectAsState()

    // Display
    val decimalPlaces by viewModel.decimalPlaces.collectAsState()
    val weekStartDay by viewModel.weekStartDay.collectAsState()
    val dateFormatPref by viewModel.dateFormatPref.collectAsState()

    // Behavior
    val autoCategorize by viewModel.autoCategorize.collectAsState()
    val defaultTxType by viewModel.defaultTransactionType.collectAsState()
    val hapticFeedback by viewModel.hapticFeedback.collectAsState()

    // Updates
    val updateCheckState by viewModel.updateCheckState.collectAsState()
    val updateDownloadState by viewModel.updateDownloadState.collectAsState()
    val autoCheckUpdates by viewModel.autoCheckUpdates.collectAsState()
    val lastUpdateCheckTime by viewModel.lastUpdateCheckTime.collectAsState()

    var showCurrencyDialog by remember { mutableStateOf(false) }
    var showCategoryDialog by remember { mutableStateOf(false) }
    var showPinSetupDialog by remember { mutableStateOf(false) }
    var showResetConfirmDialog by remember { mutableStateOf(false) }
    var showChangelogDialog by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    // Show update dialog when an update is available
    LaunchedEffect(updateCheckState) {
        if (updateCheckState is com.example.data.model.UpdateCheckState.UpdateAvailable) {
            showUpdateDialog = true
        } else if (updateCheckState is com.example.data.model.UpdateCheckState.UpToDate) {
            // Only toast if recently manually triggered
        } else if (updateCheckState is com.example.data.model.UpdateCheckState.Error) {
            Toast.makeText(context, (updateCheckState as com.example.data.model.UpdateCheckState.Error).message, Toast.LENGTH_SHORT).show()
        }
    }

    val email = firebaseUser?.email ?: googleEmail ?: ""
    val avatarInitial = when {
        displayName.isNotBlank() -> displayName.first().uppercaseChar().toString()
        email.isNotBlank() -> email.first().uppercaseChar().toString()
        else -> "?"
    }
    val avatarColor = runCatching {
        Color(android.graphics.Color.parseColor(avatarColorHex))
    }.getOrDefault(Color(0xFF6366F1))

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
    ) {

        // ── Profile Card ──────────────────────────────────────────
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToProfile)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Avatar
                    if (!profilePictureUri.isNullOrBlank()) {
                        coil.compose.AsyncImage(
                            model = profilePictureUri,
                            contentDescription = "Profile Avatar",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(avatarColor),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.material3.Text(
                                text = avatarInitial,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (displayName.isNotBlank()) displayName else "Set your name",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = if (displayName.isNotBlank()) MaterialTheme.colorScheme.onSurface
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (email.isNotBlank()) email else "Not signed in",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (firebaseUser != null) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.12f),
                                modifier = Modifier.padding(top = 4.dp)
                            ) {
                                Text(
                                    "Firebase Linked",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF10B981)
                                )
                            }
                        }
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        // ── Firebase Database & Sync ──────────────────────────────
        item {
            Text(
                text = "Firebase Database & Sync",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }
        item {
            com.example.ui.components.FirebaseSyncCard(
                viewModel = viewModel,
                onShowMessage = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
            )
        }

        // ── Preferences ───────────────────────────────────────────
        item {
            Text(
                text = "Preferences",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Currency
                    SettingsItem(
                        icon = Icons.Default.CurrencyExchange,
                        title = "Currency",
                        subtitle = "$currentCurrency ($currentSymbol)",
                        onClick = { showCurrencyDialog = true }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // Theme
                    var expandedTheme by remember { mutableStateOf(false) }
                    SettingsItem(
                        icon = Icons.Default.Palette,
                        title = "Theme Mode",
                        subtitle = currentTheme.lowercase().replaceFirstChar { it.uppercase() },
                        onClick = { expandedTheme = true }
                    )
                    DropdownMenu(expanded = expandedTheme, onDismissRequest = { expandedTheme = false }) {
                        listOf("SYSTEM" to "Follow System", "LIGHT" to "Light Mode", "DARK" to "Dark Mode").forEach { (mode, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { viewModel.setThemeMode(mode); expandedTheme = false }
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // Categories Management
                    SettingsItem(
                        icon = Icons.Default.Category,
                        title = "Manage Categories",
                        subtitle = "Customize expense & income categories",
                        onClick = { showCategoryDialog = true }
                    )
                }
            }
        }

        // ── Notifications ─────────────────────────────────────────
        item {
            Text(
                text = "Notifications",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingsSwitchItem(
                        icon = Icons.Default.Notifications,
                        title = "Due Date Reminders",
                        subtitle = "Remind before bill & loan due dates",
                        checked = dueRemindersEnabled,
                        onCheckedChange = { viewModel.setDueRemindersEnabled(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsSwitchItem(
                        icon = Icons.Default.NotificationsActive,
                        title = "Budget Alert Notifications",
                        subtitle = "Notify when approaching budget limits",
                        checked = budgetAlertsEnabled,
                        onCheckedChange = { viewModel.setBudgetAlertsEnabled(it) }
                    )
                }
            }
        }

        // ── Display & Format ──────────────────────────────────────
        item {
            Text(
                text = "Display & Format",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Decimal Places
                    var expandedDecimal by remember { mutableStateOf(false) }
                    SettingsItem(
                        icon = Icons.Default.Numbers,
                        title = "Decimal Places",
                        subtitle = if (decimalPlaces == 0) "None (e.g. $1,234)" else "$decimalPlaces decimal places (e.g. $1,234.${"0".repeat(decimalPlaces)})",
                        onClick = { expandedDecimal = true }
                    )
                    DropdownMenu(expanded = expandedDecimal, onDismissRequest = { expandedDecimal = false }) {
                        listOf(0 to "None — $1,234", 2 to "2 places — $1,234.00").forEach { (places, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                leadingIcon = { if (places == decimalPlaces) Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp)) },
                                onClick = { viewModel.setDecimalPlaces(places); expandedDecimal = false }
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // Week Start Day
                    var expandedWeek by remember { mutableStateOf(false) }
                    SettingsItem(
                        icon = Icons.Default.CalendarToday,
                        title = "Week Starts On",
                        subtitle = if (weekStartDay == "MONDAY") "Monday" else "Sunday",
                        onClick = { expandedWeek = true }
                    )
                    DropdownMenu(expanded = expandedWeek, onDismissRequest = { expandedWeek = false }) {
                        listOf("MONDAY" to "Monday", "SUNDAY" to "Sunday").forEach { (day, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                leadingIcon = { if (day == weekStartDay) Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp)) },
                                onClick = { viewModel.setWeekStartDay(day); expandedWeek = false }
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // Date Format
                    var expandedDate by remember { mutableStateOf(false) }
                    SettingsItem(
                        icon = Icons.Default.DateRange,
                        title = "Date Format",
                        subtitle = dateFormatPref,
                        onClick = { expandedDate = true }
                    )
                    DropdownMenu(expanded = expandedDate, onDismissRequest = { expandedDate = false }) {
                        listOf(
                            "MMM dd, yyyy" to "Jan 15, 2025",
                            "dd/MM/yyyy" to "15/01/2025",
                            "MM/dd/yyyy" to "01/15/2025",
                            "yyyy-MM-dd" to "2025-01-15"
                        ).forEach { (fmt, example) ->
                            DropdownMenuItem(
                                text = { Text("$example  ($fmt)") },
                                leadingIcon = { if (fmt == dateFormatPref) Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp)) },
                                onClick = { viewModel.setDateFormatPref(fmt); expandedDate = false }
                            )
                        }
                    }
                }
            }
        }

        // ── App Behavior ──────────────────────────────────────────
        item {
            Text(
                text = "App Behavior",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingsSwitchItem(
                        icon = Icons.Default.AutoAwesome,
                        title = "Auto-Categorization",
                        subtitle = "Suggest category based on merchant name",
                        checked = autoCategorize,
                        onCheckedChange = { viewModel.setAutoCategorize(it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // Default Transaction Type
                    var expandedTxType by remember { mutableStateOf(false) }
                    SettingsItem(
                        icon = Icons.Default.AddCircleOutline,
                        title = "Default Transaction Type",
                        subtitle = defaultTxType.lowercase().replaceFirstChar { it.uppercase() },
                        onClick = { expandedTxType = true }
                    )
                    DropdownMenu(expanded = expandedTxType, onDismissRequest = { expandedTxType = false }) {
                        listOf("EXPENSE" to "Expense", "INCOME" to "Income").forEach { (type, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                leadingIcon = { if (type == defaultTxType) Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp)) },
                                onClick = { viewModel.setDefaultTransactionType(type); expandedTxType = false }
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    SettingsSwitchItem(
                        icon = Icons.Default.Vibration,
                        title = "Haptic Feedback",
                        subtitle = "Vibration on button taps and interactions",
                        checked = hapticFeedback,
                        onCheckedChange = { viewModel.setHapticFeedback(it) }
                    )
                }
            }
        }

        // ── Security & Privacy ────────────────────────────────────
        item {
            Text(
                text = "Security & Privacy",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingsSwitchItem(
                        icon = Icons.Default.Lock,
                        title = "PIN Lock Protection",
                        subtitle = if (isPinLockEnabled) "App is protected by 4-digit PIN" else "Disabled — tap to enable",
                        checked = isPinLockEnabled,
                        onCheckedChange = { enable ->
                            if (enable) showPinSetupDialog = true
                            else {
                                viewModel.setPinLock(false)
                                Toast.makeText(context, "PIN Lock disabled", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsItem(
                        icon = Icons.Default.PrivacyTip,
                        title = "Data Privacy",
                        subtitle = "All data stored locally. Cloud sync is optional & encrypted.",
                        onClick = { Toast.makeText(context, "Your data stays private and local-first.", Toast.LENGTH_LONG).show() }
                    )
                }
            }
        }

        // ── Data Portability ──────────────────────────────────────
        item {
            Text(
                text = "Data Portability & Management",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingsItem(
                        icon = Icons.Default.FileDownload,
                        title = "Export Data",
                        subtitle = "Save JSON backup or CSV spreadsheet to storage",
                        onClick = onNavigateToExport
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsItem(
                        icon = Icons.Default.FileUpload,
                        title = "Import Data",
                        subtitle = "Restore or merge data from local JSON backup",
                        onClick = onNavigateToImport
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsItem(
                        icon = Icons.Default.CloudDownload,
                        title = "Load Demo Data",
                        subtitle = "Populate realistic sample expenses and budgets",
                        onClick = {
                            viewModel.seedDemoData()
                            Toast.makeText(context, "Demo data populated!", Toast.LENGTH_SHORT).show()
                        }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    SettingsItem(
                        icon = Icons.Default.DeleteSweep,
                        title = "Clear All Data",
                        subtitle = "Erase all transactions, budgets & categories",
                        titleColor = MaterialTheme.colorScheme.error,
                        iconTint = MaterialTheme.colorScheme.error,
                        onClick = { showResetConfirmDialog = true }
                    )
                }
            }
        }

        // ── App Updates & Changelog ──────────────────────────────
        item {
            Text(
                text = "App Updates & Changelog",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }
        item {
            val isChecking = updateCheckState is com.example.data.model.UpdateCheckState.Checking
            val lastCheckedFormatted = if (lastUpdateCheckTime > 0) {
                val sdf = java.text.SimpleDateFormat("MMM dd, hh:mm a", java.util.Locale.getDefault())
                "Last checked: ${sdf.format(java.util.Date(lastUpdateCheckTime))}"
            } else {
                "Not checked yet"
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Check for Updates item
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !isChecking) {
                                viewModel.checkForUpdates(isManual = true)
                            }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = "Check for Updates",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Check for Updates",
                                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
                                    )
                                    if (updateCheckState is com.example.data.model.UpdateCheckState.UpdateAvailable) {
                                        Spacer(Modifier.width(6.dp))
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = Color(0xFF10B981)
                                        ) {
                                            Text(
                                                text = "NEW",
                                                color = Color.White,
                                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = if (isChecking) "Checking for updates..." else "Current v${viewModel.currentAppVersionName} • $lastCheckedFormatted",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (isChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Check Now", tint = MaterialTheme.colorScheme.primary)
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // What's New & Release Notes
                    SettingsItem(
                        icon = Icons.Default.HistoryEdu,
                        title = "What's New & Release Notes",
                        subtitle = "View changelog history and latest feature logs",
                        onClick = { showChangelogDialog = true }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    // Auto Check Updates Switch
                    SettingsSwitchItem(
                        icon = Icons.Default.CloudSync,
                        title = "Automatic Update Check",
                        subtitle = "Check for new versions automatically on startup",
                        checked = autoCheckUpdates,
                        onCheckedChange = { viewModel.setAutoCheckUpdates(it) }
                    )
                }
            }
        }

        // App Info footer
        item {
            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Expense Tracker v${viewModel.currentAppVersionName}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Secure local-first personal financial manager",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }

    // Currency Selector Dialog
    if (showCurrencyDialog) {
        Dialog(onDismissRequest = { showCurrencyDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Select Default Currency", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.height(12.dp))
                    androidx.compose.foundation.lazy.LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(AvailableCurrencies) { curr ->
                            val isSelected = curr.code == currentCurrency
                            Surface(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                                    viewModel.setCurrency(curr)
                                    showCurrencyDialog = false
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${curr.name} (${curr.code})", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal))
                                    Text(curr.symbol, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Category Management Dialog
    if (showCategoryDialog) {
        CategoryManagementDialog(viewModel = viewModel, onDismiss = { showCategoryDialog = false })
    }

    // Setup PIN Dialog
    if (showPinSetupDialog) {
        var pinInput by remember { mutableStateOf("") }
        var pinConfirmInput by remember { mutableStateOf("") }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        Dialog(onDismissRequest = { showPinSetupDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Set Security PIN", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.height(6.dp))
                    Text("Enter a 4-digit PIN code to secure access", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = pinInput, onValueChange = { if (it.length <= 4) pinInput = it },
                        label = { Text("4-digit PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pinConfirmInput, onValueChange = { if (it.length <= 4) pinConfirmInput = it },
                        label = { Text("Confirm PIN") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    if (errorMessage != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(errorMessage!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelSmall)
                    }
                    Spacer(Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { showPinSetupDialog = false }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                        Button(
                            onClick = {
                                if (pinInput.length != 4) errorMessage = "PIN must be exactly 4 digits"
                                else if (pinInput != pinConfirmInput) errorMessage = "PINs do not match"
                                else {
                                    viewModel.setPinLock(true, pinInput)
                                    showPinSetupDialog = false
                                    Toast.makeText(context, "PIN Lock enabled!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) { Text("Save PIN") }
                    }
                }
            }
        }
    }

    // Reset Confirm Dialog
    if (showResetConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showResetConfirmDialog = false },
            title = { Text("Clear All Data?") },
            text = { Text("This will permanently remove all transactions, budgets, categories, and contacts from your device. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearAllData()
                        showResetConfirmDialog = false
                        Toast.makeText(context, "All data cleared", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Clear All") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showResetConfirmDialog = false }) { Text("Cancel") }
            }
        )
    }

    // What's New & Changelog Dialog
    if (showChangelogDialog) {
        com.example.ui.components.ChangelogDialog(
            releaseHistory = viewModel.releaseHistory,
            onDismiss = { showChangelogDialog = false }
        )
    }

    // In-App Update Dialog (Skipable)
    if (showUpdateDialog && updateCheckState is com.example.data.model.UpdateCheckState.UpdateAvailable) {
        val updateInfo = (updateCheckState as com.example.data.model.UpdateCheckState.UpdateAvailable).info
        com.example.ui.components.UpdateDialog(
            updateInfo = updateInfo,
            currentVersion = "v${viewModel.currentAppVersionName}",
            downloadState = updateDownloadState,
            onUpdateClick = {
                viewModel.downloadAndInstallUpdate(updateInfo)
            },
            onSkipClick = {
                viewModel.skipUpdateVersion(updateInfo.versionCode)
                showUpdateDialog = false
                Toast.makeText(context, "Version ${updateInfo.versionName} will be skipped", Toast.LENGTH_SHORT).show()
            },
            onDismissClick = {
                viewModel.dismissUpdatePrompt()
                showUpdateDialog = false
            }
        )
    }
}

@Composable
fun SettingsSwitchItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    iconTint: Color = Color.Unspecified
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (iconTint != Color.Unspecified) iconTint.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (iconTint != Color.Unspecified) iconTint else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    titleColor: Color = Color.Unspecified,
    iconTint: Color = Color.Unspecified
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        if (iconTint != Color.Unspecified) iconTint.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (iconTint != Color.Unspecified) iconTint else MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = if (titleColor != Color.Unspecified) titleColor else MaterialTheme.colorScheme.onSurface
                )
                Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

