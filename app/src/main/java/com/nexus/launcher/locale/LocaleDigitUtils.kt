package com.nexus.launcher.locale

import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Utility for localizing numeric digits.
 * Verified correct for standard Latin ('0'..'9') and Devanagari/Nepali ('०'..'९').
 * Other script numeral systems will require explicit verification before claiming full support.
 */
object LocaleDigitUtils {

    /**
     * Converts any ASCII Latin digits ('0'..'9') in [text] to the native numeral glyphs
     * of [locale] (e.g. '०'..'९' for Nepali/Devanagari).
     * Returns [text] unmodified for Latin-numeral locales (English, etc.).
     */
    fun localizeDigits(text: String, locale: Locale): String {
        val zeroDigit = DecimalFormatSymbols.getInstance(locale).zeroDigit
        if (zeroDigit == '0') return text
        val diff = zeroDigit - '0'
        val chars = text.toCharArray()
        for (i in chars.indices) {
            val c = chars[i]
            if (c in '0'..'9') {
                chars[i] = c + diff
            }
        }
        return String(chars)
    }

    /** Localizes an integer value directly into the numeral system of [locale]. */
    fun formatNumber(value: Int, locale: Locale): String {
        return localizeDigits(value.toString(), locale)
    }

    /**
     * Localizes a percentage value (0..100) using the locale's percent conventions and numerals.
     */
    fun formatPercent(value: Int, locale: Locale): String {
        return java.text.NumberFormat.getPercentInstance(locale).apply {
            maximumFractionDigits = 0
            minimumFractionDigits = 0
        }.format(value / 100.0)
    }

    /**
     * Localizes a percentage value (0..100) using the current Context configuration locale.
     */
    fun formatPercent(value: Int, context: android.content.Context): String {
        val locale = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
        return formatPercent(value, locale)
    }
}
