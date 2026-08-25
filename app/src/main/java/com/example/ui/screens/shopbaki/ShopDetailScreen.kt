package com.example.ui.screens.shopbaki

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.example.data.model.Shop
import com.example.data.model.ShopTimelineItem
import com.example.ui.theme.ExpenseRed
import com.example.ui.theme.IncomeGreen
import com.example.ui.viewmodel.ExpenseViewModel
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopDetailScreen(
    shopId: Long,
    viewModel: ExpenseViewModel,
    onNavigateBack: () -> Unit
) {
    val currencySymbol by viewModel.currencySymbol.collectAsState()
    
    val shop by viewModel.getShopById(shopId).collectAsState(initial = null)
    val timelineItems by viewModel.getShopTimeline(shopId).collectAsState(initial = emptyList())
    
    val shopsWithBalances by viewModel.shopsWithBalances.collectAsState()
    val shopWithBalance = shopsWithBalances.find { it.shop.id == shopId }
    val currentDue = shopWithBalance?.currentDue ?: 0.0

    var showAddBakiSheet by remember { mutableStateOf(false) }
    var showAddPaymentSheet by remember { mutableStateOf(false) }
    var showEditShopDialog by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(shop?.name ?: "Shop Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showEditShopDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Shop")
                    }
                    IconButton(
                        onClick = {
                            shop?.let {
                                viewModel.deleteShop(it) { result ->
                                    if (result.isSuccess) {
                                        onNavigateBack()
                                    } else {
                                        // TODO: Show toast (can't delete because entries exist)
                                    }
                                }
                            }
                        }
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Shop")
                    }
                }
            )
        },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                SmallFloatingActionButton(
                    onClick = { showAddPaymentSheet = true },
                    containerColor = IncomeGreen,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.Payment, contentDescription = "Add Payment")
                }
                Spacer(modifier = Modifier.height(8.dp))
                FloatingActionButton(
                    onClick = { showAddBakiSheet = true },
                    containerColor = ExpenseRed,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Default.ShoppingCart, contentDescription = "Add Purchase (Baki)")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Header with Cover and Profile Picture
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                if (!shop?.coverImageUri.isNullOrBlank()) {
                    coil.compose.AsyncImage(
                        model = com.example.data.cloud.CloudinaryUrl.preview(shop?.coverImageUri),
                        contentDescription = "Cover Image",
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    )
                }

                // Profile Picture overlapping the cover
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = 10.dp)
                ) {
                    if (!shop?.profilePictureUri.isNullOrBlank()) {
                        coil.compose.AsyncImage(
                            model = com.example.data.cloud.CloudinaryUrl.avatar(shop?.profilePictureUri),
                            contentDescription = "Profile Picture",
                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(4.dp)
                                .clip(CircleShape)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(4.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = shop?.name?.take(1)?.uppercase() ?: "?",
                                fontSize = 32.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    
                    if (shop?.isVerified == true) {
                        Icon(
                            Icons.Default.Verified,
                            contentDescription = "Verified",
                            tint = Color(0xFF1DA1F2),
                            modifier = Modifier
                                .size(24.dp)
                                .align(Alignment.BottomEnd)
                                .offset(x = (-4).dp, y = (-4).dp)
                                .background(Color.White, CircleShape)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // Header: Current Due
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Current Baki",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "$currencySymbol${String.format("%,.2f", abs(currentDue))}",
                        style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                        color = if (currentDue > 0.01) ExpenseRed else IncomeGreen
                    )
                    if (currentDue <= 0.01) {
                         Text(
                            text = "All Clear!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = IncomeGreen
                        )
                    } else {
                        Text(
                            text = "You owe this shop",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    if (!shop?.category.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = shop?.category ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            // Timeline List
            if (timelineItems.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No transactions yet", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(timelineItems, key = { it.entry.uuid }) { item ->
                        TimelineItemCard(item = item, currencySymbol = currencySymbol, dateFormat = dateFormat)
                    }
                }
            }
        }
    }
    
    if (showAddBakiSheet && shop != null) {
        AddBakiEntrySheet(
            shop = shop!!,
            viewModel = viewModel,
            onDismiss = { showAddBakiSheet = false }
        )
    }
    
    if (showAddPaymentSheet && shop != null) {
        AddShopPaymentSheet(
            shop = shop!!,
            viewModel = viewModel,
            onDismiss = { showAddPaymentSheet = false }
        )
    }
    
    if (showEditShopDialog && shop != null) {
        AddEditShopDialog(
            viewModel = viewModel,
            shop = shop,
            onDismiss = { showEditShopDialog = false }
        )
    }
}

@Composable
fun TimelineItemCard(
    item: ShopTimelineItem,
    currencySymbol: String,
    dateFormat: SimpleDateFormat
) {
    val entry = item.entry
    val isPayment = item.isPayment
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Timeline Dot / Icon
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(
                    color = if (isPayment) IncomeGreen.copy(alpha = 0.15f) else ExpenseRed.copy(alpha = 0.15f),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPayment) Icons.Default.Payment else Icons.Default.ShoppingCart,
                contentDescription = null,
                tint = if (isPayment) IncomeGreen else ExpenseRed,
                modifier = Modifier.size(20.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
            )
            val subText = if (!isPayment && entry.quantity != null && entry.unitPriceAtPurchase != null) {
                "${entry.quantity} x $currencySymbol${String.format("%,.2f", entry.unitPriceAtPurchase)}"
            } else {
                entry.note ?: if (isPayment) "Paid to shop" else "Purchase"
            }
            Text(
                text = subText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = dateFormat.format(Date(entry.date)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
        
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${if (isPayment) "-" else "+"}$currencySymbol${String.format("%,.2f", entry.amount)}",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold),
                color = if (isPayment) IncomeGreen else ExpenseRed
            )
            Text(
                text = "Bal: $currencySymbol${String.format("%,.2f", item.runningBalance)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
