package com.example.data.local

import androidx.room.*
import com.example.data.model.Shop
import com.example.data.model.ShopLedgerEntry
import com.example.data.model.ShopProduct
import kotlinx.coroutines.flow.Flow

@Dao
interface ShopDao {
    @Query("SELECT * FROM shops ORDER BY name ASC")
    fun getAllShops(): Flow<List<Shop>>

    @Query("SELECT * FROM shops ORDER BY name ASC")
    suspend fun getAllShopsSync(): List<Shop>

    @Query("SELECT * FROM shops WHERE id = :id LIMIT 1")
    fun getShopById(id: Long): Flow<Shop?>

    @Query("SELECT * FROM shops WHERE id = :id LIMIT 1")
    suspend fun getShopByIdSync(id: Long): Shop?

    @Query("SELECT * FROM shops WHERE uuid = :uuid LIMIT 1")
    suspend fun getShopByUuid(uuid: String): Shop?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertShop(shop: Shop): Long

    @Update
    suspend fun updateShop(shop: Shop)

    @Delete
    suspend fun deleteShop(shop: Shop)

    @Query("DELETE FROM shops WHERE uuid = :uuid")
    suspend fun deleteShopByUuid(uuid: String)

    @Query("DELETE FROM shops")
    suspend fun deleteAllShops()
}

@Dao
interface ShopProductDao {
    @Query("SELECT * FROM shop_products WHERE isArchived = 0 ORDER BY name ASC")
    fun getActiveProducts(): Flow<List<ShopProduct>>

    @Query("SELECT * FROM shop_products ORDER BY name ASC")
    suspend fun getAllProductsSync(): List<ShopProduct>

    @Query("SELECT * FROM shop_products WHERE id = :id LIMIT 1")
    suspend fun getProductByIdSync(id: Long): ShopProduct?

    @Query("SELECT * FROM shop_products WHERE uuid = :uuid LIMIT 1")
    suspend fun getProductByUuid(uuid: String): ShopProduct?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ShopProduct): Long

    @Update
    suspend fun updateProduct(product: ShopProduct)

    @Delete
    suspend fun deleteProduct(product: ShopProduct)

    @Query("DELETE FROM shop_products WHERE uuid = :uuid")
    suspend fun deleteProductByUuid(uuid: String)

    @Query("DELETE FROM shop_products")
    suspend fun deleteAllProducts()
}

@Dao
interface ShopLedgerEntryDao {
    @Query("SELECT * FROM shop_ledger_entries ORDER BY date DESC")
    fun getAllEntries(): Flow<List<ShopLedgerEntry>>

    @Query("SELECT * FROM shop_ledger_entries ORDER BY date DESC")
    suspend fun getAllEntriesSync(): List<ShopLedgerEntry>

    @Query("SELECT * FROM shop_ledger_entries WHERE shopId = :shopId ORDER BY date DESC")
    fun getEntriesForShop(shopId: Long): Flow<List<ShopLedgerEntry>>
    
    @Query("SELECT * FROM shop_ledger_entries WHERE shopId = :shopId ORDER BY date ASC")
    suspend fun getEntriesForShopAscSync(shopId: Long): List<ShopLedgerEntry>

    @Query("SELECT * FROM shop_ledger_entries WHERE id = :id LIMIT 1")
    suspend fun getEntryByIdSync(id: Long): ShopLedgerEntry?

    @Query("SELECT * FROM shop_ledger_entries WHERE uuid = :uuid LIMIT 1")
    suspend fun getEntryByUuid(uuid: String): ShopLedgerEntry?

    @Query("SELECT COUNT(*) FROM shop_ledger_entries WHERE shopId = :shopId")
    suspend fun getEntryCountForShop(shopId: Long): Int
    
    @Query("SELECT COUNT(*) FROM shop_ledger_entries WHERE productId = :productId")
    suspend fun getEntryCountForProduct(productId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: ShopLedgerEntry): Long

    @Update
    suspend fun updateEntry(entry: ShopLedgerEntry)

    @Delete
    suspend fun deleteEntry(entry: ShopLedgerEntry)

    @Query("DELETE FROM shop_ledger_entries WHERE uuid = :uuid")
    suspend fun deleteEntryByUuid(uuid: String)

    @Query("DELETE FROM shop_ledger_entries")
    suspend fun deleteAllLedgerEntries()
}
