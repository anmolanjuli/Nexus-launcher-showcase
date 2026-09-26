package com.nexus.launcher.ui.canvas

import com.nexus.launcher.data.prefs.NexusSettingsData

/**
 * Propagates persisted settings and rename overrides onto the canvas renderers.
 * Extracted verbatim from [LauncherCanvasView] — behavior unchanged.
 */
class CanvasSettingsApplier(private val view: LauncherCanvasView) {

    private val renamedPackages = mutableSetOf<String>()
    private var lastWallpaperSourceKey: String = ""

    /** Apply persisted NexusSettings — propagates grid dimensions and visual prefs to renderers. */
    fun applySettings(settings: NexusSettingsData) {
        // Every glass surface (widgets, boxes, folders, dock, mosaic) samples the wallpaper via
        // HomeScreenFrameCache's RenderNode caches, which only re-record when a surface's own
        // position/size changes OR HomeScreenFrameCache's version counter bumps — NOT merely
        // because canvasRenderer's wallpaper fields below changed. Without this, switching
        // wallpaper type (system capture <-> gradient <-> gallery) or editing a gradient/gallery
        // choice left any glass surface that wasn't currently scrolling/resizing showing the OLD
        // wallpaper indefinitely (reported as a Mosaic tile showing a "fixed" stale capture
        // instead of the current wallpaper). Bump the version whenever the actual wallpaper
        // source changes, and force an immediate redraw rather than waiting for one.
        val wallpaperSourceKey = "${settings.wallpaperType}|${settings.wallpaperSolidColor}|" +
            "${settings.wallpaperGradientStart}|${settings.wallpaperGradientEnd}|" +
            "${settings.wallpaperGradientDirection}|${settings.wallpaperGalleryPath}|" +
            // The dim and tint are drawn into the glass surfaces' own wallpaper sample now, so
            // moving either has to re-record them the same way changing the wallpaper does.
            "${settings.wallpaperBlur}|${settings.wallpaperTintColor}|${settings.wallpaperTintStrength}"
        if (wallpaperSourceKey != lastWallpaperSourceKey) {
            lastWallpaperSourceKey = wallpaperSourceKey
            com.nexus.launcher.ui.folder.GlassBackdropRefresh.refresh(view)
        }
        view.gridRenderer.setColumns(settings.drawerColumns)
        view.gridRenderer.layoutMode = settings.drawerEffectiveLayoutMode
        view.gridRenderer.setHomeColumns(settings.homeColumns)
        view.gridRenderer.setHomeRows(settings.homeRows)
        com.nexus.launcher.data.LayoutShapeState.baseColumns = settings.homeColumns
        com.nexus.launcher.data.LayoutShapeState.baseRows = settings.homeRows
        view.drawerIconSizeMultiplier = settings.drawerIconSizeMultiplier
        view.homeIconSizeMultiplier = settings.homeIconSizeMultiplier
        view.homePaddingLeftRightDp = settings.homePaddingLeftRightDp
        view.homePaddingTopBottomSetting = settings.homePaddingTopBottomDp
        view.homeGapHorizontalDp = settings.homeGapHorizontalDp
        view.homeGapVerticalDp = settings.homeGapVerticalDp
        view.showDrawerLabels = settings.drawerShowLabels
        view.drawerTwoLineLabels = settings.drawerTwoLineLabels
        view.drawerTransition = settings.drawerTransition
        view.onDrawerChromeSettings?.invoke(settings)
        // A–Z rail only makes sense for alphabetical sort — hide it for other orders.
        // Categories Spatial has no alphabet rail; Strip has none; Categories-List draws
        // its own with Grid's mid-screen portrait extent and 56dp / chrome clearance.
        val hideCanvasRail = settings.drawerGridOrList == "categories"
        view.onDrawerRailVisibility?.invoke(
            settings.drawerShowRail && settings.drawerSortOrder == "az" && !hideCanvasRail
        )
        view.homeScreenRenderer.userIconSizeMultiplier = settings.homeIconSizeMultiplier
        view.homeScreenRenderer.gapHorizontalPx = settings.homeGapHorizontalDp * view.resources.displayMetrics.density
        view.homeScreenRenderer.gapVerticalPx = settings.homeGapVerticalDp * view.resources.displayMetrics.density
        view.homeScreenRenderer.showLabels = settings.homeShowLabels
        view.homeScreenRenderer.twoLineLabels = settings.homeTwoLineLabels
        view.badgeStyleApp = settings.badgeStyleApp
        view.badgeStyleFolder = settings.badgeStyleFolder
        view.homeScreenRenderer.badgeStyleFolder = settings.badgeStyleFolder
        
        view.canvasRenderer.wallpaperType = settings.wallpaperType
        view.canvasRenderer.wallpaperSolidColor = settings.wallpaperSolidColor
        view.canvasRenderer.wallpaperGradientStart = settings.wallpaperGradientStart
        view.canvasRenderer.wallpaperGradientEnd = settings.wallpaperGradientEnd
        view.canvasRenderer.wallpaperGradientDirection = settings.wallpaperGradientDirection
        view.canvasRenderer.wallpaperGalleryPath = settings.wallpaperGalleryPath
        view.canvasRenderer.wallpaperBlur = settings.wallpaperBlur
        view.canvasRenderer.wallpaperTintColor = settings.wallpaperTintColor
        view.canvasRenderer.wallpaperTintStrength = settings.wallpaperTintStrength
        
        applyAccent(view, view.currentThemeTokens.accent)
        view.drawEngine.setShowPageIndicator(settings.homeShowIndicator)
        view.transitionStyle = settings.pageTransition
        view.showFeed = settings.homeShowFeed

        view.recalculateLayout()
        view.triggerDrawerPrewarm()
        view.invalidate()
    }

    fun applyRenamedLabels(renamedApps: Map<String, String>) {
        // Only clear packages that previously had a rename override
        // and are no longer in the renamed map — never touch
        // PackageManager-loaded labels
        val toReset = renamedPackages.filter { pkg ->
            pkg !in renamedApps
        }
        toReset.forEach { view.homeScreenRenderer.clearLabelOverride(it) }
        renamedPackages.clear()
        renamedApps.forEach { (pkg, label) ->
            view.homeScreenRenderer.overrideLabel(pkg, label)
            renamedPackages.add(pkg)
        }
        view.invalidate()
    }

    fun applyAccent(view: LauncherCanvasView, color: Int) {
        view.accentColor = color
        // Drawer's A–Z rail stays neutral/monochrome — decoupled from the user's Accent Color
        // (App Drawer is accent-free by design; selection/drag/home-indicator below are unrelated
        // to the drawer and keep following the live accent as before).
        view.railRenderer.setAccentColor(view.currentThemeTokens.textPrimary)
        view.railRenderer.setIdleColor(view.currentThemeTokens.textSecondary)
        // Bubble background is textPrimary — its own text needs the opposite (bg) for contrast,
        // not the hardcoded white that used to be invisible in Light theme's near-white bubble.
        view.railRenderer.setBubbleTextColor(view.currentThemeTokens.bg)
        view.dragRenderer.setAccentColor(color)
        view.drawEngine.setAccentColor(color)
    }

    fun applyColorBlindMode(view: LauncherCanvasView, mode: com.nexus.launcher.theme.ColorBlindMode) {
        view.selectionRenderer.setColorBlindMode(mode)
        view.dragRenderer.setColorBlindMode(mode)
        view.badgeRenderer.setColorBlindMode(mode)
        view.invalidate()
    }

    fun applyBadgeColor(view: LauncherCanvasView, hex: String?) {
        view.badgeRenderer.setCustomBadgeColor(hex)
        view.invalidate()
    }

    fun initThemeObservers(view: LauncherCanvasView, scope: kotlinx.coroutines.CoroutineScope) {
        com.nexus.launcher.theme.ThemeObserver.observe(view.context, scope) { tokens ->
            view.currentThemeTokens = tokens
            view.canvasRenderer.themeTokens = tokens
            view.overlayPaint.color = tokens.bg
            view.searchBgPaint.color = (tokens.bg and 0x00FFFFFF) or (0x99 shl 24)
            view.overlayBgPaint.color = (tokens.bg and 0x00FFFFFF) or (0xCC shl 24)
            view.dragRenderer.setTheme(tokens)
            applyAccent(view, tokens.accent)

            view.drawerIconCache.clear()
            view.homeScreenRenderer.iconCache.clear()
            view.homeScreenRenderer.bitmapCache.clear()
            view.drawerFolderTileCache.clear()
            view.homeScreenRenderer.refreshAllCaches(view.homeScreenItems)

            view.invalidate()
        }
        com.nexus.launcher.theme.ThemeObserver.observeBackgroundLayer(view.context, scope) { mode ->
            view.canvasRenderer.backgroundLayerMode = mode
            com.nexus.launcher.ui.folder.HomeScreenFrameCache.invalidate()
            view.invalidate()
        }
        com.nexus.launcher.theme.ThemeObserver.observeColorBlindMode(view.context, scope) { mode ->
            applyColorBlindMode(view, mode)
        }
        com.nexus.launcher.theme.ThemeObserver.observeBadgeColor(view.context, scope) { hex ->
            applyBadgeColor(view, hex)
        }
    }
}
