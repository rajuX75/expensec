package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.model.TransactionEntity
import com.example.ui.components.PinLockScreen
import com.example.ui.screens.*
import com.example.ui.screens.dhaar.ContactDetailScreen
import com.example.ui.screens.dhaar.DhaarDashboardScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.viewmodel.ExpenseViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: ExpenseViewModel = viewModel()

            // Observe settings
            val themeMode by viewModel.themeMode.collectAsState()
            val isPinLockEnabled by viewModel.isPinLockEnabled.collectAsState()
            val pinCode by viewModel.pinCode.collectAsState()
            val isAppUnlocked by viewModel.isAppUnlocked.collectAsState()

            val isDarkTheme = when (themeMode) {
                "DARK" -> true
                "LIGHT" -> false
                else -> isSystemInDarkTheme()
            }

            MyApplicationTheme(darkTheme = isDarkTheme) {
                if (isPinLockEnabled && !isAppUnlocked) {
                    PinLockScreen(
                        correctPin = pinCode,
                        onVerifyPin = { entered -> viewModel.verifyPin(entered) },
                        onUnlocked = { viewModel.unlockApp() }
                    )
                } else {
                    ExpenseAppMain(viewModel = viewModel)
                }
            }
        }
    }
}

enum class AppScreen(
    val title: String,
    val selectedIcon: androidx.compose.ui.graphics.vector.ImageVector,
    val unselectedIcon: androidx.compose.ui.graphics.vector.ImageVector
) {
    DASHBOARD("Home", Icons.Default.Dashboard, Icons.Outlined.Dashboard),
    TRANSACTIONS("Transactions", Icons.Default.ReceiptLong, Icons.Outlined.ReceiptLong),
    DHAAR("Debts & Loans", Icons.Default.Handshake, Icons.Outlined.Handshake),
    ANALYTICS("Analytics", Icons.Default.PieChart, Icons.Outlined.PieChart),
    BUDGETS("Budgets", Icons.Default.AccountBalanceWallet, Icons.Outlined.AccountBalanceWallet),
    ACCOUNTS("Accounts", Icons.Default.AccountBalance, Icons.Outlined.AccountBalance)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseAppMain(viewModel: ExpenseViewModel) {
    var currentScreen by remember { mutableStateOf(AppScreen.DASHBOARD) }
    var selectedContactIdForDetail by remember { mutableStateOf<Long?>(null) }

    // Transaction Sheet states
    var showAddEditTransactionSheet by remember { mutableStateOf(false) }
    var transactionToEdit by remember { mutableStateOf<TransactionEntity?>(null) }
    var initialTransactionType by remember { mutableStateOf("EXPENSE") }

    // Settings Sheet state
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showExportScreen by remember { mutableStateOf(false) }
    var showImportScreen by remember { mutableStateOf(false) }
    var showProfileScreen by remember { mutableStateOf(false) }

    // Bottom Navigation items (Clean 4-tab bar)
    val bottomNavScreens = remember {
        listOf(
            AppScreen.DASHBOARD,
            AppScreen.TRANSACTIONS,
            AppScreen.DHAAR,
            AppScreen.ANALYTICS
        )
    }

    // Handle back button hierarchy
    BackHandler(
        enabled = showAddEditTransactionSheet ||
                showSettingsSheet ||
                selectedContactIdForDetail != null ||
                showExportScreen ||
                showImportScreen ||
                showProfileScreen ||
                currentScreen != AppScreen.DASHBOARD
    ) {
        when {
            showAddEditTransactionSheet -> showAddEditTransactionSheet = false
            showSettingsSheet -> showSettingsSheet = false
            selectedContactIdForDetail != null -> selectedContactIdForDetail = null
            showExportScreen -> showExportScreen = false
            showImportScreen -> showImportScreen = false
            showProfileScreen -> showProfileScreen = false
            currentScreen != AppScreen.DASHBOARD -> currentScreen = AppScreen.DASHBOARD
        }
    }

    if (selectedContactIdForDetail != null) {
        ContactDetailScreen(
            viewModel = viewModel,
            contactId = selectedContactIdForDetail!!,
            onNavigateBack = { selectedContactIdForDetail = null }
        )
    } else if (showExportScreen) {
        ExportDataScreen(
            viewModel = viewModel,
            onNavigateBack = { showExportScreen = false }
        )
    } else if (showImportScreen) {
        ImportDataScreen(
            viewModel = viewModel,
            onNavigateBack = { showImportScreen = false }
        )
    } else if (showProfileScreen) {
        ProfileScreen(
            viewModel = viewModel,
            onNavigateBack = { showProfileScreen = false }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = when (currentScreen) {
                                    AppScreen.DHAAR -> Icons.Default.Handshake
                                    AppScreen.ANALYTICS -> Icons.Default.PieChart
                                    AppScreen.TRANSACTIONS -> Icons.Default.ReceiptLong
                                    AppScreen.BUDGETS -> Icons.Default.AccountBalanceWallet
                                    AppScreen.ACCOUNTS -> Icons.Default.AccountBalance
                                    else -> Icons.Default.Dashboard
                                },
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = when (currentScreen) {
                                    AppScreen.DASHBOARD -> "Expense Tracker"
                                    AppScreen.TRANSACTIONS -> "Transactions"
                                    AppScreen.DHAAR -> "Debts & Loans"
                                    AppScreen.ANALYTICS -> "Financial Insights"
                                    AppScreen.BUDGETS -> "Monthly Budgets"
                                    AppScreen.ACCOUNTS -> "Accounts & Bills"
                                },
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    },
                    navigationIcon = {
                        if (currentScreen == AppScreen.BUDGETS || currentScreen == AppScreen.ACCOUNTS) {
                            IconButton(onClick = { currentScreen = AppScreen.DASHBOARD }) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Back to Home"
                                )
                            }
                        }
                    },
                    actions = {
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
                    bottomNavScreens.forEach { screen ->
                        val isSelected = currentScreen == screen
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { currentScreen = screen },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) screen.selectedIcon else screen.unselectedIcon,
                                    contentDescription = screen.title
                                )
                            },
                            label = {
                                Text(
                                    text = screen.title,
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
                AnimatedContent(
                    targetState = currentScreen,
                    label = "ScreenTransition"
                ) { screen ->
                    when (screen) {
                        AppScreen.DASHBOARD -> {
                            DashboardScreen(
                                viewModel = viewModel,
                                onNavigateToTransactions = { currentScreen = AppScreen.TRANSACTIONS },
                                onNavigateToBudgets = { currentScreen = AppScreen.BUDGETS },
                                onNavigateToAccounts = { currentScreen = AppScreen.ACCOUNTS },
                                onNavigateToDhaar = { currentScreen = AppScreen.DHAAR },
                                onNavigateToAnalytics = { currentScreen = AppScreen.ANALYTICS },
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
                        AppScreen.TRANSACTIONS -> {
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
                        AppScreen.DHAAR -> {
                            DhaarDashboardScreen(
                                viewModel = viewModel,
                                onNavigateToContact = { contactId ->
                                    selectedContactIdForDetail = contactId
                                }
                            )
                        }
                        AppScreen.ANALYTICS -> {
                            AnalyticsScreen(viewModel = viewModel)
                        }
                        AppScreen.BUDGETS -> {
                            BudgetsScreen(viewModel = viewModel)
                        }
                        AppScreen.ACCOUNTS -> {
                            AccountsAndBillsScreen(
                                viewModel = viewModel,
                                onOpenTransfer = {
                                    transactionToEdit = null
                                    initialTransactionType = "TRANSFER"
                                    showAddEditTransactionSheet = true
                                }
                            )
                        }
                    }
                }
            }
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
                        showExportScreen = true
                    },
                    onNavigateToImport = {
                        showSettingsSheet = false
                        showImportScreen = true
                    },
                    onNavigateToProfile = {
                        showSettingsSheet = false
                        showProfileScreen = true
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
        com.example.ui.components.UpdateDialog(
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
}
