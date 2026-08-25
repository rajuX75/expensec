package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

object CategoryIconHelper {
    fun getIcon(iconName: String): ImageVector {
        return when (iconName.lowercase()) {
            "restaurant", "food", "dining" -> Icons.Default.Restaurant
            "shopping_cart", "groceries", "grocery" -> Icons.Default.ShoppingCart
            "directions_car", "transport", "transportation", "car", "gas" -> Icons.Default.DirectionsCar
            "shopping_bag", "shopping", "clothes" -> Icons.Default.ShoppingBag
            "home", "housing", "rent" -> Icons.Default.Home
            "bolt", "utilities", "electric", "power" -> Icons.Default.Bolt
            "movie", "entertainment", "game", "games" -> Icons.Default.Movie
            "local_hospital", "health", "medical", "pharmacy" -> Icons.Default.LocalHospital
            "flight", "travel", "vacation", "hotel" -> Icons.Default.Flight
            "subscriptions", "subscription", "netflix", "spotify" -> Icons.Default.Subscriptions
            "school", "education", "books" -> Icons.Default.School
            "spa", "personal_care", "beauty", "fitness" -> Icons.Default.Spa
            "payments", "salary", "wage" -> Icons.Default.Payments
            "work", "freelance", "job" -> Icons.Default.Work
            "trending_up", "investments", "stock", "crypto" -> Icons.AutoMirrored.Filled.TrendingUp
            "redeem", "bonus", "gift", "gifts" -> Icons.Default.Redeem
            "apartment", "rental" -> Icons.Default.Apartment
            "account_balance_wallet", "wallet" -> Icons.Default.AccountBalanceWallet
            "account_balance", "bank" -> Icons.Default.AccountBalance
            "credit_card", "card" -> Icons.Default.CreditCard
            "savings", "piggy" -> Icons.Default.Savings
            "attach_money", "money" -> Icons.Default.AttachMoney
            "local_cafe", "coffee" -> Icons.Default.LocalCafe
            "fitness_center", "gym" -> Icons.Default.FitnessCenter
            "pets", "pet" -> Icons.Default.Pets
            "build", "maintenance" -> Icons.Default.Build
            "wifi", "internet" -> Icons.Default.Wifi
            "phone", "mobile" -> Icons.Default.PhoneAndroid
            else -> Icons.Default.Category
        }
    }

    fun parseColor(hex: String, fallback: Color = Color(0xFF64748B)): Color {
        return try {
            val cleanHex = hex.removePrefix("#")
            when (cleanHex.length) {
                6 -> Color(android.graphics.Color.parseColor("#$cleanHex"))
                8 -> Color(android.graphics.Color.parseColor("#$cleanHex"))
                else -> fallback
            }
        } catch (e: Exception) {
            fallback
        }
    }

    val availableIcons = listOf(
        "restaurant", "shopping_cart", "directions_car", "shopping_bag",
        "home", "bolt", "movie", "local_hospital", "flight",
        "subscriptions", "school", "spa", "payments", "work",
        "trending_up", "redeem", "apartment", "account_balance_wallet",
        "account_balance", "credit_card", "savings", "local_cafe",
        "fitness_center", "pets", "build", "wifi", "phone", "category"
    )

    val availableColors = listOf(
        "#EF4444", "#F97316", "#F59E0B", "#EAB308",
        "#84CC16", "#10B981", "#059669", "#14B8A6",
        "#06B6D4", "#3B82F6", "#6366F1", "#8B5CF6",
        "#A855F7", "#D946EF", "#EC4899", "#F43F5E",
        "#64748B", "#334155"
    )
}

@Composable
fun CategoryBadge(
    iconName: String,
    colorHex: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp
) {
    val bgColor = CategoryIconHelper.parseColor(colorHex)
    Box(
        modifier = modifier
            .size(size)
            .background(bgColor.copy(alpha = 0.15f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = CategoryIconHelper.getIcon(iconName),
            contentDescription = null,
            tint = bgColor,
            modifier = Modifier.size(iconSize)
        )
    }
}
