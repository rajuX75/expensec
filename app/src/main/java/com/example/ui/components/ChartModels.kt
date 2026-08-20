package com.example.ui.components

/**
 * Data models used by the financial chart composables.
 * Extracted from FinancialCharts.kt for single-responsibility.
 */

data class ChartCategoryData(
    val name: String,
    val amount: Double,
    val percentage: Float,
    val colorHex: String,
    val iconName: String
)

data class BarChartEntry(
    val label: String,
    val expense: Double,
    val income: Double
)

data class MonthlyCategoryTrendEntry(
    val monthLabel: String,
    val year: Int,
    val month: Int,
    val totalExpense: Double,
    val categoryAmounts: Map<String, Double>
)

data class CategoryTrendMeta(
    val name: String,
    val colorHex: String,
    val iconName: String,
    val totalAcrossMonths: Double
)
