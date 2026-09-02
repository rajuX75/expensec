package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TransactionEntity
import com.example.ui.components.CategoryBadge
import com.example.ui.components.ReceiptAttachmentPicker
import com.example.ui.components.TransactionItemCard
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.TransferBlue
import com.example.ui.viewmodel.ExpenseViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(
    viewModel: ExpenseViewModel,
    onEditTransaction: (TransactionEntity) -> Unit,
    onAddNewTransaction: () -> Unit
) {
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    val transactions by viewModel.filteredTransactions.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filterType by viewModel.filterType.collectAsState()
    val filterTimeRange by viewModel.filterTimeRange.collectAsState()
    val filterCategoryId by viewModel.filterCategoryId.collectAsState()
    val filterAccountId by viewModel.filterAccountId.collectAsState()
    val allCategories by viewModel.allCategories.collectAsState()
    val allAccounts by viewModel.allAccounts.collectAsState()

    var selectedDetailTx by remember { mutableStateOf<TransactionEntity?>(null) }
    var showFilterSheet by remember { mutableStateOf(false) }

    // Group transactions by date
    val groupedTransactions = remember(transactions) {
        val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        transactions.groupBy { tx ->
            dateFormat.format(Date(tx.date))
        }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddNewTransaction,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Transaction")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Bar & Filter Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Search note, merchant, tag...",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                            androidx.compose.foundation.text.BasicTextField(
                                value = searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (searchQuery.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.setSearchQuery("") },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Clear,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                FilledTonalIconButton(
                    onClick = { showFilterSheet = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Filters",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Quick Type Filter Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val types = listOf(
                    "ALL" to "All Types",
                    "EXPENSE" to "Expenses",
                    "INCOME" to "Income",
                    "TRANSFER" to "Transfers"
                )
                items(types) { (typeKey, label) ->
                    FilterChip(
                        selected = filterType == typeKey,
                        onClick = { viewModel.setFilterType(typeKey) },
                        label = { Text(label) }
                    )
                }
            }

            // Time Range Filter Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val ranges = listOf(
                    "ALL" to "All Time",
                    "THIS_MONTH" to "This Month",
                    "LAST_MONTH" to "Last Month",
                    "THIS_WEEK" to "This Week",
                    "TODAY" to "Today"
                )
                items(ranges) { (rangeKey, label) ->
                    FilterChip(
                        selected = filterTimeRange == rangeKey,
                        onClick = { viewModel.setFilterTimeRange(rangeKey) },
                        label = { Text(label) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Transaction List
            if (transactions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.SearchOff,
                            contentDescription = null,
                            modifier = Modifier.size(54.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No matching transactions found",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Try adjusting your search or filters",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(onClick = { viewModel.resetFilters() }) {
                            Text("Reset Filters")
                        }
                    }
                }
            } else {
                val headerFormat = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault())
                val todayCal = Calendar.getInstance()
                val todayStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(todayCal.time)
                todayCal.add(Calendar.DAY_OF_YEAR, -1)
                val yesterdayStr = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(todayCal.time)

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 96.dp)
                ) {
                    groupedTransactions.forEach { (dateKey, txList) ->
                        // Calculate daily total
                        val dailyExpense = txList.filter { it.type == com.example.data.model.TransactionType.EXPENSE }.sumOf { it.amount }
                        val dailyIncome = txList.filter { it.type == com.example.data.model.TransactionType.INCOME }.sumOf { it.amount }

                        val headerTitle = when (dateKey) {
                            todayStr -> "Today"
                            yesterdayStr -> "Yesterday"
                            else -> headerFormat.format(Date(txList.first().date))
                        }

                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 14.dp, bottom = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = headerTitle,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (dailyIncome > 0) {
                                        Text(
                                            text = "+$currencySymbol${String.format("%,.0f", dailyIncome)}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = IncomeGreen
                                        )
                                    }
                                    if (dailyExpense > 0) {
                                        Text(
                                            text = "-$currencySymbol${String.format("%,.0f", dailyExpense)}",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = ExpenseRed
                                        )
                                    }
                                }
                            }
                        }

                        items(txList) { tx ->
                            TransactionItemCard(
                                transaction = tx,
                                currencySymbol = currencySymbol,
                                onClick = { selectedDetailTx = tx }
                            )
                        }
                    }
                }
            }
        }
    }

    // Transaction Detail Bottom Sheet
    if (selectedDetailTx != null) {
        val tx = selectedDetailTx!!
        val dateFormat = SimpleDateFormat("EEEE, MMMM d, yyyy 'at' h:mm a", Locale.getDefault())
        val isIncome = tx.type == com.example.data.model.TransactionType.INCOME
        val isTransfer = tx.type == com.example.data.model.TransactionType.TRANSFER

        ModalBottomSheet(
            onDismissRequest = { selectedDetailTx = null },
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .padding(bottom = 24.dp)
            ) {
                // Header badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CategoryBadge(
                        iconName = if (isTransfer) "swap_horiz" else tx.categoryIcon,
                        colorHex = if (isTransfer) "#3B82F6" else tx.categoryColorHex,
                        size = 54.dp,
                        iconSize = 28.dp
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = when {
                            isIncome -> IncomeGreen.copy(alpha = 0.15f)
                            isTransfer -> TransferBlue.copy(alpha = 0.15f)
                            else -> ExpenseRed.copy(alpha = 0.15f)
                        }
                    ) {
                        Text(
                            text = tx.type.name,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = when {
                                isIncome -> IncomeGreen
                                isTransfer -> TransferBlue
                                else -> ExpenseRed
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Amount
                Text(
                    text = "${if (isIncome) "+" else if (isTransfer) "" else "-"}$currencySymbol${String.format("%,.2f", tx.amount)}",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = when {
                        isIncome -> IncomeGreen
                        isTransfer -> TransferBlue
                        else -> ExpenseRed
                    }
                )

                Text(
                    text = if (tx.merchant.isNotBlank()) tx.merchant else tx.categoryName,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // Info Rows
                DetailRow("Date", dateFormat.format(Date(tx.date)))
                DetailRow("Category", tx.categoryName)
                DetailRow("Account", if (isTransfer && tx.toAccountName != null) "${tx.accountName} → ${tx.toAccountName}" else tx.accountName)
                DetailRow("Payment Method", tx.paymentMethod)
                if (tx.note.isNotBlank()) {
                    DetailRow("Note", tx.note)
                }
                if (tx.tags.isNotBlank()) {
                    DetailRow("Tags", tx.tags)
                }
                if (tx.isRecurring) {
                    DetailRow("Recurring", "Yes (${tx.recurringPeriod ?: "MONTHLY"})")
                }

                if (!tx.receiptUri.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    ReceiptAttachmentPicker(
                        receiptUri = tx.receiptUri,
                        onReceiptChanged = { /* read only view in detail */ }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Actions: Edit & Delete
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.deleteTransaction(tx)
                            selectedDetailTx = null
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Delete")
                    }

                    Button(
                        onClick = {
                            val toEdit = tx
                            selectedDetailTx = null
                            onEditTransaction(toEdit)
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Edit")
                    }
                }
            }
        }
    }

    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Filter Transactions",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    TextButton(
                        onClick = { viewModel.resetFilters() }
                    ) {
                        Text("Reset All", color = MaterialTheme.colorScheme.error)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Time Range
                Text(
                    text = "Time Range",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                val timeRanges = listOf(
                    "ALL" to "All Time",
                    "THIS_MONTH" to "This Month",
                    "LAST_30_DAYS" to "Last 30 Days",
                    "THIS_YEAR" to "This Year"
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(timeRanges) { (key, label) ->
                        FilterChip(
                            selected = filterTimeRange == key,
                            onClick = { viewModel.setFilterTimeRange(key) },
                            label = { Text(label) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Transaction Type
                Text(
                    text = "Transaction Type",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                val types = listOf(
                    "ALL" to "All Types",
                    "EXPENSE" to "Expenses",
                    "INCOME" to "Income",
                    "TRANSFER" to "Transfers"
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(types) { (key, label) ->
                        FilterChip(
                            selected = filterType == key,
                            onClick = { viewModel.setFilterType(key) },
                            label = { Text(label) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Account Filter
                Text(
                    text = "Account",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = filterAccountId == null,
                            onClick = { viewModel.setFilterAccountId(null) },
                            label = { Text("All Accounts") }
                        )
                    }
                    items(allAccounts) { acc ->
                        FilterChip(
                            selected = filterAccountId == acc.id,
                            onClick = {
                                viewModel.setFilterAccountId(if (filterAccountId == acc.id) null else acc.id)
                            },
                            label = { Text(acc.name) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Category Filter
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip(
                            selected = filterCategoryId == null,
                            onClick = { viewModel.setFilterCategoryId(null) },
                            label = { Text("All Categories") }
                        )
                    }
                    items(allCategories) { cat ->
                        FilterChip(
                            selected = filterCategoryId == cat.id,
                            onClick = {
                                viewModel.setFilterCategoryId(if (filterCategoryId == cat.id) null else cat.id)
                            },
                            label = { Text(cat.name) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { showFilterSheet = false },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Apply Filters")
                }
            }
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

