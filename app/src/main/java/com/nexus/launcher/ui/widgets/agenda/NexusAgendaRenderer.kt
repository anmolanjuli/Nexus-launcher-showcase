package com.nexus.launcher.ui.widgets.agenda

import android.content.Context
import android.graphics.Canvas
import android.graphics.Typeface
import com.nexus.launcher.ui.widgets.NexusNeumorphicDraw
import com.nexus.launcher.ui.widgets.NexusWidgetConfig
import com.nexus.launcher.ui.widgets.NexusWidgetRenderer
import com.nexus.launcher.ui.widgets.NexusWidgetThemeResolver
import com.nexus.launcher.ui.widgets.WidgetSize
import com.nexus.launcher.ui.widgets.calendar.CalendarEvent

/**
 * Main renderer for the Nexus Agenda widget.
 * Coordinates token resolution, font resolution, event filtering, and size-specific draw delegators.
 */
class NexusAgendaRenderer(
    private val rawEvents: List<CalendarEvent>?
) : NexusWidgetRenderer() {

    private val formatter = AgendaDateFormatter()
    private val heroDrawer = NexusAgendaDrawHero()
    private val scheduleDrawer = NexusAgendaDrawSchedule()

    override fun drawContent(
        context: Context,
        canvas: Canvas,
        width: Int,
        height: Int,
        size: WidgetSize,
        config: NexusWidgetConfig.InstanceConfig
    ) {
        val dp = context.resources.displayMetrics.density
        formatter.ensure(context)

        val tokens = NexusWidgetThemeResolver.resolve(context, config.themeMode)
        val isNeumorphic = NexusWidgetConfig.isNeumorphicSurface(config.backgroundMode, config.isExpressive)
        val palette = NexusNeumorphicDraw.resolvePalette(tokens)

        val regularTf = getTypeface(context, config, Typeface.NORMAL)
        val boldTf = getTypeface(context, config, Typeface.BOLD)

        val events = AgendaEventFilter.filterAndSort(rawEvents, config.agendaRange)

        val hDp = height / dp

        when {
            hDp < 95f -> heroDrawer.draw(
                context, canvas, width, height, dp, config, palette, tokens, events, formatter, regularTf, boldTf
            )
            // Every larger size: days and event pills, as many as the height holds.
            else -> scheduleDrawer.draw(
                context, canvas, width, height, dp, config, palette, tokens, events, formatter, regularTf, boldTf
            )
        }
    }
}
