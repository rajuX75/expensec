package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BudgetEntity
import com.example.ui.components.BudgetProgressBar
import com.example.ui.components.CategoryBadge
import com.example.ui.components.CategoryIconHelper
import com.example.ui.theme.AmberAccent
import com.example.ui.theme.AmberLight
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.ExpenseRedLight
import com.example.ui.viewmodel.ExpenseViewModel

@Composable
fun BudgetsScreen(
    viewModel: ExpenseViewModel
) {
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    val budgetStatuses by viewModel.budgetStatuses.collectAsState()
    val allCategories by viewModel.allCategories.collectAsState()

    var showAddEditDialog by remember { mutableStateOf(false) }
    var selectedBudgetToEdit by remember { mutableStateOf<BudgetEntity?>(null) }

    val overallBudgetStatus = remember(budgetStatuses) {
        budgetStatuses.find { it.budget.categoryId == null }
    }

    val categoryBudgetStatuses = remember(budgetStatuses) {
        budgetStatuses.filter { it.budget.categoryId != null }
    }

    val alertBudgets = remember(budgetStatuses) {
        budgetStatuses.filter { it.isOverBudget || it.isNearLimit }
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    selectedBudgetToEdit = null
                    showAddEditDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Budget Limit")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
        ) {
            // Budget Alerts Banner if any are triggered
            if (alertBudgets.isNotEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (alertBudgets.any { it.isOverBudget }) ExpenseRedLight else AmberLight
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Budget Alert",
                                tint = if (alertBudgets.any { it.isOverBudget }) ExpenseRed else Color(0xFFB45309),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                val overCount = alertBudgets.count { it.isOverBudget }
                                val nearCount = alertBudgets.count { it.isNearLimit }
                                Text(
                                    text = if (overCount > 0) "$overCount budget limit exceeded!" else "$nearCount budget(s) nearing limit",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = if (overCount > 0) ExpenseRed else Color(0xFF92400E)
                                )
                                Text(
                                    text = "Monitor your expenses closely to stay on track",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (overCount > 0) ExpenseRed.copy(alpha = 0.8f) else Color(0xFF92400E).copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }

            // Overall Budget Card
            if (overallBudgetStatus != null) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable {
                                selectedBudgetToEdit = overallBudgetStatus.budget
                                showAddEditDialog = true
                            },
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
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(40.dp)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            Icons.Default.AccountBalanceWallet,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = "Overall Monthly Limit",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Limit: $currencySymbol${String.format("%,.2f", overallBudgetStatus.budget.amountLimit)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                IconButton(onClick = {
                                    selectedBudgetToEdit = overallBudgetStatus.budget
                                    showAddEditDialog = true
                                }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(20.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Spent: $currencySymbol${String.format("%,.2f", overallBudgetStatus.spentAmount)}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${overallBudgetStatus.percentage.toInt()}%",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = when {
                                        overallBudgetStatus.isOverBudget -> ExpenseRed
                                        overallBudgetStatus.isNearLimit -> AmberAccent
                                        else -> MaterialTheme.colorScheme.primary
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            BudgetProgressBar(
                                spentAmount = overallBudgetStatus.spentAmount,
                                limitAmount = overallBudgetStatus.budget.amountLimit,
                                currencySymbol = currencySymbol,
                                alertThresholdPercent = overallBudgetStatus.budget.alertThresholdPercent
                            )
                        }
                    }
                }
            }

            // Category Budgets Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Category Budgets (${categoryBudgetStatuses.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    TextButton(onClick = {
                        selectedBudgetToEdit = null
                        showAddEditDialog = true
                    }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Limit")
                    }
                }
            }

            if (categoryBudgetStatuses.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Category,
                                contentDescription = null,
                                modifier = Modifier.size(44.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No category budgets set yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(categoryBudgetStatuses) { status ->
                    val catEntity = allCategories.find { it.id == status.budget.categoryId }
                    val iconName = catEntity?.iconName ?: "category"
                    val colorHex = catEntity?.colorHex ?: "#64748B"

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable {
                                selectedBudgetToEdit = status.budget
                                showAddEditDialog = true
                            },
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CategoryBadge(
                                        iconName = iconName,
                                        colorHex = colorHex,
                                        size = 38.dp,
                                        iconSize = 20.dp
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = status.budget.categoryName ?: "Category",
                                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "$currencySymbol${String.format("%,.0f", status.spentAmount)} of $currencySymbol${String.format("%,.0f", status.budget.amountLimit)}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (status.isOverBudget) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = ExpenseRedLight
                                    ) {
                                        Text(
                                            text = "OVER",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = ExpenseRed,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            BudgetProgressBar(
                                spentAmount = status.spentAmount,
                                limitAmount = status.budget.amountLimit,
                                currencySymbol = currencySymbol,
                                alertThresholdPercent = status.budget.alertThresholdPercent
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddEditDialog) {
        AddEditBudgetDialog(
            viewModel = viewModel,
            initialBudget = selectedBudgetToEdit,
            onDismiss = { showAddEditDialog = false }
        )
    }
}
