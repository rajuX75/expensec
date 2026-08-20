package com.example.data.repository

/**
 * Currency data model and list of supported currencies.
 * Extracted from UserPreferencesRepository.kt for single-responsibility.
 */

data class CurrencyInfo(
    val code: String,
    val symbol: String,
    val name: String
)

val AvailableCurrencies = listOf(
    CurrencyInfo("USD", "$", "US Dollar ($)"),
    CurrencyInfo("EUR", "€", "Euro (€)"),
    CurrencyInfo("GBP", "£", "British Pound (£)"),
    CurrencyInfo("JPY", "¥", "Japanese Yen (¥)"),
    CurrencyInfo("INR", "₹", "Indian Rupee (₹)"),
    CurrencyInfo("BDT", "৳", "Bangladeshi Taka (৳)"),
    CurrencyInfo("CAD", "C$", "Canadian Dollar (C$)"),
    CurrencyInfo("AUD", "A$", "Australian Dollar (A$)"),
    CurrencyInfo("SGD", "S$", "Singapore Dollar (S$)"),
    CurrencyInfo("CHF", "CHF", "Swiss Franc (CHF)"),
    CurrencyInfo("CNY", "¥", "Chinese Yuan (¥)"),
    CurrencyInfo("BRL", "R$", "Brazilian Real (R$)"),
    CurrencyInfo("AED", "AED", "UAE Dirham (AED)")
)
