package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.model.BudgetEntity
import com.example.data.model.CategoryEntity
import com.example.ui.viewmodel.ExpenseViewModel
import java.util.Locale
import java.util.UUID

@Composable
fun AddEditBudgetDialog(
    viewModel: ExpenseViewModel,
    initialBudget: BudgetEntity? = null,
    onDismiss: () -> Unit
) {
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    val allCategories by viewModel.allCategories.collectAsState()
    val expenseCategories = remember(allCategories) {
        allCategories.filter { it.type == "EXPENSE" }
    }

    var isOverall by remember {
        mutableStateOf(initialBudget?.categoryId == null)
    }

    var selectedCategory by remember {
        mutableStateOf(
            if (initialBudget?.categoryId != null) {
                expenseCategories.find { it.id == initialBudget.categoryId }
            } else null
        )
    }

    var limitAmountText by remember {
        mutableStateOf(initialBudget?.let { String.format(Locale.US, "%.2f", it.amountLimit) } ?: "")
    }

    var alertThreshold by remember {
        mutableStateOf(initialBudget?.alertThresholdPercent?.toFloat() ?: 80f)
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (initialBudget == null) "Set Budget Limit" else "Edit Budget",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Budget Scope Selector (Overall vs Category)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = isOverall,
                        onClick = {
                            isOverall = true
                            selectedCategory = null
                        },
                        label = { Text("Overall Monthly") }
                    )
                    FilterChip(
                        selected = !isOverall,
                        onClick = {
                            isOverall = false
                            if (selectedCategory == null) {
                                selectedCategory = expenseCategories.firstOrNull()
                            }
                        },
                        label = { Text("Specific Category") }
                    )
                }

                if (!isOverall) {
                    Spacer(modifier = Modifier.height(12.dp))
                    var expandedCategory by remember { mutableStateOf(false) }

                    OutlinedCard(
                        onClick = { expandedCategory = true },
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
                            Text(
                                text = selectedCategory?.name ?: "Select Category",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                        }
                    }

                    DropdownMenu(
                        expanded = expandedCategory,
                        onDismissRequest = { expandedCategory = false }
                    ) {
                        expenseCategories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name) },
                                onClick = {
                                    selectedCategory = cat
                                    expandedCategory = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Limit Amount Field
                OutlinedTextField(
                    value = limitAmountText,
                    onValueChange = { limitAmountText = it },
                    label = { Text("Monthly Budget Limit") },
                    leadingIcon = {
                        Text(
                            text = currencySymbol,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    },
                    placeholder = { Text("e.g. 500.00") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Alert Threshold Slider
                Text(
                    text = "Warning Alert at ${alertThreshold.toInt()}% spent",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = alertThreshold,
                    onValueChange = { alertThreshold = it },
                    valueRange = 50f..95f,
                    steps = 8
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (initialBudget != null) {
                        OutlinedButton(
                            onClick = {
                                viewModel.deleteBudget(initialBudget)
                                onDismiss()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Delete")
                        }
                    }

                    val parsedLimit = limitAmountText.replace(',', '.').toDoubleOrNull()
                    val isValid = parsedLimit != null && parsedLimit > 0.0

                    Button(
                        onClick = {
                            val limit = limitAmountText.replace(',', '.').toDoubleOrNull() ?: return@Button
                            val budget = BudgetEntity(
                                id = initialBudget?.id ?: 0,
                                uuid = initialBudget?.uuid ?: UUID.randomUUID().toString(),
                                categoryId = if (isOverall) null else selectedCategory?.id,
                                categoryName = if (isOverall) "Overall Budget" else (selectedCategory?.name ?: "Category"),
                                amountLimit = limit,
                                period = "MONTHLY",
                                alertThresholdPercent = alertThreshold.toInt()
                            )

                            if (initialBudget == null) {
                                viewModel.addBudget(budget)
                            } else {
                                viewModel.updateBudget(budget)
                            }
                            onDismiss()
                        },
                        enabled = isValid,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (initialBudget == null) "Create Budget" else "Save Changes")
                    }
                }
            }
        }
    }
}
