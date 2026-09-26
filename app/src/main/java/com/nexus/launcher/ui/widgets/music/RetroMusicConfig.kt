package com.nexus.launcher.ui.widgets.music

import android.content.Context
import com.nexus.launcher.ui.widgets.NexusWidgetConfig

/**
 * Persisted configuration for Retro Music Player Widget styles.
 * Controls toggles for album art, progress bar, shuffle/repeat, and matching UI surface options.
 */
data class RetroMusicConfig(
    val showAlbumArt: Boolean = true,
    val showShuffleRepeat: Boolean = false,
    val showProgressBar: Boolean = true,
    val themeAware: Boolean = false,
    val matchSystemUi: Boolean = false,
    val surfaceMode: SurfaceMode = SurfaceMode.DEFAULT
) {
    enum class SurfaceMode {
        DEFAULT,
        FROSTED,
        NEUMORPHIC
    }

    fun resolveEffectiveSurface(): SurfaceMode = surfaceMode

    companion object {
        const val STYLE_MODERN = 0
        const val STYLE_WINAMP = 1
        const val STYLE_AMPLIFIER = 2
        const val STYLE_TERMINAL = 3
        const val STYLE_CASSETTE = 4

        private const val KEY_SHOW_ART = "retro_music_show_art_"
        private const val KEY_SHOW_SHUFFLE_REPEAT = "retro_music_show_sr_"
        private const val KEY_SHOW_PROGRESS = "retro_music_show_prog_"
        private const val KEY_THEME_AWARE = "retro_music_theme_aware_"
        private const val KEY_MATCH_SYSTEM_UI = "retro_music_match_system_ui_"
        private const val KEY_SURFACE = "retro_music_surface_"

        fun read(context: Context, appWidgetId: Int): RetroMusicConfig {
            val prefs = context.getSharedPreferences(NexusWidgetConfig.PREFS_NAME, Context.MODE_PRIVATE)
            val surfaceStr = prefs.getString("$KEY_SURFACE$appWidgetId", SurfaceMode.DEFAULT.name)
            val surface = try {
                SurfaceMode.valueOf(surfaceStr ?: SurfaceMode.DEFAULT.name)
            } catch (_: Exception) {
                SurfaceMode.DEFAULT
            }
            return RetroMusicConfig(
                showAlbumArt = prefs.getBoolean("$KEY_SHOW_ART$appWidgetId", true),
                showShuffleRepeat = prefs.getBoolean("$KEY_SHOW_SHUFFLE_REPEAT$appWidgetId", false),
                showProgressBar = prefs.getBoolean("$KEY_SHOW_PROGRESS$appWidgetId", true),
                themeAware = prefs.getBoolean("$KEY_THEME_AWARE$appWidgetId", false),
                matchSystemUi = surface != SurfaceMode.DEFAULT,
                surfaceMode = surface
            )
        }

        fun write(context: Context, appWidgetId: Int, config: RetroMusicConfig) {
            val prefs = context.getSharedPreferences(NexusWidgetConfig.PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("$KEY_SHOW_ART$appWidgetId", config.showAlbumArt)
                .putBoolean("$KEY_SHOW_SHUFFLE_REPEAT$appWidgetId", config.showShuffleRepeat)
                .putBoolean("$KEY_SHOW_PROGRESS$appWidgetId", config.showProgressBar)
                .putBoolean("$KEY_THEME_AWARE$appWidgetId", config.themeAware)
                .putBoolean("$KEY_MATCH_SYSTEM_UI$appWidgetId", config.surfaceMode != SurfaceMode.DEFAULT)
                .putString("$KEY_SURFACE$appWidgetId", config.surfaceMode.name)
                .apply()
        }
    }
}
