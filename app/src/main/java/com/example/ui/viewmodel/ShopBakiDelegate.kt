package com.example.ui.viewmodel

import com.example.data.cloud.FirestoreSyncManager
import com.example.data.cloud.GoogleAuthManager
import com.example.data.model.Shop
import com.example.data.model.ShopLedgerEntry
import com.example.data.model.ShopProduct
import com.example.data.model.ShopTimelineItem
import com.example.data.model.TransactionEntity
import com.example.data.repository.ExpenseRepository
import com.example.data.model.ShopWithBalance
import com.example.data.repository.ShopBakiRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import kotlinx.coroutines.flow.firstOrNull

class ShopBakiDelegate(
    private val viewModelScope: CoroutineScope,
    private val shopBakiRepository: ShopBakiRepository,
    private val expenseRepository: ExpenseRepository,
    private val googleAuthManager: GoogleAuthManager,
    private val firestoreSyncManager: FirestoreSyncManager
) {
    val allShops: StateFlow<List<Shop>> = shopBakiRepository.allShops.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val activeProducts: StateFlow<List<ShopProduct>> = shopBakiRepository.activeProducts.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )
    
    val shopsWithBalances: StateFlow<List<ShopWithBalance>> = shopBakiRepository.shopsWithBalances.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    // --- Shops ---

    fun addShop(shop: Shop, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = shopBakiRepository.insertShop(shop)
            kotlinx.coroutines.withContext(Dispatchers.Main) { onCreated(id) }
            googleAuthManager.currentUserId?.let { uid ->
                firestoreSyncManager.pushShop(uid, shop.copy(id = id))
            }
        }
    }

    fun updateShop(shop: Shop) {
        viewModelScope.launch(Dispatchers.IO) {
            shopBakiRepository.updateShop(shop)
            googleAuthManager.currentUserId?.let { uid ->
                firestoreSyncManager.pushShop(uid, shop)
            }
        }
    }

    fun deleteShop(shop: Shop, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = shopBakiRepository.deleteShop(shop)
            if (result.isSuccess) {
                googleAuthManager.currentUserId?.let { uid ->
                    firestoreSyncManager.deleteShop(uid, shop.uuid)
                }
            }
            kotlinx.coroutines.withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    // --- Products ---

    fun addProduct(product: ShopProduct, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = shopBakiRepository.insertProduct(product)
            kotlinx.coroutines.withContext(Dispatchers.Main) { onCreated(id) }
            googleAuthManager.currentUserId?.let { uid ->
                firestoreSyncManager.pushShopProduct(uid, product.copy(id = id))
            }
        }
    }

    fun updateProduct(product: ShopProduct) {
        viewModelScope.launch(Dispatchers.IO) {
            shopBakiRepository.updateProduct(product)
            googleAuthManager.currentUserId?.let { uid ->
                firestoreSyncManager.pushShopProduct(uid, product)
            }
        }
    }

    fun deleteProduct(product: ShopProduct, onResult: (Result<Unit>) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = shopBakiRepository.deleteProduct(product)
            if (result.isSuccess) {
                googleAuthManager.currentUserId?.let { uid ->
                    // deleteProduct reports whether it soft- or hard-deleted:
                    //  - soft delete -> push the archived product (keeps remote in sync)
                    //  - hard delete -> remove the remote document
                    val softDeleted = result.getOrDefault(false)
                    if (softDeleted) {
                        firestoreSyncManager.pushShopProduct(uid, product.copy(isArchived = true))
                    } else {
                        firestoreSyncManager.deleteShopProduct(uid, product.uuid)
                    }
                }
            }
            kotlinx.coroutines.withContext(Dispatchers.Main) { onResult(result.map { }) }
        }
    }

    // --- Ledger Entries ---

    fun addLedgerEntry(entry: ShopLedgerEntry, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = shopBakiRepository.insertLedgerEntry(entry)
            kotlinx.coroutines.withContext(Dispatchers.Main) { onCreated(id) }

            // A PAYMENT is real money leaving the user's pocket -> it must also appear
            // in the app's Recent Transactions list (PURCHASE is credit, not yet spent).
            if (entry.type == "PAYMENT") {
                logPaymentToRecentTransactions(entry)
            }

            googleAuthManager.currentUserId?.let { uid ->
                // Resolve UUIDs for FK references
                val shop = shopBakiRepository.getShopById(entry.shopId).firstOrNull()
                val shopUuid = shop?.uuid ?: ""
                val productUuid = entry.productId?.let { pid ->
                    shopBakiRepository.getProductUuidById(pid)
                }
                firestoreSyncManager.pushShopLedgerEntry(uid, entry, shopUuid, productUuid)
            }
        }
    }

    // Mirror a shop payment into the main transactions table so it shows up in
    // "Recent Transactions" and is included in expense totals/analytics.
    private suspend fun logPaymentToRecentTransactions(entry: ShopLedgerEntry) {
        runCatching {
            val shopName = shopBakiRepository.getShopById(entry.shopId).firstOrNull()?.name ?: "Shop"
            val categories = expenseRepository.allCategories.firstOrNull().orEmpty()
            val cat = categories.firstOrNull { it.type == "EXPENSE" && it.name.equals("Shopping", true) }
                ?: categories.firstOrNull { it.type == "EXPENSE" && it.name.equals("Groceries", true) }
                ?: categories.firstOrNull { it.type == "EXPENSE" }
            val account = expenseRepository.allAccounts.firstOrNull()?.firstOrNull()
            val tx = TransactionEntity(
                type = com.example.data.model.TransactionType.EXPENSE,
                amount = entry.amount,
                categoryId = cat?.id ?: 0,
                categoryName = cat?.name ?: "Shopping",
                categoryIcon = cat?.iconName ?: "storefront",
                categoryColorHex = cat?.colorHex ?: "#E11D48",
                accountId = account?.id ?: 1,
                accountName = account?.name ?: "Main Account",
                date = entry.date,
                note = entry.note?.takeIf { it.isNotBlank() } ?: "Paid shop baki to $shopName",
                merchant = shopName,
                paymentMethod = "Shop Baki",
                tags = "ShopBaki, Payment"
            )
            val newId = expenseRepository.insertTransaction(tx)
            googleAuthManager.currentUserId?.let { uid ->
                firestoreSyncManager.pushTransaction(uid, tx.copy(id = newId))
            }
        }
    }
    
    fun deleteLedgerEntry(entry: ShopLedgerEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            shopBakiRepository.deleteLedgerEntry(entry)
            googleAuthManager.currentUserId?.let { uid ->
                firestoreSyncManager.deleteShopLedgerEntry(uid, entry.uuid)
            }
        }
    }

    fun getShopTimeline(shopId: Long): Flow<List<ShopTimelineItem>> =
        shopBakiRepository.getShopTimeline(shopId)
        
    fun getShopById(shopId: Long): Flow<Shop?> =
        shopBakiRepository.getShopById(shopId)
}
