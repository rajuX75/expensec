package com.example.ui.screens.shopbaki

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.data.model.Shop
import com.example.data.model.ShopLedgerEntry
import com.example.data.model.ShopProduct
import com.example.ui.viewmodel.ExpenseViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBakiEntrySheet(
    shop: Shop,
    viewModel: ExpenseViewModel,
    onDismiss: () -> Unit
) {
    val activeProducts by viewModel.activeShopProducts.collectAsState()
    
    var selectedProduct by remember { mutableStateOf<ShopProduct?>(null) }
    var quantityStr by remember { mutableStateOf("1") }
    var unitPriceStr by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    
    var showProductDropdown by remember { mutableStateOf(false) }
    var showAddProductDialog by remember { mutableStateOf(false) }
    
    val totalAmount = remember(quantityStr, unitPriceStr) {
        val q = quantityStr.toDoubleOrNull() ?: 0.0
        val p = unitPriceStr.toDoubleOrNull() ?: 0.0
        q * p
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Add Purchase at ${shop.name}",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Product Selection
            ExposedDropdownMenuBox(
                expanded = showProductDropdown,
                onExpandedChange = { showProductDropdown = it }
            ) {
                OutlinedTextField(
                    value = selectedProduct?.name ?: "Select Product",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Product *") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showProductDropdown) },
                    modifier = Modifier.fillMaxWidth().menuAnchor()
                )
                
                ExposedDropdownMenu(
                    expanded = showProductDropdown,
                    onDismissRequest = { showProductDropdown = false }
                ) {
                    activeProducts.forEach { prod ->
                        DropdownMenuItem(
                            text = { Text("${prod.name} (Default: ${prod.defaultPrice})") },
                            onClick = {
                                selectedProduct = prod
                                unitPriceStr = prod.defaultPrice.toString()
                                showProductDropdown = false
                            }
                        )
                    }
                    Divider()
                    DropdownMenuItem(
                        text = { Text("+ Add New Product", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) },
                        onClick = {
                            showProductDropdown = false
                            showAddProductDialog = true
                        }
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = quantityStr,
                    onValueChange = { quantityStr = it },
                    label = { Text("Qty") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
                
                OutlinedTextField(
                    value = unitPriceStr,
                    onValueChange = { unitPriceStr = it },
                    label = { Text("Unit Price") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f)
                )
            }
            
            Text(
                text = "Total: ${String.format("%,.2f", totalAmount)}",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (Optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    if (selectedProduct == null || totalAmount <= 0) return@Button
                    val entry = ShopLedgerEntry(
                        shopId = shop.id,
                        type = "PURCHASE",
                        productId = selectedProduct?.id,
                        quantity = quantityStr.toDoubleOrNull() ?: 1.0,
                        unitPriceAtPurchase = unitPriceStr.toDoubleOrNull() ?: 0.0,
                        amount = totalAmount,
                        note = note.takeIf { it.isNotBlank() }
                    )
                    viewModel.addShopLedgerEntry(entry)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedProduct != null && totalAmount > 0
            ) {
                Text("Save Purchase")
            }
        }
    }
    
    if (showAddProductDialog) {
        AddEditShopProductDialog(
            viewModel = viewModel,
            onDismiss = { showAddProductDialog = false },
            onProductSaved = { prod ->
                selectedProduct = prod
                unitPriceStr = prod.defaultPrice.toString()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddShopPaymentSheet(
    shop: Shop,
    viewModel: ExpenseViewModel,
    onDismiss: () -> Unit
) {
    var amountStr by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Record Payment to ${shop.name}",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            OutlinedTextField(
                value = amountStr,
                onValueChange = { amountStr = it },
                label = { Text("Paid Amount *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (Optional)") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val amount = amountStr.toDoubleOrNull() ?: return@Button
                    if (amount <= 0) return@Button
                    val entry = ShopLedgerEntry(
                        shopId = shop.id,
                        type = "PAYMENT",
                        amount = amount,
                        note = note.takeIf { it.isNotBlank() }
                    )
                    viewModel.addShopLedgerEntry(entry)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = amountStr.toDoubleOrNull()?.let { it > 0 } == true
            ) {
                Text("Save Payment")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditShopProductDialog(
    viewModel: ExpenseViewModel,
    product: ShopProduct? = null,
    onDismiss: () -> Unit,
    onProductSaved: (ShopProduct) -> Unit = {}
) {
    var name by remember { mutableStateOf(product?.name ?: "") }
    var defaultUnit by remember { mutableStateOf(product?.defaultUnit ?: "") }
    var defaultPriceStr by remember { mutableStateOf(product?.defaultPrice?.toString() ?: "") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (product == null) "New Product" else "Edit Product") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Product Name *") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = defaultUnit,
                    onValueChange = { defaultUnit = it },
                    label = { Text("Unit (e.g. kg, pcs)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = defaultPriceStr,
                    onValueChange = { defaultPriceStr = it },
                    label = { Text("Default Price *") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val price = defaultPriceStr.toDoubleOrNull() ?: 0.0
                    if (name.isNotBlank() && price > 0) {
                        val prod = product?.copy(
                            name = name,
                            defaultUnit = defaultUnit.takeIf { it.isNotBlank() },
                            defaultPrice = price
                        ) ?: ShopProduct(
                            name = name,
                            defaultUnit = defaultUnit.takeIf { it.isNotBlank() },
                            defaultPrice = price
                        )
                        if (product == null) {
                            viewModel.addShopProduct(prod) { id ->
                                onProductSaved(prod.copy(id = id))
                            }
                        } else {
                            viewModel.updateShopProduct(prod)
                            onProductSaved(prod)
                        }
                        onDismiss()
                    }
                }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
