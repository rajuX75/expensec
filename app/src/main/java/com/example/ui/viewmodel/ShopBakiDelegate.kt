package com.example.ui.viewmodel

import com.example.data.cloud.FirestoreSyncManager
import com.example.data.cloud.GoogleAuthManager
import com.example.data.model.Shop
import com.example.data.model.ShopLedgerEntry
import com.example.data.model.ShopProduct
import com.example.data.model.ShopTimelineItem
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
