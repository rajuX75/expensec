package com.example.data.local

import androidx.room.*
import com.example.data.model.Contact
import com.example.data.model.DhaarEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY name ASC")
    fun getAllContacts(): Flow<List<Contact>>

    @Query("SELECT * FROM contacts ORDER BY name ASC")
    suspend fun getAllContactsSync(): List<Contact>

    @Query("SELECT * FROM contacts WHERE id = :id LIMIT 1")
    fun getContactById(id: Long): Flow<Contact?>

    @Query("SELECT * FROM contacts WHERE id = :id LIMIT 1")
    suspend fun getContactByIdSync(id: Long): Contact?

    @Query("SELECT * FROM contacts WHERE uuid = :uuid LIMIT 1")
    suspend fun getContactByUuid(uuid: String): Contact?

    @Query("SELECT * FROM contacts WHERE name LIKE '%' || :query || '%' OR (phoneNumber IS NOT NULL AND phoneNumber LIKE '%' || :query || '%') ORDER BY name ASC")
    fun searchContacts(query: String): Flow<List<Contact>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: Contact): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContacts(contacts: List<Contact>)

    @Update
    suspend fun updateContact(contact: Contact)

    @Delete
    suspend fun deleteContact(contact: Contact)

    @Query("DELETE FROM contacts WHERE id = :id")
    suspend fun deleteContactById(id: Long)

    @Query("DELETE FROM contacts WHERE uuid = :uuid")
    suspend fun deleteContactByUuid(uuid: String)

    @Query("SELECT COUNT(*) FROM contacts")
    suspend fun getContactCount(): Int

    @Query("DELETE FROM contacts")
    suspend fun deleteAllContacts()
}

@Dao
interface DhaarEntryDao {
    @Query("SELECT * FROM dhaar_entries ORDER BY date DESC")
    fun getAllEntries(): Flow<List<DhaarEntry>>

    @Query("SELECT * FROM dhaar_entries ORDER BY date DESC")
    suspend fun getAllEntriesSync(): List<DhaarEntry>

    @Query("SELECT * FROM dhaar_entries WHERE contactId = :contactId ORDER BY date DESC")
    fun getEntriesForContact(contactId: Long): Flow<List<DhaarEntry>>

    @Query("SELECT * FROM dhaar_entries WHERE contactId = :contactId ORDER BY date DESC")
    suspend fun getEntriesForContactSync(contactId: Long): List<DhaarEntry>

    @Query("SELECT * FROM dhaar_entries WHERE id = :id LIMIT 1")
    fun getEntryById(id: Long): Flow<DhaarEntry?>

    @Query("SELECT * FROM dhaar_entries WHERE id = :id LIMIT 1")
    suspend fun getEntryByIdSync(id: Long): DhaarEntry?

    @Query("SELECT * FROM dhaar_entries WHERE uuid = :uuid LIMIT 1")
    suspend fun getEntryByUuid(uuid: String): DhaarEntry?

    @Query("SELECT * FROM dhaar_entries WHERE dueDate IS NOT NULL AND dueDate > 0 ORDER BY dueDate ASC")
    fun getUpcomingDueEntries(): Flow<List<DhaarEntry>>

    @Query("DELETE FROM dhaar_entries WHERE uuid = :uuid")
    suspend fun deleteEntryByUuid(uuid: String)

    @Query("SELECT COUNT(*) FROM dhaar_entries WHERE contactId = :contactId")
    suspend fun getEntryCountForContact(contactId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: DhaarEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntries(entries: List<DhaarEntry>)

    @Update
    suspend fun updateEntry(entry: DhaarEntry)

    @Delete
    suspend fun deleteEntry(entry: DhaarEntry)

    @Query("DELETE FROM dhaar_entries WHERE id = :id")
    suspend fun deleteEntryById(id: Long)

    @Query("DELETE FROM dhaar_entries WHERE contactId = :contactId")
    suspend fun deleteEntriesForContact(contactId: Long)

    @Query("DELETE FROM dhaar_entries")
    suspend fun deleteAllEntries()
}
