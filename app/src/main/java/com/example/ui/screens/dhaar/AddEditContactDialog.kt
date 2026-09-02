package com.example.ui.screens.dhaar

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
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
import com.example.data.model.Contact
import com.example.ui.viewmodel.ExpenseViewModel
import kotlinx.coroutines.launch

@Composable
fun AddEditContactDialog(
    viewModel: ExpenseViewModel,
    contactToEdit: Contact? = null,
    onDismiss: () -> Unit,
    onContactSaved: (Long) -> Unit = {}
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(contactToEdit?.name ?: "") }
    var phoneNumber by remember { mutableStateOf(contactToEdit?.phoneNumber ?: "") }
    var photoUri by remember { mutableStateOf(contactToEdit?.photoUri) }
    var isUploadingPhoto by remember { mutableStateOf(false) }
    var nameError by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    // Image Picker Launcher for Contact Avatar
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            isUploadingPhoto = true
            photoUri = it.toString()
            scope.launch {
                val persistentUri = com.example.ui.components.ImageStorageHelper.saveImageLocally(context, it, "contacts")
                photoUri = persistentUri ?: it.toString()
                isUploadingPhoto = false
            }
        }
    }

    // Safe Phone Contact Picker using CommonDataKinds.Phone
    val phoneContactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val contactUri = result.data?.data
            if (contactUri != null) {
                try {
                    val projection = arrayOf(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.PHOTO_URI
                    )
                    context.contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                            val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                            val photoIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.PHOTO_URI)

                            if (nameIdx >= 0) {
                                val pickedName = cursor.getString(nameIdx)
                                if (!pickedName.isNullOrBlank()) {
                                    name = pickedName
                                    nameError = false
                                }
                            }
                            if (numberIdx >= 0) {
                                val pickedNumber = cursor.getString(numberIdx)
                                if (!pickedNumber.isNullOrBlank()) {
                                    phoneNumber = pickedNumber
                                }
                            }
                            if (photoIdx >= 0) {
                                val pickedPhoto = cursor.getString(photoIdx)
                                if (!pickedPhoto.isNullOrBlank()) {
                                    scope.launch {
                                        val saved = com.example.ui.components.ImageStorageHelper.saveImageLocally(context, Uri.parse(pickedPhoto), "contacts")
                                        photoUri = saved ?: pickedPhoto
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    val contactInitial = if (name.isNotBlank()) name.first().uppercaseChar().toString() else "?"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (contactToEdit == null) "Add New Contact" else "Edit Contact",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Avatar with upload button
                Box(contentAlignment = Alignment.BottomEnd) {
                    if (!photoUri.isNullOrBlank()) {
                        AsyncImage(
                            model = com.example.data.cloud.CloudinaryUrl.avatar(photoUri),
                            contentDescription = "Contact Photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .clickable { imagePickerLauncher.launch("image/*") }
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable { imagePickerLauncher.launch("image/*") },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = contactInitial,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (isUploadingPhoto) {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
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
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .clickable { imagePickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.PhotoCamera,
                            contentDescription = "Pick photo",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                // Photo actions: Upload / Remove
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { imagePickerLauncher.launch("image/*") }) {
                        Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (photoUri.isNullOrBlank()) "Add Photo" else "Change Photo", fontSize = 12.sp)
                    }
                    if (!photoUri.isNullOrBlank()) {
                        TextButton(onClick = { photoUri = null }) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Remove", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                // Import from phone button
                OutlinedButton(
                    onClick = {
                        try {
                            val pickIntent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
                            phoneContactPickerLauncher.launch(pickIntent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Contacts, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Auto-fill from Phone Contacts", fontSize = 13.sp)
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        if (it.isNotBlank()) nameError = false
                    },
                    label = { Text("Contact Name *") },
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null)
                    },
                    isError = nameError,
                    supportingText = if (nameError) {
                        { Text("Name is required", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = phoneNumber,
                    onValueChange = { phoneNumber = it },
                    label = { Text("Phone Number (Optional)") },
                    leadingIcon = {
                        Icon(Icons.Default.Phone, contentDescription = null)
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        nameError = true
                        return@Button
                    }

                    if (contactToEdit == null) {
                        val newContact = Contact(
                            name = name.trim(),
                            phoneNumber = phoneNumber.trim().ifBlank { null },
                            photoUri = photoUri
                        )
                        viewModel.addContact(newContact) { newId ->
                            onContactSaved(newId)
                            onDismiss()
                        }
                    } else {
                        val updated = contactToEdit.copy(
                            name = name.trim(),
                            phoneNumber = phoneNumber.trim().ifBlank { null },
                            photoUri = photoUri
                        )
                        viewModel.updateContact(updated)
                        onContactSaved(updated.id)
                        onDismiss()
                    }
                },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(if (contactToEdit == null) "Save" else "Update")
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
