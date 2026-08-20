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
            onCreated(id)
            googleAuthManager.currentUserId?.let { uid ->
                firestoreSyncManager.pushShop(uid, shop)
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
            launch(Dispatchers.Main) { onResult(result) }
        }
    }

    // --- Products ---

    fun addProduct(product: ShopProduct, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = shopBakiRepository.insertProduct(product)
            onCreated(id)
            googleAuthManager.currentUserId?.let { uid ->
                firestoreSyncManager.pushShopProduct(uid, product)
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
                    // Since it might be a soft delete (isArchived=true), push the update if so, or delete if hard deleted.
                    // The easiest way is to re-fetch the product by ID to see if it was archived.
                    // But actually, deleteProduct returns Result. If it was soft-deleted, it's technically still there.
                    // We can just try pushing the archived version.
                    // Or, even simpler: just push the product.copy(isArchived = true) - but only if it had entries.
                    // To keep it clean, we don't know here. The repository handled the logic.
                    // We should just trigger a full sync or assume it was handled.
                    // For now, let's just trigger a full sync to ensure consistency.
                    firestoreSyncManager.syncAll(uid)
                }
            }
            launch(Dispatchers.Main) { onResult(result) }
        }
    }

    // --- Ledger Entries ---

    fun addLedgerEntry(entry: ShopLedgerEntry, onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val id = shopBakiRepository.insertLedgerEntry(entry)
            onCreated(id)
            googleAuthManager.currentUserId?.let { uid ->
                // Look up UUIDs for FKs
                val shop = shopBakiRepository.getShopById(entry.shopId).firstOrNull()
                val shopUuid = shop?.uuid ?: ""
                var productUuid: String? = null
                if (entry.productId != null) {
                   // actually we don't have a simple getProductById sync in repo right now, let's just trigger a syncAll if we can't get it easily.
                   // Or add a helper to repo.
                   firestoreSyncManager.syncAll(uid)
                } else {
                   firestoreSyncManager.pushShopLedgerEntry(uid, entry, shopUuid, null)
                }
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
