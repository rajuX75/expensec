package com.example.ui.navigation

import android.net.Uri
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.data.model.TransactionEntity
import com.example.ui.components.NotificationPopupDialog
import com.example.ui.components.UpdateDialog
import com.example.ui.screens.*
import com.example.ui.screens.dhaar.ContactDetailScreen
import com.example.ui.screens.dhaar.DhaarDashboardScreen
import com.example.ui.screens.shopbaki.ShopBakiDashboardScreen
import com.example.ui.screens.shopbaki.ShopDetailScreen
import com.example.ui.viewmodel.ExpenseViewModel
import kotlinx.serialization.Serializable

/**
 * Skill #6 (navigation-3): the custom AppScreen enum + AnimatedContent state
 * machine has been replaced with Jetpack Navigation 3.
 *
 *  - Type-safe routes ([AppRoute]) instead of an enum + loose Long? state vars
 *  - [NavBackStack] owns navigation state: proper system back handling,
 *    predictive back, and deep links (expensex://open/...) for free
 *  - [NavDisplay] renders the top entry with ContentTransform transitions
 *
 * Modal overlays that are not destinations (Add/Edit Transaction sheet, Settings
 * sheet, update dialog, notification popup) intentionally remain local state.
 */

// ── Type-safe route definitions ─────────────────────────────────────────────

@Serializable
sealed interface AppRoute : NavKey {
    @Serializable data object Dashboard : AppRoute
    @Serializable data object Transactions : AppRoute
    @Serializable data object Dhaar : AppRoute
    @Serializable data object Analytics : AppRoute
    @Serializable data object Budgets : AppRoute
    @Serializable data object Accounts : AppRoute
    @Serializable data object ShopBaki : AppRoute
    @Serializable data object Export : AppRoute
    @Serializable data object Import : AppRoute
    @Serializable data object Notifications : AppRoute
    @Serializable data object Profile : AppRoute
    @Serializable data class ContactDetail(val contactId: Long) : AppRoute
    @Serializable data class ShopDetail(val shopId: Long) : AppRoute
}

/** Bottom-bar tab metadata, tied to Nav 3 routes. */
private data class TabItem(
    val route: AppRoute,
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val bottomNavTabs = listOf(
    TabItem(AppRoute.Dashboard, "Home", Icons.Default.Dashboard, Icons.Outlined.Dashboard),
    TabItem(AppRoute.Transactions, "Transactions", Icons.AutoMirrored.Filled.ReceiptLong, Icons.AutoMirrored.Outlined.ReceiptLong),
    TabItem(AppRoute.Dhaar, "Debts & Loans", Icons.Default.Handshake, Icons.Outlined.Handshake),
    TabItem(AppRoute.Analytics, "Analytics", Icons.Default.PieChart, Icons.Outlined.PieChart)
)

private fun screenTitle(route: AppRoute?): String = when (route) {
    AppRoute.Transactions -> "Transactions"
    AppRoute.Dhaar -> "Debts & Loans"
    AppRoute.Analytics -> "Financial Insights"
    AppRoute.Budgets -> "Monthly Budgets"
    AppRoute.Accounts -> "Accounts & Bills"
    else -> "Expense Tracker"
}

private fun screenIcon(route: AppRoute?): ImageVector = when (route) {
    AppRoute.Dhaar -> Icons.Default.Handshake
    AppRoute.Analytics -> Icons.Default.PieChart
    AppRoute.Transactions -> Icons.AutoMirrored.Filled.ReceiptLong
    AppRoute.Budgets -> Icons.Default.AccountBalanceWallet
    AppRoute.Accounts -> Icons.Default.AccountBalance
    else -> Icons.Default.Dashboard
}

/** Maps expensex://open/<path> deep links onto routes. */
private fun deepLinkToRoute(uri: Uri): AppRoute? = when {
    uri.scheme != "expensex" || uri.host != "open" -> null
    uri.pathSegments.firstOrNull() == "transactions" -> AppRoute.Transactions
    uri.pathSegments.firstOrNull() == "dhaar" -> AppRoute.Dhaar
    uri.pathSegments.firstOrNull() == "analytics" -> AppRoute.Analytics
    uri.pathSegments.firstOrNull() == "budgets" -> AppRoute.Budgets
    uri.pathSegments.firstOrNull() == "accounts" -> AppRoute.Accounts
    uri.pathSegments.firstOrNull() == "notifications" -> AppRoute.Notifications
    uri.pathSegments.firstOrNull() == "shopbaki" -> AppRoute.ShopBaki
    uri.pathSegments.firstOrNull() == "contact" ->
        uri.pathSegments.getOrNull(1)?.toLongOrNull()?.let(AppRoute::ContactDetail)
    uri.pathSegments.firstOrNull() == "shop" ->
        uri.pathSegments.getOrNull(1)?.toLongOrNull()?.let(AppRoute::ShopDetail)
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseAppMain(viewModel: ExpenseViewModel, deepLink: Uri? = null) {
    val backStack = rememberNavBackStack(AppRoute.Dashboard)

    // Skill #6: deep link entry point (notifications, shortcuts, other apps).
    LaunchedEffect(deepLink) {
        deepLink?.let(::deepLinkToRoute)?.let { route ->
            if (backStack.lastOrNull() != route) backStack.add(route)
        }
    }

    // Modal overlay state (not back-stack destinations)
    var showAddEditTransactionSheet by remember { mutableStateOf(false) }
    var transactionToEdit by remember { mutableStateOf<TransactionEntity?>(null) }
    var initialTransactionType by remember { mutableStateOf("EXPENSE") }
    var showSettingsSheet by remember { mutableStateOf(false) }

    val unreadNotificationCount by viewModel.unreadNotificationCount.collectAsState()
    val popupNotification by viewModel.popupNotification.collectAsState()

    val currentRoute = backStack.lastOrNull() as? AppRoute
    val onTabSelected: (AppRoute) -> Unit = { route ->
        // Tab switch resets to a single-root stack, matching the old behavior
        // where leaving a tab always returned Home on back.
        if (currentRoute != route) {
            backStack.clear()
            backStack.add(route)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = screenIcon(currentRoute),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = screenTitle(currentRoute),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                },
                navigationIcon = {
                    if (currentRoute == AppRoute.Budgets || currentRoute == AppRoute.Accounts) {
                        IconButton(onClick = { backStack.removeLastOrNull() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Home"
                            )
                        }
                    }
                },
                actions = {
                    BadgedBox(
                        badge = {
                            if (unreadNotificationCount > 0) {
                                Badge {
                                    Text(
                                        text = if (unreadNotificationCount > 99) "99+" else unreadNotificationCount.toString(),
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    ) {
                        IconButton(onClick = {
                            viewModel.dismissNotificationPopupIfShowing()
                            backStack.add(AppRoute.Notifications)
                        }) {
                            Icon(
                                imageVector = if (unreadNotificationCount > 0) Icons.Default.Notifications else Icons.Outlined.Notifications,
                                contentDescription = "Notifications",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                bottomNavTabs.forEach { tab ->
                    val isSelected = currentRoute == tab.route
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { onTabSelected(tab.route) },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.title
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryProvider = entryProvider {
                    entry<AppRoute.Dashboard> {
                        DashboardScreen(
                            viewModel = viewModel,
                            onNavigateToTransactions = { onTabSelected(AppRoute.Transactions) },
                            onNavigateToBudgets = { backStack.add(AppRoute.Budgets) },
                            onNavigateToAccounts = { backStack.add(AppRoute.Accounts) },
                            onNavigateToDhaar = { onTabSelected(AppRoute.Dhaar) },
                            onNavigateToShopBaki = { backStack.add(AppRoute.ShopBaki) },
                            onNavigateToAnalytics = { onTabSelected(AppRoute.Analytics) },
                            onOpenAddTransaction = { type ->
                                transactionToEdit = null
                                initialTransactionType = type
                                showAddEditTransactionSheet = true
                            },
                            onTransactionClicked = { tx ->
                                transactionToEdit = tx
                                showAddEditTransactionSheet = true
                            }
                        )
                    }
                    entry<AppRoute.Transactions> {
                        TransactionsScreen(
                            viewModel = viewModel,
                            onEditTransaction = { tx ->
                                transactionToEdit = tx
                                showAddEditTransactionSheet = true
                            },
                            onAddNewTransaction = {
                                transactionToEdit = null
                                initialTransactionType = "EXPENSE"
                                showAddEditTransactionSheet = true
                            }
                        )
                    }
                    entry<AppRoute.Dhaar> {
                        DhaarDashboardScreen(
                            viewModel = viewModel,
                            onNavigateToContact = { contactId ->
                                backStack.add(AppRoute.ContactDetail(contactId))
                            }
                        )
                    }
                    entry<AppRoute.Analytics> {
                        AnalyticsScreen(viewModel = viewModel)
                    }
                    entry<AppRoute.Budgets> {
                        BudgetsScreen(viewModel = viewModel)
                    }
                    entry<AppRoute.Accounts> {
                        AccountsAndBillsScreen(
                            viewModel = viewModel,
                            onOpenTransfer = {
                                transactionToEdit = null
                                initialTransactionType = "TRANSFER"
                                showAddEditTransactionSheet = true
                            }
                        )
                    }
                    entry<AppRoute.ShopBaki> {
                        ShopBakiDashboardScreen(
                            viewModel = viewModel,
                            onNavigateBack = { backStack.removeLastOrNull() },
                            onNavigateToShopDetail = { shopId ->
                                backStack.add(AppRoute.ShopDetail(shopId))
                            }
                        )
                    }
                    entry<AppRoute.ShopDetail> { route ->
                        ShopDetailScreen(
                            shopId = route.shopId,
                            viewModel = viewModel,
                            onNavigateBack = { backStack.removeLastOrNull() }
                        )
                    }
                    entry<AppRoute.ContactDetail> { route ->
                        ContactDetailScreen(
                            viewModel = viewModel,
                            contactId = route.contactId,
                            onNavigateBack = { backStack.removeLastOrNull() }
                        )
                    }
                    entry<AppRoute.Export> {
                        ExportDataScreen(
                            viewModel = viewModel,
                            onNavigateBack = { backStack.removeLastOrNull() }
                        )
                    }
                    entry<AppRoute.Import> {
                        ImportDataScreen(
                            viewModel = viewModel,
                            onNavigateBack = { backStack.removeLastOrNull() }
                        )
                    }
                    entry<AppRoute.Notifications> {
                        NotificationsScreen(
                            viewModel = viewModel,
                            onNavigateBack = { backStack.removeLastOrNull() }
                        )
                    }
                    entry<AppRoute.Profile> {
                        ProfileScreen(
                            viewModel = viewModel,
                            onNavigateBack = { backStack.removeLastOrNull() }
                        )
                    }
                },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                popTransitionSpec = { fadeIn() togetherWith fadeOut() },
            )
        }
    }

    // Modal Add / Edit Transaction Sheet
    if (showAddEditTransactionSheet) {
        AddEditTransactionSheet(
            viewModel = viewModel,
            initialTransaction = transactionToEdit,
            initialType = initialTransactionType,
            onDismiss = { showAddEditTransactionSheet = false },
            onSaved = { showAddEditTransactionSheet = false }
        )
    }

    // Modal Settings Sheet
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = { showSettingsSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                SettingsScreen(
                    viewModel = viewModel,
                    onNavigateToExport = {
                        showSettingsSheet = false
                        backStack.add(AppRoute.Export)
                    },
                    onNavigateToImport = {
                        showSettingsSheet = false
                        backStack.add(AppRoute.Import)
                    },
                    onNavigateToProfile = {
                        showSettingsSheet = false
                        backStack.add(AppRoute.Profile)
                    }
                )
            }
        }
    }

    // In-App Update Prompt on Home / Dashboard when not in Settings
    val updateCheckState by viewModel.updateCheckState.collectAsState()
    val updateDownloadState by viewModel.updateDownloadState.collectAsState()
    if (!showSettingsSheet && updateCheckState is com.example.data.model.UpdateCheckState.UpdateAvailable) {
        val updateInfo = (updateCheckState as com.example.data.model.UpdateCheckState.UpdateAvailable).info
        UpdateDialog(
            updateInfo = updateInfo,
            currentVersion = "v${viewModel.currentAppVersionName}",
            downloadState = updateDownloadState,
            onUpdateClick = {
                viewModel.downloadAndInstallUpdate(updateInfo)
            },
            onSkipClick = {
                viewModel.skipUpdateVersion(updateInfo.versionCode)
            },
            onDismissClick = {
                viewModel.dismissUpdatePrompt()
            }
        )
    }

    // Admin notification popup — suppressed on every pushed destination and the
    // settings sheet so dialogs never stack on top of full-screen content.
    val anyFullScreenOpen = showSettingsSheet || backStack.size > 1
    popupNotification?.let { notification ->
        if (!anyFullScreenOpen) {
            NotificationPopupDialog(
                notification = notification,
                onDismiss = { viewModel.dismissNotificationPopup(notification.id) },
                onActionClick = { url ->
                    viewModel.dismissNotificationPopup(notification.id)
                    viewModel.openNotificationAction(url)
                }
            )
        }
    }
}
