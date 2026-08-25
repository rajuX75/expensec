package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.cloud.CloudinaryUploader
import com.example.ui.theme.ExpenseGreen
import com.example.ui.viewmodel.ExpenseViewModel
import kotlinx.coroutines.launch

@Composable
fun CloudinarySyncCard(
    viewModel: ExpenseViewModel,
    onShowMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val currentCloudName by viewModel.cloudinaryCloudName.collectAsState()
    val currentApiKey by viewModel.cloudinaryApiKey.collectAsState()
    val currentApiSecret by viewModel.cloudinaryApiSecret.collectAsState()
    val currentUploadPreset by viewModel.cloudinaryUploadPreset.collectAsState()

    var showConfigDialog by remember { mutableStateOf(false) }

    val config = remember(currentCloudName, currentApiKey, currentApiSecret, currentUploadPreset) {
        CloudinaryUploader.getConfig(context)
    }
    val isConfigured = config.isConfigured()

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF3448C5).copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = "Cloudinary",
                        tint = Color(0xFF3448C5),
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Cloudinary Image Hosting",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isConfigured) ExpenseGreen else Color(0xFFF59E0B))
                        )
                        Text(
                            text = if (isConfigured) {
                                "Connected (${config.cloudName})"
                            } else {
                                "Not Configured / Incomplete"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = if (isConfigured) ExpenseGreen else Color(0xFFF59E0B)
                        )
                    }
                }

                IconButton(onClick = { showConfigDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Configure Cloudinary",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Text(
                text = "Receipts, avatars, and proof photos are compressed and hosted on Cloudinary with fast CDN thumbnail transformations.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 16.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = { showConfigDialog = true },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (isConfigured) "Edit Credentials" else "Setup Cloudinary")
                }
            }
        }
    }

    if (showConfigDialog) {
        CloudinaryConfigDialog(
            initialCloudName = config.cloudName,
            initialApiKey = config.apiKey,
            initialApiSecret = config.apiSecret,
            initialUploadPreset = config.uploadPreset,
            onDismiss = { showConfigDialog = false },
            onTest = { testCloud, testKey, testSecret, testPreset ->
                viewModel.setCloudinaryConfig(testCloud, testKey, testSecret, testPreset)
                scope.launch {
                    val res = viewModel.testCloudinaryConnection()
                    res.onSuccess { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }.onFailure { err ->
                        Toast.makeText(context, "Connection failed: ${err.message}", Toast.LENGTH_LONG).show()
                    }
                }
            },
            onSave = { cloud, key, secret, preset ->
                viewModel.setCloudinaryConfig(cloud, key, secret, preset)
                showConfigDialog = false
                ImageStorageHelper.enqueueUploadWorker(context)
                onShowMessage("Cloudinary settings saved & sync queued.")
            }
        )
    }
}

@Composable
fun CloudinaryConfigDialog(
    initialCloudName: String,
    initialApiKey: String,
    initialApiSecret: String,
    initialUploadPreset: String,
    onDismiss: () -> Unit,
    onTest: (String, String, String, String) -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    var cloudName by remember { mutableStateOf(initialCloudName) }
    var apiKey by remember { mutableStateOf(initialApiKey) }
    var apiSecret by remember { mutableStateOf(initialApiSecret) }
    var uploadPreset by remember { mutableStateOf(initialUploadPreset) }
    var showSecret by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Cloudinary Settings",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Provide your Cloudinary credentials for image storage. Signed uploads use API Key & Secret; unsigned uploads use Upload Preset.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = cloudName,
                    onValueChange = { cloudName = it },
                    label = { Text("Cloud Name *") },
                    placeholder = { Text("e.g. my-cloud-name") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key (Signed)") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = apiSecret,
                    onValueChange = { apiSecret = it },
                    label = { Text("API Secret (Signed)") },
                    singleLine = true,
                    visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showSecret = !showSecret }) {
                            Icon(
                                if (showSecret) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showSecret) "Hide secret" else "Show secret"
                            )
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = uploadPreset,
                    onValueChange = { uploadPreset = it },
                    label = { Text("Upload Preset (Unsigned)") },
                    placeholder = { Text("e.g. ml_default") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(cloudName, apiKey, apiSecret, uploadPreset) },
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onTest(cloudName, apiKey, apiSecret, uploadPreset) }) {
                    Text("Test Connection")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
        shape = RoundedCornerShape(16.dp)
    )
}
