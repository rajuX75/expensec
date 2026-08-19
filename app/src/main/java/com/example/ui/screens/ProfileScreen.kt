package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.viewmodel.ExpenseViewModel
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.*

private val avatarColors = listOf(
    "#6366F1", "#EC4899", "#F59E0B", "#10B981",
    "#3B82F6", "#8B5CF6", "#EF4444", "#06B6D4",
    "#14B8A6", "#D946EF", "#F97316", "#84CC16"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ExpenseViewModel,
    onNavigateBack: () -> Unit
) {
    val displayName by viewModel.displayName.collectAsState()
    val avatarColorHex by viewModel.avatarColorHex.collectAsState()
    val profilePictureUri by viewModel.profilePictureUri.collectAsState()
    val googleEmail by viewModel.googleAccountEmail.collectAsState()
    val firebaseUser by viewModel.firebaseUser.collectAsState()
    val allTransactions by viewModel.allTransactions.collectAsState()
    val allAccounts by viewModel.allAccounts.collectAsState()
    val allContacts by viewModel.allContacts.collectAsState()
    val financialSummary by viewModel.financialSummary.collectAsState()
    val currencySymbol by viewModel.currencySymbol.collectAsState()

    var showEditNameDialog by remember { mutableStateOf(false) }
    var showColorPickerDialog by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    // Image Picker Launcher for Profile Picture
    val photoPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        uri?.let {
            scope.launch {
                val persistentUri = com.example.ui.components.ImageStorageHelper.saveImageLocally(context, it, "profile")
                viewModel.setProfilePictureUri(persistentUri ?: it.toString())
            }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Avatar & Name Hero Card
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
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Avatar with photo or initials + camera upload button
                        Box(contentAlignment = Alignment.BottomEnd) {
                            if (!profilePictureUri.isNullOrBlank()) {
                                coil.compose.AsyncImage(
                                    model = profilePictureUri,
                                    contentDescription = "Profile Picture",
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(CircleShape)
                                        .clickable { photoPickerLauncher.launch("image/*") }
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(CircleShape)
                                        .background(avatarColor)
                                        .clickable { photoPickerLauncher.launch("image/*") },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = avatarInitial,
                                        fontSize = 42.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }

                            // Camera / Upload badge button
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .clickable { photoPickerLauncher.launch("image/*") },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.PhotoCamera,
                                    contentDescription = "Upload profile photo",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        // Photo options row (Upload / Change / Remove / Color)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = { photoPickerLauncher.launch("image/*") }
                            ) {
                                Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (profilePictureUri.isNullOrBlank()) "Upload Photo" else "Change Photo", fontSize = 12.sp)
                            }

                            if (!profilePictureUri.isNullOrBlank()) {
                                TextButton(
                                    onClick = { viewModel.setProfilePictureUri(null) }
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Remove", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                }
                            } else {
                                TextButton(
                                    onClick = { showColorPickerDialog = true }
                                ) {
                                    Icon(Icons.Default.Palette, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Avatar Color", fontSize = 12.sp)
                                }
                            }
                        }

                        // Display Name
                        if (displayName.isNotBlank()) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                                textAlign = TextAlign.Center
                            )
                        } else {
                            Text(
                                text = "Tap to set your name",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                        if (email.isNotBlank()) {
                            Text(
                                text = email,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }

                        OutlinedButton(
                            onClick = { showEditNameDialog = true },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Edit Display Name")
                        }
                    }
                }
            }

            // Financial Stats Label
            item {
                Text(
                    text = "Financial Overview",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Stats Card
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        // Stats Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            ProfileStatItem(
                                label = "Transactions",
                                value = allTransactions.size.toString(),
                                icon = Icons.Default.ReceiptLong,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            VerticalDivider(modifier = Modifier.height(60.dp))
                            ProfileStatItem(
                                label = "Accounts",
                                value = allAccounts.size.toString(),
                                icon = Icons.Default.AccountBalance,
                                tint = Color(0xFF10B981)
                            )
                            VerticalDivider(modifier = Modifier.height(60.dp))
                            ProfileStatItem(
                                label = "Contacts",
                                value = allContacts.size.toString(),
                                icon = Icons.Default.People,
                                tint = Color(0xFFF59E0B)
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                        // Net Worth
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.AccountBalanceWallet,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("Net Worth", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                    Text("All accounts combined", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text(
                                text = "$currencySymbol${"%.2f".format(financialSummary.totalBalance)}",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (financialSummary.totalBalance >= 0) Color(0xFF10B981) else Color(0xFFEF4444)
                                )
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        // This month income
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0xFF10B981).copy(alpha = 0.12f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.TrendingUp, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("This Month Income", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                    Text("Savings rate: %.0f%%".format(financialSummary.savingsRate), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text(
                                text = "$currencySymbol${"%.2f".format(financialSummary.thisMonthIncome)}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                            )
                        }

                        Spacer(Modifier.height(12.dp))

                        // This month expense
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(Color(0xFFEF4444).copy(alpha = 0.12f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.TrendingDown, contentDescription = null, tint = Color(0xFFEF4444), modifier = Modifier.size(18.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text("This Month Expenses", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                    Text("Total spending so far", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text(
                                text = "$currencySymbol${"%.2f".format(financialSummary.thisMonthExpense)}",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                            )
                        }
                    }
                }
            }

            // Connected Account section
            if (email.isNotBlank()) {
                item {
                    Text(
                        text = "Connected Account",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                item {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(2.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.AccountCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Google Account", style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold))
                                Text(email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (firebaseUser != null) {
                                    Text(
                                        "UID: ${firebaseUser!!.uid.take(12)}…",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFF10B981).copy(alpha = 0.12f)
                            ) {
                                Text(
                                    "Linked",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF10B981)
                                )
                            }
                        }
                    }
                }
            }

            // About section
            item {
                Text(
                    text = "About App",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
            }

            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        ProfileInfoRow(Icons.Default.Info, "App Version", "1.0.0")
                        HorizontalDivider()
                        ProfileInfoRow(Icons.Default.Storage, "Data Storage", "Local + Firebase Cloud")
                        HorizontalDivider()
                        ProfileInfoRow(Icons.Default.Shield, "Security", "Android Keystore · SHA-256 PIN")
                        HorizontalDivider()
                        ProfileInfoRow(Icons.Default.Code, "Built With", "Jetpack Compose · Room · Firestore")
                    }
                }
            }

            item { Spacer(Modifier.height(40.dp)) }
        }
    }

    // Edit Name Dialog
    if (showEditNameDialog) {
        var nameInput by remember { mutableStateOf(displayName) }
        Dialog(onDismissRequest = { showEditNameDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Edit Display Name", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Your name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("e.g. Alex Johnson") }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { showEditNameDialog = false }, modifier = Modifier.weight(1f)) { Text("Cancel") }
                        Button(
                            onClick = {
                                viewModel.setDisplayName(nameInput.trim())
                                showEditNameDialog = false
                            },
                            modifier = Modifier.weight(1f),
                            enabled = nameInput.isNotBlank()
                        ) { Text("Save") }
                    }
                }
            }
        }
    }

    // Color Picker Dialog
    if (showColorPickerDialog) {
        Dialog(onDismissRequest = { showColorPickerDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Choose Avatar Color", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                    avatarColors.chunked(4).forEach { rowColors ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            rowColors.forEach { hex ->
                                val color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color(0xFF6366F1))
                                val isSelected = hex == avatarColorHex
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(color)
                                        .then(if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                        .clickable { viewModel.setAvatarColorHex(hex); showColorPickerDialog = false },
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                    OutlinedButton(onClick = { showColorPickerDialog = false }, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
                }
            }
        }
    }
}

@Composable
private fun ProfileStatItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(horizontal = 8.dp)) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(tint.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Text(text = value, style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ProfileInfoRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(text = value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold))
    }
}
