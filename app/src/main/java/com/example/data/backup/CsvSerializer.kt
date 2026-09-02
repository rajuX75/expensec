package com.example.data.backup

import com.example.data.model.TransactionEntity
import java.text.SimpleDateFormat
import java.util.*

/**
 * Exports transaction data to a spreadsheet-compatible CSV string.
 * Extracted from BackupSerializer.kt for single-responsibility.
 */
object CsvSerializer {

    /**
     * Exports transactions including EXPENSE, INCOME, and TRANSFER to a CSV string.
     */
    fun exportTransactionsToCsv(transactions: List<TransactionEntity>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val sb = StringBuilder()

        // CSV Header
        sb.append("UUID,ID,Type,Amount,Currency,Category,Account,ToAccount,Timestamp,FormattedDate,Merchant,Note,Tags,PaymentMethod,IsRecurring,RecurringPeriod\n")

        transactions.forEach { tx ->
            val formattedDate = dateFormat.format(Date(tx.date))
            val toAccount = tx.toAccountName ?: ""
            val recurringPeriod = tx.recurringPeriod ?: ""

            val row = listOf(
                escapeCsv(tx.uuid),
                tx.id.toString(),
                escapeCsv(tx.type.name),
                String.format(Locale.US, "%.2f", tx.amount),
                escapeCsv(tx.currency),
                escapeCsv(tx.categoryName),
                escapeCsv(tx.accountName),
                escapeCsv(toAccount),
                tx.date.toString(),
                escapeCsv(formattedDate),
                escapeCsv(tx.merchant),
                escapeCsv(tx.note),
                escapeCsv(tx.tags),
                escapeCsv(tx.paymentMethod),
                tx.isRecurring.toString(),
                escapeCsv(recurringPeriod)
            )
            sb.append(row.joinToString(",")).append("\n")
        }

        return sb.toString()
    }

    private fun escapeCsv(value: String): String {
        var result = value
        if (result.contains(",") || result.contains("\"") || result.contains("\n") || result.contains("\r")) {
            result = result.replace("\"", "\"\"")
            result = "\"$result\""
        }
        return result
    }
}
