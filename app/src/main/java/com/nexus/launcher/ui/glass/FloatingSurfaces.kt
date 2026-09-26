package com.nexus.launcher.ui.glass

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import com.nexus.launcher.theme.NexusColorTokens

/**
 * The **floating** surface role: dialogs, bottom sheets and anchored menus that sit in their own
 * window above the launcher.
 *
 * Its sibling is [FrostedPanelLayout], the *panel* role for bounded surfaces that frost themselves
 * from the wallpaper. A floating surface cannot do that — it is a separate window over whatever
 * the launcher is showing — so its glass is a window-level blur behind it plus a translucent card
 * on top. This file owns both halves, so every sheet gets the same pair.
 *
 * ## What it replaced
 *
 * Six sheets carried their own copy of the blur: `FLAG_BLUR_BEHIND` at 25dp, gated only on the
 * Android version. None asked for the UI Style, so in Neumorphism and Default every one of them
 * still blurred the screen behind it — frost leaking into the two styles that are defined by not
 * having any. And their cards split two ways under Frosted Glass: the folder sheets drew a
 * translucent frosted card, while three feed sheets drew an opaque `surface` slab that never
 * looked frosted at all, over a blur it could not show.
 *
 * ## The dim is deliberately not here
 *
 * Each sheet keeps its own `FLAG_DIM_BEHIND` amount. The dim is a per-sheet design decision (how
 * much it separates from the screen), not part of the glass recipe, and it is also what carries
 * the separation in the styles that have no blur.
 */
object FloatingSurfaces {

    /** The one blur radius a floating sheet uses, in dp. */
    const val SHEET_BLUR_DP = 25f

    /**
     * How far the home screen dims behind an edit surface when it is not blurred — the same 50%
     * black as the long-press context menu's spotlight scrim, so every "something is being edited"
     * state separates from the home screen by the same amount.
     */
    const val WORKSPACE_DIM = 0.5f

    /**
     * The window dim for an *edit* sheet — icon, folder, widget — which sits over the live home
     * screen rather than over a blank backdrop.
     *
     * Frosted Glass: no dim. The workspace blur already separates the sheet, and a dim on top of a
     * translucent card over a blur crushes it toward flat black.
     *
     * Other styles: [WORKSPACE_DIM]. These sheets used to clear their dim outright and rely on the
     * workspace blur for separation; once that blur became Frosted-only they sat over a crisp home
     * screen whose icons and dock read as part of the sheet.
     */
    fun applyEditSheetDim(window: Window) {
        if (FrostedGlassEngine.isGlobalFrostedGlassEnabled) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setDimAmount(0f)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window.setDimAmount(WORKSPACE_DIM)
        }
    }

    /**
     * Blur behind [window] in Frosted Glass; explicitly none in the other styles.
     *
     * Clearing matters as much as setting: a dialog restyled while open, or a window reused across
     * a style change, would otherwise keep the flag from before.
     */
    fun applyBlurBehind(window: Window, radiusDp: Float = SHEET_BLUR_DP) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        trackCrossWindowBlur(window.context)
        if (FrostedGlassEngine.isGlobalFrostedGlassEnabled) {
            val density = window.context.resources.displayMetrics.density
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.attributes = window.attributes.apply {
                blurBehindRadius = (radiusDp * density).toInt()
            }
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
        }
    }

    /**
     * Whether a window blur would actually appear right now.
     *
     * `FLAG_BLUR_BEHIND` is a request, not a guarantee. The system switches cross-window blur off
     * whenever it decides it cannot afford it — battery saver, and under memory or GPU pressure,
     * which is exactly the state the launcher is in after a PDF reader has been on top of it. A
     * translucent card asked to sit on a blur that never arrived is a window onto whatever is
     * behind it, which is how the feed's sheets came back see-through from the reader.
     */
    @Volatile
    private var crossWindowBlurEnabled: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    private var blurListenerAttached = false

    fun isBlurBehindActive(): Boolean =
        FrostedGlassEngine.isGlobalFrostedGlassEnabled && crossWindowBlurEnabled

    /** Starts following the system's answer, once per process. */
    private fun trackCrossWindowBlur(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val wm = context.applicationContext.getSystemService(WindowManager::class.java) ?: return
        crossWindowBlurEnabled = wm.isCrossWindowBlurEnabled
        if (blurListenerAttached) return
        blurListenerAttached = true
        try {
            wm.addCrossWindowBlurEnabledListener { enabled -> crossWindowBlurEnabled = enabled }
        } catch (_: Exception) {
            blurListenerAttached = false
        }
    }

    /**
     * Sets [view]'s background to the sheet card and keeps it right: when the system turns
     * cross-window blur off or on while the sheet is open, the card is repainted to match.
     */
    fun bindSheetCard(view: View, tokens: NexusColorTokens, cornerRadiusPx: Float, density: Float) {
        trackCrossWindowBlur(view.context)
        view.background = sheetCard(tokens, cornerRadiusPx, density)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val wm = view.context.applicationContext.getSystemService(WindowManager::class.java) ?: return
        val listener = java.util.function.Consumer<Boolean> { enabled ->
            crossWindowBlurEnabled = enabled
            view.post { view.background = sheetCard(tokens, cornerRadiusPx, density) }
        }
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                try { wm.addCrossWindowBlurEnabledListener(listener) } catch (_: Exception) {}
            }

            override fun onViewDetachedFromWindow(v: View) {
                try { wm.removeCrossWindowBlurEnabledListener(listener) } catch (_: Exception) {}
            }
        })
        if (view.isAttachedToWindow) {
            try { wm.addCrossWindowBlurEnabledListener(listener) } catch (_: Exception) {}
        }
    }

    /**
     * The card a floating sheet draws its content on.
     *
     * Frosted Glass: the frosted surface at sheet density with the canonical glass border — light
     * enough for the blur behind to read through, but only while there is a blur behind it to read
     * ([isBlurBehindActive]); without one the same card is opaque, since translucency over a sharp
     * screen is not frost, just a hole. Other styles: the theme's own opaque `surface` and divider,
     * the same flat treatment [FrostedPanelLayout] gives a panel.
     */
    fun sheetCard(tokens: NexusColorTokens, cornerRadiusPx: Float, density: Float): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = cornerRadiusPx
            val stroke = (1 * density).toInt().coerceAtLeast(1)
            if (isBlurBehindActive()) {
                val glass = FrostedGlassEngine.resolveFrostedTokens(tokens)
                val alpha = (FrostedGlassEngine.sheetFillAlpha(1f, 0.70f) * 255f).toInt()
                setColor((glass.surface and 0x00FFFFFF) or (alpha shl 24))
                setStroke(stroke, glass.border)
            } else if (FrostedGlassEngine.isGlobalFrostedGlassEnabled) {
                // Frosted Glass with no blur to sit on: the glass border stays, the fill turns solid.
                val glass = FrostedGlassEngine.resolveFrostedTokens(tokens)
                setColor(glass.surface or 0xFF000000.toInt())
                setStroke(stroke, glass.border)
            } else {
                setColor(tokens.surface or 0xFF000000.toInt())
                setStroke(stroke, tokens.divider)
            }
        }
}
