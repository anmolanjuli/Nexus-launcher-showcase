package com.nexus.launcher.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.launcher.data.prefs.NexusSettingsData
import com.nexus.launcher.data.prefs.PreferenceManager
import com.nexus.launcher.data.prefs.SettingsRepository
import com.nexus.launcher.locale.AppLocale
import com.nexus.launcher.locale.LocaleController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val preferenceManager: PreferenceManager,
    private val iconPackDetector: com.nexus.launcher.ui.icons.IconPackDetector,
    private val localeController: LocaleController,
    private val fontFamilyController: com.nexus.launcher.typography.FontFamilyController,
    private val themeController: com.nexus.launcher.theme.ThemeController
) : ViewModel() {

    val currentThemeMode: StateFlow<com.nexus.launcher.theme.ThemeMode> = themeController.themeMode

    fun setThemeMode(mode: com.nexus.launcher.theme.ThemeMode) = viewModelScope.launch {
        themeController.setThemeMode(mode)
    }

    val currentBackgroundLayerMode: StateFlow<com.nexus.launcher.theme.BackgroundLayerMode> = themeController.backgroundLayerMode

    fun setBackgroundLayerMode(mode: com.nexus.launcher.theme.BackgroundLayerMode) = viewModelScope.launch {
        themeController.setBackgroundLayerMode(mode)
    }

    val currentCalmPalette: StateFlow<com.nexus.launcher.theme.CalmPalette> = themeController.calmPalette

    fun setCalmPalette(palette: com.nexus.launcher.theme.CalmPalette) = viewModelScope.launch {
        themeController.setCalmPalette(palette)
    }

    val currentColorBlindMode: StateFlow<com.nexus.launcher.theme.ColorBlindMode> = themeController.colorBlindMode

    fun setColorBlindMode(mode: com.nexus.launcher.theme.ColorBlindMode) = viewModelScope.launch {
        themeController.setColorBlindMode(mode)
    }

    val currentNotificationBadgeColor: StateFlow<String?> = themeController.notificationBadgeColor

    fun setNotificationBadgeColor(hex: String?) = viewModelScope.launch {
        themeController.setNotificationBadgeColor(hex)
    }

    val currentAppLocale: StateFlow<AppLocale> = localeController.appLocale

    fun setAppLocale(locale: AppLocale) = viewModelScope.launch {
        localeController.setAppLocale(locale)
    }

    val currentFontFamily: StateFlow<com.nexus.launcher.typography.AppFontFamily> = fontFamilyController.fontFamily
    val currentFontSelectionKey: StateFlow<String> = fontFamilyController.fontSelectionKey
    val customFonts: StateFlow<List<com.nexus.launcher.typography.CustomFontEntry>> = fontFamilyController.customFonts

    fun setFontFamily(family: com.nexus.launcher.typography.AppFontFamily) = viewModelScope.launch {
        fontFamilyController.setFontFamily(family)
    }

    fun setFontSelection(selectionKey: String) = viewModelScope.launch {
        fontFamilyController.setFontSelection(selectionKey)
    }

    fun addCustomFont(entry: com.nexus.launcher.typography.CustomFontEntry) = viewModelScope.launch {
        fontFamilyController.addCustomFont(entry)
    }

    fun deleteCustomFont(id: String) = viewModelScope.launch {
        fontFamilyController.deleteCustomFont(id)
    }

    val settings: StateFlow<NexusSettingsData> = settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, NexusSettingsData())

    private val draftStore = SettingsDraftStore(settingsRepository, preferenceManager, viewModelScope)
    val draft: StateFlow<NexusSettingsData?> = draftStore.draft
    val draftAnimSpeed: StateFlow<Float> = draftStore.animSpeed
    val hasUnsaved: StateFlow<Boolean> = draftStore.unsaved
    val bindEpoch: StateFlow<Int> = draftStore.bindEpoch

    val availableIconPacks = kotlinx.coroutines.flow.MutableStateFlow<List<com.nexus.launcher.ui.icons.IconPackInfo>>(emptyList())

    init {
        viewModelScope.launch {
            availableIconPacks.value = iconPackDetector.getAvailableIconPacks()
        }
    }

    fun updateHomeColumns(value: Int) =
        viewModelScope.launch { settingsRepository.updateHomeColumns(value) }

    fun updateHomeRows(value: Int) =
        viewModelScope.launch { settingsRepository.updateHomeRows(value) }

    fun updateHomeIconSize(value: Float) =
        viewModelScope.launch { settingsRepository.updateHomeIconSize(value) }

    fun updateHomeShowLabels(value: Boolean) =
        viewModelScope.launch { settingsRepository.updateHomeShowLabels(value) }

    fun updateHomeShowIndicator(value: Boolean) =
        viewModelScope.launch { settingsRepository.updateHomeShowIndicator(value) }

    fun updateHomePaddingLeftRight(value: Float) =
        viewModelScope.launch { settingsRepository.updateHomePaddingLeftRight(value) }

    fun updateHomePaddingTopBottom(value: Float) =
        viewModelScope.launch { settingsRepository.updateHomePaddingTopBottom(value) }

    fun updateHomeGapHorizontal(value: Float) =
        viewModelScope.launch { settingsRepository.updateHomeGapHorizontal(value) }

    fun updateHomeGapVertical(value: Float) =
        viewModelScope.launch { settingsRepository.updateHomeGapVertical(value) }

    fun updateDrawerColumns(value: Int) =
        viewModelScope.launch { settingsRepository.updateDrawerColumns(value) }

    fun updateDrawerIconSize(value: Float) =
        viewModelScope.launch { settingsRepository.updateDrawerIconSize(value) }

    fun updateDrawerShowLabels(value: Boolean) =
        viewModelScope.launch { settingsRepository.updateDrawerShowLabels(value) }

    fun updateDrawerShowCategoryBar(value: Boolean) =
        viewModelScope.launch { settingsRepository.updateDrawerShowCategoryBar(value) }

    fun updateDrawerShowRail(value: Boolean) =
        viewModelScope.launch { settingsRepository.updateDrawerShowRail(value) }

    fun updateDrawerSortOrder(value: String) =
        viewModelScope.launch { settingsRepository.updateDrawerSortOrder(value) }

    fun updateDrawerTransition(value: String) =
        viewModelScope.launch { settingsRepository.updateDrawerTransition(value) }

    fun updateDrawerLayout(value: String) =
        viewModelScope.launch { settingsRepository.updateDrawerLayout(value) }

    fun updateAccentColor(color: String) =
        viewModelScope.launch { settingsRepository.updateAccentColor(color) }

    fun updateMatchWallpaperColor(value: Boolean) =
        viewModelScope.launch { settingsRepository.updateMatchWallpaperColor(value) }

    fun updateWallpaperType(type: String) =
        viewModelScope.launch { settingsRepository.updateWallpaperType(type) }

    fun updateWallpaperSolidColor(value: String) =
        viewModelScope.launch { settingsRepository.updateWallpaperSolidColor(value) }

    fun updateWallpaperGradient(start: String, end: String, direction: String) =
        viewModelScope.launch { settingsRepository.updateWallpaperGradient(start, end, direction) }

    fun updateBadgeStyleApp(value: Int) =
        viewModelScope.launch { settingsRepository.updateBadgeStyleApp(value) }

    fun updateBadgeStyleFolder(value: Int) =
        viewModelScope.launch { settingsRepository.updateBadgeStyleFolder(value) }

    fun updatePageTransition(value: String) =
        viewModelScope.launch { settingsRepository.updatePageTransition(value) }

    fun updateIconPack(value: String) =
        viewModelScope.launch { settingsRepository.updateIconPack(value) }

    fun updateIconShape(value: Int) =
        viewModelScope.launch { settingsRepository.updateIconShape(value) }

    fun updateIconTheming(value: Boolean) =
        viewModelScope.launch { settingsRepository.updateIconTheming(value) }

    fun updateFrostedGlassEnabled(value: Boolean) =
        viewModelScope.launch { settingsRepository.updateFrostedGlassEnabled(value) }

    fun updateUiStyleMode(value: String) =
        viewModelScope.launch { settingsRepository.updateUiStyleMode(value) }

    fun updateGlobalSwipeUp(value: String) = viewModelScope.launch {
        settingsRepository.updateGlobalSwipeUp(value)
    }

    fun updateGlobalSwipeDown(value: String) = viewModelScope.launch {
        settingsRepository.updateGlobalSwipeDown(value)
    }

    fun updateGlobalTwoFingerSwipeUp(value: String) = viewModelScope.launch {
        settingsRepository.updateGlobalTwoFingerSwipeUp(value)
    }

    fun updateGlobalTwoFingerSwipeDown(value: String) =
        viewModelScope.launch { settingsRepository.updateGlobalTwoFingerSwipeDown(value) }

    suspend fun ensureDraftReady() = draftStore.ensureLoaded()

    /** Immediate by default. Pass [staged] for changes that reflow the home grid — see
     *  [SettingsDraftStore.patch]. */
    fun patchDraft(
        staged: Boolean = false,
        transform: (NexusSettingsData) -> NexusSettingsData
    ) = draftStore.patch(staged, transform)

    fun patchAnimSpeed(value: Float) = draftStore.patchAnimSpeed(value)

    fun getReduceMotion(): Boolean = preferenceManager.getReduceMotion()

    fun setReduceMotion(enabled: Boolean) = preferenceManager.setReduceMotion(enabled)


    fun discardDraft() = draftStore.discard()

    fun applyDraft() {
        if (!draftStore.unsaved.value) return
        viewModelScope.launch { draftStore.apply() }
    }
}
