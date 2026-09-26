package com.nexus.launcher.ui.widgets

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.nexus.launcher.R
import com.nexus.launcher.ui.MainActivity
import com.nexus.launcher.ui.widgets.battery.NexusBatteryRenderer
import com.nexus.launcher.ui.widgets.calendar.NexusCalendarRenderer
import com.nexus.launcher.ui.widgets.clock.NexusClockRenderer
import com.nexus.launcher.ui.widgets.music.NexusMusicRenderer
import com.nexus.launcher.ui.widgets.performance.NexusPerformanceRenderer
import com.nexus.launcher.ui.widgets.weather.NexusWeatherRenderer

/** Places the widget settings card opposite the widget and snapshots or directly renders a live preview. */
object NexusWidgetSettingsSheetLayout {

    data class Placement(
        val gravity: Int,
        val topMargin: Int,
        val bottomMargin: Int,
        val capPx: Int,
        /** The system gesture-nav inset alone (not folded into [bottomMargin] any more) — callers
         *  add this as INTERNAL bottom padding on the card's own footer instead of an external
         *  margin, so the card's frosted fill still extends visually behind the gesture-nav area
         *  instead of leaving an un-frosted gap of plain scrim there (see [placement]'s comment). */
        val navBarInset: Int
    )

    fun placement(activity: MainActivity, widgetRect: Rect, density: Float): Placement {
        val container = activity.findViewById<android.widget.FrameLayout>(
            com.nexus.launcher.R.id.main_container
        )
        val screenH = container.height.takeIf { it > 0 }
            ?: activity.resources.displayMetrics.heightPixels
        val rootInsets = ViewCompat.getRootWindowInsets(activity.window.decorView)
        val topBarInset = rootInsets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: (24 * density).toInt()
        val navBarInset = rootInsets?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: (16 * density).toInt()
        val edge = (16 * density).toInt()
        val topMargin = topBarInset + edge
        // Was `navBarInset + edge` as an EXTERNAL margin on the card itself, leaving a gap of
        // bare (un-frosted) scrim between the card's bottom edge and the true screen bottom —
        // visually a distinct "different fragment" right where the system gesture-nav pill sits,
        // unlike the real-Dialog-based Icon/Folder edit sheets which never leave this gap because
        // the system positions those dialog windows above the nav bar automatically. Keep only
        // the cosmetic edge margin here; navBarInset now goes into the footer's own bottom
        // padding below instead, so the card's frosted fill still reaches the true screen edge.
        val bottomMargin = 0
        val availableCardHeight = screenH - topMargin - navBarInset
        val cap = (availableCardHeight - (260 * density).toInt()).coerceAtLeast((220 * density).toInt())
        return Placement(Gravity.BOTTOM, topMargin, bottomMargin, cap, navBarInset)
    }

    fun calculateScrollCapPx(activity: MainActivity, density: Float): Int {
        val screenH = activity.resources.displayMetrics.heightPixels
        val rootInsets = ViewCompat.getRootWindowInsets(activity.window.decorView)
        val topBarInset = rootInsets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: (24 * density).toInt()
        val navBarInset = rootInsets?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: (16 * density).toInt()
        val baseBottomMargin = (8 * density).toInt()
        val cardBottomMargin = com.nexus.launcher.ui.LandscapeSheets.cardBottomMargin(activity, navBarInset, baseBottomMargin)
        val topMargin = topBarInset + (16 * density).toInt()
        val availableSheetHeight = screenH - topMargin - cardBottomMargin
        val fixedOverhead = (270 * density).toInt()
        return (availableSheetHeight - fixedOverhead).coerceIn((160 * density).toInt(), (screenH * 0.42f).toInt())
    }

    fun renderLivePreview(
        context: Context,
        appWidgetId: Int,
        config: NexusWidgetConfig.InstanceConfig,
        host: AppWidgetHostView?,
        into: ImageView,
        density: Float
    ) {
        val appWidgetManager = AppWidgetManager.getInstance(context)
        val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
        val className = info?.provider?.className ?: ""

        val widthPx = host?.width?.takeIf { it > 0 } ?: (into.width.takeIf { it > 0 } ?: (140 * density).toInt())
        val heightPx = host?.height?.takeIf { it > 0 } ?: (into.height.takeIf { it > 0 } ?: (100 * density).toInt())
        val minWDp = (widthPx / density).toInt().coerceAtLeast(1)
        val minHDp = (heightPx / density).toInt().coerceAtLeast(1)

        val directBmp: Bitmap? = when {
            className.contains("NexusProgressWidgetProvider") -> {
                com.nexus.launcher.ui.widgets.progress.NexusProgressRenderer().render(context, widthPx, heightPx, config, minWDp, minHDp, -1f)
            }
            className.contains("NexusPerformanceWidgetProvider") -> {
                NexusPerformanceRenderer().render(context, widthPx, heightPx, config, minWDp, minHDp, -1f)
            }
            className.contains("NexusWeatherWidgetProvider") -> {
                val mockWeather = NexusWidgetPreviewCache.createMockWeatherData(context)
                NexusWeatherRenderer(mockWeather).render(context, widthPx, heightPx, config, minWDp, minHDp, -1f)
            }
            className.contains("NexusCalendarWidgetProvider") -> {
                val mockEvents = NexusWidgetPreviewCache.createMockCalendarEvents(context)
                NexusCalendarRenderer(mockEvents).render(context, widthPx, heightPx, config, minWDp, minHDp, -1f)
            }
            className.contains("NexusMusicWidgetProvider") -> {
                NexusMusicRenderer().render(context, widthPx, heightPx, config, minWDp, minHDp, 0.42f)
            }
            className.contains("NexusClockWidgetProvider") -> {
                NexusClockRenderer().render(context, widthPx, heightPx, config, minWDp, minHDp, -1f)
            }
            className.contains("NexusAgendaWidgetProvider") -> {
                val mockEvents = NexusWidgetPreviewCache.createMockCalendarEvents(context)
                com.nexus.launcher.ui.widgets.agenda.NexusAgendaRenderer(mockEvents).render(context, widthPx, heightPx, config, minWDp, minHDp, -1f)
            }
            className.contains("NexusNotesWidgetProvider") -> {
                val note = com.nexus.launcher.ui.widgets.notes.NotesDataStore.read(context, appWidgetId)
                val displayNote = if (note.isEmpty()) {
                    com.nexus.launcher.ui.widgets.notes.NoteData(
                        title = context.getString(com.nexus.launcher.R.string.widget_notes_sample_title),
                        body = context.getString(com.nexus.launcher.R.string.widget_notes_sample_body),
                        urgency = com.nexus.launcher.ui.widgets.notes.NoteData.URGENCY_SCHEDULE,
                        status = com.nexus.launcher.ui.widgets.notes.NoteData.STATUS_WIP,
                        progress = 65,
                        updatedAt = System.currentTimeMillis()
                    )
                } else note
                com.nexus.launcher.ui.widgets.notes.NexusNotesRenderer(displayNote).render(context, widthPx, heightPx, config, minWDp, minHDp, -1f)
            }
            className.contains("NexusGlanceWidgetProvider") -> {
                val mockWeather = NexusWidgetPreviewCache.createMockWeatherData(context)
                com.nexus.launcher.ui.widgets.glance.NexusGlanceRenderer(mockWeather).render(context, widthPx, heightPx, config, minWDp, minHDp, -1f)
            }
            className.contains("NexusBatteryWidgetProvider") -> {
                NexusBatteryRenderer().render(context, widthPx, heightPx, config, minWDp, minHDp, -1f)
            }
            className.contains("NexusSearchWidgetProvider") -> {
                com.nexus.launcher.search.ui.NexusSearchWidgetRenderer().render(context, widthPx, heightPx, config, minWDp, minHDp)
            }
            else -> null
        }

        if (directBmp != null) {
            into.setImageBitmap(directBmp)
            WidgetGlassPreviewDrawable.bind(into, config, directBmp)
        } else if (host != null && host.width > 0 && host.height > 0) {
            val bmp = Bitmap.createBitmap(host.width, host.height, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.TRANSPARENT)
            host.draw(Canvas(bmp))
            into.setImageBitmap(bmp)
        }
    }

    fun sectionHeader(context: Context, label: String, tokens: com.nexus.launcher.theme.NexusColorTokens, dp: Float) =
        android.widget.TextView(context).apply {
            text = label
            com.nexus.launcher.typography.NexusTypeScale.labelSmall.bindTo(this, tokens.textSecondary)
            setPadding(0, (10 * dp).toInt(), 0, (4 * dp).toInt())
        }

    fun cardBackground(tokens: com.nexus.launcher.theme.NexusColorTokens, dp: Float) =
        NexusEditBottomSheetHelper.buildCardBackground(tokens, dp)

    fun buildHeaderRow(
        context: Context,
        tokens: com.nexus.launcher.theme.NexusColorTokens,
        dp: Float,
        onClose: () -> Unit
    ): LinearLayout {
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (8 * dp).toInt()
            }
        }
        val titleText = TextView(context).apply {
            text = context.getString(R.string.widget_settings_title)
            com.nexus.launcher.typography.NexusTypeScale.title.bindTo(this, tokens.textPrimary)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeBtn = ImageView(context).apply {
            setImageResource(R.drawable.ic_close)
            imageTintList = ColorStateList.valueOf(tokens.textSecondary)
            val pad = (6 * dp).toInt()
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams((32 * dp).toInt(), (32 * dp).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(tokens.surfaceRaised)
            }
            setOnClickListener {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                onClose()
            }
        }
        headerRow.addView(titleText)
        headerRow.addView(closeBtn)
        return headerRow
    }
}

