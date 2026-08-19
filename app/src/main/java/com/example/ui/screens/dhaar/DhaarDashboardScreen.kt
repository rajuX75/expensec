package com.example.ui.screens.dhaar

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ContactWithBalance
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.viewmodel.ExpenseViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

enum class DhaarFilter(val label: String) {
    ALL("All"),
    PABO("You'll Get"),
    DEBO("You'll Pay"),
    SETTLED("Settled")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DhaarDashboardScreen(
    viewModel: ExpenseViewModel,
    onNavigateToContact: (Long) -> Unit
) {
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    val summary by viewModel.dhaarDashboardSummary.collectAsState()
    val contactsWithBalances by viewModel.contactsWithBalances.collectAsState()
    val upcomingReminders by viewModel.upcomingDhaarReminders.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(DhaarFilter.ALL) }
    var showAddEntrySheet by remember { mutableStateOf(false) }
    var showNewContactDialog by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("dd MMM, yyyy", Locale.getDefault()) }

    // Filter and search
    val filteredContacts = remember(contactsWithBalances, searchQuery, selectedFilter) {
        contactsWithBalances.filter { item ->
            val matchesQuery = item.contact.name.contains(searchQuery, ignoreCase = true) ||
                    (item.contact.phoneNumber?.contains(searchQuery) == true)

            val matchesFilter = when (selectedFilter) {
                DhaarFilter.ALL -> true
                DhaarFilter.PABO -> item.netBalance > 0.001
                DhaarFilter.DEBO -> item.netBalance < -0.001
                DhaarFilter.SETTLED -> abs(item.netBalance) <= 0.001
            }

            matchesQuery && matchesFilter
        }.sortedByDescending { abs(it.netBalance) }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddEntrySheet = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Entry")
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Record", fontWeight = FontWeight.Bold)
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Summary Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Text(
                            text = "Debts & Loans Summary",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Total You'll Get
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = IncomeGreen.copy(alpha = 0.12f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.ArrowDownward,
                                            contentDescription = null,
                                            tint = IncomeGreen,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "You'll Get",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = IncomeGreen
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "$currencySymbol${String.format("%,.2f", summary.totalYouWillGet)}",
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                        color = IncomeGreen
                                    )
                                }
                            }

                            // Total You'll Pay
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = ExpenseRed.copy(alpha = 0.12f),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.ArrowUpward,
                                            contentDescription = null,
                                            tint = ExpenseRed,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "You'll Pay",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = ExpenseRed
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "$currencySymbol${String.format("%,.2f", summary.totalYouWillPay)}",
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                        color = ExpenseRed
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Net Position Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Net Position",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val net = summary.netPosition
                            Text(
                                text = "${if (net > 0) "+" else ""}$currencySymbol${String.format("%,.2f", net)}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                color = if (net > 0.01) IncomeGreen else if (net < -0.01) ExpenseRed else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Upcoming Reminders Strip (if any)
            if (upcomingReminders.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Upcoming Due Dates (${upcomingReminders.size})",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            upcomingReminders.take(3).forEach { reminder ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .clickable { onNavigateToContact(reminder.entry.contactId) },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(reminder.contactName, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold))
                                        Text(
                                            text = if (reminder.isOverdue) "Overdue by ${reminder.daysRemaining * -1} days" else "Due in ${reminder.daysRemaining} days (${dateFormat.format(Date(reminder.entry.dueDate ?: 0))})",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (reminder.isOverdue) ExpenseRed else MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                    Text(
                                        text = "$currencySymbol${String.format("%,.2f", reminder.entry.amount)}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = if (reminder.entry.type == "GIVEN") IncomeGreen else ExpenseRed
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Search Bar & Add Contact Button
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search by name or phone") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )

                    FilledTonalIconButton(
                        onClick = { showNewContactDialog = true },
                        modifier = Modifier.size(52.dp),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "New Contact")
                    }
                }
            }

            // Filter Chips
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(DhaarFilter.values()) { filter ->
                        val isSelected = selectedFilter == filter
                        FilterChip(
                            selected = isSelected,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter.label) },
                            shape = RoundedCornerShape(10.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            // Contact List
            if (filteredContacts.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Outlined.People,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (searchQuery.isNotEmpty()) "No matching contacts found" else "No debt or loan records yet",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { showAddEntrySheet = true },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Record")
                            }
                        }
                    }
                }
            } else {
                items(filteredContacts, key = { it.contact.id }) { item ->
                    ContactListItemCard(
                        item = item,
                        currencySymbol = currencySymbol,
                        dateFormat = dateFormat,
                        onClick = { onNavigateToContact(item.contact.id) }
                    )
                }
            }
        }
    }

    // Modal Add Dhaar Entry Sheet
    if (showAddEntrySheet) {
        AddEditDhaarDialog(
            viewModel = viewModel,
            onDismiss = { showAddEntrySheet = false }
        )
    }

    // Modal New Contact Dialog
    if (showNewContactDialog) {
        AddEditContactDialog(
            viewModel = viewModel,
            onDismiss = { showNewContactDialog = false },
            onContactSaved = { newId ->
                onNavigateToContact(newId)
            }
        )
    }
}

@Composable
fun ContactListItemCard(
    item: ContactWithBalance,
    currencySymbol: String,
    dateFormat: SimpleDateFormat,
    onClick: () -> Unit
) {
    val contact = item.contact
    val netBalance = item.netBalance
    val isTheyOweYou = netBalance > 0.001
    val isYouOweThem = netBalance < -0.001
    val isSettled = !isTheyOweYou && !isYouOweThem

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Avatar & Name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                if (!contact.photoUri.isNullOrBlank()) {
                    coil.compose.AsyncImage(
                        model = contact.photoUri,
                        contentDescription = contact.name,
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .size(46.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Surface(
                        shape = CircleShape,
                        color = when {
                            isTheyOweYou -> IncomeGreen.copy(alpha = 0.15f)
                            isYouOweThem -> ExpenseRed.copy(alpha = 0.15f)
                            else -> MaterialTheme.colorScheme.primaryContainer
                        },
                        modifier = Modifier.size(46.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = contact.name.take(1).uppercase(),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = when {
                                    isTheyOweYou -> IncomeGreen
                                    isYouOweThem -> ExpenseRed
                                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = contact.name,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (!contact.phoneNumber.isNullOrBlank()) {
                        Text(
                            text = contact.phoneNumber,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else if (item.lastEntryDate != null) {
                        Text(
                            text = "Last: ${dateFormat.format(Date(item.lastEntryDate))}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Balance & Status
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$currencySymbol${String.format("%,.2f", abs(netBalance))}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = when {
                        isTheyOweYou -> IncomeGreen
                        isYouOweThem -> ExpenseRed
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = when {
                        isTheyOweYou -> IncomeGreen.copy(alpha = 0.15f)
                        isYouOweThem -> ExpenseRed.copy(alpha = 0.15f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    }
                ) {
                    Text(
                        text = when {
                            isTheyOweYou -> "You'll Get"
                            isYouOweThem -> "You'll Pay"
                            else -> "Settled"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = when {
                            isTheyOweYou -> IncomeGreen
                            isYouOweThem -> ExpenseRed
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}
