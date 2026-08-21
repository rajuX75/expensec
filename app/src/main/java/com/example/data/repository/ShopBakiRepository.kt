package com.example.data.repository

import com.example.data.local.ShopDao
import com.example.data.local.ShopLedgerEntryDao
import com.example.data.local.ShopProductDao
import com.example.data.model.Shop
import com.example.data.model.ShopLedgerEntry
import com.example.data.model.ShopProduct
import com.example.data.model.ShopTimelineItem
import com.example.data.model.ShopWithBalance
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ShopBakiRepository(
    private val shopDao: ShopDao,
    private val shopProductDao: ShopProductDao,
    private val shopLedgerEntryDao: ShopLedgerEntryDao
) {
    val allShops: Flow<List<Shop>> = shopDao.getAllShops()
    val activeProducts: Flow<List<ShopProduct>> = shopProductDao.getActiveProducts()
    val allLedgerEntries: Flow<List<ShopLedgerEntry>> = shopLedgerEntryDao.getAllEntries()

    val shopsWithBalances: Flow<List<ShopWithBalance>> = combine(
        allShops,
        allLedgerEntries
    ) { shops, entries ->
        val entriesByShop = entries.groupBy { it.shopId }

        shops.map { shop ->
            val shopEntries = entriesByShop[shop.id] ?: emptyList()
            var totalBaki = 0.0
            var totalPaid = 0.0

            shopEntries.forEach { entry ->
                when (entry.type.uppercase()) {
                    "PURCHASE" -> totalBaki += entry.amount
                    "PAYMENT" -> totalPaid += entry.amount
                }
            }

            ShopWithBalance(
                shop = shop,
                totalBaki = totalBaki,
                totalPaid = totalPaid,
                currentDue = totalBaki - totalPaid,
                lastActivityDate = shopEntries.maxOfOrNull { it.date }
            )
        }.sortedByDescending { it.currentDue }
    }

    fun getShopById(shopId: Long): Flow<Shop?> = shopDao.getShopById(shopId)

    suspend fun insertShop(shop: Shop): Long = withContext(Dispatchers.IO) {
        shopDao.insertShop(shop)
    }

    suspend fun updateShop(shop: Shop) = withContext(Dispatchers.IO) {
        shopDao.updateShop(shop)
    }

    suspend fun deleteShop(shop: Shop): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val count = shopLedgerEntryDao.getEntryCountForShop(shop.id)
            if (count > 0) {
                throw IllegalStateException("Cannot delete shop with active ledger entries.")
            }
            shopDao.deleteShop(shop)
        }
    }
    
    // --- Products ---

    suspend fun insertProduct(product: ShopProduct): Long = withContext(Dispatchers.IO) {
        shopProductDao.insertProduct(product)
    }

    suspend fun getProductUuidById(productId: Long): String? = withContext(Dispatchers.IO) {
        shopProductDao.getProductByIdSync(productId)?.uuid
    }

    suspend fun updateProduct(product: ShopProduct) = withContext(Dispatchers.IO) {
        shopProductDao.updateProduct(product)
    }

    /**
     * Deletes a product, or soft-deletes (archives) it when ledger entries reference it.
     * Returns true when the product was soft-deleted (archived), false on hard delete.
     */
    suspend fun deleteProduct(product: ShopProduct): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val count = shopLedgerEntryDao.getEntryCountForProduct(product.id)
            if (count > 0) {
                // Soft delete
                shopProductDao.updateProduct(product.copy(isArchived = true))
                true
            } else {
                shopProductDao.deleteProduct(product)
                false
            }
        }
    }

    // --- Ledger Entries ---

    suspend fun insertLedgerEntry(entry: ShopLedgerEntry): Long = withContext(Dispatchers.IO) {
        shopLedgerEntryDao.insertEntry(entry)
    }

    suspend fun deleteLedgerEntry(entry: ShopLedgerEntry) = withContext(Dispatchers.IO) {
        shopLedgerEntryDao.deleteEntry(entry)
    }
    
    suspend fun updateLedgerEntry(entry: ShopLedgerEntry) = withContext(Dispatchers.IO) {
        shopLedgerEntryDao.updateEntry(entry)
    }

    fun getShopTimeline(shopId: Long): Flow<List<ShopTimelineItem>> = combine(
        shopLedgerEntryDao.getEntriesForShop(shopId), // ordered by date DESC
        shopProductDao.getActiveProducts() // Need all products to resolve names, actually we need even archived ones!
    ) { entries, _ ->
        // Note: we should use getAllProductsSync or a flow of ALL products (including archived)
        // to properly resolve names for old entries. Let's fetch all products here.
        val allProducts = shopProductDao.getAllProductsSync().associateBy { it.id }
        
        // To compute running balance, we need to iterate chronologically (ASC)
        val chronologicalEntries = entries.reversed()
        var runningBal = 0.0
        val timelineItemsAsc = chronologicalEntries.map { entry ->
            if (entry.type.uppercase() == "PURCHASE") {
                runningBal += entry.amount
            } else {
                runningBal -= entry.amount
            }
            
            val title = if (entry.type.uppercase() == "PURCHASE") {
                val productName = entry.productId?.let { allProducts[it]?.name } ?: "Unknown Product"
                productName
            } else {
                "Payment"
            }
            
            ShopTimelineItem(
                entry = entry,
                title = title,
                isPayment = entry.type.uppercase() == "PAYMENT",
                runningBalance = runningBal
            )
        }
        
        // Return descending for UI
        timelineItemsAsc.reversed()
    }
}
