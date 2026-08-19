package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

enum class DhaarType {
    GIVEN,       // দিলাম / I lent / gave
    RECEIVED,    // পেলাম / I borrowed / received
    SETTLEMENT   // নিষ্পত্তি / Settlement / repayment
}

@Entity(tableName = "contacts")
data class Contact(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val name: String,
    val phoneNumber: String? = null,
    val photoUri: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "dhaar_entries",
    foreignKeys = [
        ForeignKey(
            entity = Contact::class,
            parentColumns = ["id"],
            childColumns = ["contactId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["contactId"]),
        Index(value = ["dueDate"])
    ]
)
data class DhaarEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uuid: String = UUID.randomUUID().toString(),
    val contactId: Long,
    val type: String, // "GIVEN", "RECEIVED", "SETTLEMENT"
    val amount: Double,
    val currencyCode: String = "USD",
    val date: Long = System.currentTimeMillis(),
    val dueDate: Long? = null,
    val note: String = "",
    val tagPhotoUri: String? = null,
    val linkedAccountId: Long? = null,
    val isSettlementGive: Boolean? = null // For SETTLEMENT: true = I paid them, false = They paid me
)

data class ContactWithBalance(
    val contact: Contact,
    val netBalance: Double, // positive = they owe you (পাওনা / Pabo), negative = you owe them (দেনা / Debo), zero = settled
    val totalGiven: Double,
    val totalReceived: Double,
    val totalSettled: Double,
    val lastEntryDate: Long?,
    val entryCount: Int,
    val hasOverdue: Boolean = false
)

data class DhaarDashboardSummary(
    val totalYouWillGet: Double, // Sum of positive balances (পাওনা)
    val totalYouWillPay: Double, // Sum of negative balances (দেনা)
    val netPosition: Double,     // totalYouWillGet - totalYouWillPay
    val activeContactsCount: Int,
    val settledContactsCount: Int
)

data class DhaarReminderItem(
    val entry: DhaarEntry,
    val contactName: String,
    val contactPhone: String?,
    val isOverdue: Boolean,
    val daysRemaining: Long
)
