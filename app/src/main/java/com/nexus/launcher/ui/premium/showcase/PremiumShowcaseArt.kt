package com.nexus.launcher.ui.premium.showcase

import android.content.Context
import android.view.View
import com.nexus.launcher.premium.PremiumFeature
import com.nexus.launcher.theme.NexusColorTokens

/**
 * The picture for each Premium feature, used by the hero, the tiles and the full-screen preview.
 *
 * Wherever the launcher already draws a feature for its own settings, that drawing is reused, so
 * the picture follows the user's theme and accent and cannot drift from the real UI: a frosted
 * sheet over a real blur ([PremiumShowcaseGlass]), the real widgets' own renderers
 * ([PremiumShowcaseWidgets]), the drawer settings preview's parts in motion
 * ([PremiumShowcaseDrawerAnimation]). Everything else is drawn from the tokens too
 * ([PremiumShowcaseFeatureArt], [PremiumShowcaseMotion], [PremiumShowcaseMock]) — no screenshots,
 * so nothing here goes stale against a theme or a redesign.
 *
 * Each call builds a fresh view: the same feature appears as a tile and as a preview at once.
 */
internal object PremiumShowcaseArt {

    fun view(context: Context, feature: PremiumFeature, tokens: NexusColorTokens): View = when (feature) {
        PremiumFeature.FROSTED_GLASS -> PremiumShowcaseGlass(context, tokens)
        PremiumFeature.NEXUS_WIDGETS -> PremiumShowcaseWidgets(context, tokens)
        PremiumFeature.DRAWER_CUSTOMIZATION -> PremiumShowcaseDrawerAnimation(context, tokens)
        PremiumFeature.EINK_FEED, PremiumFeature.DOCUMENT_LIBRARY, PremiumFeature.ADVANCED_GESTURES,
        -> PremiumShowcaseFeatureArt(context, feature, tokens)
        PremiumFeature.NOTIFICATION_BADGES, PremiumFeature.PAGE_TRANSITIONS, PremiumFeature.CUSTOM_FONTS,
        PremiumFeature.WALLPAPER_EFFECTS, PremiumFeature.HIDDEN_APPS,
        -> PremiumShowcaseMotion(context, feature, tokens)
        else -> PremiumShowcaseMock(context, feature, tokens)
    }
}
