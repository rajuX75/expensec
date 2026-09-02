package com.example.domain.usecase

import com.example.data.cloud.FirestoreSyncManager
import com.example.data.model.AccountEntity
import com.example.data.model.BillEntity
import com.example.data.model.CategoryEntity
import com.example.data.model.TransactionEntity
import com.example.data.model.TransactionType
import com.example.data.repository.ExpenseRepository

class MarkBillPaidUseCase(
    private val repository: ExpenseRepository,
    private val firestoreSyncManager: FirestoreSyncManager,
    private val getCurrentUserId: () -> String?
) {
    suspend operator fun invoke(
        bill: BillEntity,
        accounts: List<AccountEntity>,
        categories: List<CategoryEntity>
    ) {
        val updated = bill.copy(isPaid = true, lastPaidDate = System.currentTimeMillis())
        repository.updateBill(updated)
        getCurrentUserId()?.let { uid ->
            firestoreSyncManager.pushBill(uid, updated)
        }

        if (bill.autoLogTransaction) {
            val account = accounts.find { it.id == bill.accountId } ?: accounts.firstOrNull()
            
            // Phase 4: Remove hardcoded icon/color strings
            val fallbackIcon = "bolt"
            val fallbackColor = "#06B6D4"
            val existingCategory = categories.find { it.id == bill.categoryId || it.name.equals(bill.categoryName, true) }
            val icon = existingCategory?.iconName ?: fallbackIcon
            val color = existingCategory?.colorHex ?: fallbackColor

            val tx = TransactionEntity(
                type = TransactionType.EXPENSE,
                amount = bill.amount,
                categoryId = bill.categoryId,
                categoryName = bill.categoryName,
                categoryIcon = icon,
                categoryColorHex = color,
                accountId = account?.id ?: 1,
                accountName = account?.name ?: "Main Account",
                date = System.currentTimeMillis(),
                note = "Paid Bill: ${bill.title}",
                merchant = bill.title,
                paymentMethod = "Auto Bill Pay",
                tags = "BillPay, ${bill.frequency}"
            )
            val newId = repository.insertTransaction(tx)
            getCurrentUserId()?.let { uid ->
                firestoreSyncManager.pushTransaction(uid, tx.copy(id = newId))
            }
        }
    }
}

class TransferFundsUseCase(
    private val repository: ExpenseRepository,
    private val firestoreSyncManager: FirestoreSyncManager,
    private val getCurrentUserId: () -> String?
) {
    suspend operator fun invoke(
        fromAccount: AccountEntity,
        toAccount: AccountEntity,
        amount: Double,
        note: String,
        date: Long = System.currentTimeMillis()
    ) {
        val transferTx = TransactionEntity(
            type = TransactionType.TRANSFER,
            amount = amount,
            accountId = fromAccount.id,
            accountName = fromAccount.name,
            toAccountId = toAccount.id,
            toAccountName = toAccount.name,
            categoryName = "Transfer",
            categoryIcon = "swap_horiz",
            categoryColorHex = "#3B82F6",
            date = date,
            note = note.ifBlank { "Transfer from ${fromAccount.name} to ${toAccount.name}" },
            merchant = "Internal Transfer",
            paymentMethod = "Transfer",
            tags = "Transfer"
        )
        val newId = repository.insertTransaction(transferTx)
        getCurrentUserId()?.let { uid ->
            firestoreSyncManager.pushTransaction(uid, transferTx.copy(id = newId))
        }
    }
}

class SuggestCategoryUseCase {
    operator fun invoke(
        merchant: String,
        transactions: List<TransactionEntity>,
        categories: List<CategoryEntity>
    ): CategoryEntity? {
        if (merchant.isBlank()) return null
        val lowerMerchant = merchant.lowercase().trim()
        
        // 1. Try to find a previous transaction with this exact merchant
        val match = transactions.firstOrNull { it.merchant.lowercase().trim() == lowerMerchant }
        if (match != null) {
            val found = categories.find { it.id == match.categoryId || it.name.equals(match.categoryName, true) }
            if (found != null) return found
        }
        
        // 2. Fallback to keyword heuristics
        return when {
            lowerMerchant.contains("food") || lowerMerchant.contains("restaurant") || lowerMerchant.contains("cafe") ||
            lowerMerchant.contains("starbucks") || lowerMerchant.contains("mcdonald") || lowerMerchant.contains("burger") ||
            lowerMerchant.contains("pizza") || lowerMerchant.contains("ramen") || lowerMerchant.contains("chipotle") -> {
                categories.find { it.name.contains("Food", ignoreCase = true) }
            }
            lowerMerchant.contains("grocery") || lowerMerchant.contains("market") || lowerMerchant.contains("walmart") ||
            lowerMerchant.contains("whole foods") || lowerMerchant.contains("trader joe") || lowerMerchant.contains("kroger") ||
            lowerMerchant.contains("target") || lowerMerchant.contains("supermarket") -> {
                categories.find { it.name.contains("Groceries", ignoreCase = true) }
            }
            lowerMerchant.contains("uber") || lowerMerchant.contains("lyft") || lowerMerchant.contains("gas") ||
            lowerMerchant.contains("shell") || lowerMerchant.contains("chevron") || lowerMerchant.contains("transit") ||
            lowerMerchant.contains("subway") || lowerMerchant.contains("train") || lowerMerchant.contains("airline") -> {
                categories.find { it.name.contains("Transport", ignoreCase = true) }
            }
            lowerMerchant.contains("netflix") || lowerMerchant.contains("spotify") || lowerMerchant.contains("youtube") ||
            lowerMerchant.contains("apple") || lowerMerchant.contains("hulu") || lowerMerchant.contains("disney") ||
            lowerMerchant.contains("patreon") || lowerMerchant.contains("subscription") -> {
                categories.find { it.name.contains("Subscription", ignoreCase = true) }
            }
            lowerMerchant.contains("rent") || lowerMerchant.contains("apartment") || lowerMerchant.contains("mortgage") ||
            lowerMerchant.contains("housing") -> {
                categories.find { it.name.contains("Housing", ignoreCase = true) }
            }
            lowerMerchant.contains("electric") || lowerMerchant.contains("power") || lowerMerchant.contains("water") ||
            lowerMerchant.contains("internet") || lowerMerchant.contains("wifi") || lowerMerchant.contains("verizon") ||
            lowerMerchant.contains("at&t") || lowerMerchant.contains("t-mobile") -> {
                categories.find { it.name.contains("Utilities", ignoreCase = true) }
            }
            lowerMerchant.contains("amazon") || lowerMerchant.contains("ebay") || lowerMerchant.contains("zara") ||
            lowerMerchant.contains("nike") || lowerMerchant.contains("h&m") || lowerMerchant.contains("clothing") ||
            lowerMerchant.contains("mall") -> {
                categories.find { it.name.contains("Shopping", ignoreCase = true) }
            }
            lowerMerchant.contains("pharmacy") || lowerMerchant.contains("cvs") || lowerMerchant.contains("walgreens") ||
            lowerMerchant.contains("doctor") || lowerMerchant.contains("dental") || lowerMerchant.contains("hospital") -> {
                categories.find { it.name.contains("Health", ignoreCase = true) }
            }
            lowerMerchant.contains("salary") || lowerMerchant.contains("payroll") || lowerMerchant.contains("wage") ||
            lowerMerchant.contains("bonus") -> {
                categories.find { it.name.contains("Salary", ignoreCase = true) || it.name.contains("Income", ignoreCase = true) }
            }
            else -> null
        }
    }
}
