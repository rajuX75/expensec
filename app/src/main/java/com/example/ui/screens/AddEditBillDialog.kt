package com.example.ui.screens

import android.app.DatePickerDialog
import android.widget.DatePicker
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.model.BillEntity
import com.example.ui.viewmodel.ExpenseViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AddEditBillDialog(
    viewModel: ExpenseViewModel,
    initialBill: BillEntity? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    val allCategories by viewModel.allCategories.collectAsState()
    val allAccounts by viewModel.allAccounts.collectAsState()

    var title by remember { mutableStateOf(initialBill?.title ?: "") }
    var amountText by remember {
        mutableStateOf(initialBill?.let { String.format("%.2f", it.amount) } ?: "")
    }
    var dueDate by remember { mutableStateOf(initialBill?.dueDate ?: System.currentTimeMillis() + (7 * 24 * 60 * 60 * 1000L)) }
    var frequency by remember { mutableStateOf(initialBill?.frequency ?: "MONTHLY") }
    var selectedCategory by remember(allCategories) {
        mutableStateOf(allCategories.find { it.name.equals(initialBill?.categoryName, ignoreCase = true) } ?: allCategories.firstOrNull())
    }
    var selectedAccount by remember(allAccounts) {
        mutableStateOf(allAccounts.find { it.id == initialBill?.accountId } ?: allAccounts.firstOrNull())
    }
    var autoLog by remember { mutableStateOf(initialBill?.autoLogTransaction ?: true) }

    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    val cal = Calendar.getInstance().apply { timeInMillis = dueDate }

    val datePickerDialog = DatePickerDialog(
        context,
        { _: DatePicker, year: Int, month: Int, day: Int ->
            val newCal = Calendar.getInstance().apply { set(year, month, day) }
            dueDate = newCal.timeInMillis
        },
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH),
        cal.get(Calendar.DAY_OF_MONTH)
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (initialBill == null) "Add Bill / Subscription" else "Edit Bill",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Title
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Bill / Service Title") },
                    placeholder = { Text("e.g. Electric Bill, Rent, Netflix") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Amount
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Due Amount") },
                    leadingIcon = {
                        Text(
                            text = currencySymbol,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Due Date Card
                OutlinedCard(
                    onClick = { datePickerDialog.show() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
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
                                text = "Due Date: ${dateFormat.format(Date(dueDate))}",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                        }
                        Text("Change", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Frequency
                Text(
                    text = "Recurring Frequency",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                val frequencies = listOf("ONETIME" to "One-time", "WEEKLY" to "Weekly", "MONTHLY" to "Monthly", "YEARLY" to "Yearly")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(frequencies) { (freqKey, label) ->
                        FilterChip(
                            selected = frequency == freqKey,
                            onClick = { frequency = freqKey },
                            label = { Text(label) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Auto-log Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Auto-record expense when marked paid",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Switch(checked = autoLog, onCheckedChange = { autoLog = it })
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (initialBill != null) {
                        OutlinedButton(
                            onClick = {
                                viewModel.deleteBill(initialBill)
                                onDismiss()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Delete")
                        }
                    }

                    val isValid = title.isNotBlank() && amountText.toDoubleOrNull() != null

                    Button(
                        onClick = {
                            val amount = amountText.toDoubleOrNull() ?: 0.0
                            val bill = BillEntity(
                                id = initialBill?.id ?: 0,
                                uuid = initialBill?.uuid ?: UUID.randomUUID().toString(),
                                title = title.trim(),
                                amount = amount,
                                dueDate = dueDate,
                                frequency = frequency,
                                categoryId = selectedCategory?.id ?: 0,
                                categoryName = selectedCategory?.name ?: "Utilities",
                                accountId = selectedAccount?.id,
                                isPaid = initialBill?.isPaid ?: false,
                                lastPaidDate = initialBill?.lastPaidDate,
                                autoLogTransaction = autoLog
                            )

                            if (initialBill == null) {
                                viewModel.addBill(bill)
                            } else {
                                viewModel.updateBill(bill)
                            }
                            onDismiss()
                        },
                        enabled = isValid,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (initialBill == null) "Set Reminder" else "Save Changes")
                    }
                }
            }
        }
    }
}
