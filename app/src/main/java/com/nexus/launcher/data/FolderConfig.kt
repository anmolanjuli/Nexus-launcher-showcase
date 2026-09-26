package com.nexus.launcher.data

data class FolderConfig(
    val backgroundColor: String? = null,
    val shapeStyle: Int = 1,
    val previewStyle: Int = 0,
    val customIconPackage: String? = null,
    val openAnimation: Int = 1,
    val sortMode: Int = 0,
    val iconSizeDp: Int? = null,
    val swipeUpAction: String? = null,
    val swipeDownAction: String? = null,
    val swipeLeftAction: String? = null,
    val swipeRightAction: String? = null,
    val gridColumns: Int = 5,
    val showLabels: Boolean = true,
    val showAccentRing: Boolean = false,
    val windowBackgroundMode: String = "TRANSPARENT",
    val frostedGradientIndex: Int = 0,
    val solidBackgroundColor: String? = null,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val backgroundOpacity: Float = 1.0f,
    /** Open-folder card / window glass opacity (independent of closed-icon opacity). */
    val windowBackgroundOpacity: Float = 1.0f,
    /** Intensity of glass specular reflection / refraction (0.0 to 1.0). */
    val glassRefraction: Float = 0.70f,
    val previewStyleExplicitlySet: Boolean = false,
    val isExpressive: Boolean = false,
    /** Closed-icon plate: GLASS or NEUMORPHIC (Soft UI). Defaults to Glass (matching widgets'
     *  own default) so a never-configured folder actually responds to the global Frosted Glass
     *  toggle out of the box — isGlass gates on `iconBackgroundMode == BG_GLASS &&
     *  isGlobalFrostedGlassEnabled`, so with the toggle off this same default just falls through
     *  to the normal Neumorphic rendering path anyway. Only affects folders with nothing
     *  explicitly saved yet. */
    val iconBackgroundMode: String = com.nexus.launcher.ui.widgets.NexusWidgetConfig.BG_GLASS,
    /** Per-folder theme override — same tokens as widget theme picker. */
    val themeMode: String = com.nexus.launcher.ui.widgets.NexusWidgetConfig.THEME_FOLLOW
)
