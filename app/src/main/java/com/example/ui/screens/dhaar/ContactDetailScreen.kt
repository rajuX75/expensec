package com.example.ui.screens.dhaar

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Contact
import com.example.data.model.ContactWithBalance
import com.example.data.model.DhaarEntry
import com.example.ui.theme.Emerald400
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.viewmodel.ExpenseViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailScreen(
    viewModel: ExpenseViewModel,
    contactId: Long,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    val allContactsWithBalances by viewModel.contactsWithBalances.collectAsState()
    val allAccounts by viewModel.allAccounts.collectAsState()

    val contactWithBalance = remember(contactId, allContactsWithBalances) {
        allContactsWithBalances.find { it.contact.id == contactId }
    }

    val contactFlow = remember(contactId) { viewModel.getContactById(contactId) }
    val contactState by contactFlow.collectAsState(initial = null)
    val contact = contactWithBalance?.contact ?: contactState

    val entriesFlow = remember(contactId) { viewModel.getEntriesForContact(contactId) }
    val entries by entriesFlow.collectAsState(initial = emptyList())

    val netBalance = contactWithBalance?.netBalance ?: 0.0

    var showEditContactDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var showAddEntrySheet by remember { mutableStateOf(false) }
    var addEntryInitialType by remember { mutableStateOf("GIVEN") }
    var showSettleUpDialog by remember { mutableStateOf(false) }
    var entryToEdit by remember { mutableStateOf<DhaarEntry?>(null) }
    var entryToDelete by remember { mutableStateOf<DhaarEntry?>(null) }
    var previewPhotoUri by remember { mutableStateOf<String?>(null) }
    var showMoreMenu by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
    val shortDateFormat = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    if (contact == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!contact.photoUri.isNullOrBlank()) {
                            coil.compose.AsyncImage(
                                model = contact.photoUri,
                                contentDescription = contact.name,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = contact.name.take(1).uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = contact.name,
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            if (!contact.phoneNumber.isNullOrBlank()) {
                                Text(
                                    text = contact.phoneNumber,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!contact.phoneNumber.isNullOrBlank()) {
                        // Phone Call
                        IconButton(onClick = {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${contact.phoneNumber}"))
                            context.startActivity(intent)
                        }) {
                            Icon(Icons.Default.Phone, contentDescription = "Call Contact")
                        }

                        // SMS Reminder
                        IconButton(onClick = {
                            val reminderMsg = if (netBalance > 0.01) {
                                "Hello ${contact.name}, a polite reminder about the remaining balance of $currencySymbol${String.format("%.2f", netBalance)}. Thank you!"
                            } else {
                                "Hello ${contact.name}, checking in on our pending ledger balance. Thanks!"
                            }
                            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${contact.phoneNumber}")).apply {
                                putExtra("sms_body", reminderMsg)
                            }
                            context.startActivity(intent)
                        }) {
                            Icon(Icons.Default.Message, contentDescription = "SMS Reminder")
                        }
                    }

                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More options")
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit Contact") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    showEditContactDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Contact", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMoreMenu = false
                                    showDeleteConfirmDialog = true
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Hero Balance Card
            item {
                val isTheyOweYou = netBalance > 0.001
                val isYouOweThem = netBalance < -0.001
                val isSettled = !isTheyOweYou && !isYouOweThem

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = when {
                            isTheyOweYou -> IncomeGreen.copy(alpha = 0.14f)
                            isYouOweThem -> ExpenseRed.copy(alpha = 0.14f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = when {
                                isTheyOweYou -> IncomeGreen.copy(alpha = 0.25f)
                                isYouOweThem -> ExpenseRed.copy(alpha = 0.25f)
                                else -> MaterialTheme.colorScheme.outlineVariant
                            }
                        ) {
                            Text(
                                text = when {
                                    isTheyOweYou -> "They Owe You"
                                    isYouOweThem -> "You Owe Them"
                                    else -> "Fully Settled"
                                },
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = when {
                                    isTheyOweYou -> IncomeGreen
                                    isYouOweThem -> ExpenseRed
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "$currencySymbol${String.format("%,.2f", abs(netBalance))}",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 36.sp
                            ),
                            color = when {
                                isTheyOweYou -> IncomeGreen
                                isYouOweThem -> ExpenseRed
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Stats Summary Row
                        val givenSum = contactWithBalance?.totalGiven ?: entries.filter { it.type == "GIVEN" }.sumOf { it.amount }
                        val receivedSum = contactWithBalance?.totalReceived ?: entries.filter { it.type == "RECEIVED" }.sumOf { it.amount }
                        val settledSum = contactWithBalance?.totalSettled ?: entries.filter { it.type == "SETTLEMENT" }.sumOf { it.amount }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Total Lent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$currencySymbol${String.format("%,.0f", givenSum)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = IncomeGreen)
                            }
                            HorizontalDivider(modifier = Modifier.height(24.dp).width(1.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Total Borrowed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$currencySymbol${String.format("%,.0f", receivedSum)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = ExpenseRed)
                            }
                            HorizontalDivider(modifier = Modifier.height(24.dp).width(1.dp), color = MaterialTheme.colorScheme.outlineVariant)
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Settled", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("$currencySymbol${String.format("%,.0f", settledSum)}", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }

            // Quick Actions: Gave / Received / Settle Up
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Gave Button
                    Button(
                        onClick = {
                            addEntryInitialType = "GIVEN"
                            showAddEntrySheet = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = IncomeGreen),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(46.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) {
                        Icon(Icons.Default.ArrowOutward, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Lent / Gave", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    // Received Button
                    Button(
                        onClick = {
                            addEntryInitialType = "RECEIVED"
                            showAddEntrySheet = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ExpenseRed),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(46.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) {
                        Icon(Icons.Default.CallReceived, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Borrowed", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }

                    // Settle Up Button
                    FilledTonalButton(
                        onClick = { showSettleUpDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f).height(46.dp),
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) {
                        Icon(Icons.Default.DoneAll, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Settle Up", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            // Section Title
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Transaction History (${entries.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            // Entries List
            if (entries.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Outlined.ReceiptLong,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No transactions recorded yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(entries, key = { it.id }) { entry ->
                    DhaarEntryCard(
                        entry = entry,
                        currencySymbol = currencySymbol,
                        accountName = allAccounts.find { it.id == entry.linkedAccountId }?.name,
                        dateFormat = dateFormat,
                        shortDateFormat = shortDateFormat,
                        onEdit = { entryToEdit = entry },
                        onDelete = { entryToDelete = entry },
                        onPhotoClick = { uri -> previewPhotoUri = uri }
                    )
                }
            }
        }
    }

    // Modal Add / Edit Entry
    if (showAddEntrySheet) {
        AddEditDhaarDialog(
            viewModel = viewModel,
            preselectedContactId = contact.id,
            preselectedType = addEntryInitialType,
            onDismiss = { showAddEntrySheet = false }
        )
    }

    if (entryToEdit != null) {
        AddEditDhaarDialog(
            viewModel = viewModel,
            initialEntry = entryToEdit,
            onDismiss = { entryToEdit = null }
        )
    }

    // Settle Up Dialog
    if (showSettleUpDialog) {
        SettleUpDialog(
            viewModel = viewModel,
            contact = contact,
            currentNetBalance = netBalance,
            onDismiss = { showSettleUpDialog = false }
        )
    }

    // Edit Contact Dialog
    if (showEditContactDialog) {
        AddEditContactDialog(
            viewModel = viewModel,
            contactToEdit = contact,
            onDismiss = { showEditContactDialog = false }
        )
    }

    // Delete Contact Confirmation
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Contact?") },
            text = {
                Text(
                    text = "Are you sure you want to delete '${contact.name}'? This contact has ${entries.size} transaction records which will also be removed permanently.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteContact(contact, deleteEntries = true) {
                            showDeleteConfirmDialog = false
                            onNavigateBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Single Entry Confirmation
    if (entryToDelete != null) {
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("Delete Entry?") },
            text = { Text("This entry will be permanently removed.") },
            confirmButton = {
                Button(
                    onClick = {
                        entryToDelete?.let { viewModel.deleteDhaarEntry(it) }
                        entryToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Photo Preview
    if (previewPhotoUri != null) {
        PhotoPreviewDialog(
            photoUri = previewPhotoUri!!,
            onDismiss = { previewPhotoUri = null }
        )
    }
}

@Composable
fun DhaarEntryCard(
    entry: DhaarEntry,
    currencySymbol: String,
    accountName: String?,
    dateFormat: SimpleDateFormat,
    shortDateFormat: SimpleDateFormat,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPhotoClick: (String) -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    val isGiven = entry.type == "GIVEN"
    val isReceived = entry.type == "RECEIVED"
    val isSettlement = entry.type == "SETTLEMENT"

    val isOverdue = entry.dueDate != null && entry.dueDate < System.currentTimeMillis()

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Type Badge & Date
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = when {
                            isGiven -> IncomeGreen.copy(alpha = 0.15f)
                            isReceived -> ExpenseRed.copy(alpha = 0.15f)
                            else -> MaterialTheme.colorScheme.primaryContainer
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = when {
                                    isGiven -> Icons.Default.ArrowOutward
                                    isReceived -> Icons.Default.CallReceived
                                    else -> Icons.Default.Check
                                },
                                contentDescription = null,
                                tint = when {
                                    isGiven -> IncomeGreen
                                    isReceived -> ExpenseRed
                                    else -> MaterialTheme.colorScheme.primary
                                },
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = when {
                                isGiven -> "Lent / Gave"
                                isReceived -> "Borrowed / Received"
                                else -> "Settlement"
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = when {
                                isGiven -> IncomeGreen
                                isReceived -> ExpenseRed
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                        Text(
                            text = dateFormat.format(Date(entry.date)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Amount & Menu
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${if (isGiven) "+" else if (isReceived) "-" else ""}$currencySymbol${String.format("%,.2f", entry.amount)}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = when {
                            isGiven -> IncomeGreen
                            isReceived -> ExpenseRed
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
                    Box {
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Options", modifier = Modifier.size(18.dp))
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit") },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onEdit()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = {
                                    showMenu = false
                                    onDelete()
                                }
                            )
                        }
                    }
                }
            }

            // Note / Description
            if (entry.note.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = entry.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            // Badges row: Due Date, Linked Account, Photo
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (entry.dueDate != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (isOverdue) ExpenseRed.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Event,
                                contentDescription = null,
                                tint = if (isOverdue) ExpenseRed else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Due: ${shortDateFormat.format(Date(entry.dueDate))}${if (isOverdue) " (Overdue)" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isOverdue) ExpenseRed else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (accountName != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.AccountBalance,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = accountName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }

                if (!entry.tagPhotoUri.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                        modifier = Modifier.clickable { onPhotoClick(entry.tagPhotoUri) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.AttachFile,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Photo Attached",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PhotoPreviewDialog(
    photoUri: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Receipt Photo") },
        text = {
            AsyncImage(
                model = photoUri,
                contentDescription = "Receipt Attachment",
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 350.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
