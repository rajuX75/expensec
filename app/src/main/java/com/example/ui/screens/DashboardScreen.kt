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
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BillEntity
import com.example.data.model.TransactionEntity
import com.example.ui.components.BudgetProgressBar
import com.example.ui.components.TransactionItemCard
import com.example.ui.theme.*
import com.example.ui.viewmodel.ExpenseViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@Composable
fun DashboardScreen(
    viewModel: ExpenseViewModel,
    onNavigateToTransactions: () -> Unit,
    onNavigateToBudgets: () -> Unit,
    onNavigateToAccounts: () -> Unit,
    onNavigateToDhaar: () -> Unit = {},
    onNavigateToAnalytics: () -> Unit = {},
    onOpenAddTransaction: (String) -> Unit, // EXPENSE, INCOME, TRANSFER
    onTransactionClicked: (TransactionEntity) -> Unit
) {
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    val summary by viewModel.financialSummary.collectAsState()
    val recentTransactions by viewModel.allTransactions.collectAsState()
    val budgetStatuses by viewModel.budgetStatuses.collectAsState()
    val bills by viewModel.allBills.collectAsState()
    val dhaarSummary by viewModel.dhaarDashboardSummary.collectAsState()
    val allAccounts by viewModel.allAccounts.collectAsState()

    val overallBudgetStatus = remember(budgetStatuses) {
        budgetStatuses.find { it.budget.categoryId == null } ?: budgetStatuses.firstOrNull()
    }

    val upcomingUnpaidBills = remember(bills) {
        bills.filter { !it.isPaid }.sortedBy { it.dueDate }
    }

    val netSavings = summary.thisMonthIncome - summary.thisMonthExpense
    val savingsRate = if (summary.thisMonthIncome > 0) {
        ((netSavings / summary.thisMonthIncome) * 100).toInt().coerceIn(-100, 100)
    } else {
        0
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 96.dp)
    ) {
        // Hero Balance Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFF006C4C),
                                    Color(0xFF004D30),
                                    Color(0xFF0F2419)
                                )
                            )
                        )
                        .padding(22.dp)
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Total Net Balance",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.White.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = "${allAccounts.size} Accounts",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Large Total Balance Text
                        Text(
                            text = "$currencySymbol${String.format("%,.2f", summary.totalBalance)}",
                            style = MaterialTheme.typography.headlineLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 34.sp
                            ),
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(18.dp))

                        // Income & Expense Monthly Overview Pills
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Monthly Income
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                color = Color.Black.copy(alpha = 0.25f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(IncomeGreen.copy(alpha = 0.3f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDownward,
                                            contentDescription = null,
                                            tint = Emerald400,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Income (Mo)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            text = "$currencySymbol${String.format("%,.0f", summary.thisMonthIncome)}",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }

                            // Monthly Expense
                            Surface(
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                color = Color.Black.copy(alpha = 0.25f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .background(ExpenseRed.copy(alpha = 0.3f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowUpward,
                                            contentDescription = null,
                                            tint = Color(0xFFFCA5A5),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "Expense (Mo)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            text = "$currencySymbol${String.format("%,.0f", summary.thisMonthExpense)}",
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                                            color = Color.White,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Quick Action Buttons Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionPill(
                    title = "Expense",
                    icon = Icons.Default.Remove,
                    bgColor = ExpenseRed.copy(alpha = 0.12f),
                    tintColor = ExpenseRed,
                    modifier = Modifier.weight(1f),
                    onClick = { onOpenAddTransaction("EXPENSE") }
                )
                QuickActionPill(
                    title = "Income",
                    icon = Icons.Default.Add,
                    bgColor = IncomeGreen.copy(alpha = 0.12f),
                    tintColor = IncomeGreen,
                    modifier = Modifier.weight(1f),
                    onClick = { onOpenAddTransaction("INCOME") }
                )
                QuickActionPill(
                    title = "Transfer",
                    icon = Icons.Default.SwapHoriz,
                    bgColor = TransferBlue.copy(alpha = 0.12f),
                    tintColor = TransferBlue,
                    modifier = Modifier.weight(1f),
                    onClick = { onOpenAddTransaction("TRANSFER") }
                )
            }
        }

        // Bento Grid Navigation Section Title
        item {
            Text(
                text = "Overview & Hub",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // Bento Tile 1: Debts & Loans Card (Prominent Wide Hub)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onNavigateToDhaar() },
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Handshake,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.tertiary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Debts & Loans Ledger",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${dhaarSummary.activeContactsCount} active records",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Icon(
                            Icons.Default.ChevronRight,
                            contentDescription = "Navigate to Debts & Loans",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = IncomeGreen.copy(alpha = 0.1f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("You'll Get", style = MaterialTheme.typography.labelSmall, color = IncomeGreen)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "$currencySymbol${String.format("%,.2f", dhaarSummary.totalYouWillGet)}",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                    color = IncomeGreen
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = ExpenseRed.copy(alpha = 0.1f),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("You'll Pay", style = MaterialTheme.typography.labelSmall, color = ExpenseRed)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "$currencySymbol${String.format("%,.2f", dhaarSummary.totalYouWillPay)}",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                                    color = ExpenseRed
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bento Row 1: Accounts & Monthly Budgets (2-Column Grid)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Bento Card: Accounts & Wallets
                BentoCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.AccountBalance,
                    iconTint = MaterialTheme.colorScheme.primary,
                    iconBg = MaterialTheme.colorScheme.primaryContainer,
                    title = "Accounts",
                    subtitle = "${allAccounts.size} Wallets",
                    value = "$currencySymbol${String.format("%,.0f", summary.totalBalance)}",
                    valueLabel = "Total balance",
                    onClick = onNavigateToAccounts
                )

                // Bento Card: Monthly Budgets
                val budgetText = if (overallBudgetStatus != null) {
                    val pct = ((overallBudgetStatus.spentAmount / overallBudgetStatus.budget.amountLimit) * 100).toInt()
                    "$pct% used"
                } else {
                    "Set budget"
                }
                val budgetStatusColor = when {
                    overallBudgetStatus?.isOverBudget == true -> ExpenseRed
                    overallBudgetStatus?.isNearLimit == true -> Color(0xFFB45309)
                    else -> IncomeGreen
                }

                BentoCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.AccountBalanceWallet,
                    iconTint = budgetStatusColor,
                    iconBg = budgetStatusColor.copy(alpha = 0.15f),
                    title = "Budgets",
                    subtitle = if (overallBudgetStatus?.isOverBudget == true) "Over limit" else "Monthly plan",
                    value = budgetText,
                    valueLabel = if (overallBudgetStatus != null) "of $currencySymbol${String.format("%,.0f", overallBudgetStatus.budget.amountLimit)}" else "Track spending",
                    onClick = onNavigateToBudgets
                )
            }
        }

        // Bento Row 2: Analytics & Upcoming Bills (2-Column Grid)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Bento Card: Financial Analytics
                BentoCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Default.PieChart,
                    iconTint = Color(0xFF8B5CF6),
                    iconBg = Color(0xFF8B5CF6).copy(alpha = 0.15f),
                    title = "Analytics",
                    subtitle = "Monthly trends",
                    value = if (netSavings >= 0) "+$savingsRate% saved" else "${savingsRate}% deficit",
                    valueLabel = "Savings rate",
                    onClick = onNavigateToAnalytics
                )

                // Bento Card: Upcoming Bills
                val hasBills = upcomingUnpaidBills.isNotEmpty()
                val nextBill = upcomingUnpaidBills.firstOrNull()
                val billValue = if (nextBill != null) "$currencySymbol${String.format("%,.0f", nextBill.amount)}" else "All clear"
                val billLabel = if (nextBill != null) nextBill.title else "No due bills"

                BentoCard(
                    modifier = Modifier.weight(1f),
                    icon = if (hasBills) Icons.Default.NotificationsActive else Icons.Default.CheckCircle,
                    iconTint = if (hasBills) Color(0xFF0891B2) else IncomeGreen,
                    iconBg = if (hasBills) Color(0xFF06B6D4).copy(alpha = 0.15f) else IncomeGreen.copy(alpha = 0.15f),
                    title = "Bills & Dues",
                    subtitle = if (hasBills) "${upcomingUnpaidBills.size} pending" else "Up to date",
                    value = billValue,
                    valueLabel = billLabel,
                    onClick = onNavigateToAccounts
                )
            }
        }

        // Overall Monthly Budget Detailed Progress Card (if set)
        if (overallBudgetStatus != null) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onNavigateToBudgets() },
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Savings,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = overallBudgetStatus.budget.categoryName ?: "Monthly Budget Progress",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Limit: $currencySymbol${String.format("%,.0f", overallBudgetStatus.budget.amountLimit)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (overallBudgetStatus.isOverBudget) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = ExpenseRedLight
                                ) {
                                    Text(
                                        text = "OVER BUDGET",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = ExpenseRed,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            } else if (overallBudgetStatus.isNearLimit) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = AmberLight
                                ) {
                                    Text(
                                        text = "NEAR LIMIT",
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFFB45309),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

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

        // Recent Transactions Section Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Transactions",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = onNavigateToTransactions) {
                    Text("See All (${recentTransactions.size})")
                }
            }
        }

        if (recentTransactions.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.ReceiptLong,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No transactions recorded yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(recentTransactions.take(5)) { tx ->
                TransactionItemCard(
                    transaction = tx,
                    currencySymbol = currencySymbol,
                    onClick = { onTransactionClicked(tx) }
                )
            }
        }
    }
}

@Composable
fun QuickActionPill(
    title: String,
    icon: ImageVector,
    bgColor: Color,
    tintColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 1.dp,
        modifier = modifier.height(48.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .background(bgColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tintColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun BentoCard(
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    title: String,
    subtitle: String,
    value: String,
    valueLabel: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(iconBg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Icon(
                    imageVector = Icons.Default.ArrowOutward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = iconTint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = valueLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
