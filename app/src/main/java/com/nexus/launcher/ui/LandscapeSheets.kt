package com.nexus.launcher.ui

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.nexus.launcher.ui.canvas.LayoutProfile

/**
 * Phone-landscape policy for sheets and full-screen settings content — the one place it lives,
 * so every sheet gets the same treatment instead of each being patched for landscape.
 *
 * In phone landscape the screen is ~400dp tall and ~900dp wide, with the camera cutout (and a
 * 3-button nav bar) on a side edge. Sheets built for portrait there stretch edge to edge, run
 * under the cutout, and open half-collapsed with most of their content below the fold.
 */
object LandscapeSheets {

    /** Sheet width cap in landscape; centred, which also keeps it clear of the side cutout. */
    private const val SHEET_MAX_WIDTH_DP = 600f

    /** Readable width for full-screen content such as the Settings pages. */
    const val CONTENT_MAX_WIDTH_DP = 720f

    /**
     * For a sheet created in phone landscape: cap and centre its width, and open it fully
     * expanded (no half-open peek). Applied at creation and again once the sheet is attached,
     * after the sheet's own setup has had its say. No-op in portrait and on large screens.
     */
    fun apply(dialog: BottomSheetDialog) {
        keepImmersive(dialog)
        if (!LayoutProfile.isPhoneLandscape(dialog.context)) return
        val maxWidth = (SHEET_MAX_WIDTH_DP * dialog.context.resources.displayMetrics.density).toInt()
        fun configure() {
            val behavior = dialog.behavior
            behavior.maxWidth = maxWidth
            behavior.skipCollapsed = true
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }
        configure()
        dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?.post { configure() }
    }

    /**
     * A sheet is its own window, and a new window brings the system bars back. Over the home
     * screen in immersive mode, hide them on the sheet's window too, so the sheet keeps the full
     * height and the gesture bar only appears on an edge swipe. Sheets opened from Settings (not
     * immersive) are left alone.
     */
    private fun keepImmersive(dialog: android.app.Dialog) {
        if (!ImmersiveModeController.isActive || hostActivity(dialog.context) !is MainActivity) return
        ImmersiveModeController.hideOn(dialog.window ?: return)
    }

    private fun hostActivity(context: android.content.Context): android.app.Activity? {
        var c: android.content.Context? = context
        while (c is android.content.ContextWrapper) {
            if (c is android.app.Activity) return c
            c = c.baseContext
        }
        return null
    }

    /**
     * Bottom margin for a sheet's card: the navigation bar plus [gap] in portrait; in phone
     * landscape just the navigation bar, so the card uses the height down to it — and down to the
     * screen edge in immersive mode, where the navigation bar inset is 0.
     */
    fun cardBottomMargin(context: android.content.Context, navBarBottom: Int, gap: Int): Int =
        if (LayoutProfile.isPhoneLandscape(context)) navBarBottom else navBarBottom + gap

    /**
     * Pads [view] left/right so its content clears the camera cutout and a side nav bar in
     * phone landscape and is centred at [CONTENT_MAX_WIDTH_DP]. Keeps the view's top/bottom
     * padding and passes the insets on to its children untouched. No-op padding otherwise.
     */
    fun installReadableWidth(view: View) {
        val baseLeft = view.paddingLeft
        val baseRight = view.paddingRight
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            var left = baseLeft
            var right = baseRight
            if (LayoutProfile.isPhoneLandscape(v.context)) {
                val side = insets.getInsets(
                    WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.navigationBars()
                )
                val screenWidth = v.resources.displayMetrics.widthPixels
                val maxWidth = (CONTENT_MAX_WIDTH_DP * v.resources.displayMetrics.density).toInt()
                val centring = ((screenWidth - maxWidth) / 2).coerceAtLeast(0)
                left = maxOf(side.left, centring) + baseLeft
                right = maxOf(side.right, centring) + baseRight
            }
            v.setPadding(left, v.paddingTop, right, v.paddingBottom)
            insets
        }
    }
}
