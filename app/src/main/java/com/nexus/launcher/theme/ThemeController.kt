package com.nexus.launcher.theme

import android.app.Application
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ThemeController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val keyThemeMode = stringPreferencesKey("theme_mode")
    private val keyCalmPalette = stringPreferencesKey("calm_palette")
    private val keyBackgroundLayerMode = stringPreferencesKey("background_layer_mode")
    private val keyAccentColor = stringPreferencesKey("accent_color")
    private val keyColorBlindMode = stringPreferencesKey("color_blind_mode")
    private val keyNotificationBadgeColor = stringPreferencesKey("notification_badge_color")

    private val isSystemNight: Boolean
        get() = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES

    private val systemNightMode = MutableStateFlow(isSystemNight)

    init {
        val callbacks = object : ComponentCallbacks {
            override fun onConfigurationChanged(newConfig: Configuration) {
                val isNight = (newConfig.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                        Configuration.UI_MODE_NIGHT_YES
                systemNightMode.value = isNight
            }

            override fun onLowMemory() {}
        }
        (context.applicationContext as? Application)?.registerComponentCallbacks(callbacks)
            ?: context.registerComponentCallbacks(callbacks)
    }

    val backgroundLayerMode: StateFlow<BackgroundLayerMode> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            val stored = prefs[keyBackgroundLayerMode]
            if (stored != null) {
                try {
                    BackgroundLayerMode.valueOf(stored)
                } catch (_: IllegalArgumentException) {
                    BackgroundLayerMode.WALLPAPER
                }
            } else {
                BackgroundLayerMode.WALLPAPER
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(scope, SharingStarted.Eagerly, BackgroundLayerMode.WALLPAPER)

    val themeMode: StateFlow<ThemeMode> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            val stored = prefs[keyThemeMode]
            if (stored != null) {
                try {
                    ThemeMode.valueOf(stored)
                } catch (_: IllegalArgumentException) {
                    ThemeMode.AUTOMATIC
                }
            } else {
                ThemeMode.AUTOMATIC
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(scope, SharingStarted.Eagerly, ThemeMode.AUTOMATIC)

    val calmPalette: StateFlow<CalmPalette> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            val stored = prefs[keyCalmPalette]
            if (stored != null) {
                try {
                    CalmPalette.valueOf(stored)
                } catch (_: IllegalArgumentException) {
                    CalmPalette.OCEAN_BLUE
                }
            } else {
                CalmPalette.OCEAN_BLUE
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(scope, SharingStarted.Eagerly, CalmPalette.OCEAN_BLUE)

    val colorBlindMode: StateFlow<ColorBlindMode> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            val stored = prefs[keyColorBlindMode]
            if (stored != null) {
                try {
                    ColorBlindMode.valueOf(stored)
                } catch (_: IllegalArgumentException) {
                    ColorBlindMode.NONE
                }
            } else {
                ColorBlindMode.NONE
            }
        }
        .flowOn(Dispatchers.IO)
        .stateIn(scope, SharingStarted.Eagerly, ColorBlindMode.NONE)

    val notificationBadgeColor: StateFlow<String?> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> prefs[keyNotificationBadgeColor] }
        .flowOn(Dispatchers.IO)
        .stateIn(scope, SharingStarted.Eagerly, null)

    private val savedAccentHex: StateFlow<String?> = dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs -> prefs[keyAccentColor] }
        .flowOn(Dispatchers.IO)
        .stateIn(scope, SharingStarted.Eagerly, null)

    val currentTokens: StateFlow<NexusColorTokens> = combine(
        themeMode,
        calmPalette,
        systemNightMode,
        savedAccentHex,
        colorBlindMode
    ) { mode, palette, isNight, accentHex, colorBlind ->
        val base = resolveTokens(mode, palette, isNight)
        val accented = applyAccentOverride(base, accentHex)
        val finalTokens = applyColorBlindOverride(accented, colorBlind)
        com.nexus.launcher.ui.widgets.NexusWidgetPreviewCache.clear()
        com.nexus.launcher.ui.widgets.performance.PerformancePreviewBuilder.clearCache()
        com.nexus.launcher.ui.widgets.mosaic.NexusCatalogPreviewDrawers.clearCache()
        android.util.Log.d(TAG, "currentTokens emission: mode=$mode, palette=$palette, isNight=$isNight, accentHex=$accentHex, colorBlind=$colorBlind, danger=#${Integer.toHexString(finalTokens.danger).uppercase()}")
        finalTokens
    }.stateIn(
        scope,
        SharingStarted.Eagerly,
        applyColorBlindOverride(
            applyAccentOverride(
                resolveTokens(themeMode.value, calmPalette.value, systemNightMode.value),
                savedAccentHex.value
            ),
            colorBlindMode.value
        ).also {
            android.util.Log.d(TAG, "currentTokens initial: mode=${themeMode.value}, palette=${calmPalette.value}, isNight=${systemNightMode.value}, danger=#${Integer.toHexString(it.danger).uppercase()}")
        }
    )

    private fun applyAccentOverride(baseTokens: NexusColorTokens, accentHex: String?): NexusColorTokens {
        if (accentHex.isNullOrBlank()) return baseTokens
        return try {
            val parsedAccent = android.graphics.Color.parseColor(accentHex)
            baseTokens.copy(
                accent = parsedAccent,
                accentMuted = NexusColorTokens.computeAccentMuted(parsedAccent)
            )
        } catch (_: Exception) {
            baseTokens
        }
    }

    private fun applyColorBlindOverride(tokens: NexusColorTokens, colorBlind: ColorBlindMode): NexusColorTokens {
        val safeDanger = colorBlind.safeDangerColor ?: return tokens
        return tokens.copy(danger = safeDanger)
    }

    fun resolveTokens(mode: ThemeMode, palette: CalmPalette, isNight: Boolean): NexusColorTokens {
        return when (mode) {
            ThemeMode.LIGHT -> NexusColorTokens.Light
            ThemeMode.DARK -> NexusColorTokens.Dark
            ThemeMode.AMOLED -> NexusColorTokens.Amoled
            ThemeMode.CALM -> NexusColorTokens.getCalmTokens(palette)
            ThemeMode.AUTOMATIC -> if (isNight) NexusColorTokens.Dark else NexusColorTokens.Light
        }
    }

    fun resolveTokens(mode: ThemeMode, isNight: Boolean): NexusColorTokens {
        return resolveTokens(mode, calmPalette.value, isNight)
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        android.util.Log.d(TAG, "setThemeMode() called with mode=$mode")
        withContext(Dispatchers.IO) {
            dataStore.edit { prefs ->
                prefs[keyThemeMode] = mode.name
            }
        }
    }

    suspend fun setCalmPalette(palette: CalmPalette) {
        android.util.Log.d(TAG, "setCalmPalette() called with palette=$palette")
        withContext(Dispatchers.IO) {
            dataStore.edit { prefs ->
                prefs[keyCalmPalette] = palette.name
            }
        }
    }

    suspend fun setBackgroundLayerMode(mode: BackgroundLayerMode) {
        android.util.Log.d(TAG, "setBackgroundLayerMode() called with mode=$mode")
        withContext(Dispatchers.IO) {
            dataStore.edit { prefs ->
                prefs[keyBackgroundLayerMode] = mode.name
            }
        }
    }

    suspend fun setColorBlindMode(mode: ColorBlindMode) {
        android.util.Log.d(TAG, "setColorBlindMode() called with mode=$mode")
        withContext(Dispatchers.IO) {
            dataStore.edit { prefs ->
                prefs[keyColorBlindMode] = mode.name
            }
        }
    }

    suspend fun setNotificationBadgeColor(hex: String?) {
        android.util.Log.d(TAG, "setNotificationBadgeColor() called with hex=$hex")
        withContext(Dispatchers.IO) {
            dataStore.edit { prefs ->
                if (hex.isNullOrBlank()) {
                    prefs.remove(keyNotificationBadgeColor)
                } else {
                    prefs[keyNotificationBadgeColor] = hex
                }
            }
        }
    }

    companion object {
        private const val TAG = "ThemeController"
    }
}
