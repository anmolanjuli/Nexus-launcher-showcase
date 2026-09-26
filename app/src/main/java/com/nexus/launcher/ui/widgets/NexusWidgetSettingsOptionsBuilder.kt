package com.nexus.launcher.ui.widgets

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.widgets.clock.LaCrosseClockConfig
import com.nexus.launcher.ui.widgets.music.NexusWidgetSettingsRetroMusicViews
import com.nexus.launcher.ui.widgets.music.RetroMusicConfig

/**
 * Builds widget-specific option sections (Calendar, Agenda, Notes, Retro Music) for NexusWidgetSettingsSheet.
 */
object NexusWidgetSettingsOptionsBuilder {

    fun attachWidgetOptions(
        context: Context,
        content: LinearLayout,
        dp: Float,
        tokens: NexusColorTokens,
        isCalendar: Boolean,
        isAgenda: Boolean,
        isNotes: Boolean,
        isMusic: Boolean,
        appWidgetId: Int,
        retroMusicContainer: LinearLayout,
        getConfig: () -> NexusWidgetConfig.InstanceConfig,
        updateConfig: (NexusWidgetConfig.InstanceConfig) -> Unit,
        onChanged: () -> Unit
    ): (() -> Unit)? {
        if (isCalendar) {
            content.addView(sectionHeader(context, context.getString(R.string.widget_options_calendar), dp, tokens))
            val calendarGroup = NexusWidgetSettingsSheetViews.buildCalendarGroup(
                context = context,
                getConfig = getConfig,
                updateConfig = updateConfig,
                onChanged = onChanged
            )
            content.addView(calendarGroup)
        }

        if (isAgenda) {
            content.addView(sectionHeader(context, context.getString(R.string.widget_options_agenda), dp, tokens))
            val (agendaGroup, _) = NexusWidgetSettingsAgendaViews.buildAgendaGroup(
                context = context,
                dp = dp,
                tokens = tokens,
                getConfig = getConfig,
                updateConfig = updateConfig,
                onChanged = onChanged
            )
            content.addView(agendaGroup)
        }

        if (isNotes) {
            content.addView(sectionHeader(context, context.getString(R.string.widget_options_notes), dp, tokens))
            val (notesGroup, _) = NexusWidgetSettingsNotesViews.buildNotesGroup(
                context = context,
                dp = dp,
                tokens = tokens,
                getConfig = getConfig,
                updateConfig = updateConfig,
                onChanged = onChanged
            )
            content.addView(notesGroup)
        }

        var resetRetroMusic: (() -> Unit)? = null
        if (isMusic) {
            val (musicGroup, resetMusic) = NexusWidgetSettingsRetroMusicViews.buildRetroMusicGroup(
                context = context,
                appWidgetId = appWidgetId,
                tokens = tokens,
                onChanged = onChanged
            )
            resetRetroMusic = resetMusic
            retroMusicContainer.addView(sectionHeader(context, context.getString(R.string.widget_retro_music_section_title), dp, tokens))
            retroMusicContainer.addView(musicGroup)
            val cfg = getConfig()
            retroMusicContainer.visibility = if (cfg.clockStyle > 0) View.VISIBLE else View.GONE
            content.addView(retroMusicContainer)
        }

        return resetRetroMusic
    }

    fun isSubConfigDirty(
        context: Context,
        appWidgetId: Int,
        isClock: Boolean,
        isMusic: Boolean,
        origLaCrosse: LaCrosseClockConfig?,
        origRetro: RetroMusicConfig?
    ): Boolean {
        if (isClock && origLaCrosse != null) {
            if (LaCrosseClockConfig.read(context, appWidgetId) != origLaCrosse) return true
        }
        if (isMusic && origRetro != null) {
            if (RetroMusicConfig.read(context, appWidgetId) != origRetro) return true
        }
        return false
    }

    fun revertSubConfigs(
        context: Context,
        appWidgetId: Int,
        origLaCrosse: LaCrosseClockConfig?,
        origRetro: RetroMusicConfig?
    ) {
        origLaCrosse?.let { LaCrosseClockConfig.write(context, appWidgetId, it) }
        origRetro?.let { RetroMusicConfig.write(context, appWidgetId, it) }
    }

    private fun sectionHeader(context: Context, label: String, dp: Float, tokens: NexusColorTokens) = TextView(context).apply {
        text = label
        NexusTypeScale.labelSmall.bindTo(this, tokens.textSecondary)
        setPadding(0, (10 * dp).toInt(), 0, (4 * dp).toInt())
    }
}
