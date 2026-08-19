package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.ui.viewmodel.ExpenseViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ExportDataDialog(
    viewModel: ExpenseViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val transactions by viewModel.allTransactions.collectAsState()
    val accounts by viewModel.allAccounts.collectAsState()
    val currencySymbol by viewModel.currencySymbol.collectAsState()

    var exportFormat by remember { mutableStateOf("CSV") } // CSV or JSON

    val formattedData = remember(transactions, exportFormat) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        if (exportFormat == "CSV") {
            val sb = StringBuilder()
            sb.append("ID,Type,Amount,Category,Account,Date,Merchant,Note,Tags,PaymentMethod\n")
            transactions.forEach { tx ->
                val dateStr = dateFormat.format(Date(tx.date))
                val sanitizedMerchant = tx.merchant.replace(",", " ")
                val sanitizedNote = tx.note.replace(",", " ")
                val sanitizedTags = tx.tags.replace(",", ";")
                sb.append("${tx.id},${tx.type},${tx.amount},${tx.categoryName},${tx.accountName},$dateStr,$sanitizedMerchant,$sanitizedNote,$sanitizedTags,${tx.paymentMethod}\n")
            }
            sb.toString()
        } else {
            // Summary JSON format
            val sb = StringBuilder()
            sb.append("[\n")
            transactions.forEachIndexed { index, tx ->
                val dateStr = dateFormat.format(Date(tx.date))
                sb.append("  {\n")
                sb.append("    \"id\": ${tx.id},\n")
                sb.append("    \"type\": \"${tx.type}\",\n")
                sb.append("    \"amount\": ${tx.amount},\n")
                sb.append("    \"category\": \"${tx.categoryName}\",\n")
                sb.append("    \"account\": \"${tx.accountName}\",\n")
                sb.append("    \"merchant\": \"${tx.merchant.replace("\"", "\\\"")}\",\n")
                sb.append("    \"date\": \"$dateStr\"\n")
                sb.append("  }${if (index < transactions.size - 1) "," else ""}\n")
            }
            sb.append("]")
            sb.toString()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Export Transactions",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = exportFormat == "CSV",
                        onClick = { exportFormat = "CSV" },
                        label = { Text("CSV Format") }
                    )
                    FilterChip(
                        selected = exportFormat == "JSON",
                        onClick = { exportFormat = "JSON" },
                        label = { Text("JSON Format") }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Preview (${transactions.size} records)",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Scrollable data preview box
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = formattedData.ifBlank { "No records to export" },
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Expense Data", formattedData)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy")
                    }

                    Button(
                        onClick = {
                            val sendIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_TEXT, formattedData)
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Share Expense Data"))
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share")
                    }
                }
            }
        }
    }
}
