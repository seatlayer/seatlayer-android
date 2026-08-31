package io.seatlayer.android.compose

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

public fun interface SeatLayerPickerMoneyFormatter {
    public fun format(amount: Double, currency: String): String

    public companion object {
        @JvmStatic
        public fun localized(locale: Locale = Locale.getDefault()): SeatLayerPickerMoneyFormatter =
            SeatLayerPickerMoneyFormatter { amount, currency ->
                val formatter = NumberFormat.getCurrencyInstance(locale)
                val known = runCatching { Currency.getInstance(currency) }.getOrNull()
                if (known == null) {
                    val number = NumberFormat.getNumberInstance(locale).apply {
                        maximumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
                    }
                    "${number.format(amount)} $currency".trim()
                } else {
                    formatter.currency = known
                    formatter.maximumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
                    formatter.format(amount)
                }
            }
    }
}
