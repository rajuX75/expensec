package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Skill: algorithmic-color-palette & color-mode-and-theme (Dembrandt Stage 1).
 *
 * Neutrals are tinted with the brand slate hue (Slate 50–950), avoiding harsh
 * pure #000000 on #FFFFFF.
 *
 * Semantic colors (Income, Expense, Warning, Transfer) are balanced in perceived
 * saturation and lightness, and provide dual-mode container variants so that alert
 * cards in Dark Mode do not produce glaring pastel flashes.
 */

// Primary Emerald Palette (Brand)
val Emerald900 = Color(0xFF003822)
val Emerald800 = Color(0xFF004D30)
val Emerald700 = Color(0xFF006C44)
val Emerald600 = Color(0xFF00875A)
val Emerald500 = Color(0xFF10B981)
val Emerald400 = Color(0xFF34D399)
val Emerald200 = Color(0xFFA7F3D0)
val Emerald100 = Color(0xFFD1FAE5)
val Emerald50 = Color(0xFFECFDF5)

// Secondary Cool Slate Neutrals
val Slate950 = Color(0xFF020617)
val Slate900 = Color(0xFF0F172A)
val Slate800 = Color(0xFF1E293B)
val Slate700 = Color(0xFF334155)
val Slate600 = Color(0xFF475569)
val Slate500 = Color(0xFF64748B)
val Slate400 = Color(0xFF94A3B8)
val Slate300 = Color(0xFFCBD5E1)
val Slate200 = Color(0xFFE2E8F0)
val Slate100 = Color(0xFFF1F5F9)
val Slate50 = Color(0xFFF8FAFC)

// Financial Semantic Accents — Base
val ExpenseRed = Color(0xFFE11D48)
val ExpenseRedLight = Color(0xFFFFE4E6)
val ExpenseRedDarkContainer = Color(0xFF4C1D24)
val ExpenseRedDarkText = Color(0xFFFDA4AF)

val IncomeGreen = Color(0xFF059669)
val IncomeGreenLight = Color(0xFFD1FAE5)
val IncomeGreenDarkContainer = Color(0xFF063023)
val IncomeGreenDarkText = Color(0xFF6EE7B7)
val ExpenseGreen = IncomeGreen

val TransferBlue = Color(0xFF2563EB)
val TransferBlueLight = Color(0xFFDBEAFE)
val TransferBlueDarkContainer = Color(0xFF13284C)
val TransferBlueDarkText = Color(0xFF93C5FD)

val AmberAccent = Color(0xFFF59E0B)
val AmberLight = Color(0xFFFEF3C7)
val AmberDarkContainer = Color(0xFF452404)
val AmberDarkText = Color(0xFFFCD34D)

val PurpleAccent = Color(0xFF8B5CF6)
val PurpleLight = Color(0xFFEDE9FE)
val PurpleDarkContainer = Color(0xFF2E1065)
val PurpleDarkText = Color(0xFFC4B5FD)

// Card & Surface background
val CardDark = Color(0xFF1E293B)
val CardLight = Color(0xFFFFFFFF)
val SurfaceVariantDark = Color(0xFF26334D)
val SurfaceVariantLight = Color(0xFFF1F5F9)

/**
 * High-level semantic palette specifically for financial management.
 * Guarantees proper contrast and container depth in both Light and Dark modes.
 */
@Immutable
data class FinancialColors(
    val income: Color,
    val onIncome: Color,
    val incomeContainer: Color,
    val onIncomeContainer: Color,
    val expense: Color,
    val onExpense: Color,
    val expenseContainer: Color,
    val onExpenseContainer: Color,
    val warning: Color,
    val onWarning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val transfer: Color,
    val onTransfer: Color,
    val transferContainer: Color,
    val onTransferContainer: Color
)

val LightFinancialColors = FinancialColors(
    income = IncomeGreen,
    onIncome = Color.White,
    incomeContainer = IncomeGreenLight,
    onIncomeContainer = Emerald900,
    expense = ExpenseRed,
    onExpense = Color.White,
    expenseContainer = ExpenseRedLight,
    onExpenseContainer = Color(0xFF881337),
    warning = Color(0xFFB45309),
    onWarning = Color.White,
    warningContainer = AmberLight,
    onWarningContainer = Color(0xFF78350F),
    transfer = TransferBlue,
    onTransfer = Color.White,
    transferContainer = TransferBlueLight,
    onTransferContainer = Color(0xFF1E3A8A)
)

val DarkFinancialColors = FinancialColors(
    income = Emerald400,
    onIncome = Emerald900,
    incomeContainer = IncomeGreenDarkContainer,
    onIncomeContainer = IncomeGreenDarkText,
    expense = Color(0xFFFB7185),
    onExpense = Color(0xFF4C0519),
    expenseContainer = ExpenseRedDarkContainer,
    onExpenseContainer = ExpenseRedDarkText,
    warning = Color(0xFFFBBF24),
    onWarning = Color(0xFF451A03),
    warningContainer = AmberDarkContainer,
    onWarningContainer = AmberDarkText,
    transfer = Color(0xFF60A5FA),
    onTransfer = Color(0xFF172554),
    transferContainer = TransferBlueDarkContainer,
    onTransferContainer = TransferBlueDarkText
)

val LocalFinancialColors = staticCompositionLocalOf { LightFinancialColors }
