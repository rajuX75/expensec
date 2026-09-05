package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.BudgetEntity
import com.example.ui.components.BudgetProgressBar
import com.example.ui.components.CategoryBadge
import com.example.ui.components.CategoryIconHelper
import com.example.ui.theme.*
import com.example.ui.viewmodel.ExpenseViewModel
import java.util.Locale

/**
 * Skill: component-family-consistency & status-colors-and-errors (Dembrandt Stage 2 & 4).
 *
 * Replaces hardcoded light pastels with theme-aware container tokens, standardizes FAB
 * shape to ShapeTokens.large, pairs cards with cardBorderStroke, and applies tabular
 * numerals to budget limits and spent figures.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(
    viewModel: ExpenseViewModel,
    onNavigateBack: () -> Unit = {}
) {
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    val budgetStatuses by viewModel.budgetStatuses.collectAsState()
    val allCategories by viewModel.allCategories.collectAsState()
    val financialColors = MaterialTheme.financialColors

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
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Monthly Budgets",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    selectedBudgetToEdit = null
                    showAddEditDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = ShapeTokens.large
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
            // Budget Alerts Banner if any are triggered (Theme-Aware Containers)
            if (alertBudgets.isNotEmpty()) {
                val hasOver = alertBudgets.any { it.isOverBudget }
                val bannerBg = if (hasOver) financialColors.expenseContainer else financialColors.warningContainer
                val bannerBorder = if (hasOver) financialColors.expense else financialColors.warning
                val bannerTextColor = if (hasOver) financialColors.onExpenseContainer else financialColors.onWarningContainer
                val bannerIconTint = if (hasOver) financialColors.expense else financialColors.warning

                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = ShapeTokens.large,
                        colors = CardDefaults.cardColors(
                            containerColor = bannerBg,
                            contentColor = bannerTextColor
                        ),
                        border = cardBorderStroke(bannerBorder.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Budget Alert",
                                tint = bannerIconTint,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                val overCount = alertBudgets.count { it.isOverBudget }
                                val nearCount = alertBudgets.count { it.isNearLimit }
                                Text(
                                    text = if (overCount > 0) "$overCount budget limit exceeded!" else "$nearCount budget(s) nearing limit",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                    color = bannerTextColor
                                )
                                Text(
                                    text = "Monitor your expenses closely to stay on track",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = bannerTextColor.copy(alpha = 0.85f)
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
                            .clip(ShapeTokens.large)
                            .clickable {
                                selectedBudgetToEdit = overallBudgetStatus.budget
                                showAddEditDialog = true
                            },
                        shape = ShapeTokens.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = cardBorderStroke(),
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
                                            text = "Limit: $currencySymbol${String.format(Locale.US, "%,.2f", overallBudgetStatus.budget.amountLimit)}",
                                            style = MaterialTheme.typography.labelSmall.tabular(),
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
                                    text = "Spent: $currencySymbol${String.format(Locale.US, "%,.2f", overallBudgetStatus.spentAmount)}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold).tabular(),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${overallBudgetStatus.percentage.toInt()}%",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold).tabular(),
                                    color = when {
                                        overallBudgetStatus.isOverBudget -> financialColors.expense
                                        overallBudgetStatus.isNearLimit -> financialColors.warning
                                        else -> financialColors.income
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
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = ShapeTokens.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                        border = cardBorderStroke()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Category,
                                contentDescription = null,
                                modifier = Modifier.size(44.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "No category budgets set yet",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Set individual limits for food, entertainment, utilities, and more.",
                                style = MaterialTheme.typography.bodySmall,
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
                            .clip(ShapeTokens.large)
                            .clickable {
                                selectedBudgetToEdit = status.budget
                                showAddEditDialog = true
                            },
                        shape = ShapeTokens.large,
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = cardBorderStroke(),
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
                                            text = "$currencySymbol${String.format(Locale.US, "%,.0f", status.spentAmount)} of $currencySymbol${String.format(Locale.US, "%,.0f", status.budget.amountLimit)}",
                                            style = MaterialTheme.typography.labelSmall.tabular(),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (status.isOverBudget) {
                                    Surface(
                                        shape = ShapeTokens.small,
                                        color = financialColors.expenseContainer
                                    ) {
                                        Text(
                                            text = "OVER",
                                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                            color = financialColors.onExpenseContainer,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
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
