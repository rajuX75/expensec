package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.components.CategoryBadge
import com.example.ui.components.CategoryDonutChart
import com.example.ui.components.CategoryIconHelper
import com.example.ui.components.MonthlyCategoryTrendsChart
import com.example.ui.components.SpendingTrendBarChart
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.viewmodel.ExpenseViewModel

@Composable
fun AnalyticsScreen(
    viewModel: ExpenseViewModel
) {
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    val analyticsPeriod by viewModel.analyticsPeriod.collectAsState()
    val categorySpendingData by viewModel.categorySpendingData.collectAsState()
    val monthlyTrendsData by viewModel.monthlyTrendsData.collectAsState()
    val monthlyCategoryTrendsState by viewModel.monthlyCategoryTrendsData.collectAsState()
    val (monthlyCategoryTrends, categoryTrendMetas) = monthlyCategoryTrendsState
    val topMerchants by viewModel.topMerchants.collectAsState()
    val summary by viewModel.financialSummary.collectAsState()

    var showExportDialog by remember { mutableStateOf(false) }

    val totalPeriodSpent = remember(categorySpendingData) {
        categorySpendingData.sumOf { it.amount }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
    ) {
        // Time Period Filter Bar & Export Action
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    val periods = listOf(
                        "THIS_MONTH" to "This Month",
                        "LAST_MONTH" to "Last Month",
                        "THIS_YEAR" to "This Year",
                        "ALL" to "All Time"
                    )
                    items(periods) { (key, label) ->
                        FilterChip(
                            selected = analyticsPeriod == key,
                            onClick = { viewModel.setAnalyticsPeriod(key) },
                            label = { Text(label) }
                        )
                    }
                }

                FilledTonalIconButton(
                    onClick = { showExportDialog = true },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.FileDownload, contentDescription = "Export Report")
                }
            }
        }

        // Summary Metric Cards
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(28.dp).background(ExpenseRed.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.TrendingDown, contentDescription = null, tint = ExpenseRed, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Total Spent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "$currencySymbol${String.format("%,.2f", totalPeriodSpent)}",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier.size(28.dp).background(IncomeGreen.copy(alpha = 0.15f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.TrendingUp, contentDescription = null, tint = IncomeGreen, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Net Savings (Mo)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "$currencySymbol${String.format("%,.2f", summary.netSavings)}",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (summary.netSavings >= 0) IncomeGreen else ExpenseRed
                        )
                    }
                }
            }
        }

        // Category Spending Donut Chart
        item {
            CategoryDonutChart(
                categories = categorySpendingData,
                currencySymbol = currencySymbol,
                totalExpense = totalPeriodSpent
            )
        }

        // Monthly Category Trends Chart (Recharts-style multi-category stacked/grouped trend)
        item {
            MonthlyCategoryTrendsChart(
                trendEntries = monthlyCategoryTrends,
                categories = categoryTrendMetas,
                currencySymbol = currencySymbol
            )
        }

        // Monthly Comparative Trend Chart
        item {
            SpendingTrendBarChart(
                entries = monthlyTrendsData,
                currencySymbol = currencySymbol
            )
        }

        // Top Spending Merchants Ranking
        if (topMerchants.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Text(
                            text = "Top Spending Merchants",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        val highestMerchantAmt = topMerchants.maxOfOrNull { it.amount } ?: 1.0

                        topMerchants.forEachIndexed { index, m ->
                            val progressRatio = (m.amount / highestMerchantAmt).toFloat().coerceIn(0f, 1f)

                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.surfaceVariant,
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = "${index + 1}",
                                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold)
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(
                                            text = m.merchant,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Text(
                                        text = "$currencySymbol${String.format("%,.2f", m.amount)}",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(progressRatio)
                                            .fillMaxHeight()
                                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(3.dp))
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showExportDialog) {
        ExportDataDialog(
            viewModel = viewModel,
            onDismiss = { showExportDialog = false }
        )
    }
}
