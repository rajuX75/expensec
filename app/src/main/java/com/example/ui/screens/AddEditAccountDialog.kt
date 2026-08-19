package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.model.AccountEntity
import com.example.ui.components.CategoryBadge
import com.example.ui.components.CategoryIconHelper
import com.example.ui.viewmodel.ExpenseViewModel
import java.util.UUID

@Composable
fun AddEditAccountDialog(
    viewModel: ExpenseViewModel,
    initialAccount: AccountEntity? = null,
    onDismiss: () -> Unit
) {
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    var name by remember { mutableStateOf(initialAccount?.name ?: "") }
    var accountType by remember { mutableStateOf(initialAccount?.type ?: "BANK") }
    var initialBalanceText by remember {
        mutableStateOf(initialAccount?.let { String.format("%.2f", it.balance) } ?: "0.00")
    }
    var selectedColor by remember {
        mutableStateOf(initialAccount?.colorHex ?: "#00875A")
    }
    var selectedIcon by remember {
        mutableStateOf(initialAccount?.iconName ?: "account_balance")
    }

    val accountTypes = listOf(
        "BANK" to "Bank Account",
        "CASH" to "Cash / Physical",
        "CREDIT" to "Credit Card",
        "SAVINGS" to "Savings",
        "WALLET" to "Digital Wallet"
    )

    val accountIcons = listOf("account_balance", "wallet", "credit_card", "savings", "payments", "attach_money")

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
                        text = if (initialAccount == null) "New Account" else "Edit Account",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Account Name") },
                    placeholder = { Text("e.g. Chase Checking, Cash, Apple Card") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Account Type Chips
                Text(
                    text = "Account Type",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(accountTypes) { (typeKey, label) ->
                        FilterChip(
                            selected = accountType == typeKey,
                            onClick = {
                                accountType = typeKey
                                selectedIcon = when (typeKey) {
                                    "CASH" -> "wallet"
                                    "CREDIT" -> "credit_card"
                                    "SAVINGS" -> "savings"
                                    "WALLET" -> "payments"
                                    else -> "account_balance"
                                }
                            },
                            label = { Text(label) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Starting / Base Balance
                OutlinedTextField(
                    value = initialBalanceText,
                    onValueChange = { initialBalanceText = it },
                    label = { Text("Starting Balance") },
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

                // Color Picker
                Text(
                    text = "Theme Color",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(CategoryIconHelper.availableColors) { hex ->
                        val color = CategoryIconHelper.parseColor(hex)
                        val isSelected = selectedColor.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(color)
                                .clickable { selectedColor = hex }
                                .padding(4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(Color.White, CircleShape)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (initialAccount != null) {
                        OutlinedButton(
                            onClick = {
                                viewModel.deleteAccount(initialAccount)
                                onDismiss()
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Delete")
                        }
                    }

                    val isValid = name.isNotBlank() && initialBalanceText.toDoubleOrNull() != null

                    Button(
                        onClick = {
                            val bal = initialBalanceText.toDoubleOrNull() ?: 0.0
                            val account = AccountEntity(
                                id = initialAccount?.id ?: 0,
                                uuid = initialAccount?.uuid ?: UUID.randomUUID().toString(),
                                name = name.trim(),
                                type = accountType,
                                balance = bal,
                                currency = viewModel.currency.value,
                                colorHex = selectedColor,
                                iconName = selectedIcon
                            )

                            if (initialAccount == null) {
                                viewModel.addAccount(account)
                            } else {
                                viewModel.updateAccount(account)
                            }
                            onDismiss()
                        },
                        enabled = isValid,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(if (initialAccount == null) "Create Account" else "Save Changes")
                    }
                }
            }
        }
    }
}
