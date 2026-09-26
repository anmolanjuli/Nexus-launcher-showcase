package com.nexus.launcher.ui.canvas

import java.text.Normalizer
import java.util.Locale

/**
 * Supplies locale-aware alphabet letter lists for the drawer scrub rail.
 * Supports Latin (A-Z), Spanish (with Ñ), Arabic (28 letters), Hebrew (22 letters), and Devanagari (Hindi/Nepali).
 */
object DrawerAlphabetHelper {

    private val ENGLISH_ALPHABET: List<Char> = ('A'..'Z').toList()

    // Spanish 27 letters (includes Ñ)
    private val SPANISH_ALPHABET: List<Char> = listOf(
        'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'Ñ',
        'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z'
    )

    // 28 standard Arabic letters + '#' for Latin/other apps
    private val ARABIC_ALPHABET: List<Char> = listOf(
        'أ', 'ب', 'ت', 'ث', 'ج', 'ح', 'خ', 'د', 'ذ', 'ر', 'ز', 'س', 'ش', 'ص',
        'ض', 'ط', 'ظ', 'ع', 'غ', 'ف', 'ق', 'ك', 'ل', 'م', 'ن', 'ه', 'و', 'ي', '#'
    )

    // 22 Hebrew letters + '#' for Latin/other apps
    private val HEBREW_ALPHABET: List<Char> = listOf(
        'א', 'ב', 'ג', 'ד', 'ה', 'ו', 'ז', 'ח', 'ט', 'י', 'כ', 'ל', 'מ', 'נ',
        'ס', 'ע', 'פ', 'צ', 'ק', 'ר', 'ש', 'ת', '#'
    )

    // Devanagari independent vowels + primary consonants + '#' for Latin/other apps
    private val DEVANAGARI_ALPHABET: List<Char> = listOf(
        'अ', 'आ', 'इ', 'ई', 'उ', 'ऊ', 'ए', 'ऐ', 'ओ', 'औ',
        'क', 'ख', 'ग', 'घ', 'च', 'छ', 'ज', 'झ', 'ट', 'ठ', 'ड', 'ढ', 'त', 'थ',
        'द', 'ध', 'न', 'प', 'फ', 'ब', 'भ', 'म', 'य', 'र', 'ल', 'व', 'श', 'स', 'ह', '#'
    )

    fun getAlphabet(locale: Locale): List<Char> {
        return when (locale.language.lowercase()) {
            "ar" -> ARABIC_ALPHABET
            "iw", "he" -> HEBREW_ALPHABET
            "ne", "hi" -> DEVANAGARI_ALPHABET
            "es" -> SPANISH_ALPHABET
            else -> ENGLISH_ALPHABET
        }
    }

    /**
     * Determines the rail letter for a given item label under the current locale.
     * Keeps characters present in the active alphabet (such as 'Ñ' in Spanish),
     * normalizes accented Latin letters to their base form (e.g. 'É' -> 'E', 'Ä' -> 'A'),
     * and maps non-alphabet initial characters to '#'.
     */
    fun getRailLetter(label: String, locale: Locale): Char {
        // Called for every app, per rail letter, on every rail frame: memoize per language so the
        // accent normalization runs once per label instead of thousands of times a frame.
        // Main thread only (drawing), so no synchronization.
        if (locale.language != cachedLanguage || letterCache.size > MAX_CACHED_LABELS) {
            letterCache.clear()
            cachedLanguage = locale.language
        }
        return letterCache.getOrPut(label) { computeRailLetter(label, locale) }
    }

    private const val MAX_CACHED_LABELS = 4000
    private var cachedLanguage: String? = null
    private val letterCache = HashMap<String, Char>()

    private fun computeRailLetter(label: String, locale: Locale): Char {
        val firstChar = label.trim().firstOrNull() ?: return '#'
        val upper = firstChar.uppercaseChar()
        val alphabet = getAlphabet(locale)

        if (upper in alphabet) {
            return upper
        }

        val normalized = Normalizer.normalize(upper.toString(), Normalizer.Form.NFD)
        val baseChar = normalized.firstOrNull { it in 'A'..'Z' }
        if (baseChar != null && baseChar in alphabet) {
            return baseChar
        }

        return '#'
    }
}
