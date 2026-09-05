package com.example.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.TransactionEntity
import com.example.ui.theme.ShapeTokens
import com.example.ui.theme.cardBorderStroke
import com.example.ui.theme.financialColors
import com.example.ui.theme.tabular
import java.text.SimpleDateFormat
import java.util.*

/**
 * Skill: repeated-component-alignment & modular-scale-typography (Dembrandt Stage 2 & 4).
 *
 * Implements a strict fixed slot model across repeated transaction rows:
 *  - Slot 1: Category Icon / Avatar Badge (pinned 44x44dp)
 *  - Slot 2: Title / Merchant / Note + formatted date & account (clamp + recover)
 *  - Slot 3: Amount with tabular numerals (tnum) and right-aligned category label
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionItemCard(
    transaction: TransactionEntity,
    currencySymbol: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val dateFormat = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
    val formattedDate = dateFormat.format(Date(transaction.date))

    val isIncome = transaction.type == com.example.data.model.TransactionType.INCOME
    val isTransfer = transaction.type == com.example.data.model.TransactionType.TRANSFER

    val financialColors = MaterialTheme.financialColors
    val amountColor = when {
        isIncome -> financialColors.income
        isTransfer -> financialColors.transfer
        else -> financialColors.expense
    }

    val amountPrefix = when {
        isIncome -> "+"
        isTransfer -> ""
        else -> "-"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(ShapeTokens.large)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = ShapeTokens.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = cardBorderStroke(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Slot 1: Category Icon Badge (Pinned Dimensions)
            CategoryBadge(
                iconName = if (isTransfer) "swap_horiz" else transaction.categoryIcon,
                colorHex = if (isTransfer) "#3B82F6" else transaction.categoryColorHex,
                size = 44.dp,
                iconSize = 22.dp
            )

            Spacer(modifier = Modifier.width(14.dp))

            // Slot 2: Transaction Details (Clamped text)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val mainTitle = when {
                        transaction.merchant.isNotBlank() -> transaction.merchant
                        transaction.note.isNotBlank() -> transaction.note
                        else -> transaction.categoryName
                    }
                    Text(
                        text = mainTitle,
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (transaction.receiptUri != null && transaction.receiptUri.isNotBlank()) {
                        Icon(
                            imageVector = Icons.Default.Receipt,
                            contentDescription = "Has receipt attachment",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (transaction.isRecurring) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = "Recurring bill",
                            modifier = Modifier.size(16.dp),
                            tint = financialColors.warning
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val accountDisplay = if (isTransfer && transaction.toAccountName != null) {
                        "${transaction.accountName} → ${transaction.toAccountName}"
                    } else {
                        transaction.accountName
                    }

                    Text(
                        text = accountDisplay,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Tag Chips if present
                if (transaction.tags.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        transaction.tags.split(",").take(2).forEach { tag ->
                            val cleanTag = tag.trim()
                            if (cleanTag.isNotBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                                    shape = ShapeTokens.small
                                ) {
                                    Text(
                                        text = "#$cleanTag",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Slot 3: Amount Column (Tabular Numerals, Right Aligned)
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "$amountPrefix$currencySymbol${String.format(Locale.US, "%,.2f", transaction.amount)}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold).tabular(),
                    color = amountColor
                )

                Text(
                    text = if (isTransfer) "Transfer" else transaction.categoryName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
