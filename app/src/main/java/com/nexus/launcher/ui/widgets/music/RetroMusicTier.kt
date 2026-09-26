package com.nexus.launcher.ui.widgets.music

/**
 * Responsive size tiers for Retro and First-Party Music Widgets.
 *
 * Sizing thresholds:
 * - Tier 1 (Tiny, 1x1): wDp < 115 && hDp < 115. Minimal play/pause only.
 * - Tier 2 (Compact, 2x1): hDp < 115 && wDp < 250. Album art (48dp), metadata, progress, transport.
 * - Tier 3 (Comfortable, 2x2, 3x2): hDp >= 115 && wDp < 250. 72dp art, full title/artist/timestamps, content progress.
 * - Tier 4 (Expanded, 4x2+): wDp >= 250 && hDp >= 115, or hDp >= 180. Full 100% fill with secondary era elements.
 */
enum class RetroMusicTier {
    TIER_1_TINY,
    TIER_2_COMPACT,
    TIER_3_COMFORTABLE,
    TIER_4_EXPANDED;

    companion object {
        fun resolve(wDp: Float, hDp: Float): RetroMusicTier {
            return when {
                wDp < 115f && hDp < 115f -> TIER_1_TINY
                hDp < 115f && wDp < 250f -> TIER_2_COMPACT
                wDp >= 250f && hDp >= 115f -> TIER_4_EXPANDED
                hDp >= 180f -> TIER_4_EXPANDED
                else -> TIER_3_COMFORTABLE
            }
        }
    }
}
