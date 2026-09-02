package com.example

import com.example.domain.usecase.SuggestCategoryUseCase
import com.example.data.model.CategoryEntity
import com.example.data.model.TransactionEntity
import com.example.data.model.TransactionType
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for [SuggestCategoryUseCase].
 *
 * Verifies:
 * 1. Historical match takes precedence over keyword heuristics.
 * 2. Keyword heuristics work correctly for each category bucket.
 * 3. Returns null for an unknown merchant.
 * 4. Blank merchant returns null without throwing.
 */
class SuggestCategoryUseCaseTest {

    private val useCase = SuggestCategoryUseCase()

    private fun cat(id: Long, name: String) = CategoryEntity(
        id = id, name = name, iconName = "category", colorHex = "#000", type = "EXPENSE"
    )

    private fun tx(merchant: String, categoryId: Long, categoryName: String) = TransactionEntity(
        type = TransactionType.EXPENSE,
        amount = 10.0,
        merchant = merchant,
        categoryId = categoryId,
        categoryName = categoryName
    )

    @Test
    fun `returns null for blank merchant`() {
        val result = useCase("  ", emptyList(), listOf(cat(1, "Food")))
        assertNull(result)
    }

    @Test
    fun `returns null for completely unknown merchant`() {
        val result = useCase("ZzZUnknownShop", emptyList(), listOf(cat(1, "Food")))
        assertNull(result)
    }

    @Test
    fun `historical match takes priority over keyword heuristics`() {
        // "Starbucks" would normally match Food by keyword.
        // But the transaction history says categoryId=7 (Salary), so history wins.
        val categories = listOf(cat(1, "Food"), cat(7, "Salary"))
        val history = listOf(tx("Starbucks", 7, "Salary"))
        val result = useCase("Starbucks", history, categories)
        assertEquals(7L, result?.id)
    }

    @Test
    fun `keyword heuristic matches food category for restaurant merchant`() {
        val categories = listOf(cat(1, "Food"), cat(2, "Transport"))
        val result = useCase("Chipotle", emptyList(), categories)
        assertEquals(1L, result?.id)
    }

    @Test
    fun `keyword heuristic matches transport for uber`() {
        val categories = listOf(cat(1, "Food"), cat(2, "Transport"))
        val result = useCase("Uber Eats", emptyList(), categories)
        // "uber" keyword → Transport
        assertEquals(2L, result?.id)
    }

    @Test
    fun `keyword heuristic matches groceries for walmart`() {
        val categories = listOf(cat(1, "Groceries"), cat(2, "Food"))
        val result = useCase("Walmart Supercenter", emptyList(), categories)
        assertEquals(1L, result?.id)
    }

    @Test
    fun `keyword heuristic matches subscription for netflix`() {
        val categories = listOf(cat(1, "Subscription"), cat(2, "Food"))
        val result = useCase("Netflix", emptyList(), categories)
        assertEquals(1L, result?.id)
    }

    @Test
    fun `keyword heuristic matches health for pharmacy`() {
        val categories = listOf(cat(1, "Health"), cat(2, "Food"))
        val result = useCase("CVS Pharmacy", emptyList(), categories)
        assertEquals(1L, result?.id)
    }

    @Test
    fun `merchant lookup is case insensitive`() {
        val categories = listOf(cat(1, "Food"))
        val history = listOf(tx("chipotle", 1, "Food"))
        val result = useCase("CHIPOTLE", history, categories)
        assertEquals(1L, result?.id)
    }
}
