package com.nexus.launcher.ui.glass

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager

/**
 * The **full-screen chrome** role: the launcher itself, blurred behind something covering it.
 *
 * The third of the three surface roles, beside [FloatingSurfaces] (a sheet or menu in its own
 * window) and [FrostedPanelLayout] (a bounded panel that frosts itself from the wallpaper). This
 * one is the other side of those: not the surface, but the home screen going soft underneath it —
 * behind the drawer, search, a folder, a picker, the context menu, Home Edit, the Feed panel.
 *
 * ## Why it had to be collected
 *
 * Five coordinators blurred the launcher, each with its own numbers, and they were visible against
 * each other. The canvas blurred with [Shader.TileMode.CLAMP] while the widgets and dock sitting on
 * top of it used [Shader.TileMode.MIRROR], so the same wallpaper went soft two different ways in
 * one frame — clearest at the screen edges, where CLAMP smears the last row of pixels outward and
 * MIRROR folds the image back on itself. The window blur behind a surface was 40 in one path and 50
 * in another. Each also carried its own copy of the rule about which UI Style may blur at all.
 *
 * The numbers live here now. How a given coordinator *applies* them still differs, and should:
 * Home Edit puts one effect on the whole workspace container, which is cheapest and matches
 * exactly; the folder path blurs canvas, widget overlay and dock separately because a widget being
 * long-pressed has to stay sharp while everything around it blurs, and one container-wide effect
 * cannot make that exception.
 */
object ChromeBackdrop {

    /** What the launcher blurs to behind a full-screen surface. */
    const val RADIUS_PX = 70f

    /** The window-level blur behind that surface, for the system to composite. */
    const val WINDOW_RADIUS_PX = 40

    /**
     * The drawer's own blur while its search field is open — deliberately slight. The grid stays
     * legible behind the results because it is what the search is filtering.
     */
    const val LIGHT_RADIUS_PX = 10f

    /**
     * An open folder blurred behind a *floating* surface — a dialog or a context menu that covers
     * part of it. Lighter than [RADIUS_PX] because the folder is still the context for what the
     * menu is about, and the card is small enough that the full radius erases it.
     *
     * A surface covering the whole screen does not blur the folder at all: the app picker hides it
     * while it is up (PickerOverlayStyle), so a picker opened from the folder's "+" sits over the
     * same backdrop as one opened from the folder icon's menu.
     */
    const val OVER_FOLDER_RADIUS_PX = 24f

    /**
     * One tile mode everywhere. Mirroring folds the image back at the edges; clamping drags the
     * last row of pixels outward into a streak, which is what made two blurred layers in the same
     * frame look like two different blurs.
     */
    val TILE_MODE: Shader.TileMode = Shader.TileMode.MIRROR

    /**
     * Whether the launcher may blur behind a surface at all.
     *
     * Only Frosted Glass. The other two styles are defined by not having frost, and a crisp menu
     * over a smeared home screen is frost by another name; their separation comes from each
     * surface's own dim. Callers gate the *on* request with this and let "off" always run, so a
     * blur raised before a style change is still cleared afterwards.
     */
    fun isAllowed(): Boolean = FrostedGlassEngine.isGlobalFrostedGlassEnabled

    /** The effect for [radius], or null when there is nothing to blur with. */
    fun effect(radius: Float): RenderEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        if (radius <= 0.5f) return null
        return BlurEffectCache.get(radius, TILE_MODE)
    }

    /** Blurs [view] at [radius], or clears it at zero. */
    fun applyTo(view: View, radius: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        view.setRenderEffect(effect(radius))
    }

    /**
     * Blurs the launcher's own chrome — everything of the launcher that sits *beside* the
     * workspace rather than inside it: the drawer's search pill and category bar, the search FAB,
     * the overflow trigger, the search overlay.
     *
     * `workspace_container` holds the canvas, the widget overlay and the dock, and the coordinators
     * blur those directly. These are its siblings, so a blur applied to the workspace went around
     * them: a picker opened over the drawer had a blurred backdrop with a perfectly sharp search
     * pill sitting in it. The list is explicit because these views are always background — never
     * the surface being shown on top.
     */
    fun applyToLauncherChrome(activity: android.app.Activity, radius: Float) {
        val ids = intArrayOf(
            com.nexus.launcher.R.id.search_fab_container,
            com.nexus.launcher.R.id.drawer_overflow_btn,
            com.nexus.launcher.R.id.search_overlay,
        )
        for (id in ids) {
            activity.findViewById<View>(id)?.let { applyTo(it, radius) }
        }
        (activity as? com.nexus.launcher.ui.MainActivity)?.let { main ->
            try {
                main.drawerChromeViews().forEach { applyTo(it, radius) }
            } catch (_: Throwable) {
                // The drawer's bars are built lazily; nothing to blur before they exist.
            }
        }
    }

    /** The window blur behind a full-screen surface: flags and radius, set or cleared together. */
    fun applyWindow(window: Window, blurred: Boolean, radiusPx: Int = WINDOW_RADIUS_PX) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        if (blurred) {
            window.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.setBackgroundBlurRadius(radiusPx)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            window.setBackgroundBlurRadius(0)
        }
    }
}
