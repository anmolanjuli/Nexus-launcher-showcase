package com.nexus.launcher.ui

import android.content.Context
import android.content.res.Configuration
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Outer layout of [WallpaperSheet] — split out so the sheet stays under the line cap.
 *
 * The sheet used to be one bottom-aligned column: a preview sized at 34% of the screen height
 * over an options list fixed at 52% of it. Together with the Apply row that is taller than the
 * screen, so the column overflowed and the options were squeezed and hard to scroll — worst in
 * landscape, where the screen is short.
 *
 * Portrait: preview block on top at its natural size, options filling exactly the rest.
 * Landscape: preview block on the left, options on the right, both full height.
 * Either way everything is on screen and the options scroll within their own area.
 */
internal object WallpaperSheetLayout {

    private const val LANDSCAPE_PREVIEW_WEIGHT = 0.45f
    private const val LANDSCAPE_OPTIONS_WEIGHT = 0.55f

    /** Horizontal room the dim and tint sliders take beside the preview. */
    private const val SLIDER_ROOM_DP = 140f

    fun isLandscape(context: Context): Boolean =
        context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    /**
     * Preview frame size, keeping the screen's own aspect ratio: 34% of the screen height in
     * portrait; in landscape as large as fits the left pane beside its sliders.
     */
    fun previewSize(context: Context, density: Float): Pair<Int, Int> {
        val dm = context.resources.displayMetrics
        val aspect = dm.widthPixels.toFloat() / dm.heightPixels.coerceAtLeast(1)
        if (!isLandscape(context)) {
            val h = (dm.heightPixels * 0.34f).toInt()
            return (h * aspect).toInt() to h
        }
        val maxW = dm.widthPixels * LANDSCAPE_PREVIEW_WEIGHT - SLIDER_ROOM_DP * density
        val maxH = dm.heightPixels * 0.5f
        val h = minOf(maxH, maxW / aspect).coerceAtLeast(1f)
        return (h * aspect).toInt() to h.toInt()
    }

    /** Arranges [previewBlock] (preview, sliders, Apply) and [options] for the orientation. */
    fun build(context: Context, previewBlock: LinearLayout, options: ScrollView): LinearLayout {
        val landscape = isLandscape(context)
        val root = LinearLayout(context).apply {
            orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        if (landscape) {
            previewBlock.gravity = Gravity.CENTER
            previewBlock.layoutParams =
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, LANDSCAPE_PREVIEW_WEIGHT)
            options.layoutParams =
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, LANDSCAPE_OPTIONS_WEIGHT)
        } else {
            previewBlock.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            options.layoutParams =
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        // Both panes now fill the screen, so a tap on their empty space must not fall through
        // to the sheet's tap-outside-to-dismiss.
        previewBlock.isClickable = true
        options.isClickable = true
        root.addView(previewBlock)
        root.addView(options)
        // Clear the status bar, and in landscape the camera cutout / side nav bar. The options
        // list pads its own bottom for the navigation bar.
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(bars.left, bars.top, bars.right, 0)
            insets
        }
        return root
    }
}
