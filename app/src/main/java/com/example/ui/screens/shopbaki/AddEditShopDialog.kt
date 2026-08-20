package com.example.ui.screens.shopbaki

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.data.model.Shop
import com.example.ui.viewmodel.ExpenseViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditShopDialog(
    viewModel: ExpenseViewModel,
    shop: Shop? = null,
    onDismiss: () -> Unit,
    onShopSaved: (Long) -> Unit = {}
) {
    var name by remember { mutableStateOf(shop?.name ?: "") }
    var phone by remember { mutableStateOf(shop?.phoneNumber ?: "") }
    var address by remember { mutableStateOf(shop?.address ?: "") }
    var note by remember { mutableStateOf(shop?.note ?: "") }

    var isNameError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (shop == null) "Add New Shop" else "Edit Shop") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { 
                        name = it
                        isNameError = it.isBlank()
                    },
                    label = { Text("Shop Name *") },
                    singleLine = true,
                    isError = isNameError,
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        isNameError = true
                        return@Button
                    }
                    val newShop = shop?.copy(
                        name = name.trim(),
                        phoneNumber = phone.trim().takeIf { it.isNotEmpty() },
                        address = address.trim().takeIf { it.isNotEmpty() },
                        note = note.trim().takeIf { it.isNotEmpty() }
                    ) ?: Shop(
                        name = name.trim(),
                        phoneNumber = phone.trim().takeIf { it.isNotEmpty() },
                        address = address.trim().takeIf { it.isNotEmpty() },
                        note = note.trim().takeIf { it.isNotEmpty() }
                    )
                    
                    if (shop == null) {
                        viewModel.addShop(newShop, onCreated = { id ->
                            onShopSaved(id)
                            onDismiss()
                        })
                    } else {
                        viewModel.updateShop(newShop)
                        onShopSaved(newShop.id)
                        onDismiss()
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
