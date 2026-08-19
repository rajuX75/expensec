package com.example.ui.screens.dhaar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.Contact
import com.example.data.model.DhaarEntry
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.viewmodel.ExpenseViewModel
import java.util.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettleUpDialog(
    viewModel: ExpenseViewModel,
    contact: Contact,
    currentNetBalance: Double,
    onDismiss: () -> Unit,
    onSettled: () -> Unit = {}
) {
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    val currencyCode by viewModel.currency.collectAsState()
    val allAccounts by viewModel.allAccounts.collectAsState()

    val isTheyOweYou = currentNetBalance > 0.001
    val isYouOweThem = currentNetBalance < -0.001
    val outstandingAbs = abs(currentNetBalance)

    var amountText by remember {
        mutableStateOf(String.format(Locale.US, "%.2f", outstandingAbs))
    }
    var note by remember {
        mutableStateOf(if (isTheyOweYou) "Received settlement from ${contact.name}" else "Settled debt to ${contact.name}")
    }
    var linkToAccount by remember { mutableStateOf(false) }
    var selectedAccountId by remember {
        mutableStateOf(allAccounts.firstOrNull()?.id ?: 1L)
    }
    var accountDropdownExpanded by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf(false) }

    val parsedAmount = amountText.toDoubleOrNull() ?: 0.0
    val isOverpaying = parsedAmount > (outstandingAbs + 0.001)
    val overpayAmount = if (isOverpaying) parsedAmount - outstandingAbs else 0.0

    // Compute remaining balance preview
    val projectedBalance = when {
        isTheyOweYou -> currentNetBalance - parsedAmount
        isYouOweThem -> currentNetBalance + parsedAmount
        else -> 0.0
    }

    val selectedAccount = remember(selectedAccountId, allAccounts) {
        allAccounts.find { it.id == selectedAccountId } ?: allAccounts.firstOrNull()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Settle Up",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = contact.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Outstanding Balance Banner
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isTheyOweYou) IncomeGreen.copy(alpha = 0.12f)
                           else if (isYouOweThem) ExpenseRed.copy(alpha = 0.12f)
                           else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = when {
                                    isTheyOweYou -> "Currently Receivable (They Owe You)"
                                    isYouOweThem -> "Currently Payable (You Owe Them)"
                                    else -> "Currently Settled"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isTheyOweYou) IncomeGreen else if (isYouOweThem) ExpenseRed else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "$currencySymbol${String.format("%,.2f", outstandingAbs)}",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = if (isTheyOweYou) IncomeGreen else if (isYouOweThem) ExpenseRed else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                // Amount Field
                Column {
                    Text(
                        text = "Settle Amount *",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = {
                            amountText = it
                            if (it.isNotBlank()) amountError = false
                        },
                        leadingIcon = {
                            Text(
                                text = currencySymbol,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        },
                        isError = amountError,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Quick Chips: Full / Half
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilledTonalButton(
                            onClick = { amountText = String.format(Locale.US, "%.2f", outstandingAbs) },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Full (${String.format("%.2f", outstandingAbs)})", style = MaterialTheme.typography.labelSmall)
                        }

                        if (outstandingAbs > 1.0) {
                            OutlinedButton(
                                onClick = { amountText = String.format(Locale.US, "%.2f", outstandingAbs / 2.0) },
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Half (${String.format("%.2f", outstandingAbs / 2.0)})", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }

                // Projected Balance & Overpayment Warning
                if (isOverpaying) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Warning: Settling $currencySymbol${String.format("%.2f", overpayAmount)} more than outstanding debt. Excess will create a reverse balance.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                } else if (parsedAmount > 0) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Remaining Balance:", style = MaterialTheme.typography.labelSmall)
                            Text(
                                text = "$currencySymbol${String.format("%,.2f", abs(projectedBalance))}",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    }
                }

                // Note
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note / Description") },
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Optional Account Linking
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Link to Account",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = "Updates account balance automatically",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = linkToAccount,
                        onCheckedChange = { linkToAccount = it }
                    )
                }

                if (linkToAccount) {
                    ExposedDropdownMenuBox(
                        expanded = accountDropdownExpanded,
                        onExpandedChange = { accountDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = selectedAccount?.name ?: "Select Account",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Account") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = accountDropdownExpanded) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )

                        ExposedDropdownMenu(
                            expanded = accountDropdownExpanded,
                            onDismissRequest = { accountDropdownExpanded = false }
                        ) {
                            allAccounts.forEach { acc ->
                                DropdownMenuItem(
                                    text = { Text(acc.name) },
                                    onClick = {
                                        selectedAccountId = acc.id
                                        accountDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amt = amountText.toDoubleOrNull()
                    if (amt == null || amt <= 0) {
                        amountError = true
                        return@Button
                    }

                    // For settlement:
                    // If you owed them (current balance < 0), you are paying them back -> isSettlementGive = true
                    // If they owed you (current balance > 0), they are paying you back -> isSettlementGive = false
                    val isGive = isYouOweThem

                    val settlementEntry = DhaarEntry(
                        contactId = contact.id,
                        type = "SETTLEMENT",
                        amount = amt,
                        currencyCode = currencyCode,
                        date = System.currentTimeMillis(),
                        note = note.trim(),
                        linkedAccountId = if (linkToAccount) selectedAccountId else null,
                        isSettlementGive = isGive
                    )

                    viewModel.addDhaarEntry(
                        entry = settlementEntry,
                        linkToAccount = linkToAccount,
                        accountName = selectedAccount?.name
                    )

                    onSettled()
                    onDismiss()
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Confirm Settlement")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(20.dp)
    )
}
