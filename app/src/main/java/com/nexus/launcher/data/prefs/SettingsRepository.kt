package com.nexus.launcher.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

import kotlinx.coroutines.flow.first

class SettingsRepository(
    internal val dataStore: DataStore<Preferences>
) {
    val settingsFlow: Flow<NexusSettingsData> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs -> SettingsDataMapper.map(prefs) }

    /** One-shot snapshot for backup export (additive helper). */
    suspend fun snapshot(): NexusSettingsData = settingsFlow.first()

    suspend fun updateHomeColumns(value: Int) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.HOME_COLUMNS] = value.coerceIn(
            NexusDefaults.HOME_COLUMNS_MIN, NexusDefaults.HOME_COLUMNS_MAX
        )
    }

    suspend fun updateHomeRows(value: Int) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.HOME_ROWS] = value.coerceIn(
            NexusDefaults.HOME_ROWS_MIN, NexusDefaults.HOME_ROWS_MAX
        )
    }

    suspend fun updateHomePaddingLeftRight(value: Float) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.HOME_PADDING_LR] = value.coerceIn(-4f, 64f)
    }

    suspend fun updateHomePaddingTopBottom(value: Float) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.HOME_PADDING_TB] = value.coerceIn(-32f, 64f)
    }

    suspend fun updateHomeGapHorizontal(value: Float) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.HOME_GAP_H] = value.coerceIn(
            NexusDefaults.HOME_GAP_MIN, NexusDefaults.HOME_GAP_MAX
        )
    }

    suspend fun updateHomeGapVertical(value: Float) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.HOME_GAP_V] = value.coerceIn(
            NexusDefaults.HOME_GAP_MIN, NexusDefaults.HOME_GAP_MAX
        )
    }

    suspend fun updateHomeIconSize(value: Float) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.HOME_ICON_SIZE_MULTIPLIER] = value.coerceIn(
            NexusDefaults.HOME_ICON_SIZE_MULTIPLIER_MIN,
            NexusDefaults.HOME_ICON_SIZE_MULTIPLIER_MAX
        )
    }

    suspend fun updateHomeShowLabels(value: Boolean) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.HOME_SHOW_LABELS] = value
    }

    suspend fun updateHomeTwoLineLabels(value: Boolean) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.HOME_TWO_LINE_LABELS] = value
    }

    suspend fun updateHomeShowIndicator(value: Boolean) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.HOME_SHOW_INDICATOR] = value
    }

    suspend fun updateDrawerColumns(value: Int) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.DRAWER_COLUMNS] = value.coerceIn(2, 10)
    }

    suspend fun updateDrawerIconSize(value: Float) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.DRAWER_ICON_SIZE_MULTIPLIER] = value.coerceIn(0.4f, 0.85f)
    }

    suspend fun updateDrawerShowLabels(value: Boolean) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.DRAWER_SHOW_LABELS] = value
    }

    suspend fun updateDrawerTwoLineLabels(value: Boolean) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.DRAWER_TWO_LINE_LABELS] = value
    }

    suspend fun updateDrawerShowCategoryBar(value: Boolean) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.DRAWER_SHOW_CATEGORY_BAR] = value
    }

    suspend fun updateDrawerShowRail(value: Boolean) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.DRAWER_SHOW_RAIL] = value
    }

    suspend fun updateDrawerSortOrder(value: String) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.DRAWER_SORT_ORDER] =
            if (value in setOf("az", "za", "new")) value else NexusDefaults.DRAWER_SORT_ORDER
    }

    suspend fun updateDrawerShowSearchPill(value: Boolean) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.DRAWER_SHOW_SEARCH_PILL] = value
    }

    suspend fun updateDrawerCategoryMode(value: String) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.DRAWER_CATEGORY_MODE] =
            if (value in setOf("in_pill", "dropdown", "strip")) value
            else NexusDefaults.DRAWER_CATEGORY_MODE
    }

    suspend fun updateDrawerCategoryPosition(value: String) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.DRAWER_CATEGORY_POSITION] =
            if (value in setOf("top", "bottom")) value else NexusDefaults.DRAWER_CATEGORY_POSITION
    }

    suspend fun updateDrawerSearchBarPosition(value: String) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.DRAWER_SEARCH_BAR_POSITION] =
            if (value in setOf("top", "bottom")) value else NexusDefaults.DRAWER_SEARCH_BAR_POSITION
    }

    suspend fun updateDrawerLayout(value: String) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.DRAWER_LAYOUT] =
            if (value in setOf("grid", "list", "list_1", "list_2")) value else NexusDefaults.DRAWER_LAYOUT
    }

    suspend fun updateDrawerGridOrList(value: String) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.DRAWER_GRID_OR_LIST] =
            if (value in DrawerLayoutModes.GRID_OR_LIST) value else NexusDefaults.DRAWER_GRID_OR_LIST
    }

    suspend fun updateDrawerListColumns(value: Int) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.DRAWER_LIST_COLUMNS] = value.coerceIn(1, 2)
    }

    suspend fun updateDrawerCategoryLayout(value: String) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.DRAWER_CATEGORY_LAYOUT] =
            if (value in DrawerLayoutModes.CATEGORY_LAYOUTS) value
            else NexusDefaults.DRAWER_CATEGORY_LAYOUT
    }

    suspend fun updateDrawerTransition(value: String) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.DRAWER_TRANSITION] =
            if (value in setOf("default", "cube", "zoom", "tilt", "stack")) value
            else NexusDefaults.DRAWER_TRANSITION
    }

    private val drawerLayoutMigration = DrawerLayoutMigration(dataStore)

    /** See [DrawerLayoutMigration] — extracted to its own file to keep this one under 400 lines. */
    suspend fun migrateDrawerLayoutIfNeeded() = drawerLayoutMigration.migrateIfNeeded()

    suspend fun updateBackupFolderUri(value: String) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.BACKUP_FOLDER_URI] = value
    }

    suspend fun updateAccentColor(value: String) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.ACCENT_COLOR] = value
    }

    suspend fun updateMatchWallpaperColor(value: Boolean) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.MATCH_WALLPAPER_COLOR] = value
    }

    suspend fun updateWallpaperType(value: String) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.WALLPAPER_TYPE] = value
        // System wallpaper shows via FLAG_SHOW_WALLPAPER; non-zero treatment forces the
        // blurred-cache draw path and blanks the live wallpaper on cold start.
        if (value == "system") clearWallpaperTreatment(prefs)
    }

    suspend fun updateWallpaperSolidColor(value: String) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.WALLPAPER_SOLID_COLOR] = value
    }

    suspend fun updateWallpaperGradient(start: String, end: String, direction: String) =
        dataStore.edit { prefs ->
            prefs[NexusSettingsKeys.WALLPAPER_GRADIENT_START] = start
            prefs[NexusSettingsKeys.WALLPAPER_GRADIENT_END] = end
            prefs[NexusSettingsKeys.WALLPAPER_GRADIENT_DIRECTION] = direction
        }

    /** Persists the internal copy's path and flips type to gallery atomically,
     *  so the type never points at a not-yet-saved image. */
    suspend fun updateWallpaperGallery(path: String) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.WALLPAPER_GALLERY_PATH] = path
        prefs[NexusSettingsKeys.WALLPAPER_TYPE] = "gallery"
    }
    
    suspend fun updateWallpaperTreatment(blur: Float, tintColor: String, tintStrength: Float) = dataStore.edit { prefs ->
        val type = prefs[NexusSettingsKeys.WALLPAPER_TYPE] ?: NexusDefaults.WALLPAPER_TYPE
        if (type == "system") {
            // Ignore staged treatment for System (Apply may race after updateWallpaperType).
            clearWallpaperTreatment(prefs)
        } else {
            prefs[NexusSettingsKeys.WALLPAPER_BLUR] = blur
            prefs[NexusSettingsKeys.WALLPAPER_TINT_COLOR] = tintColor
            prefs[NexusSettingsKeys.WALLPAPER_TINT_STRENGTH] = tintStrength
        }
    }

    private fun clearWallpaperTreatment(prefs: androidx.datastore.preferences.core.MutablePreferences) {
        prefs[NexusSettingsKeys.WALLPAPER_BLUR] = NexusDefaults.WALLPAPER_BLUR
        prefs[NexusSettingsKeys.WALLPAPER_TINT_COLOR] = NexusDefaults.WALLPAPER_TINT_COLOR
        prefs[NexusSettingsKeys.WALLPAPER_TINT_STRENGTH] = NexusDefaults.WALLPAPER_TINT_STRENGTH
    }

    private fun systemSafeBlur(type: String, blur: Float): Float =
        if (type == "system") NexusDefaults.WALLPAPER_BLUR else blur

    private fun systemSafeTintColor(type: String, color: String): String =
        if (type == "system") NexusDefaults.WALLPAPER_TINT_COLOR else color

    private fun systemSafeTintStrength(type: String, strength: Float): Float =
        if (type == "system") NexusDefaults.WALLPAPER_TINT_STRENGTH else strength

    suspend fun updateBadgeStyleApp(value: Int) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.BADGE_STYLE_APP] = value
    }

    suspend fun updateBadgeStyleFolder(value: Int) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.BADGE_STYLE_FOLDER] = value
    }

    suspend fun updateHomePageCount(value: Int) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.HOME_PAGE_COUNT] = value
    }

    suspend fun updatePageTransition(value: String) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.PAGE_TRANSITION] = value
    }

    suspend fun updateIconPack(value: String) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.ICON_PACK] = value
    }

    suspend fun updateIconShape(value: Int) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.ICON_SHAPE] = value
    }

    suspend fun updateGlobalSwipeUp(value: String) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.GLOBAL_SWIPE_UP] = value
    }

    suspend fun updateGlobalSwipeDown(value: String) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.GLOBAL_SWIPE_DOWN] = value
    }

    suspend fun updateGlobalTwoFingerSwipeUp(value: String) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.GLOBAL_TWO_FINGER_SWIPE_UP] = value
    }

    suspend fun updateGlobalTwoFingerSwipeDown(value: String) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.GLOBAL_TWO_FINGER_SWIPE_DOWN] = value
    }

    suspend fun updateHomeShowFeed(value: Boolean) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.HOME_SHOW_FEED] = value
    }

    suspend fun updateFeedRefreshInterval(value: String) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.FEED_REFRESH_INTERVAL] = value
    }

    suspend fun updateFeedEInkMode(value: Boolean) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.FEED_EINK_MODE] = value
    }

    suspend fun updateFeedEInkDark(value: Boolean) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.FEED_EINK_DARK] = value
    }

    suspend fun updateIconTheming(value: Boolean) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.ICON_THEMING] = value
    }

    suspend fun updateFrostedGlassEnabled(value: Boolean) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.FROSTED_GLASS_ENABLED] = value
    }

    /** The actual write path for the Settings > Appearance "UI Style" picker (Neumorphism /
     *  Frosted Glass / Default) — also derives [NexusSettingsKeys.FROSTED_GLASS_ENABLED] from it
     *  in the same edit, so every existing `isGlobalFrostedGlassEnabled`-gated call site across
     *  widgets/folders/dock keeps working unchanged (true only for "FROSTED_GLASS"). */
    suspend fun updateUiStyleMode(value: String) = dataStore.edit { prefs ->
        prefs[NexusSettingsKeys.UI_STYLE_MODE] = value
        prefs[NexusSettingsKeys.FROSTED_GLASS_ENABLED] =
            (value == com.nexus.launcher.ui.glass.FrostedGlassEngine.UI_STYLE_FROSTED_GLASS)
    }

    suspend fun applySnapshot(s: NexusSettingsData) = dataStore.edit { prefs ->
        SettingsSnapshotWriter.write(prefs, s)
    }

    /** Backup restore: the snapshot is the whole intended state, wallpaper and pages included. */
    suspend fun restoreSnapshot(s: NexusSettingsData) = dataStore.edit { prefs ->
        SettingsSnapshotWriter.write(prefs, s, includeWallpaperAndPages = true)
    }
}
