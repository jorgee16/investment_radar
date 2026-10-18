package com.investmentradar.desktop.format

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object FinancialFormatter {
    fun price(value:Double?, currency:String?):String {
        if (value == null || !value.isFinite()) return "—"
        if (currency.isNullOrBlank() || currency.length != 3) return "%.2f".format(Locale.US,value)
        return runCatching {
            NumberFormat.getCurrencyInstance(Locale.US).apply {
                this.currency = Currency.getInstance(currency)
                maximumFractionDigits = 2
            }.format(value)
        }.getOrElse { "%.2f %s".format(Locale.US,value,currency) }
    }
    fun percent(value:Double?):String =
        if (value == null || !value.isFinite()) "—" else "%+.2f%%".format(Locale.US,value)
}
