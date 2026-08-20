package com.example.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Interactive monthly category trends chart with stacked/grouped bar modes.
 * Extracted from FinancialCharts.kt for single-responsibility.
 */

@Composable
fun MonthlyCategoryTrendsChart(
    trendEntries: List<MonthlyCategoryTrendEntry>,
    categories: List<CategoryTrendMeta>,
    currencySymbol: String,
    modifier: Modifier = Modifier
) {
    var selectedMonthIndex by remember { mutableStateOf<Int?>(null) }
    var focusedCategoryName by remember { mutableStateOf<String?>(null) }
    var isStackedMode by remember { mutableStateOf(true) }

    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(trendEntries) {
        animatedProgress.snapTo(0f)
        animatedProgress.animateTo(1f, animationSpec = tween(700))
        // Default select latest month if not set
        if (selectedMonthIndex == null && trendEntries.isNotEmpty()) {
            selectedMonthIndex = trendEntries.indexOfLast { it.totalExpense > 0 }.takeIf { it >= 0 }
                ?: (trendEntries.size - 1)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .animateContentSize()
        ) {
            // Header with Title & Mode Switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Monthly Category Trends",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (focusedCategoryName != null) "Focus: $focusedCategoryName" else "Category distribution over last 6 months",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // View Mode Toggle
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Row(modifier = Modifier.padding(2.dp)) {
                        FilledTonalIconButton(
                            onClick = { isStackedMode = true },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (isStackedMode) MaterialTheme.colorScheme.primary else Color.Transparent,
                                contentColor = if (isStackedMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.size(32.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                Icons.Default.Layers,
                                contentDescription = "Stacked View",
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        FilledTonalIconButton(
                            onClick = { isStackedMode = false },
                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                containerColor = if (!isStackedMode) MaterialTheme.colorScheme.primary else Color.Transparent,
                                contentColor = if (!isStackedMode) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.size(32.dp),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(
                                Icons.Default.BarChart,
                                contentDescription = "Grouped View",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Recharts-inspired Interactive Legend & Category Filter Bar
            if (categories.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = focusedCategoryName == null,
                            onClick = { focusedCategoryName = null },
                            label = { Text("All Categories", style = MaterialTheme.typography.labelSmall) },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    items(categories.take(8)) { cat ->
                        val isFocused = focusedCategoryName == cat.name
                        val catColor = CategoryIconHelper.parseColor(cat.colorHex)

                        FilterChip(
                            selected = isFocused,
                            onClick = {
                                focusedCategoryName = if (isFocused) null else cat.name
                            },
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(catColor, CircleShape)
                                )
                            },
                            label = {
                                Text(
                                    text = cat.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            shape = RoundedCornerShape(10.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val hasData = trendEntries.any { it.totalExpense > 0 }

            if (!hasData) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.ShowChart,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No category trend data recorded yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                // Calculate scale maximum
                val maxMonthlyValue = if (focusedCategoryName != null) {
                    trendEntries.maxOfOrNull { it.categoryAmounts[focusedCategoryName] ?: 0.0 } ?: 1.0
                } else {
                    trendEntries.maxOfOrNull { it.totalExpense } ?: 1.0
                }.let { if (it > 0) it else 1.0 }

                // Main Recharts-style Visual Chart Container
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(190.dp)
                        .padding(top = 8.dp, bottom = 4.dp)
                ) {
                    // Background horizontal grid lines
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 24.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        for (step in 3 downTo 0) {
                            val gridValue = (maxMonthlyValue * (step / 3.0))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (gridValue >= 1000) "${currencySymbol}${String.format("%.1fk", gridValue / 1000)}"
                                    else "${currencySymbol}${gridValue.toInt()}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.width(36.dp),
                                    textAlign = TextAlign.End
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                    thickness = 1.dp
                                )
                            }
                        }
                    }

                    // Stacked / Grouped Bars for each Month
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(start = 42.dp, bottom = 4.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        trendEntries.forEachIndexed { index, entry ->
                            val isSelected = selectedMonthIndex == index
                            val monthTotal = entry.totalExpense

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        selectedMonthIndex = if (isSelected) null else index
                                    }
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                        else Color.Transparent
                                    )
                                    .padding(horizontal = 4.dp),
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    contentAlignment = Alignment.BottomCenter
                                ) {
                                    if (monthTotal > 0) {
                                        if (focusedCategoryName != null) {
                                            // Focused Single Category View
                                            val catAmount = entry.categoryAmounts[focusedCategoryName] ?: 0.0
                                            val catMeta = categories.find { it.name.equals(focusedCategoryName, ignoreCase = true) }
                                            val catColor = CategoryIconHelper.parseColor(catMeta?.colorHex ?: "#00875A")
                                            val heightRatio = ((catAmount / maxMonthlyValue).toFloat().coerceIn(0.02f, 1f) * animatedProgress.value).coerceIn(0.001f, 1f)

                                            Box(
                                                modifier = Modifier
                                                    .width(18.dp)
                                                    .fillMaxHeight(heightRatio)
                                                    .background(
                                                        catColor,
                                                        RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                                                    )
                                            )
                                        } else if (isStackedMode) {
                                            // Stacked Category Segments
                                            val barHeightRatio = ((monthTotal / maxMonthlyValue).toFloat().coerceIn(0.04f, 1f) * animatedProgress.value).coerceIn(0.001f, 1f)

                                            Column(
                                                modifier = Modifier
                                                    .width(20.dp)
                                                    .fillMaxHeight(barHeightRatio)
                                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)),
                                                verticalArrangement = Arrangement.Bottom
                                            ) {
                                                val sortedMonthCats = entry.categoryAmounts.entries
                                                    .filter { it.value > 0 }
                                                    .sortedBy { it.value }

                                                sortedMonthCats.forEach { (catName, amount) ->
                                                    val catMeta = categories.find { it.name.equals(catName, ignoreCase = true) }
                                                    val catColor = CategoryIconHelper.parseColor(catMeta?.colorHex ?: "#64748B")
                                                    val segmentRatio = (amount / monthTotal).toFloat().coerceIn(0.02f, 1f)

                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .weight(segmentRatio, fill = false)
                                                            .background(catColor)
                                                    )
                                                    Spacer(modifier = Modifier.height(1.dp))
                                                }
                                            }
                                        } else {
                                            // Grouped Category Bars (top 3 in month)
                                            val topInMonth = entry.categoryAmounts.entries
                                                .filter { it.value > 0 }
                                                .sortedByDescending { it.value }
                                                .take(3)

                                            Row(
                                                horizontalArrangement = Arrangement.Center,
                                                verticalAlignment = Alignment.Bottom
                                            ) {
                                                topInMonth.forEach { (catName, amount) ->
                                                    val catMeta = categories.find { it.name.equals(catName, ignoreCase = true) }
                                                    val catColor = CategoryIconHelper.parseColor(catMeta?.colorHex ?: "#64748B")
                                                    val hRatio = ((amount / maxMonthlyValue).toFloat().coerceIn(0.04f, 1f) * animatedProgress.value).coerceIn(0.001f, 1f)

                                                    Box(
                                                        modifier = Modifier
                                                            .width(6.dp)
                                                            .fillMaxHeight(hRatio)
                                                            .background(catColor, RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                                    )
                                                    Spacer(modifier = Modifier.width(2.dp))
                                                }
                                            }
                                        }
                                    } else {
                                        // Empty month indicator
                                        Box(
                                            modifier = Modifier
                                                .width(12.dp)
                                                .height(4.dp)
                                                .background(
                                                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                                                    CircleShape
                                                )
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                // Month Label
                                Text(
                                    text = entry.monthLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    ),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Recharts-inspired Interactive Tooltip / Breakdown Card
                val activeMonth = selectedMonthIndex?.let { trendEntries.getOrNull(it) }
                if (activeMonth != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        )
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${activeMonth.monthLabel} Total Spend",
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Text(
                                    text = "$currencySymbol${String.format("%,.2f", activeMonth.totalExpense)}",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            if (activeMonth.categoryAmounts.isNotEmpty()) {
                                Spacer(modifier = Modifier.height(10.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                Spacer(modifier = Modifier.height(8.dp))

                                val sortedCats = activeMonth.categoryAmounts.entries
                                    .filter { it.value > 0 }
                                    .sortedByDescending { it.value }

                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    sortedCats.forEach { (catName, amount) ->
                                        val catMeta = categories.find { it.name.equals(catName, ignoreCase = true) }
                                        val catColor = CategoryIconHelper.parseColor(catMeta?.colorHex ?: "#64748B")
                                        val percent = if (activeMonth.totalExpense > 0) ((amount / activeMonth.totalExpense) * 100) else 0.0

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .background(catColor, CircleShape)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = catName,
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                                color = MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(
                                                text = "${String.format("%.1f", percent)}%",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(end = 12.dp)
                                            )
                                            Text(
                                                text = "$currencySymbol${String.format("%,.2f", amount)}",
                                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Tap on any month column to inspect category breakdown",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
