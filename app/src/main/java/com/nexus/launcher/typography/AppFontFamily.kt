package com.nexus.launcher.typography

import androidx.annotation.FontRes
import androidx.annotation.StringRes
import com.nexus.launcher.R

/** Supported font families for Nexus Launcher. */
enum class AppFontFamily(
    val key: String,
    @StringRes val displayNameRes: Int,
    val familyName: String? = null,
    @FontRes val fontResId: Int? = null
) {
    NEXUS_DEFAULT("nexus_default", R.string.font_family_nexus_default, null, R.font.google_sans_flex),
    SYSTEM("system", R.string.font_family_system, null, null),
    SANS_SERIF("sans_serif", R.string.font_family_sans_serif, "sans-serif", null),
    SERIF("serif", R.string.font_family_serif, "serif", null),
    MONOSPACE("monospace", R.string.font_family_monospace, "monospace", null),
    MEDIUM("sans_serif_medium", R.string.font_family_sans_serif_medium, "sans-serif-medium", null),
    ROBOTO("roboto", R.string.font_family_roboto, null, R.font.roboto),
    INTER("inter", R.string.font_family_inter, null, R.font.inter),
    MANROPE("manrope", R.string.font_family_manrope, null, R.font.manrope),
    IBM_PLEX_SANS("ibm_plex_sans", R.string.font_family_ibm_plex_sans, null, R.font.ibm_plex_sans),
    LORA("lora", R.string.font_family_lora, null, R.font.lora),
    PLAYPEN_SANS("playpen_sans", R.string.font_family_playpen_sans, null, R.font.playpen_sans),
    ELMS_SANS("elms_sans", R.string.font_family_elms_sans, null, R.font.elms_sans),
    BITCOUNT_SINGLE("bitcount_single", R.string.font_family_bitcount_single, null, R.font.bitcount_single);

    companion object {
        fun fromKey(key: String?): AppFontFamily {
            if (key.isNullOrEmpty()) return NEXUS_DEFAULT
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: NEXUS_DEFAULT
        }
    }
}
