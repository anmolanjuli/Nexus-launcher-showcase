package com.nexus.launcher.reader.doc

import android.content.Context
import android.text.format.DateFormat
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.nexus.launcher.reader.NexusReaderThemeHelper
import java.util.Date

/**
 * The line at the foot of the page while the toolbars are away: the time, and where you are.
 *
 * Reading full-screen means giving up the status bar, and with it the clock — so a reader checks
 * the time by leaving the page, which is the one thing immersive mode is for. Every dedicated
 * reader prints it in the margin instead, small and grey, close enough to glance at and quiet
 * enough to read past. This is that margin line: it appears only when the toolbars hide, and goes
 * as soon as they come back.
 */
class NexusReaderImmersiveStatus(
    context: Context,
    private val palette: NexusReaderThemeHelper.ReaderPalette,
) : TextView(context) {

    private val dp = resources.displayMetrics.density
    private var pageStatus: String = ""

    /** The clock only needs to move once a minute, and only while it is on screen. */
    private val tick = object : Runnable {
        override fun run() {
            render()
            postDelayed(this, MINUTE_MS)
        }
    }

    init {
        textSize = 11f
        setTextColor(palette.textSecondary)
        alpha = 0f
        visibility = View.GONE
        gravity = Gravity.CENTER
        letterSpacing = 0.04f
        includeFontPadding = false
        isClickable = false
        isFocusable = false
    }

    /** Places the line above the navigation bar; call again when the insets change. */
    fun applyInsets(navBarHeight: Int) {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            bottomMargin = navBarHeight + (10 * dp).toInt()
        }
    }

    /** Follows the page's own paper, so the line sits on the sheet rather than beside it. */
    fun applyTheme(theme: PdfThemeModeStore.ThemeMode) {
        setTextColor(PdfThemeFilterApplier.inkColor(theme, palette.textSecondary))
    }

    /** The reader's own status line — "Page 34 of 210", "Chapter 3 of 24". */
    fun setPageStatus(status: String) {
        pageStatus = status
        if (visibility == View.VISIBLE) render()
    }

    fun show() {
        if (visibility == View.VISIBLE) return
        render()
        visibility = View.VISIBLE
        animate().alpha(1f).setDuration(160).start()
        removeCallbacks(tick)
        postDelayed(tick, MINUTE_MS)
    }

    fun hide() {
        removeCallbacks(tick)
        if (visibility != View.VISIBLE) return
        animate().alpha(0f).setDuration(120).withEndAction { visibility = View.GONE }.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(tick)
    }

    private fun render() {
        val pattern = if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a"
        val clock = DateFormat.format(pattern, Date()).toString()
        text = if (pageStatus.isBlank()) clock else "$clock   ·   $pageStatus"
    }

    private companion object {
        const val MINUTE_MS = 60_000L
    }
}
