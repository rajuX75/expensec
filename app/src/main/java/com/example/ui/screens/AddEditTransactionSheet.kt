package com.example.ui.screens

import android.app.DatePickerDialog
import android.widget.DatePicker
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AccountEntity
import com.example.data.model.CategoryEntity
import com.example.data.model.TransactionEntity
import com.example.ui.components.CategoryBadge
import com.example.ui.components.CategoryIconHelper
import com.example.ui.components.ReceiptAttachmentPicker
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.theme.TransferBlue
import com.example.ui.viewmodel.ExpenseViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTransactionSheet(
    viewModel: ExpenseViewModel,
    initialTransaction: TransactionEntity? = null,
    initialType: String = "EXPENSE",
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    val allCategories by viewModel.allCategories.collectAsState()
    val allAccounts by viewModel.allAccounts.collectAsState()

    var selectedType by remember {
        mutableStateOf(initialTransaction?.type ?: initialType)
    }

    var amountText by remember {
        mutableStateOf(initialTransaction?.let { String.format(Locale.US, "%.2f", it.amount) } ?: "")
    }

    val filteredCategories = remember(allCategories, selectedType) {
        if (selectedType == "TRANSFER") emptyList()
        else allCategories.filter { it.type.equals(selectedType, ignoreCase = true) }
    }

    var selectedCategory by remember(filteredCategories) {
        mutableStateOf(
            if (initialTransaction != null) {
                allCategories.find { it.name.equals(initialTransaction.categoryName, ignoreCase = true) }
                    ?: filteredCategories.firstOrNull()
            } else {
                filteredCategories.firstOrNull()
            }
        )
    }

    var selectedAccount by remember(allAccounts) {
        mutableStateOf(
            if (initialTransaction != null) {
                allAccounts.find { it.id == initialTransaction.accountId } ?: allAccounts.firstOrNull()
            } else {
                allAccounts.firstOrNull()
            }
        )
    }

    var selectedToAccount by remember(allAccounts) {
        mutableStateOf(
            if (initialTransaction?.toAccountId != null) {
                allAccounts.find { it.id == initialTransaction.toAccountId }
            } else {
                allAccounts.getOrNull(1) ?: allAccounts.firstOrNull()
            }
        )
    }

    var merchant by remember { mutableStateOf(initialTransaction?.merchant ?: "") }
    var note by remember { mutableStateOf(initialTransaction?.note ?: "") }
    var tagsText by remember { mutableStateOf(initialTransaction?.tags ?: "") }
    var selectedDate by remember { mutableStateOf(initialTransaction?.date ?: System.currentTimeMillis()) }
    var paymentMethod by remember { mutableStateOf(initialTransaction?.paymentMethod ?: "Card") }
    var receiptUri by remember { mutableStateOf(initialTransaction?.receiptUri) }
    var isRecurring by remember { mutableStateOf(initialTransaction?.isRecurring ?: false) }
    var recurringPeriod by remember { mutableStateOf(initialTransaction?.recurringPeriod ?: "MONTHLY") }

    val dateFormat = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (initialTransaction == null) "Add Transaction" else "Edit Transaction",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Transaction Type Selector (Expense | Income | Transfer)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val types = listOf("EXPENSE" to "Expense", "INCOME" to "Income", "TRANSFER" to "Transfer")
                types.forEach { (type, label) ->
                    val isSelected = selectedType == type
                    val activeColor = when (type) {
                        "EXPENSE" -> ExpenseRed
                        "INCOME" -> IncomeGreen
                        else -> TransferBlue
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) activeColor else Color.Transparent)
                            .clickable {
                                selectedType = type
                                if (type == "EXPENSE") {
                                    selectedCategory = allCategories.firstOrNull { it.type == "EXPENSE" }
                                } else if (type == "INCOME") {
                                    selectedCategory = allCategories.firstOrNull { it.type == "INCOME" }
                                }
                            }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Amount Input
            OutlinedTextField(
                value = amountText,
                onValueChange = { amountText = it },
                label = { Text("Amount") },
                leadingIcon = {
                    Text(
                        text = currencySymbol,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                },
                placeholder = { Text("0.00") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                textStyle = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Category Selection (for Expense/Income)
            if (selectedType != "TRANSFER") {
                Text(
                    text = "Category",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(filteredCategories) { cat ->
                        val isSelected = selectedCategory?.id == cat.id
                        val catColor = CategoryIconHelper.parseColor(cat.colorHex)

                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { selectedCategory = cat },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) catColor.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, catColor) else null
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CategoryBadge(
                                    iconName = cat.iconName,
                                    colorHex = cat.colorHex,
                                    size = 28.dp,
                                    iconSize = 16.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = cat.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Merchant / Payee Input with Smart Auto-Categorization
            OutlinedTextField(
                value = merchant,
                onValueChange = { input ->
                    merchant = input
                    // Smart suggestion
                    val suggested = viewModel.suggestCategoryForMerchant(input)
                    if (suggested != null && selectedType == suggested.type) {
                        selectedCategory = suggested
                    }
                },
                label = { Text(if (selectedType == "INCOME") "Payer / Source" else "Merchant / Payee") },
                placeholder = { Text("e.g. Starbucks, Uber, Salary, Amazon") },
                leadingIcon = { Icon(Icons.Default.Storefront, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Account Selector (From Account & To Account if Transfer)
            if (selectedType == "TRANSFER") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // From Account
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "From Account",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        var expandedFrom by remember { mutableStateOf(false) }
                        OutlinedCard(
                            onClick = { expandedFrom = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = selectedAccount?.name ?: "Select",
                                modifier = Modifier.padding(12.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        DropdownMenu(
                            expanded = expandedFrom,
                            onDismissRequest = { expandedFrom = false }
                        ) {
                            allAccounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text(acc.name) },
                                    onClick = {
                                        selectedAccount = acc
                                        expandedFrom = false
                                    }
                                )
                            }
                        }
                    }

                    // To Account
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "To Account",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        var expandedTo by remember { mutableStateOf(false) }
                        OutlinedCard(
                            onClick = { expandedTo = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = selectedToAccount?.name ?: "Select",
                                modifier = Modifier.padding(12.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        DropdownMenu(
                            expanded = expandedTo,
                            onDismissRequest = { expandedTo = false }
                        ) {
                            allAccounts.filter { it.id != selectedAccount?.id }.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text(acc.name) },
                                    onClick = {
                                        selectedToAccount = acc
                                        expandedTo = false
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                // Single Account selector
                var expandedAccount by remember { mutableStateOf(false) }
                OutlinedCard(
                    onClick = { expandedAccount = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.AccountBalance, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = selectedAccount?.name ?: "Select Account",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                        }
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                }
                DropdownMenu(
                    expanded = expandedAccount,
                    onDismissRequest = { expandedAccount = false }
                ) {
                    allAccounts.forEach { acc ->
                        DropdownMenuItem(
                            text = { Text("${acc.name} (${acc.type})") },
                            onClick = {
                                selectedAccount = acc
                                expandedAccount = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Date Picker Card
            OutlinedCard(
                onClick = {
                    val cal = Calendar.getInstance().apply { timeInMillis = selectedDate }
                    DatePickerDialog(
                        context,
                        { _: DatePicker, year: Int, month: Int, dayOfMonth: Int ->
                            val newCal = Calendar.getInstance().apply {
                                set(year, month, dayOfMonth)
                            }
                            selectedDate = newCal.timeInMillis
                        },
                        cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH),
                        cal.get(Calendar.DAY_OF_MONTH)
                    ).show()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = dateFormat.format(Date(selectedDate)),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                        )
                    }
                    Text(
                        text = "Change",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Note & Tags
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note / Description") },
                placeholder = { Text("Add details or notes...") },
                leadingIcon = { Icon(Icons.Default.Notes, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = tagsText,
                onValueChange = { tagsText = it },
                label = { Text("Tags / Labels (comma-separated)") },
                placeholder = { Text("e.g. Vacation, Work, TaxDeductible") },
                leadingIcon = { Icon(Icons.Default.Label, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Receipt Attachment Component
            ReceiptAttachmentPicker(
                receiptUri = receiptUri,
                onReceiptChanged = { receiptUri = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Recurring Options
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Repeat, contentDescription = null, tint = Color(0xFFF59E0B))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Recurring Transaction",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                        }
                        Switch(
                            checked = isRecurring,
                            onCheckedChange = { isRecurring = it }
                        )
                    }

                    if (isRecurring) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf("WEEKLY", "MONTHLY", "YEARLY").forEach { period ->
                                FilterChip(
                                    selected = recurringPeriod == period,
                                    onClick = { recurringPeriod = period },
                                    label = { Text(period.lowercase().replaceFirstChar { it.uppercase() }) }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Save Action Button
            val parsedAmount = amountText.replace(',', '.').toDoubleOrNull()
            val isValid = parsedAmount != null && parsedAmount > 0.0

            Button(
                onClick = {
                    val amount = amountText.replace(',', '.').toDoubleOrNull() ?: return@Button
                    val finalTx = TransactionEntity(
                        id = initialTransaction?.id ?: 0,
                        uuid = initialTransaction?.uuid ?: UUID.randomUUID().toString(),
                        type = selectedType,
                        amount = amount,
                        currency = viewModel.currency.value,
                        categoryId = selectedCategory?.id ?: 0,
                        categoryName = if (selectedType == "TRANSFER") "Transfer" else (selectedCategory?.name ?: "General"),
                        categoryIcon = if (selectedType == "TRANSFER") "swap_horiz" else (selectedCategory?.iconName ?: "category"),
                        categoryColorHex = if (selectedType == "TRANSFER") "#3B82F6" else (selectedCategory?.colorHex ?: "#64748B"),
                        accountId = selectedAccount?.id ?: 1,
                        accountName = selectedAccount?.name ?: "Main Account",
                        toAccountId = if (selectedType == "TRANSFER") selectedToAccount?.id else null,
                        toAccountName = if (selectedType == "TRANSFER") selectedToAccount?.name else null,
                        date = selectedDate,
                        note = note.trim(),
                        merchant = merchant.trim(),
                        paymentMethod = paymentMethod,
                        receiptUri = receiptUri,
                        tags = tagsText.trim(),
                        isRecurring = isRecurring,
                        recurringPeriod = if (isRecurring) recurringPeriod else null
                    )

                    if (initialTransaction == null) {
                        viewModel.addTransaction(finalTx)
                    } else {
                        viewModel.updateTransaction(finalTx)
                    }
                    onSaved()
                },
                enabled = isValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when (selectedType) {
                        "EXPENSE" -> ExpenseRed
                        "INCOME" -> IncomeGreen
                        else -> TransferBlue
                    }
                )
            ) {
                Icon(Icons.Default.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (initialTransaction == null) "Save Transaction" else "Update Transaction",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
        }
    }
}
