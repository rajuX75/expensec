package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "shops")
data class Shop(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val name: String,
    val phoneNumber: String? = null,
    val address: String? = null,
    val note: String? = null,
    val profilePictureUri: String? = null,
    val coverImageUri: String? = null,
    val email: String? = null,
    val businessId: String? = null,
    val category: String? = null,
    val isVerified: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "shop_products")
data class ShopProduct(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val name: String,
    val defaultUnit: String? = null,
    val defaultPrice: Double,
    val isArchived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "shop_ledger_entries",
    foreignKeys = [
        ForeignKey(
            entity = Shop::class,
            parentColumns = ["id"],
            childColumns = ["shopId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ShopProduct::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            // Use SET_NULL so if a product is strictly deleted (unlikely given soft-delete rule, but safe), the ledger entry remains with just the amount
            // Actually, wait, if we soft-delete, we don't hard delete. Let's stick to RESTRICT or SET_NULL. Since productId is nullable for PAYMENT, we can use SET_NULL.
            // But let's just use NO_ACTION or RESTRICT and enforce soft delete. The user said: "if a product has existing ledger entries, don't hard delete - soft delete". So RESTRICT is perfect.
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index("shopId"),
        Index("productId")
    ]
)
data class ShopLedgerEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val shopId: Long,
    val type: String, // "PURCHASE" or "PAYMENT"
    val productId: Long? = null, // Nullable for PAYMENT
    val quantity: Double? = null, // Nullable for PAYMENT
    val unitPriceAtPurchase: Double? = null, // Nullable for PAYMENT
    val amount: Double, // Total price for PURCHASE, paid amount for PAYMENT
    val date: Long = System.currentTimeMillis(),
    val note: String? = null
)

// UI Data Classes
data class ShopWithBalance(
    val shop: Shop,
    val totalBaki: Double,    // sum of PURCHASE amounts
    val totalPaid: Double,    // sum of PAYMENT amounts
    val currentDue: Double,   // totalBaki - totalPaid
    val lastActivityDate: Long?
)

data class ShopTimelineItem(
    val entry: ShopLedgerEntry,
    val title: String, // Product Name or "Payment"
    val isPayment: Boolean,
    val runningBalance: Double
)
