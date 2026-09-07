package com.rjx.expensex.data.local

import androidx.room.TypeConverter
import com.rjx.expensex.data.model.TransactionType

class RoomConverters {

    @TypeConverter
    fun fromTransactionType(value: TransactionType?): String? {
        return value?.name
    }

    @TypeConverter
    fun toTransactionType(value: String?): TransactionType? {
        return value?.let { 
            try {
                TransactionType.valueOf(it.uppercase())
            } catch (e: IllegalArgumentException) {
                null
            }
        }
    }
}
