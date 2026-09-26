package com.nexus.launcher.ui.widgets.music

import android.content.Context
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.ui.settings.views.NexusSegmentedRow
import com.nexus.launcher.ui.settings.views.NexusToggleRow
import com.nexus.launcher.ui.settings.views.SettingsSectionGroupView

/**
 * Builds the settings options group for Retro Music Player Widget styles.
 * Configures surface appearance (Default / Frosted / Neumorphic), album art visibility,
 * progress bar display, and shuffle/repeat buttons.
 */
object NexusWidgetSettingsRetroMusicViews {

    fun buildRetroMusicGroup(
        context: Context,
        appWidgetId: Int,
        tokens: NexusColorTokens,
        onChanged: () -> Unit
    ): Pair<SettingsSectionGroupView, () -> Unit> {
        val group = SettingsSectionGroupView(context)
        var cfg = RetroMusicConfig.read(context, appWidgetId)

        // 1. Surface Style Selector (Default / Frosted / Neumorphism)
        val surfaceRow = NexusSegmentedRow(context).apply {
            val options = listOf(
                RetroMusicConfig.SurfaceMode.DEFAULT.name to context.getString(R.string.widget_retro_surface_default),
                RetroMusicConfig.SurfaceMode.FROSTED.name to context.getString(R.string.widget_retro_surface_frosted),
                RetroMusicConfig.SurfaceMode.NEUMORPHIC.name to context.getString(R.string.widget_retro_surface_neumorphic)
            )
            configure(
                label = context.getString(R.string.widget_retro_surface_title),
                options = options,
                initialValue = cfg.surfaceMode.name
            )
            onValueChanged = { selected ->
                val mode = try {
                    RetroMusicConfig.SurfaceMode.valueOf(selected)
                } catch (_: Exception) {
                    RetroMusicConfig.SurfaceMode.DEFAULT
                }
                cfg = cfg.copy(surfaceMode = mode)
                RetroMusicConfig.write(context, appWidgetId, cfg)
                onChanged()
            }
        }
        group.addChildRow(surfaceRow)

        // 2. Show Album Art Toggle
        val artToggle = NexusToggleRow(context).apply {
            configure(context.getString(R.string.widget_retro_music_show_art), cfg.showAlbumArt)
            onCheckedChanged = { checked ->
                cfg = cfg.copy(showAlbumArt = checked)
                RetroMusicConfig.write(context, appWidgetId, cfg)
                onChanged()
            }
        }
        group.addChildRow(artToggle)

        // 3. Show Progress Bar Toggle
        val progressToggle = NexusToggleRow(context).apply {
            configure(context.getString(R.string.widget_retro_music_show_progress), cfg.showProgressBar)
            onCheckedChanged = { checked ->
                cfg = cfg.copy(showProgressBar = checked)
                RetroMusicConfig.write(context, appWidgetId, cfg)
                onChanged()
            }
        }
        group.addChildRow(progressToggle)

        // 4. Show Shuffle & Repeat Toggle
        val shuffleRepeatToggle = NexusToggleRow(context).apply {
            configure(context.getString(R.string.widget_retro_music_show_shuffle_repeat), cfg.showShuffleRepeat)
            onCheckedChanged = { checked ->
                cfg = cfg.copy(showShuffleRepeat = checked)
                RetroMusicConfig.write(context, appWidgetId, cfg)
                onChanged()
            }
        }
        group.addChildRow(shuffleRepeatToggle)

        val resetAction = {
            val defaults = RetroMusicConfig()
            RetroMusicConfig.write(context, appWidgetId, defaults)
            cfg = defaults
            surfaceRow.setValue(defaults.surfaceMode.name)
            artToggle.configure(context.getString(R.string.widget_retro_music_show_art), defaults.showAlbumArt)
            progressToggle.configure(context.getString(R.string.widget_retro_music_show_progress), defaults.showProgressBar)
            shuffleRepeatToggle.configure(context.getString(R.string.widget_retro_music_show_shuffle_repeat), defaults.showShuffleRepeat)
        }

        return Pair(group, resetAction)
    }
}
