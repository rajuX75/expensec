package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen

/**
 * Donut chart, income-vs-spending bar chart, and budget progress bar.
 * Extracted from FinancialCharts.kt for single-responsibility.
 */

@Composable
fun CategoryDonutChart(
    categories: List<ChartCategoryData>,
    currencySymbol: String,
    totalExpense: Double,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf<ChartCategoryData?>(null) }
    val animatedProgress = remember { Animatable(0f) }

    LaunchedEffect(categories) {
        animatedProgress.snapTo(0f)
        animatedProgress.animateTo(1f, animationSpec = tween(700))
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
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Spending by Category",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (categories.isEmpty() || totalExpense <= 0.0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No expense data for this period",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokeWidth = 32.dp.toPx()
                        val diameter = size.minDimension - strokeWidth
                        val radius = diameter / 2f
                        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                        val arcSize = Size(diameter, diameter)

                        var startAngle = -90f

                        categories.forEach { item ->
                            val sweepAngle = (item.percentage / 100f) * 360f * animatedProgress.value
                            val color = CategoryIconHelper.parseColor(item.colorHex)
                            val isSelected = selectedCategory == item

                            drawArc(
                                color = color,
                                startAngle = startAngle,
                                sweepAngle = if (sweepAngle > 0.5f) sweepAngle - 1.5f else sweepAngle,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(
                                    width = if (isSelected) strokeWidth + 6.dp.toPx() else strokeWidth,
                                    cap = StrokeCap.Round
                                )
                            )
                            startAngle += sweepAngle
                        }
                    }

                    // Center Content inside donut
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val displayTitle = selectedCategory?.name ?: "Total Spent"
                        val displayAmount = selectedCategory?.amount ?: totalExpense

                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$currencySymbol${String.format("%,.2f", displayAmount)}",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (selectedCategory != null) {
                            Text(
                                text = "${String.format("%.1f", selectedCategory!!.percentage)}%",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = CategoryIconHelper.parseColor(selectedCategory!!.colorHex)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Interactive Category Legends
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.take(6).forEach { cat ->
                        val isSelected = selectedCategory == cat
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedCategory = if (isSelected) null else cat
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(CategoryIconHelper.parseColor(cat.colorHex), CircleShape)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = cat.name,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${String.format("%.1f", cat.percentage)}%",
                                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(end = 12.dp)
                                )
                                Text(
                                    text = "$currencySymbol${String.format("%,.2f", cat.amount)}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SpendingTrendBarChart(
    entries: List<BarChartEntry>,
    currencySymbol: String,
    modifier: Modifier = Modifier
) {
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
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Income vs Spending",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(10.dp).background(IncomeGreen, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Income", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(modifier = Modifier.size(10.dp).background(ExpenseRed, CircleShape))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Expense", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No trend data available",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                val maxVal = entries.maxOfOrNull { maxOf(it.income, it.expense) } ?: 1.0
                val safeMax = if (maxVal > 0) maxVal else 1.0

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    entries.forEach { entry ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .height(120.dp)
                                    .padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.Bottom
                            ) {
                                // Income bar
                                val incomeHeightRatio = (entry.income / safeMax).toFloat().coerceIn(0.04f, 1f)
                                Box(
                                    modifier = Modifier
                                        .width(14.dp)
                                        .fillMaxHeight(incomeHeightRatio)
                                        .background(IncomeGreen, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                )

                                Spacer(modifier = Modifier.width(4.dp))

                                // Expense bar
                                val expenseHeightRatio = (entry.expense / safeMax).toFloat().coerceIn(0.04f, 1f)
                                Box(
                                    modifier = Modifier
                                        .width(14.dp)
                                        .fillMaxHeight(expenseHeightRatio)
                                        .background(ExpenseRed, RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = entry.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BudgetProgressBar(
    spentAmount: Double,
    limitAmount: Double,
    currencySymbol: String,
    alertThresholdPercent: Int = 80,
    modifier: Modifier = Modifier
) {
    val progress = if (limitAmount > 0) (spentAmount / limitAmount).toFloat() else 0f
    val clampedProgress = progress.coerceIn(0f, 1f)
    val percentInt = (progress * 100).toInt()

    val progressColor = when {
        progress >= 1.0f -> ExpenseRed
        percentInt >= alertThresholdPercent -> Color(0xFFF59E0B) // Amber warning
        else -> MaterialTheme.colorScheme.primary
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$percentInt% spent",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = progressColor
            )
            val remaining = limitAmount - spentAmount
            Text(
                text = if (remaining >= 0) "$currencySymbol${String.format("%,.2f", remaining)} left"
                else "$currencySymbol${String.format("%,.2f", -remaining)} over budget",
                style = MaterialTheme.typography.labelMedium,
                color = if (remaining >= 0) MaterialTheme.colorScheme.onSurfaceVariant else ExpenseRed
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(5.dp))
        ) {
            if (clampedProgress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(clampedProgress)
                        .fillMaxHeight()
                        .background(progressColor, RoundedCornerShape(5.dp))
                )
            }
        }
    }
}
