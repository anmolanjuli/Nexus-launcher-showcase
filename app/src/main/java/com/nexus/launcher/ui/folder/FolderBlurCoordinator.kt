package com.nexus.launcher.ui.folder

import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import android.view.View
import android.view.ViewGroup
import com.nexus.launcher.ui.canvas.LauncherCanvasView
import com.nexus.launcher.ui.dock.DockLayout
import com.nexus.launcher.ui.widgets.WidgetOverlayLayout

/**
 * Workspace hardware blur while folder / edit / picker / context-menu UI is open.
 * Blurs both the canvas (icons/folders) and [WidgetOverlayLayout] (widgets + mosaics).
 */
object FolderBlurCoordinator {

    private var blurAnimator: ValueAnimator? = null
    @Volatile
    private var mainCanvasRef: java.lang.ref.WeakReference<LauncherCanvasView>? = null

    fun setMainCanvas(canvas: LauncherCanvasView) {
        mainCanvasRef = java.lang.ref.WeakReference(canvas)
    }

    private fun unwrapActivity(context: Context): Activity? {
        var cur: Context? = context
        while (cur is android.content.ContextWrapper) {
            if (cur is Activity) return cur
            cur = cur.baseContext
        }
        return cur as? Activity
    }

    fun findCanvas(context: android.content.Context): LauncherCanvasView? {
        val activity = unwrapActivity(context)
        if (activity != null) {
            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            if (content != null) {
                val canvas = FolderCanvasInvalidator.findCanvasInTree(content)
                if (canvas != null) return canvas
            }
        }
        val main = mainCanvasRef?.get()
        if (main != null && main.isAttachedToWindow) return main
        return null
    }

    fun findWidgetOverlay(context: Context): WidgetOverlayLayout? {
        val activity = unwrapActivity(context) ?: return null
        if (activity is com.nexus.launcher.ui.MainActivity) {
            try {
                return activity.widgetHostLifecycle.widgetOverlayLayout
            } catch (_: Throwable) { }
        }
        val root = activity.findViewById<ViewGroup>(com.nexus.launcher.R.id.main_container)
            ?: activity.findViewById<ViewGroup>(android.R.id.content)
            ?: return null
        return findWidgetOverlayInTree(root)
    }

    private fun findWidgetOverlayInTree(root: ViewGroup): WidgetOverlayLayout? {
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child is WidgetOverlayLayout) return child
            if (child is ViewGroup) {
                findWidgetOverlayInTree(child)?.let { return it }
            }
        }
        return null
    }

    fun findDock(context: Context): DockLayout? {
        val activity = unwrapActivity(context) ?: return null
        val root = activity.findViewById<ViewGroup>(com.nexus.launcher.R.id.main_container)
            ?: activity.findViewById<ViewGroup>(android.R.id.content)
            ?: return null
        return findDockInTree(root)
    }

    fun findCategoriesHost(
        context: Context,
    ): com.nexus.launcher.ui.drawercategories.CategoriesDrawerHost? {
        val activity = unwrapActivity(context) ?: return null
        val root = activity.findViewById<ViewGroup>(com.nexus.launcher.R.id.main_container)
            ?: activity.findViewById<ViewGroup>(android.R.id.content)
            ?: return null
        return findCategoriesHostInTree(root)
    }

    private fun findCategoriesHostInTree(
        root: ViewGroup,
    ): com.nexus.launcher.ui.drawercategories.CategoriesDrawerHost? {
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child is com.nexus.launcher.ui.drawercategories.CategoriesDrawerHost) return child
            if (child is ViewGroup) {
                findCategoriesHostInTree(child)?.let { return it }
            }
        }
        return null
    }

    private fun findDockInTree(root: ViewGroup): DockLayout? {
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            if (child is DockLayout) return child
            if (child is ViewGroup) {
                findDockInTree(child)?.let { return it }
            }
        }
        return null
    }

    private fun findCanvasView(root: ViewGroup): View? {
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            // Type checks, not simpleName strings: R8 renames these classes in release
            // (LauncherCanvasView -> dn0), so a name comparison silently never matches and this
            // returns null — folder blur then quietly does nothing on exactly the builds users
            // install. findDockInTree above already does it this way.
            if (child is com.nexus.launcher.ui.canvas.LauncherCanvasView ||
                child is com.nexus.launcher.ui.canvas.LauncherDrawEngine
            ) {
                return child
            }
            if (child is ViewGroup) {
                val found = findCanvasView(child)
                if (found != null) return found
            }
        }
        return null
    }

    /**
     * Fallback RenderEffect path when [LauncherCanvasView.setBlurState] is unavailable.
     * Prefer [setWorkspaceBlur] which uses the strong canvas (70px) + widget overlay path.
     */
    fun setCanvasBlur(context: Context, blurred: Boolean) {
        val activity = context as? Activity ?: return
        val decor = activity.window.decorView as? ViewGroup ?: return
        val canvasView = findCanvasView(decor) ?: activity.findViewById<ViewGroup>(android.R.id.content) ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            findCanvas(context)?.cancelBlurAnimator()
            blurAnimator?.cancel()
            val startRadius = if (blurred) 0f else com.nexus.launcher.ui.glass.ChromeBackdrop.RADIUS_PX
            val endRadius = if (blurred) com.nexus.launcher.ui.glass.ChromeBackdrop.RADIUS_PX else 0f
            val durationMs = if (blurred) 250L else 150L

            blurAnimator = ValueAnimator.ofFloat(startRadius, endRadius).apply {
                duration = durationMs
                addUpdateListener { va ->
                    com.nexus.launcher.ui.glass.ChromeBackdrop.applyTo(canvasView, va.animatedValue as Float)
                }
                start()
            }
        } else {
            canvasView.visibility = if (blurred) View.INVISIBLE else View.VISIBLE
        }
    }

    /**
     * Blurs the whole home screen — canvas, widget overlay and dock — behind a menu, sheet, picker
     * or search. Some forty call sites come through here, so this is the one place the UI Style
     * is honoured for it.
     *
     * Only Frosted Glass blurs. Default and Neumorphism asked for it too and got it: a crisp menu
     * or sheet over a smeared home screen, which is frost in the two styles defined by having
     * none. Their separation comes from each surface's own dim instead — the context menu's
     * spotlight scrim, a sheet's window dim, search's opaque background.
     *
     * Only the *on* request is filtered. Turning blur off always runs in full, so a blur raised
     * under Frosted Glass is still cleared after the user switches style while it is showing.
     */
    fun setWorkspaceBlur(
        context: android.content.Context,
        blurred: Boolean,
        windowBlurRadiusPx: Int = com.nexus.launcher.ui.glass.ChromeBackdrop.WINDOW_RADIUS_PX,
    ) {
        @Suppress("NAME_SHADOWING")
        val blurred = blurred && com.nexus.launcher.ui.glass.ChromeBackdrop.isAllowed()
        val activity = context as? Activity
        activity?.window?.let { com.nexus.launcher.ui.glass.ChromeBackdrop.applyWindow(it, blurred, windowBlurRadiusPx) }
        val canvas = findCanvas(context)
        if (canvas != null) {
            // Strong animated blur (70px) on the canvas content (icons/text)
            canvas.setBlurState(blurred)
        } else {
            setCanvasBlur(context, blurred)
        }
        // Widgets / mosaics live on a sibling overlay — must blur separately or they bleed sharp
        findWidgetOverlay(context)?.setBlurState(blurred)
        findDock(context)?.setBlurState(blurred)
        findCategoriesHost(context)?.setBlurState(blurred)
        blurDrawerChrome(context, blurred)
    }

    /** The launcher's chrome beside the workspace — see [ChromeBackdrop.applyToLauncherChrome]. */
    private fun blurDrawerChrome(context: android.content.Context, blurred: Boolean) {
        val activity = unwrapActivity(context) ?: return
        com.nexus.launcher.ui.glass.ChromeBackdrop.applyToLauncherChrome(
            activity,
            if (blurred) com.nexus.launcher.ui.glass.ChromeBackdrop.RADIUS_PX else 0f,
        )
    }

    fun hideSourceIcon(canvas: LauncherCanvasView?, folderItemId: Int) {
        canvas?.setFolderOpenState(folderItemId, morphProgress = 1f)
        canvas?.let { DockLayout.findFrom(it)?.setHiddenOpenFolderId(folderItemId) }
    }

    fun restoreSourceIcon(canvas: LauncherCanvasView?) {
        canvas?.setFolderOpenState(null, morphProgress = 0f)
        canvas?.let { DockLayout.findFrom(it)?.setHiddenOpenFolderId(null) }
    }
}
