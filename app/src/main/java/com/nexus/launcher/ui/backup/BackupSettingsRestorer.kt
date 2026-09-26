package com.nexus.launcher.ui.backup

import com.google.gson.JsonObject
import com.nexus.launcher.data.prefs.NexusDefaults
import com.nexus.launcher.data.prefs.PreferenceManager
import com.nexus.launcher.data.prefs.SettingsRepository
import com.nexus.launcher.ui.dock.settings.DockBackgroundMode
import com.nexus.launcher.ui.dock.settings.DockSettingsRepository

object BackupSettingsRestorer {

    suspend fun restoreSettings(
        settingsObj: JsonObject,
        settingsRepo: SettingsRepository,
        dockRepo: DockSettingsRepository
    ) {
        val home = settingsObj.getAsJsonObject("homeScreen") ?: throw BackupImporter.ImportException("Missing homeScreen settings")
        settingsRepo.updateHomeColumns(home.get("columns")?.asInt ?: 6)
        settingsRepo.updateHomeRows(home.get("rows")?.asInt ?: 12)
        settingsRepo.updateHomePaddingLeftRight(home.get("paddingLeftRightDp")?.asFloat ?: 16f)
        settingsRepo.updateHomePaddingTopBottom(home.get("paddingTopBottomDp")?.asFloat ?: 48f)
        settingsRepo.updateHomeGapHorizontal(home.get("gapHorizontalDp")?.asFloat ?: 8f)
        settingsRepo.updateHomeGapVertical(home.get("gapVerticalDp")?.asFloat ?: 16f)
        settingsRepo.updateHomeIconSize(home.get("iconSizeMultiplier")?.asFloat ?: 1f)
        settingsRepo.updateHomeShowLabels(home.get("showLabels")?.asBoolean ?: true)
        settingsRepo.updateHomeTwoLineLabels(home.get("twoLineLabels")?.asBoolean ?: false)
        settingsRepo.updateHomeShowIndicator(home.get("showPageIndicator")?.asBoolean ?: true)
        settingsRepo.updateHomePageCount(home.get("pageCount")?.asInt ?: 1)
        settingsRepo.updatePageTransition(home.get("pageTransition")?.asString ?: "DEFAULT")
        settingsRepo.updateBadgeStyleApp(home.get("badgeStyleApp")?.asInt ?: 0)
        settingsRepo.updateBadgeStyleFolder(home.get("badgeStyleFolder")?.asInt ?: 0)

        restoreDock(settingsObj, dockRepo)

        val drawer = settingsObj.getAsJsonObject("appDrawer") ?: throw BackupImporter.ImportException("Missing appDrawer settings")
        settingsRepo.updateDrawerColumns(drawer.get("columns")?.asInt ?: 5)
        settingsRepo.updateDrawerIconSize(drawer.get("iconSizeMultiplier")?.asFloat ?: 1f)
        settingsRepo.updateDrawerShowLabels(drawer.get("showLabels")?.asBoolean ?: true)
        settingsRepo.updateDrawerTwoLineLabels(drawer.get("twoLineLabels")?.asBoolean ?: false)
        settingsRepo.updateDrawerShowCategoryBar(drawer.get("showCategoryBar")?.asBoolean ?: false)
        settingsRepo.updateDrawerShowRail(drawer.get("showRail")?.asBoolean ?: false)
        settingsRepo.updateDrawerSortOrder(drawer.get("sortOrder")?.asString ?: "ALPHABETICAL")
        settingsRepo.updateDrawerShowSearchPill(
            drawer.get("showSearchPill")?.asBoolean ?: NexusDefaults.DRAWER_SHOW_SEARCH_PILL
        )
        settingsRepo.updateDrawerCategoryMode(
            drawer.get("categoryMode")?.asString ?: NexusDefaults.DRAWER_CATEGORY_MODE
        )
        settingsRepo.updateDrawerCategoryPosition(
            drawer.get("categoryPosition")?.asString ?: NexusDefaults.DRAWER_CATEGORY_POSITION
        )
        settingsRepo.updateDrawerSearchBarPosition(
            drawer.get("searchBarPosition")?.asString ?: NexusDefaults.DRAWER_SEARCH_BAR_POSITION
        )
        settingsRepo.updateDrawerLayout(drawer.get("layout")?.asString ?: "GRID")

        val theme = settingsObj.getAsJsonObject("theme") ?: throw BackupImporter.ImportException("Missing theme settings")
        settingsRepo.updateAccentColor(theme.get("accentColor")?.asString ?: com.nexus.launcher.data.prefs.NexusDefaults.ACCENT_COLOR)
        settingsRepo.updateMatchWallpaperColor(theme.get("matchWallpaperColor")?.asBoolean ?: true)
        settingsRepo.updateWallpaperType(theme.get("wallpaperType")?.asString ?: "DEFAULT")
        val solidColor = theme.get("wallpaperSolidColor")
        if (solidColor != null && !solidColor.isJsonNull) {
            settingsRepo.updateWallpaperSolidColor(solidColor.asString)
        }
        settingsRepo.updateWallpaperGradient(
            theme.get("wallpaperGradientStart")?.asString ?: "",
            theme.get("wallpaperGradientEnd")?.asString ?: "",
            theme.get("wallpaperGradientDirection")?.asString ?: "TOP_BOTTOM"
        )
        settingsRepo.updateWallpaperTreatment(
            theme.get("wallpaperBlur")?.asFloat ?: 0f,
            theme.get("wallpaperTintColor")?.asString ?: "#00000000",
            theme.get("wallpaperTintStrength")?.asFloat ?: 0f
        )
        settingsRepo.updateIconPack(theme.get("iconPack")?.asString ?: "")
        settingsRepo.updateIconShape(theme.get("iconShape")?.asInt ?: 0)
    }

    /**
     * Dock settings live in their own repository, so they are restored from the manifest's
     * readable block on both the v1 and v2 paths — the v2 settings snapshot only covers
     * NexusSettingsData.
     */
    suspend fun restoreDock(settingsObj: JsonObject, dockRepo: DockSettingsRepository) {
        val dock = settingsObj.getAsJsonObject("dock") ?: throw BackupImporter.ImportException("Missing dock settings")
        dockRepo.updateMaxIcons(dock.get("maxIcons")?.asInt ?: 5)
        dockRepo.updateDockHeightDp(dock.get("heightDp")?.asInt ?: 80)
        dockRepo.updateIconSizeDp(dock.get("iconSizeDp")?.asInt ?: 56)
        dockRepo.updateShuffleMotionPercent(dock.get("shuffleMotionPercent")?.asInt ?: 100)
        val modeStr = dock.get("backgroundMode")?.asString ?: "TRANSPARENT"
        dockRepo.updateBackgroundMode(DockBackgroundMode.valueOf(modeStr))
        dockRepo.updateSolidColorArgb(dock.get("solidColorArgb")?.asInt ?: 0)
        dockRepo.updateFrostedGradientIndex(dock.get("frostedGradientIndex")?.asInt ?: 0)
        dockRepo.updateShowLabels(dock.get("showLabels")?.asBoolean ?: false)
        dockRepo.updateLabelFontSizeSp(dock.get("labelFontSizeSp")?.asInt ?: 12)
        dockRepo.updateSearchInDock(dock.get("searchInDock")?.asBoolean ?: false)
        dockRepo.updateSearchSlotIndex(dock.get("searchSlotIndex")?.asInt ?: 0)
        dockRepo.updateCornerRadiusDp(dock.get("cornerRadiusDp")?.asInt ?: 0)
        dockRepo.updateDockBackgroundOpacity(dock.get("backgroundOpacity")?.asFloat ?: 1f)
        dockRepo.updateDockGlassRefraction(dock.get("glassRefraction")?.asFloat ?: DockSettingsRepository.DEFAULT_DOCK_GLASS_REFRACTION)
    }

    fun restorePerAppPreferences(prefsObj: JsonObject, prefs: PreferenceManager) {
        prefs.clearHiddenApps()
        prefs.clearRenamedApps()
        prefs.clearSwipeUpActions()
        prefs.clearSwipeDownActions()
        prefs.clearDoubleTapActions()
        prefs.clearAppIconShapes()

        val hiddenAppsArr = prefsObj.getAsJsonArray("hiddenApps")
        if (hiddenAppsArr != null) {
            val hiddenApps = mutableSetOf<String>()
            hiddenAppsArr.forEach { hiddenApps.add(it.asString) }
            prefs.saveHiddenApps(hiddenApps)
        }
        
        val renamedAppsObj = prefsObj.getAsJsonObject("renamedApps")
        if (renamedAppsObj != null) {
            for (key in renamedAppsObj.keySet()) {
                prefs.setAppRename(key, renamedAppsObj.get(key).asString)
            }
        }
        
        val gestureActions = prefsObj.getAsJsonObject("gestureActions")
        if (gestureActions != null) {
            for (pkg in gestureActions.keySet()) {
                val actions = gestureActions.getAsJsonObject(pkg)
                if (actions.has("swipeUp")) prefs.setSwipeUpAction(pkg, actions.get("swipeUp").asString)
                if (actions.has("swipeDown")) prefs.setSwipeDownAction(pkg, actions.get("swipeDown").asString)
                if (actions.has("doubleTap")) prefs.setDoubleTapAction(pkg, actions.get("doubleTap").asString)
            }
        }
        
        val iconShapeOverrides = prefsObj.getAsJsonObject("iconShapeOverrides")
        if (iconShapeOverrides != null) {
            for (pkg in iconShapeOverrides.keySet()) {
                prefs.setAppIconShape(pkg, iconShapeOverrides.get(pkg).asInt)
            }
        }
    }
}
