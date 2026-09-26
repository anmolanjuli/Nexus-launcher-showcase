package com.nexus.launcher.ui.dock.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nexus.launcher.ui.HomeScreenViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DockSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val keyMaxIcons = intPreferencesKey("dock_max_icons")
    private val keyDockGlassRefraction = floatPreferencesKey("dock_glass_refraction")
    private val keyDockHeightDp = intPreferencesKey("dock_height_dp")
    private val keyIconSizeDp = intPreferencesKey("dock_icon_size")
    private val keyShuffleMotionPercent = intPreferencesKey("dock_shuffle_motion_percent")
    private val keyBackgroundMode = stringPreferencesKey("dock_background_mode")
    private val keySolidColorArgb = intPreferencesKey("dock_solid_color_argb")
    private val keyFrostedGradientIndex = intPreferencesKey("dock_frosted_gradient_index")
    private val keyShowLabels = booleanPreferencesKey("dock_show_labels")
    private val keyLabelFontSizeSp = intPreferencesKey("dock_label_font_size_sp")
    private val keySearchInDock = booleanPreferencesKey("dock_search_in_dock")
    private val keySearchSlotIndex = intPreferencesKey("dock_search_slot_index")
    private val keyCornerRadiusDp = intPreferencesKey("dock_corner_radius_dp")
    private val keyDockBackgroundOpacity = floatPreferencesKey("dock_background_opacity")

    val shuffleMotionPercent: StateFlow<Int> = dataStore.data
        .map { prefs -> prefs[keyShuffleMotionPercent] ?: DEFAULT_SHUFFLE_MOTION_PERCENT }
        .map { it.coerceIn(MIN_SHUFFLE_MOTION_PERCENT, MAX_SHUFFLE_MOTION_PERCENT) }
        .stateIn(scope, SharingStarted.Eagerly, DEFAULT_SHUFFLE_MOTION_PERCENT)

    val maxIcons: StateFlow<Int> = dataStore.data
        .map { prefs -> prefs[keyMaxIcons] ?: 4 }
        .map { it.coerceIn(1, 10) }
        .stateIn(scope, SharingStarted.Eagerly, 4)

    val dockHeightDp: StateFlow<Int> = dataStore.data
        .map { prefs -> prefs[keyDockHeightDp] ?: DEFAULT_DOCK_HEIGHT_DP }
        .map { it.coerceIn(MIN_DOCK_HEIGHT_DP, MAX_DOCK_HEIGHT_DP) }
        .stateIn(scope, SharingStarted.Eagerly, DEFAULT_DOCK_HEIGHT_DP)

    val iconSizeDp: StateFlow<Int> = dataStore.data
        .map { prefs -> prefs[keyIconSizeDp] ?: DEFAULT_ICON_SIZE_DP }
        .map { it.coerceIn(MIN_ICON_SIZE_DP, MAX_ICON_SIZE_DP) }
        .stateIn(scope, SharingStarted.Eagerly, DEFAULT_ICON_SIZE_DP)

    val backgroundMode: StateFlow<DockBackgroundMode> = dataStore.data
        .map { prefs -> DockBackgroundMode.fromStored(prefs[keyBackgroundMode]) }
        .stateIn(scope, SharingStarted.Eagerly, DockBackgroundMode.TRANSPARENT)

    val solidColorArgb: StateFlow<Int> = dataStore.data
        .map { prefs -> prefs[keySolidColorArgb] ?: DEFAULT_SOLID_COLOR_ARGB }
        .stateIn(scope, SharingStarted.Eagerly, DEFAULT_SOLID_COLOR_ARGB)

    val frostedGradientIndex: StateFlow<Int> = dataStore.data
        .map { prefs -> prefs[keyFrostedGradientIndex] ?: DEFAULT_FROSTED_GRADIENT_INDEX }
        .map { it.coerceIn(0, MAX_FROSTED_GRADIENT_INDEX) }
        .stateIn(scope, SharingStarted.Eagerly, DEFAULT_FROSTED_GRADIENT_INDEX)

    val showLabels: StateFlow<Boolean> = dataStore.data
        .map { prefs -> prefs[keyShowLabels] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val labelFontSizeSp: StateFlow<Int> = dataStore.data
        .map { prefs -> prefs[keyLabelFontSizeSp] ?: DEFAULT_LABEL_FONT_SIZE_SP }
        .map { it.coerceIn(MIN_LABEL_FONT_SIZE_SP, MAX_LABEL_FONT_SIZE_SP) }
        .stateIn(scope, SharingStarted.Eagerly, DEFAULT_LABEL_FONT_SIZE_SP)

    val searchInDock: StateFlow<Boolean> = dataStore.data
        .map { prefs -> prefs[keySearchInDock] ?: false }
        .stateIn(scope, SharingStarted.Eagerly, false)

    val searchSlotIndex: StateFlow<Int> = dataStore.data
        .map { prefs -> prefs[keySearchSlotIndex] ?: -1 }
        .stateIn(scope, SharingStarted.Eagerly, -1)

    val cornerRadiusDp: StateFlow<Int> = dataStore.data
        .map { prefs -> prefs[keyCornerRadiusDp] ?: DEFAULT_CORNER_RADIUS_DP }
        .map { it.coerceIn(MIN_CORNER_RADIUS_DP, MAX_CORNER_RADIUS_DP) }
        .stateIn(scope, SharingStarted.Eagerly, DEFAULT_CORNER_RADIUS_DP)

    val dockBackgroundOpacity: StateFlow<Float> = dataStore.data
        .map { prefs -> prefs[keyDockBackgroundOpacity] ?: DEFAULT_DOCK_BACKGROUND_OPACITY }
        .map { it.coerceIn(0f, 1f) }
        .stateIn(scope, SharingStarted.Eagerly, DEFAULT_DOCK_BACKGROUND_OPACITY)

    val dockGlassRefraction: StateFlow<Float> = dataStore.data
        .map { prefs -> prefs[keyDockGlassRefraction] ?: DEFAULT_DOCK_GLASS_REFRACTION }
        .map { it.coerceIn(0f, 1f) }
        .stateIn(scope, SharingStarted.Eagerly, DEFAULT_DOCK_GLASS_REFRACTION)

    suspend fun updateMaxIcons(value: Int) {
        dataStore.edit { prefs -> prefs[keyMaxIcons] = value.coerceIn(1, 10) }
    }

    suspend fun updateDockHeightDp(value: Int) {
        val coerced = value.coerceIn(MIN_DOCK_HEIGHT_DP, MAX_DOCK_HEIGHT_DP)
        dataStore.edit { prefs -> prefs[keyDockHeightDp] = coerced }
    }

    suspend fun updateIconSizeDp(value: Int) {
        val coerced = value.coerceIn(MIN_ICON_SIZE_DP, MAX_ICON_SIZE_DP)
        dataStore.edit { prefs -> prefs[keyIconSizeDp] = coerced }
    }

    suspend fun updateShuffleMotionPercent(value: Int) {
        val coerced = value.coerceIn(MIN_SHUFFLE_MOTION_PERCENT, MAX_SHUFFLE_MOTION_PERCENT)
        dataStore.edit { prefs -> prefs[keyShuffleMotionPercent] = coerced }
    }

    suspend fun updateBackgroundMode(mode: DockBackgroundMode) {
        dataStore.edit { prefs -> prefs[keyBackgroundMode] = mode.name }
    }

    suspend fun updateSolidColorArgb(argb: Int) {
        dataStore.edit { prefs -> prefs[keySolidColorArgb] = argb }
    }

    suspend fun updateFrostedGradientIndex(index: Int) {
        val coerced = index.coerceIn(0, MAX_FROSTED_GRADIENT_INDEX)
        dataStore.edit { prefs -> prefs[keyFrostedGradientIndex] = coerced }
    }

    suspend fun updateShowLabels(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[keyShowLabels] = enabled }
    }

    suspend fun updateLabelFontSizeSp(value: Int) {
        val coerced = value.coerceIn(MIN_LABEL_FONT_SIZE_SP, MAX_LABEL_FONT_SIZE_SP)
        dataStore.edit { prefs -> prefs[keyLabelFontSizeSp] = coerced }
    }

    suspend fun updateSearchInDock(value: Boolean) {
        dataStore.edit { prefs -> prefs[keySearchInDock] = value }
    }

    suspend fun updateSearchSlotIndex(index: Int) {
        dataStore.edit { prefs -> prefs[keySearchSlotIndex] = index.coerceAtLeast(0) }
    }

    suspend fun updateCornerRadiusDp(value: Int) {
        val coerced = value.coerceIn(MIN_CORNER_RADIUS_DP, MAX_CORNER_RADIUS_DP)
        dataStore.edit { prefs -> prefs[keyCornerRadiusDp] = coerced }
    }

    suspend fun updateDockBackgroundOpacity(value: Float) {
        val coerced = value.coerceIn(0f, 1f)
        dataStore.edit { prefs -> prefs[keyDockBackgroundOpacity] = coerced }
    }

    suspend fun updateDockGlassRefraction(value: Float) {
        val coerced = value.coerceIn(0f, 1f)
        dataStore.edit { prefs -> prefs[keyDockGlassRefraction] = coerced }
    }

    suspend fun saveAll(pending: PendingDockSettings) {
        dataStore.edit { prefs ->
            prefs[keyMaxIcons] = pending.maxIcons.coerceIn(1, 10)
            prefs[keyDockHeightDp] = pending.dockHeightDp.coerceIn(MIN_DOCK_HEIGHT_DP, MAX_DOCK_HEIGHT_DP)
            prefs[keyIconSizeDp] = pending.iconSizeDp.coerceIn(MIN_ICON_SIZE_DP, MAX_ICON_SIZE_DP)
            prefs[keyBackgroundMode] = pending.backgroundMode.name
            prefs[keySolidColorArgb] = pending.solidColorArgb
            prefs[keyFrostedGradientIndex] = pending.frostedGradientIndex.coerceIn(0, MAX_FROSTED_GRADIENT_INDEX)
            prefs[keyShowLabels] = pending.showLabels
            prefs[keyLabelFontSizeSp] = pending.labelFontSizeSp.coerceIn(MIN_LABEL_FONT_SIZE_SP, MAX_LABEL_FONT_SIZE_SP)
            prefs[keySearchInDock] = pending.searchInDock
            prefs[keyCornerRadiusDp] = pending.cornerRadiusDp.coerceIn(MIN_CORNER_RADIUS_DP, MAX_CORNER_RADIUS_DP)
            prefs[keyDockBackgroundOpacity] = pending.dockBackgroundOpacity.coerceIn(0f, 1f)
            prefs[keyDockGlassRefraction] = pending.dockGlassRefraction.coerceIn(0f, 1f)
        }
    }

    fun save(pending: PendingDockSettings) {
        scope.launch {
            saveAll(pending)
        }
    }

    suspend fun resetToDefaults() {
        dataStore.edit { prefs ->
            prefs.remove(keyMaxIcons)
            prefs.remove(keyDockHeightDp)
            prefs.remove(keyIconSizeDp)
            prefs.remove(keyShuffleMotionPercent)
            prefs.remove(keyBackgroundMode)
            prefs.remove(keySolidColorArgb)
            prefs.remove(keyFrostedGradientIndex)
            prefs.remove(keyShowLabels)
            prefs.remove(keyLabelFontSizeSp)
            prefs.remove(keySearchInDock)
            prefs.remove(keySearchSlotIndex)
            prefs.remove(keyCornerRadiusDp)
            prefs.remove(keyDockBackgroundOpacity)
            prefs.remove(keyDockGlassRefraction)
        }
    }

    /** One-shot snapshot from current StateFlow values (additive helper for backup). */
    fun snapshot(): DockSettingsSnapshot = DockSettingsSnapshot(
        maxIcons = maxIcons.value,
        dockHeightDp = dockHeightDp.value,
        iconSizeDp = iconSizeDp.value,
        shuffleMotionPercent = shuffleMotionPercent.value,
        backgroundMode = backgroundMode.value.name,
        solidColorArgb = solidColorArgb.value,
        frostedGradientIndex = frostedGradientIndex.value,
        showLabels = showLabels.value,
        labelFontSizeSp = labelFontSizeSp.value,
        searchInDock = searchInDock.value,
        searchSlotIndex = searchSlotIndex.value,
        cornerRadiusDp = cornerRadiusDp.value,
        dockBackgroundOpacity = dockBackgroundOpacity.value,
        dockGlassRefraction = dockGlassRefraction.value
    )

    companion object {
        const val DEFAULT_SHUFFLE_MOTION_PERCENT = 100
        const val MIN_SHUFFLE_MOTION_PERCENT = 0
        const val MAX_SHUFFLE_MOTION_PERCENT = 100
        const val DEFAULT_DOCK_HEIGHT_DP = 90
        const val MIN_DOCK_HEIGHT_DP = 60
        const val MAX_DOCK_HEIGHT_DP = 120
        const val ABSOLUTE_MAX_DOCK_HEIGHT_DP = 120
        fun effectiveMaxDockHeightDp(): Int = MAX_DOCK_HEIGHT_DP
        const val DEFAULT_ICON_SIZE_DP = 48
        const val MIN_ICON_SIZE_DP = 32
        const val MAX_ICON_SIZE_DP = 72
        const val DEFAULT_SOLID_COLOR_ARGB = 0xCC0E0C18.toInt()
        const val DEFAULT_LABEL_FONT_SIZE_SP = 11
        const val MIN_LABEL_FONT_SIZE_SP = 8
        const val MAX_LABEL_FONT_SIZE_SP = 16
        const val DEFAULT_FROSTED_GRADIENT_INDEX = 0
        const val MAX_FROSTED_GRADIENT_INDEX = 14
        const val DEFAULT_CORNER_RADIUS_DP = 20
        const val MIN_CORNER_RADIUS_DP = 0
        const val MAX_CORNER_RADIUS_DP = 40
        const val DEFAULT_DOCK_BACKGROUND_OPACITY = 0.8f
        const val DEFAULT_DOCK_GLASS_REFRACTION = 0.70f
    }
}
