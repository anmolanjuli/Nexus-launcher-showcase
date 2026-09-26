package com.nexus.launcher.ui.widgets.notes

import android.content.Context
import android.graphics.Canvas
import android.graphics.Typeface
import com.nexus.launcher.ui.widgets.NexusNeumorphicDraw
import com.nexus.launcher.ui.widgets.NexusWidgetConfig
import com.nexus.launcher.ui.widgets.NexusWidgetRenderer
import com.nexus.launcher.ui.widgets.NexusWidgetThemeResolver
import com.nexus.launcher.ui.widgets.WidgetSize

/**
 * Main renderer for the first-party Notes widget.
 * Coordinates token resolution, custom typography, and multi-size layout rendering.
 */
class NexusNotesRenderer(
    private val note: NoteData
) : NexusWidgetRenderer() {

    private val cardDrawer = NexusNotesDrawCard()

    override fun drawContent(
        context: Context,
        canvas: Canvas,
        width: Int,
        height: Int,
        size: WidgetSize,
        config: NexusWidgetConfig.InstanceConfig
    ) {
        val dp = context.resources.displayMetrics.density

        val tokens = NexusWidgetThemeResolver.resolve(context, config.themeMode)
        val isNeumorphic = NexusWidgetConfig.isNeumorphicSurface(config.backgroundMode, config.isExpressive)
        val palette = if (isNeumorphic) NexusNeumorphicDraw.resolvePalette(tokens) else null

        val regularTf = getTypeface(context, config, Typeface.NORMAL)
        val boldTf = getTypeface(context, config, Typeface.BOLD)

        val wDp = width / dp
        val hDp = height / dp
        val isExpanded = wDp >= 240f && hDp >= 150f

        cardDrawer.draw(
            context, canvas, width, height, dp, config, palette, tokens, note, regularTf, boldTf, isExpanded
        )
    }
}
