package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.AccountEntity
import com.example.data.model.BillEntity
import com.example.ui.components.CategoryBadge
import com.example.ui.components.CategoryIconHelper
import com.example.ui.theme.*
import com.example.ui.viewmodel.ExpenseViewModel
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * Skill: component-family-consistency & repeated-component-alignment (Dembrandt Stage 2 & 4).
 *
 * Harmonizes tabs, cards, and FAB shapes with ShapeTokens, applies the Dembrandt
 * "Shadow + 1px subtle border" rule, uses theme-aware containers for bill status badges,
 * and renders financial balances with tabular numerals.
 */
@Composable
fun AccountsAndBillsScreen(
    viewModel: ExpenseViewModel,
    onOpenTransfer: () -> Unit
) {
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    val accountsWithBalances by viewModel.accountsWithBalances.collectAsState()
    val bills by viewModel.allBills.collectAsState()
    val financialColors = MaterialTheme.financialColors

    var selectedTab by remember { mutableStateOf(0) } // 0: Accounts, 1: Bills

    var showAddEditAccountDialog by remember { mutableStateOf(false) }
    var selectedAccountToEdit by remember { mutableStateOf<AccountEntity?>(null) }

    var showAddEditBillDialog by remember { mutableStateOf(false) }
    var selectedBillToEdit by remember { mutableStateOf<BillEntity?>(null) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (selectedTab == 0) {
                        selectedAccountToEdit = null
                        showAddEditAccountDialog = true
                    } else {
                        selectedBillToEdit = null
                        showAddEditBillDialog = true
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = ShapeTokens.large
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = if (selectedTab == 0) "Add Account" else "Add Bill"
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Segmented Top Tab
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            "Accounts (${accountsWithBalances.size})",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            "Bills & Reminders (${bills.size})",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                )
            }

            if (selectedTab == 0) {
                // ACCOUNTS TAB
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
                ) {
                    // Transfer Quick Action Bar
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(ShapeTokens.large)
                                .clickable(onClick = onOpenTransfer),
                            shape = ShapeTokens.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                            border = cardBorderStroke()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.SwapHoriz,
                                        contentDescription = "Transfer",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Transfer Between Accounts",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Move funds with automatic ledger logging",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Icon(Icons.Default.ChevronRight, contentDescription = null)
                            }
                        }
                    }

                    items(accountsWithBalances) { item ->
                        val acc = item.account
                        val color = CategoryIconHelper.parseColor(acc.colorHex)

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(ShapeTokens.large)
                                .clickable {
                                    selectedAccountToEdit = acc
                                    showAddEditAccountDialog = true
                                },
                            shape = ShapeTokens.large,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = cardBorderStroke(),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .background(color.copy(alpha = 0.15f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = CategoryIconHelper.getIcon(acc.iconName),
                                            contentDescription = null,
                                            tint = color,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column {
                                        Text(
                                            text = acc.name,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = acc.type,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "$currencySymbol${String.format(Locale.US, "%,.2f", item.liveBalance)}",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold).tabular(),
                                        color = if (item.liveBalance >= 0) MaterialTheme.colorScheme.onSurface else financialColors.expense
                                    )
                                    Text(
                                        text = "Balance",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // BILLS & REMINDERS TAB
                val now = System.currentTimeMillis()
                val dateFormat = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 12.dp, bottom = 96.dp)
                ) {
                    if (bills.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                shape = ShapeTokens.large,
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                                border = cardBorderStroke()
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.EventNote,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = "No bills or subscriptions logged",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Tap + to track electricity, rent, internet or subscriptions",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(bills) { bill ->
                            val daysDiff = TimeUnit.MILLISECONDS.toDays(bill.dueDate - now)
                            val isOverdue = !bill.isPaid && daysDiff < 0

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(ShapeTokens.large)
                                    .clickable {
                                        selectedBillToEdit = bill
                                        showAddEditBillDialog = true
                                    },
                                shape = ShapeTokens.large,
                                colors = CardDefaults.cardColors(
                                    containerColor = if (bill.isPaid) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                    else MaterialTheme.colorScheme.surface
                                ),
                                border = cardBorderStroke(),
                                elevation = CardDefaults.cardElevation(defaultElevation = if (bill.isPaid) 0.dp else 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(42.dp)
                                                .background(
                                                    if (bill.isPaid) financialColors.income.copy(alpha = 0.15f)
                                                    else if (isOverdue) financialColors.expense.copy(alpha = 0.15f)
                                                    else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                                    CircleShape
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (bill.isPaid) Icons.Default.CheckCircle
                                                else if (isOverdue) Icons.Default.Warning
                                                else Icons.Default.Notifications,
                                                contentDescription = null,
                                                tint = if (bill.isPaid) financialColors.income
                                                else if (isOverdue) financialColors.expense
                                                else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        Column {
                                            Text(
                                                text = bill.title,
                                                style = MaterialTheme.typography.bodyLarge.copy(
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "${dateFormat.format(Date(bill.dueDate))} • ${bill.frequency}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = when {
                                                    bill.isPaid -> financialColors.income
                                                    isOverdue -> financialColors.expense
                                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                }
                                            )
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "$currencySymbol${String.format(Locale.US, "%,.2f", bill.amount)}",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold).tabular(),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))

                                        if (!bill.isPaid) {
                                            FilledTonalButton(
                                                onClick = { viewModel.markBillAsPaid(bill) },
                                                shape = ShapeTokens.small,
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier.height(30.dp)
                                            ) {
                                                Text("Mark Paid", style = MaterialTheme.typography.labelSmall)
                                            }
                                        } else {
                                            Surface(
                                                shape = ShapeTokens.small,
                                                color = financialColors.incomeContainer
                                            ) {
                                                Text(
                                                    text = "PAID",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = financialColors.onIncomeContainer,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddEditAccountDialog) {
        AddEditAccountDialog(
            viewModel = viewModel,
            initialAccount = selectedAccountToEdit,
            onDismiss = { showAddEditAccountDialog = false }
        )
    }

    if (showAddEditBillDialog) {
        AddEditBillDialog(
            viewModel = viewModel,
            initialBill = selectedBillToEdit,
            onDismiss = { showAddEditBillDialog = false }
        )
    }
}
