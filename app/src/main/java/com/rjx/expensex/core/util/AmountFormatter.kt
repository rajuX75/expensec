package com.rjx.expensex.core.util

import java.util.Locale

/**
 * Central amount formatter that honours the user's "Decimal Precision"
 * setting (Settings → Decimal Precision).
 *
 * Bug fix: previously every screen hard-coded "%.2f" / "%,.2f", so choosing
 * "No decimals" in settings had no effect and amounts were still shown with
 * decimals. All display code now routes through [AmountFormatter.format],
 * which is kept in sync with the stored preference by
 * [com.rjx.expensex.data.repository.UserPreferencesRepository].
 */
object AmountFormatter {
    @Volatile
    var decimalPlaces: Int = 2

    fun format(amount: Double): String =
        String.format(Locale.US, "%,.${decimalPlaces}f", amount)
}
