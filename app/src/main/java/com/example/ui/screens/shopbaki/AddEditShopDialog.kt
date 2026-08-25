package com.example.ui.screens.shopbaki

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Shop
import com.example.ui.components.ImageStorageHelper
import com.example.ui.viewmodel.ExpenseViewModel
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditShopDialog(
    viewModel: ExpenseViewModel,
    shop: Shop? = null,
    onDismiss: () -> Unit,
    onShopSaved: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf(shop?.name ?: "") }
    var phone by remember { mutableStateOf(shop?.phoneNumber ?: "") }
    var address by remember { mutableStateOf(shop?.address ?: "") }
    var note by remember { mutableStateOf(shop?.note ?: "") }
    var email by remember { mutableStateOf(shop?.email ?: "") }
    var businessId by remember { mutableStateOf(shop?.businessId ?: "") }
    var category by remember { mutableStateOf(shop?.category ?: "") }
    var isVerified by remember { mutableStateOf(shop?.isVerified ?: false) }
    
    var profilePictureUri by remember { mutableStateOf(shop?.profilePictureUri) }
    var coverImageUri by remember { mutableStateOf(shop?.coverImageUri) }
    var isUploadingProfile by remember { mutableStateOf(false) }
    var isUploadingCover by remember { mutableStateOf(false) }

    var isNameError by remember { mutableStateOf(false) }

    val profilePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            isUploadingProfile = true
            profilePictureUri = it.toString()
            scope.launch {
                val saved = ImageStorageHelper.saveImageLocally(context, it, "shops")
                profilePictureUri = saved ?: it.toString()
                isUploadingProfile = false
            }
        }
    }

    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            isUploadingCover = true
            coverImageUri = it.toString()
            scope.launch {
                val saved = ImageStorageHelper.saveImageLocally(context, it, "shops")
                coverImageUri = saved ?: it.toString()
                isUploadingCover = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (shop == null) "Add New Shop" else "Edit Shop") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Cover Image
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { coverPicker.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    if (!coverImageUri.isNullOrBlank()) {
                        AsyncImage(
                            model = com.example.data.cloud.CloudinaryUrl.preview(coverImageUri),
                            contentDescription = "Cover Image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Add Cover Image", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    if (isUploadingCover) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.4f)),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = Color.White,
                                strokeWidth = 3.dp
                            )
                        }
                    }
                }

                // Profile Image
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(contentAlignment = Alignment.BottomEnd) {
                        if (!profilePictureUri.isNullOrBlank()) {
                            AsyncImage(
                                model = com.example.data.cloud.CloudinaryUrl.avatar(profilePictureUri),
                                contentDescription = "Profile Picture",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .clickable { profilePicker.launch("image/*") }
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .clickable { profilePicker.launch("image/*") },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Storefront,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }

                        if (isUploadingProfile) {
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.4f)),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(28.dp),
                                    color = Color.White,
                                    strokeWidth = 2.5.dp
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable { profilePicker.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PhotoCamera,
                                contentDescription = "Pick photo",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Fields
                OutlinedTextField(
                    value = name,
                    onValueChange = { 
                        name = it
                        isNameError = it.isBlank()
                    },
                    label = { Text("Shop Name *") },
                    singleLine = true,
                    isError = isNameError,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isVerified,
                        onCheckedChange = { isVerified = it }
                    )
                    Text("Verified Shop", style = MaterialTheme.typography.bodyMedium)
                }
                
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("Category (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone Number (Optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email (Optional)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address (Optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = businessId,
                    onValueChange = { businessId = it },
                    label = { Text("Business ID / GST (Optional)") },
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
                        note = note.trim().takeIf { it.isNotEmpty() },
                        email = email.trim().takeIf { it.isNotEmpty() },
                        businessId = businessId.trim().takeIf { it.isNotEmpty() },
                        category = category.trim().takeIf { it.isNotEmpty() },
                        isVerified = isVerified,
                        profilePictureUri = profilePictureUri,
                        coverImageUri = coverImageUri
                    ) ?: Shop(
                        name = name.trim(),
                        phoneNumber = phone.trim().takeIf { it.isNotEmpty() },
                        address = address.trim().takeIf { it.isNotEmpty() },
                        note = note.trim().takeIf { it.isNotEmpty() },
                        email = email.trim().takeIf { it.isNotEmpty() },
                        businessId = businessId.trim().takeIf { it.isNotEmpty() },
                        category = category.trim().takeIf { it.isNotEmpty() },
                        isVerified = isVerified,
                        profilePictureUri = profilePictureUri,
                        coverImageUri = coverImageUri
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
