package io.seatlayer.android.compose

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** How every amount in the native chrome is rendered. */
public fun interface SeatLayerMoneyFormatter {
    public fun format(amount: Double, currency: String): String

    public companion object {
        /**
         * The platform's own formatting for [locale].
         *
         * A currency this device does not know keeps its code beside the
         * number rather than being dressed as the locale's own money.
         */
        @JvmStatic
        @JvmOverloads
        public fun localized(locale: Locale = Locale.getDefault()): SeatLayerMoneyFormatter =
            SeatLayerMoneyFormatter { amount, currency ->
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

/** The name this formatter had before the four SDKs agreed on one. */
@Deprecated("Renamed to SeatLayerMoneyFormatter.", ReplaceWith("SeatLayerMoneyFormatter"))
public typealias SeatLayerPickerMoneyFormatter = SeatLayerMoneyFormatter
