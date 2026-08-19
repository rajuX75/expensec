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
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
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
    var legalDocToView by remember { mutableStateOf<com.example.data.model.LegalDocType?>(null) }

    // Show update dialog when an update is available
    LaunchedEffect(updateCheckState) {
        if (updateCheckState is com.example.data.model.UpdateCheckState.UpdateAvailable) {
            showUpdateDialog = true
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

        // ── 1. Hero Profile Banner Card ───────────────────────────
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNavigateToProfile)
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Profile Avatar
                        Box(contentAlignment = Alignment.BottomEnd) {
                            if (!profilePictureUri.isNullOrBlank()) {
                                AsyncImage(
                                    model = profilePictureUri,
                                    contentDescription = "Profile Avatar",
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier
                                        .size(62.dp)
                                        .clip(CircleShape)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(62.dp)
                                        .clip(CircleShape)
                                        .background(avatarColor),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = avatarInitial,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                            if (firebaseUser != null) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Synced",
                                        tint = Color.White,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (displayName.isNotBlank()) displayName else "Set your name",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (displayName.isNotBlank()) MaterialTheme.colorScheme.onSurface
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = if (email.isNotBlank()) email else "Local mode • Sign in for cloud sync",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(Modifier.height(6.dp))
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (firebaseUser != null) Color(0xFF10B981).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (firebaseUser != null) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant)
                                    )
                                    Text(
                                        text = if (firebaseUser != null) "Cloud Synced • Active" else "Offline Storage",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = if (firebaseUser != null) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = "View Profile",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // ── 2. Firebase Database & Cloud Sync ─────────────────────
        item {
            SettingsSectionHeader(title = "Cloud & Database Sync")
        }
        item {
            com.example.ui.components.FirebaseSyncCard(
                viewModel = viewModel,
                onShowMessage = { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
            )
        }

        // ── 3. General Preferences ────────────────────────────────
        item {
            SettingsSectionHeader(title = "General Preferences")
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
                        iconTint = Color(0xFF6366F1),
                        onClick = { showCurrencyDialog = true }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Theme
                    var expandedTheme by remember { mutableStateOf(false) }
                    SettingsItem(
                        icon = Icons.Default.Palette,
                        title = "Theme Mode",
                        subtitle = when (currentTheme) {
                            "DARK" -> "Dark Mode"
                            "LIGHT" -> "Light Mode"
                            else -> "System Default"
                        },
                        iconTint = Color(0xFFEC4899),
                        onClick = { expandedTheme = true }
                    )
                    DropdownMenu(expanded = expandedTheme, onDismissRequest = { expandedTheme = false }) {
                        listOf("SYSTEM" to "System Default", "LIGHT" to "Light Mode", "DARK" to "Dark Mode").forEach { (mode, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                leadingIcon = { if (mode == currentTheme) Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp)) },
                                onClick = { viewModel.setThemeMode(mode); expandedTheme = false }
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Categories Management
                    SettingsItem(
                        icon = Icons.Default.Category,
                        title = "Manage Categories",
                        subtitle = "Customize expense & income categories",
                        iconTint = Color(0xFFF59E0B),
                        onClick = { showCategoryDialog = true }
                    )
                }
            }
        }

        // ── 4. Display & Formatting ───────────────────────────────
        item {
            SettingsSectionHeader(title = "Display & Formatting")
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
                        title = "Decimal Precision",
                        subtitle = if (decimalPlaces == 0) "No decimals (e.g. $1,234)" else "2 decimal places (e.g. $1,234.00)",
                        iconTint = Color(0xFF0284C7),
                        onClick = { expandedDecimal = true }
                    )
                    DropdownMenu(expanded = expandedDecimal, onDismissRequest = { expandedDecimal = false }) {
                        listOf(0 to "No decimals — $1,234", 2 to "2 places — $1,234.00").forEach { (places, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                leadingIcon = { if (places == decimalPlaces) Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp)) },
                                onClick = { viewModel.setDecimalPlaces(places); expandedDecimal = false }
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Week Start Day
                    var expandedWeek by remember { mutableStateOf(false) }
                    SettingsItem(
                        icon = Icons.Default.CalendarToday,
                        title = "First Day of Week",
                        subtitle = if (weekStartDay == "MONDAY") "Monday" else "Sunday",
                        iconTint = Color(0xFF0D9488),
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

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Date Format
                    var expandedDate by remember { mutableStateOf(false) }
                    SettingsItem(
                        icon = Icons.Default.DateRange,
                        title = "Date Display Format",
                        subtitle = dateFormatPref,
                        iconTint = Color(0xFF8B5CF6),
                        onClick = { expandedDate = true }
                    )
                    DropdownMenu(expanded = expandedDate, onDismissRequest = { expandedDate = false }) {
                        listOf(
                            "MMM dd, yyyy" to "Jan 15, 2026",
                            "dd/MM/yyyy" to "15/01/2026",
                            "MM/dd/yyyy" to "01/15/2026",
                            "yyyy-MM-dd" to "2026-01-15"
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

        // ── 5. Notifications & Alerts ─────────────────────────────
        item {
            SettingsSectionHeader(title = "Notifications & Reminders")
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
                        subtitle = "Alerts before bill & loan due dates",
                        iconTint = Color(0xFFF59E0B),
                        checked = dueRemindersEnabled,
                        onCheckedChange = { viewModel.setDueRemindersEnabled(it) }
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    SettingsSwitchItem(
                        icon = Icons.Default.NotificationsActive,
                        title = "Budget Alerts",
                        subtitle = "Notify when approaching monthly limits",
                        iconTint = Color(0xFFEF4444),
                        checked = budgetAlertsEnabled,
                        onCheckedChange = { viewModel.setBudgetAlertsEnabled(it) }
                    )
                }
            }
        }

        // ── 6. App Behavior ───────────────────────────────────────
        item {
            SettingsSectionHeader(title = "Smart Features & Behavior")
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
                        title = "Smart Auto-Categorization",
                        subtitle = "Detect category from payee / merchant",
                        iconTint = Color(0xFF8B5CF6),
                        checked = autoCategorize,
                        onCheckedChange = { viewModel.setAutoCategorize(it) }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Default Transaction Type
                    var expandedTxType by remember { mutableStateOf(false) }
                    SettingsItem(
                        icon = Icons.Default.AddCircleOutline,
                        title = "Default Entry Type",
                        subtitle = defaultTxType.lowercase().replaceFirstChar { it.uppercase() },
                        iconTint = Color(0xFF10B981),
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

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    SettingsSwitchItem(
                        icon = Icons.Default.Vibration,
                        title = "Haptic Touch Feedback",
                        subtitle = "Subtle vibrations on actions",
                        iconTint = Color(0xFF64748B),
                        checked = hapticFeedback,
                        onCheckedChange = { viewModel.setHapticFeedback(it) }
                    )
                }
            }
        }

        // ── 7. Security & App Lock ────────────────────────────────
        item {
            SettingsSectionHeader(title = "Security & Privacy")
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
                        title = "4-Digit PIN Lock",
                        subtitle = if (isPinLockEnabled) "App is protected with passcode" else "Disabled — tap to protect app",
                        iconTint = Color(0xFFF97316),
                        checked = isPinLockEnabled,
                        onCheckedChange = { enable ->
                            if (enable) showPinSetupDialog = true
                            else {
                                viewModel.setPinLock(false)
                                Toast.makeText(context, "PIN Lock disabled", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    if (isPinLockEnabled) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        SettingsItem(
                            icon = Icons.Default.Key,
                            title = "Change PIN Code",
                            subtitle = "Update your existing 4-digit passcode",
                            iconTint = Color(0xFFF97316),
                            onClick = { showPinSetupDialog = true }
                        )
                    }
                }
            }
        }

        // ── 8. Updates & Changelog ────────────────────────────────
        item {
            SettingsSectionHeader(title = "App Updates & Version")
        }
        item {
            val isChecking = updateCheckState is com.example.data.model.UpdateCheckState.Checking
            val lastCheckedFormatted = remember(lastUpdateCheckTime) {
                if (lastUpdateCheckTime == 0L) "Not checked yet"
                else {
                    val diffMin = (System.currentTimeMillis() - lastUpdateCheckTime) / 60000
                    when {
                        diffMin < 1 -> "Just now"
                        diffMin < 60 -> "${diffMin}m ago"
                        diffMin < 1440 -> "${diffMin / 60}h ago"
                        else -> "${diffMin / 1440}d ago"
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Check for Updates Row
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
                                    .size(42.dp)
                                    .background(Color(0xFF7C3AED).copy(alpha = 0.12f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SystemUpdate,
                                    contentDescription = "Check for Updates",
                                    tint = Color(0xFF7C3AED),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text("Check for Updates", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                                Text(
                                    text = if (isChecking) "Checking GitHub repository..." else "Version ${viewModel.currentAppVersionName} • $lastCheckedFormatted",
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
                            FilledTonalButton(
                                onClick = { viewModel.checkForUpdates(isManual = true) },
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text("Check", fontSize = 12.sp)
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // What's New & Release Notes
                    SettingsItem(
                        icon = Icons.Default.HistoryEdu,
                        title = "What's New & Release Notes",
                        subtitle = "Changelog history and feature breakdown",
                        iconTint = Color(0xFF7C3AED),
                        onClick = { showChangelogDialog = true }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Auto Check Updates Switch
                    SettingsSwitchItem(
                        icon = Icons.Default.CloudSync,
                        title = "Automatic Startup Check",
                        subtitle = "Prompt automatically when a newer release is published",
                        iconTint = Color(0xFF7C3AED),
                        checked = autoCheckUpdates,
                        onCheckedChange = { viewModel.setAutoCheckUpdates(it) }
                    )
                }
            }
        }

        // ── 9. Legal & Documentation ─────────────────────────────
        item {
            SettingsSectionHeader(title = "Legal & Documentation")
        }
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    SettingsItem(
                        icon = Icons.Default.Security,
                        title = "Privacy Policy",
                        subtitle = "How your financial records and data are protected",
                        iconTint = Color(0xFF10B981),
                        onClick = { legalDocToView = com.example.data.model.LegalDocType.PRIVACY_POLICY }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    SettingsItem(
                        icon = Icons.Default.Description,
                        title = "Terms of Service",
                        subtitle = "Personal finance usage agreement and disclaimers",
                        iconTint = Color(0xFF3B82F6),
                        onClick = { legalDocToView = com.example.data.model.LegalDocType.TERMS_OF_SERVICE }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    SettingsItem(
                        icon = Icons.Default.Lock,
                        title = "Security & Data Protection",
                        subtitle = "On-device isolation, encryption & Firebase rules",
                        iconTint = Color(0xFFF59E0B),
                        onClick = { legalDocToView = com.example.data.model.LegalDocType.SECURITY }
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    SettingsItem(
                        icon = Icons.Default.Info,
                        title = "About Expense Tracker",
                        subtitle = "App version details, features, and source repository",
                        iconTint = Color(0xFF8B5CF6),
                        onClick = { legalDocToView = com.example.data.model.LegalDocType.ABOUT }
                    )
                }
            }
        }

        // ── 10. Data Management & Danger Zone ─────────────────────
        item {
            SettingsSectionHeader(title = "Data Management")
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
                        title = "Export Financial Records",
                        subtitle = "Export transactions to CSV or JSON file",
                        iconTint = Color(0xFF059669),
                        onClick = onNavigateToExport
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    SettingsItem(
                        icon = Icons.Default.FileUpload,
                        title = "Import Records",
                        subtitle = "Restore transactions from a CSV backup",
                        iconTint = Color(0xFF0284C7),
                        onClick = onNavigateToImport
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    SettingsItem(
                        icon = Icons.Default.DeleteSweep,
                        title = "Erase All Local Data",
                        subtitle = "Reset all transactions, contacts & accounts to blank",
                        iconTint = Color(0xFFEF4444),
                        titleColor = MaterialTheme.colorScheme.error,
                        onClick = { showResetConfirmDialog = true }
                    )
                }
            }
        }

        // App Info footer
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Expense Tracker v${viewModel.currentAppVersionName}",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Local-first personal finance with real-time cloud sync",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        }
    }

    // ── Dialogs ───────────────────────────────────────────────────

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
                ) {
                    Text("Clear Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirmDialog = false }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(20.dp)
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

    // Legal & Documentation Dialog
    legalDocToView?.let { docType ->
        com.example.ui.components.LegalDocsDialog(
            initialDoc = docType,
            onDismiss = { legalDocToView = null }
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
    )
}

@Composable
fun SettingsSwitchItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    iconTint: Color = Color(0xFF6366F1)
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
                    .size(42.dp)
                    .background(iconTint.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
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
    iconTint: Color = Color(0xFF6366F1)
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
                    .size(42.dp)
                    .background(iconTint.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = if (titleColor != Color.Unspecified) titleColor else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(14.dp)
        )
    }
}
