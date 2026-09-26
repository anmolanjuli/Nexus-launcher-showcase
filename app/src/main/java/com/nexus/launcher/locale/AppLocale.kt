package com.nexus.launcher.locale

import com.nexus.launcher.R

/** Supported in-app locales for Nexus Launcher. */
enum class AppLocale(val tag: String, val displayNameRes: Int) {
    SYSTEM("", R.string.locale_system_default),
    ENGLISH("en", R.string.locale_english),
    NEPALI("ne", R.string.locale_nepali),
    ARABIC("ar", R.string.locale_arabic),
    HEBREW("iw", R.string.locale_hebrew),
    SPANISH("es", R.string.locale_spanish),
    PORTUGUESE_BR("pt-BR", R.string.locale_portuguese_br),
    HINDI("hi", R.string.locale_hindi),
    INDONESIAN("id", R.string.locale_indonesian),
    GERMAN("de", R.string.locale_german),
    FRENCH("fr", R.string.locale_french);

    companion object {
        private val ALIASES = mapOf(
            "in" to "id",
            "he" to "iw",
            "pt-rbr" to "pt-br",
            "pt_br" to "pt-br"
        )

        private fun normalize(tag: String?): String {
            if (tag.isNullOrEmpty()) return ""
            val clean = tag.lowercase().replace('_', '-')
            return ALIASES[clean] ?: clean
        }

        fun fromTag(tag: String?): AppLocale {
            val norm = normalize(tag)
            if (norm.isEmpty()) return SYSTEM
            return entries.firstOrNull { normalize(it.tag) == norm } ?: SYSTEM
        }
    }
}
