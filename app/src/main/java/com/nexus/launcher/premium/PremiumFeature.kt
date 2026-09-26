package com.nexus.launcher.premium

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.nexus.launcher.R

/**
 * The features Premium unlocks.
 *
 * Adding one is a single entry here plus a `PremiumGate` check at the control — deliberately, since
 * the set is expected to grow. Nothing else needs to know the list: the Premium page renders it,
 * and every gate asks [PremiumManager] rather than testing a feature name of its own.
 *
 * The constant names are not persisted anywhere, so they can be renamed freely.
 *
 * [iconRes] belongs here rather than at the row that draws it: the Premium page renders this list
 * generically, so a feature that did not carry its own icon would have to share one with every
 * other feature — which is exactly what the page did before.
 */
enum class PremiumFeature(
    @param:StringRes val titleRes: Int,
    @param:StringRes val descriptionRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    NEXUS_WIDGETS(
        R.string.premium_feature_widgets,
        R.string.premium_feature_widgets_desc,
        R.drawable.ic_widgets,
    ),
    FROSTED_GLASS(
        R.string.premium_feature_frosted,
        R.string.premium_feature_frosted_desc,
        R.drawable.ic_palette_theme,
    ),
    /** Drawer layouts, search pill and category bar placement, category styles, rail off. */
    DRAWER_CUSTOMIZATION(
        R.string.premium_feature_drawer_modes,
        R.string.premium_feature_drawer_modes_desc,
        R.drawable.ic_drawermode,
    ),
    /** Calm and AMOLED. */
    EXTRA_THEMES(
        R.string.premium_feature_calm,
        R.string.premium_feature_calm_desc,
        R.drawable.ic_palette,
    ),
    COLOR_ACCESSIBILITY(
        R.string.premium_feature_color_access,
        R.string.premium_feature_color_access_desc,
        R.drawable.ic_colors,
    ),
    EINK_FEED(
        R.string.premium_feature_eink_feed,
        R.string.premium_feature_eink_feed_desc,
        R.drawable.ic_feed_rss,
    ),
    DOCUMENT_LIBRARY(
        R.string.premium_feature_library,
        R.string.premium_feature_library_desc,
        R.drawable.ic_library,
    ),
    /** Immersive Mode with its status row, and hiding the dock. */
    IMMERSIVE_HOME(
        R.string.premium_feature_immersive,
        R.string.premium_feature_immersive_desc,
        R.drawable.ic_home,
    ),
    NOTIFICATION_BADGES(
        R.string.premium_feature_badges,
        R.string.premium_feature_badges_desc,
        R.drawable.ic_notification,
    ),
    ADVANCED_GESTURES(
        R.string.premium_feature_gestures,
        R.string.premium_feature_gestures_desc,
        R.drawable.ic_gesture,
    ),
    CUSTOM_FONTS(
        R.string.premium_feature_fonts,
        R.string.premium_feature_fonts_desc,
        R.drawable.ic_fonts,
    ),
    WALLPAPER_EFFECTS(
        R.string.premium_feature_wallpaper,
        R.string.premium_feature_wallpaper_desc,
        R.drawable.ic_menu_wallpaper,
    ),
    PAGE_TRANSITIONS(
        R.string.premium_feature_transitions,
        R.string.premium_feature_transitions_desc,
        R.drawable.ic_transition,
    ),
    /** Unit conversion in search. The calculator stays free. */
    SMART_SEARCH(
        R.string.premium_feature_smart_search,
        R.string.premium_feature_smart_search_desc,
        R.drawable.outline_function_24,
    ),
    HIDDEN_APPS(
        R.string.premium_feature_hidden_apps,
        R.string.premium_feature_hidden_apps_desc,
        R.drawable.ic_hidden,
    ),
}
