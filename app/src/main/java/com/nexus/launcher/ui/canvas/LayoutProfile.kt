package com.nexus.launcher.ui.canvas

import android.content.Context
import android.content.res.Configuration

/**
 * Screen size profile governing launcher layout modes.
 *
 * LARGE: smallestScreenWidthDp >= 600 (foldable inner screen, tablets, desktop windows).
 * COMPACT: standard phones and foldable cover screens.
 *
 * Phone landscape (side dock, swapped grid axes) applies ONLY to COMPACT in landscape.
 * LARGE always retains a bottom dock and unswapped grid in both orientations.
 */
enum class LayoutProfile {
    COMPACT,
    LARGE;

    companion object {
        const val LARGE_SCREEN_MIN_SW_DP = 600

        fun of(config: Configuration): LayoutProfile =
            if (config.smallestScreenWidthDp >= LARGE_SCREEN_MIN_SW_DP) LARGE else COMPACT

        fun of(context: Context): LayoutProfile =
            of(context.resources.configuration)

        fun isPhoneLandscape(config: Configuration): Boolean =
            of(config) == COMPACT && config.orientation == Configuration.ORIENTATION_LANDSCAPE

        fun isPhoneLandscape(context: Context): Boolean =
            isPhoneLandscape(context.resources.configuration)
    }
}
