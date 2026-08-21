package com.example.data.cloud

import android.util.Log
import com.example.data.local.AppDatabase
import com.example.data.model.Shop
import com.example.data.model.ShopLedgerEntry
import com.example.data.model.ShopProduct
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class FirestoreShopBakiSyncer(
    private val firestore: FirebaseFirestore,
    private val database: AppDatabase
) {
    private val tag = "FirestoreShopBakiSyncer"

    fun attachShopsRealtimeListener(userId: String, scope: CoroutineScope): ListenerRegistration {
        return firestore.collection("users").document(userId)
            .collection("shops")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null && !snapshot.metadata.hasPendingWrites()) {
                    scope.launch(Dispatchers.IO) {
                        for (change in snapshot.documentChanges) {
                            if (change.type == com.google.firebase.firestore.DocumentChange.Type.REMOVED) {
                                runCatching { database.shopDao().deleteShopByUuid(change.document.id) }
                            }
                        }
                        for (doc in snapshot.documents) {
                            try {
                                val data = doc.data ?: continue
                                val uuid = doc.id
                                val existing = database.shopDao().getShopByUuid(uuid)
                                val shop = Shop(
                                    id = existing?.id ?: 0L,
                                    uuid = uuid,
                                    name = data["name"] as? String ?: "",
                                    phoneNumber = data["phoneNumber"] as? String,
                                    address = data["address"] as? String,
                                    note = data["note"] as? String,
                                    createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                                )
                                if (existing != null) {
                                    database.shopDao().updateShop(shop)
                                } else {
                                    database.shopDao().insertShop(shop)
                                }
                            } catch (e: Exception) {
                                Log.e(tag, "Error processing realtime shop: ${e.message}")
                            }
                        }
                    }
                }
            }
    }

    fun attachProductsRealtimeListener(userId: String, scope: CoroutineScope): ListenerRegistration {
        return firestore.collection("users").document(userId)
            .collection("shop_products")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null && !snapshot.metadata.hasPendingWrites()) {
                    scope.launch(Dispatchers.IO) {
                        for (change in snapshot.documentChanges) {
                            if (change.type == com.google.firebase.firestore.DocumentChange.Type.REMOVED) {
                                runCatching { database.shopProductDao().deleteProductByUuid(change.document.id) }
                            }
                        }
                        for (doc in snapshot.documents) {
                            try {
                                val data = doc.data ?: continue
                                val uuid = doc.id
                                val existing = database.shopProductDao().getProductByUuid(uuid)
                                val product = ShopProduct(
                                    id = existing?.id ?: 0L,
                                    uuid = uuid,
                                    name = data["name"] as? String ?: "",
                                    defaultUnit = data["defaultUnit"] as? String,
                                    defaultPrice = (data["defaultPrice"] as? Number)?.toDouble() ?: 0.0,
                                    isArchived = data["isArchived"] as? Boolean ?: false,
                                    createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
                                )
                                if (existing != null) {
                                    database.shopProductDao().updateProduct(product)
                                } else {
                                    database.shopProductDao().insertProduct(product)
                                }
                            } catch (e: Exception) {
                                Log.e(tag, "Error processing realtime shop product: ${e.message}")
                            }
                        }
                    }
                }
            }
    }

    fun attachEntriesRealtimeListener(userId: String, scope: CoroutineScope): ListenerRegistration {
        return firestore.collection("users").document(userId)
            .collection("shop_ledger_entries")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null && !snapshot.metadata.hasPendingWrites()) {
                    scope.launch(Dispatchers.IO) {
                        for (change in snapshot.documentChanges) {
                            if (change.type == com.google.firebase.firestore.DocumentChange.Type.REMOVED) {
                                runCatching { database.shopLedgerEntryDao().deleteEntryByUuid(change.document.id) }
                            }
                        }
                        for (doc in snapshot.documents) {
                            try {
                                val data = doc.data ?: continue
                                val uuid = doc.id
                                val shopUuid = data["shopUuid"] as? String ?: ""
                                val productUuid = data["productUuid"] as? String
                                
                                var localShop = database.shopDao().getShopByUuid(shopUuid)
                                if (localShop == null && shopUuid.isNotBlank()) {
                                    for (attempt in 1..3) {
                                        delay(500L)
                                        localShop = database.shopDao().getShopByUuid(shopUuid)
                                        if (localShop != null) break
                                    }
                                }
                                if (localShop == null) continue
                                
                                var localProduct: ShopProduct? = null
                                if (!productUuid.isNullOrBlank()) {
                                    localProduct = database.shopProductDao().getProductByUuid(productUuid)
                                    if (localProduct == null) {
                                        for (attempt in 1..3) {
                                            delay(500L)
                                            localProduct = database.shopProductDao().getProductByUuid(productUuid)
                                            if (localProduct != null) break
                                        }
                                    }
                                }

                                val existing = database.shopLedgerEntryDao().getEntryByUuid(uuid)
                                val entry = ShopLedgerEntry(
                                    id = existing?.id ?: 0L,
                                    uuid = uuid,
                                    shopId = localShop.id,
                                    type = data["type"] as? String ?: "PURCHASE",
                                    productId = localProduct?.id,
                                    quantity = (data["quantity"] as? Number)?.toDouble(),
                                    unitPriceAtPurchase = (data["unitPriceAtPurchase"] as? Number)?.toDouble(),
                                    amount = (data["amount"] as? Number)?.toDouble() ?: 0.0,
                                    date = (data["date"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                                    note = data["note"] as? String
                                )
                                database.shopLedgerEntryDao().insertEntry(entry)
                            } catch (e: Exception) {
                                Log.e(tag, "Error processing realtime ledger entry: ${e.message}")
                            }
                        }
                    }
                }
            }
    }

    suspend fun sync(userId: String) {
        val shopsCollection = firestore.collection("users").document(userId).collection("shops")
        val productsCollection = firestore.collection("users").document(userId).collection("shop_products")
        val entriesCollection = firestore.collection("users").document(userId).collection("shop_ledger_entries")

        // 1. Sync Shops
        val localShops = database.shopDao().getAllShopsSync()
        for (shop in localShops) {
            val docRef = shopsCollection.document(shop.uuid)
            val data = hashMapOf(
                "uuid" to shop.uuid,
                "name" to shop.name,
                "phoneNumber" to shop.phoneNumber,
                "address" to shop.address,
                "note" to shop.note,
                "createdAt" to shop.createdAt,
                "updatedAt" to System.currentTimeMillis()
            )
            docRef.set(data, SetOptions.merge()).await()
        }
        val remoteShops = shopsCollection.get(com.google.firebase.firestore.Source.SERVER).await().documents
        val remoteShopUuids = mutableSetOf<String>()
        for (doc in remoteShops) {
            val data = doc.data ?: continue
            val uuid = doc.id
            remoteShopUuids.add(uuid)
            val existing = database.shopDao().getShopByUuid(uuid)
            val shop = Shop(
                id = existing?.id ?: 0L,
                uuid = uuid,
                name = data["name"] as? String ?: "",
                phoneNumber = data["phoneNumber"] as? String,
                address = data["address"] as? String,
                note = data["note"] as? String,
                createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
            if (existing != null) database.shopDao().updateShop(shop) else database.shopDao().insertShop(shop)
        }
        // SAFETY: never run the delete pass when the server returned zero docs while local
        // data exists -- that would wipe local data on any failed/empty fetch.
        val shouldDeleteMissingShops = remoteShopUuids.isNotEmpty() || localShops.isEmpty()
        if (shouldDeleteMissingShops) for (local in localShops) {
            if (local.uuid.isNotBlank() && local.uuid !in remoteShopUuids) {
                val entryCount = database.shopLedgerEntryDao().getEntryCountForShop(local.id)
                if (entryCount == 0) {
                    runCatching { database.shopDao().deleteShopByUuid(local.uuid) }
                }
            }
        }

        // 2. Sync Products
        val localProducts = database.shopProductDao().getAllProductsSync()
        for (product in localProducts) {
            val docRef = productsCollection.document(product.uuid)
            val data = hashMapOf(
                "uuid" to product.uuid,
                "name" to product.name,
                "defaultUnit" to product.defaultUnit,
                "defaultPrice" to product.defaultPrice,
                "isArchived" to product.isArchived,
                "createdAt" to product.createdAt,
                "updatedAt" to System.currentTimeMillis()
            )
            docRef.set(data, SetOptions.merge()).await()
        }
        val remoteProducts = productsCollection.get(com.google.firebase.firestore.Source.SERVER).await().documents
        val remoteProductUuids = mutableSetOf<String>()
        for (doc in remoteProducts) {
            val data = doc.data ?: continue
            val uuid = doc.id
            remoteProductUuids.add(uuid)
            val existing = database.shopProductDao().getProductByUuid(uuid)
            val product = ShopProduct(
                id = existing?.id ?: 0L,
                uuid = uuid,
                name = data["name"] as? String ?: "",
                defaultUnit = data["defaultUnit"] as? String,
                defaultPrice = (data["defaultPrice"] as? Number)?.toDouble() ?: 0.0,
                isArchived = data["isArchived"] as? Boolean ?: false,
                createdAt = (data["createdAt"] as? Number)?.toLong() ?: System.currentTimeMillis()
            )
            if (existing != null) database.shopProductDao().updateProduct(product) else database.shopProductDao().insertProduct(product)
        }
        // SAFETY: same empty-fetch guard as shops.
        val shouldDeleteMissingProducts = remoteProductUuids.isNotEmpty() || localProducts.isEmpty()
        if (shouldDeleteMissingProducts) for (local in localProducts) {
            if (local.uuid.isNotBlank() && local.uuid !in remoteProductUuids) {
                val entryCount = database.shopLedgerEntryDao().getEntryCountForProduct(local.id)
                if (entryCount == 0) {
                    runCatching { database.shopProductDao().deleteProductByUuid(local.uuid) }
                }
            }
        }

        // 3. Sync Ledger Entries
        val localEntries = database.shopLedgerEntryDao().getAllEntriesSync()
        for (entry in localEntries) {
            val shop = database.shopDao().getShopByIdSync(entry.shopId)
            val product = entry.productId?.let { database.shopProductDao().getProductByIdSync(it) }
            val docRef = entriesCollection.document(entry.uuid)
            val data = hashMapOf(
                "uuid" to entry.uuid,
                "shopUuid" to (shop?.uuid ?: ""),
                "productUuid" to product?.uuid,
                "type" to entry.type,
                "quantity" to entry.quantity,
                "unitPriceAtPurchase" to entry.unitPriceAtPurchase,
                "amount" to entry.amount,
                "date" to entry.date,
                "note" to entry.note,
                "updatedAt" to System.currentTimeMillis()
            )
            docRef.set(data, SetOptions.merge()).await()
        }

        val remoteEntries = entriesCollection.get(com.google.firebase.firestore.Source.SERVER).await().documents
        val remoteEntryUuids = mutableSetOf<String>()
        for (doc in remoteEntries) {
            val data = doc.data ?: continue
            val uuid = doc.id
            remoteEntryUuids.add(uuid)
            
            val shopUuid = data["shopUuid"] as? String ?: ""
            val productUuid = data["productUuid"] as? String
            
            val localShop = database.shopDao().getShopByUuid(shopUuid) ?: continue
            val localProduct = if (!productUuid.isNullOrBlank()) database.shopProductDao().getProductByUuid(productUuid) else null

            val existing = database.shopLedgerEntryDao().getEntryByUuid(uuid)
            val entry = ShopLedgerEntry(
                id = existing?.id ?: 0L,
                uuid = uuid,
                shopId = localShop.id,
                type = data["type"] as? String ?: "PURCHASE",
                productId = localProduct?.id,
                quantity = (data["quantity"] as? Number)?.toDouble(),
                unitPriceAtPurchase = (data["unitPriceAtPurchase"] as? Number)?.toDouble(),
                amount = (data["amount"] as? Number)?.toDouble() ?: 0.0,
                date = (data["date"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                note = data["note"] as? String
            )
            database.shopLedgerEntryDao().insertEntry(entry)
        }

        // SAFETY: same empty-fetch guard as shops.
        val shouldDeleteMissingEntries = remoteEntryUuids.isNotEmpty() || localEntries.isEmpty()
        if (shouldDeleteMissingEntries) for (local in localEntries) {
            if (local.uuid.isNotBlank() && local.uuid !in remoteEntryUuids) {
                runCatching { database.shopLedgerEntryDao().deleteEntryByUuid(local.uuid) }
            }
        }
    }

    suspend fun pushShop(userId: String, shop: Shop) = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext
        try {
            firestore.collection("users").document(userId).collection("shops").document(shop.uuid)
                .set(hashMapOf(
                    "uuid" to shop.uuid,
                    "name" to shop.name,
                    "phoneNumber" to shop.phoneNumber,
                    "address" to shop.address,
                    "note" to shop.note,
                    "createdAt" to shop.createdAt,
                    "updatedAt" to System.currentTimeMillis()
                ), SetOptions.merge()).await()
        } catch (e: Exception) { Log.e(tag, "Failed to push shop: ${e.message}") }
    }

    suspend fun deleteShop(userId: String, uuid: String) = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext
        try { firestore.collection("users").document(userId).collection("shops").document(uuid).delete().await() }
        catch (e: Exception) { Log.e(tag, "Failed to delete shop: ${e.message}") }
    }
    
    suspend fun pushProduct(userId: String, product: ShopProduct) = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext
        try {
            firestore.collection("users").document(userId).collection("shop_products").document(product.uuid)
                .set(hashMapOf(
                    "uuid" to product.uuid,
                    "name" to product.name,
                    "defaultUnit" to product.defaultUnit,
                    "defaultPrice" to product.defaultPrice,
                    "isArchived" to product.isArchived,
                    "createdAt" to product.createdAt,
                    "updatedAt" to System.currentTimeMillis()
                ), SetOptions.merge()).await()
        } catch (e: Exception) { Log.e(tag, "Failed to push shop product: ${e.message}") }
    }
    
    suspend fun deleteProduct(userId: String, uuid: String) = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext
        try { firestore.collection("users").document(userId).collection("shop_products").document(uuid).delete().await() }
        catch (e: Exception) { Log.e(tag, "Failed to delete shop product: ${e.message}") }
    }

    suspend fun pushLedgerEntry(userId: String, entry: ShopLedgerEntry, shopUuid: String, productUuid: String?) = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext
        try {
            firestore.collection("users").document(userId).collection("shop_ledger_entries").document(entry.uuid)
                .set(hashMapOf(
                    "uuid" to entry.uuid,
                    "shopUuid" to shopUuid,
                    "productUuid" to productUuid,
                    "type" to entry.type,
                    "quantity" to entry.quantity,
                    "unitPriceAtPurchase" to entry.unitPriceAtPurchase,
                    "amount" to entry.amount,
                    "date" to entry.date,
                    "note" to entry.note,
                    "updatedAt" to System.currentTimeMillis()
                ), SetOptions.merge()).await()
        } catch (e: Exception) { Log.e(tag, "Failed to push ledger entry: ${e.message}") }
    }

    suspend fun deleteLedgerEntry(userId: String, uuid: String) = withContext(Dispatchers.IO) {
        if (userId.isBlank()) return@withContext
        try { firestore.collection("users").document(userId).collection("shop_ledger_entries").document(uuid).delete().await() }
        catch (e: Exception) { Log.e(tag, "Failed to delete ledger entry: ${e.message}") }
    }
}
