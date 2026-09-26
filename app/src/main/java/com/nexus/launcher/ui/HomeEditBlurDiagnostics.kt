package com.nexus.launcher.ui

import android.app.Activity
import android.os.Build
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.nexus.launcher.ui.canvas.LauncherCanvasView
import com.nexus.launcher.ui.dock.DockLayout
import com.nexus.launcher.ui.widgets.WidgetOverlayLayout
import com.nexus.launcher.util.NexusDiag

/**
 * Diagnostic logger for workspace blur state across [LauncherCanvasView],
 * [WidgetOverlayLayout], and [DockLayout].
 */
internal object HomeEditBlurDiagnostics {

    private const val TAG = "HomeBlurDiag"

    fun logWorkspaceBlurState(activity: Activity, tag: String) {
        if (!NexusDiag.ENABLED) return
        val root = activity.findViewById<FrameLayout>(com.nexus.launcher.R.id.main_container)
        if (root == null) {
            Log.e(TAG, "[$tag] main_container is NULL!")
            return
        }

        Log.i(TAG, "========== [$tag] WORKSPACE BLUR DIAGNOSTICS START ==========")
        Log.i(TAG, "main_container: childCount=${root.childCount}, w=${root.width}, h=${root.height}")

        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            val layerTypeStr = layerTypeToString(child.layerType)
            val renderEffectStr = safeGetRenderEffect(child)
            val hwAccel = child.isHardwareAccelerated
            val vis = visibilityToString(child.visibility)

            Log.i(TAG, "Child #$i [${child.javaClass.simpleName}]: vis=$vis, bounds=(${child.left},${child.top}-${child.right},${child.bottom}), layerType=$layerTypeStr, hwAccel=$hwAccel, renderEffect=$renderEffectStr")

            if (child.id == com.nexus.launcher.R.id.workspace_container && child is FrameLayout) {
                Log.i(TAG, "  -> workspace_container: childCount=${child.childCount}")
                for (w in 0 until child.childCount) {
                    val wsChild = child.getChildAt(w)
                    val wLayer = layerTypeToString(wsChild.layerType)
                    val wEffect = safeGetRenderEffect(wsChild)
                    Log.i(TAG, "     WorkspaceChild #$w [${wsChild.javaClass.simpleName}]: vis=${visibilityToString(wsChild.visibility)}, bounds=(${wsChild.left},${wsChild.top}-${wsChild.right},${wsChild.bottom}), layer=$wLayer, effect=$wEffect")
                }
            }

            when (child) {
                is LauncherCanvasView -> {
                    Log.i(TAG, "  -> LauncherCanvasView: isManagePagesOpen=${child.isManagePagesOpen}, theme=${child.currentThemeTokens.javaClass.simpleName}")
                }
                is WidgetOverlayLayout -> {
                    Log.i(TAG, "  -> WidgetOverlayLayout: childCount=${child.childCount}")
                    for (c in 0 until child.childCount) {
                        val widgetChild = child.getChildAt(c)
                        val wLayer = layerTypeToString(widgetChild.layerType)
                        val wEffect = safeGetRenderEffect(widgetChild)
                        Log.i(TAG, "     WidgetChild #$c [${widgetChild.javaClass.simpleName}]: vis=${visibilityToString(widgetChild.visibility)}, layer=$wLayer, effect=$wEffect")
                    }
                }
                is DockLayout -> {
                    val outline = child.outlineProvider?.javaClass?.simpleName ?: "null"
                    val clip = child.clipToOutline
                    Log.i(TAG, "  -> DockLayout: outlineProvider=$outline, clipToOutline=$clip, childCount=${child.childCount}")
                    for (d in 0 until child.childCount) {
                        val dockChild = child.getChildAt(d)
                        val dLayer = layerTypeToString(dockChild.layerType)
                        val dEffect = safeGetRenderEffect(dockChild)
                        Log.i(TAG, "     DockChild #$d [${dockChild.javaClass.simpleName}]: vis=${visibilityToString(dockChild.visibility)}, bounds=(${dockChild.left},${dockChild.top}-${dockChild.right},${dockChild.bottom}), layer=$dLayer, effect=$dEffect")
                    }
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val flags = activity.window.attributes.flags
            val hasBlurBehind = (flags and android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND) != 0
            Log.i(TAG, "Window: FLAG_BLUR_BEHIND=$hasBlurBehind")
        }
        Log.i(TAG, "========== [$tag] WORKSPACE BLUR DIAGNOSTICS END ==========")
    }

    private fun safeGetRenderEffect(view: View): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return "unsupported (<S)"
        return try {
            val field = View::class.java.getDeclaredField("mRenderEffect").apply { isAccessible = true }
            field.get(view)?.toString() ?: "null"
        } catch (e: Throwable) {
            "applied (internal field hidden)"
        }
    }

    private fun layerTypeToString(type: Int): String = when (type) {
        View.LAYER_TYPE_NONE -> "LAYER_TYPE_NONE (0)"
        View.LAYER_TYPE_SOFTWARE -> "LAYER_TYPE_SOFTWARE (1)"
        View.LAYER_TYPE_HARDWARE -> "LAYER_TYPE_HARDWARE (2)"
        else -> "UNKNOWN ($type)"
    }

    private fun visibilityToString(vis: Int): String = when (vis) {
        View.VISIBLE -> "VISIBLE"
        View.INVISIBLE -> "INVISIBLE"
        View.GONE -> "GONE"
        else -> "UNKNOWN ($vis)"
    }
}
