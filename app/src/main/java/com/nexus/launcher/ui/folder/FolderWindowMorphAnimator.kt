package com.nexus.launcher.ui.folder

import android.graphics.Outline
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewOutlineProvider
import com.nexus.launcher.ui.canvas.LauncherCanvasView

object FolderWindowMorphAnimator {

    fun playOpen(
        canvas: LauncherCanvasView?,
        card: View,
        scrim: View,
        holder: View,
        iconScreenX: Float,
        iconScreenY: Float,
        iconSize: Float,
        folderItemId: Int,
        shapeStyle: Int = 1,
        onGridReveal: () -> Unit = {}
    ) {
        val density = card.resources.displayMetrics.density
        holder.elevation = 22f * density
        card.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)

        FolderBlurCoordinator.hideSourceIcon(canvas, folderItemId)

        val finalRadius = FolderGlassEdgeBuilder.cornerRadiusPx(card.context)
        card.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(0, 0, view.width, view.height, finalRadius)
            }
        }
        card.clipToOutline = true

        card.scaleX = 1f
        card.scaleY = 1f
        card.alpha = 1f
        card.visibility = View.VISIBLE
        scrim.alpha = 1f
        scrim.visibility = View.VISIBLE
        onGridReveal()
    }

    fun playClose(
        canvas: LauncherCanvasView?,
        card: View,
        scrim: View,
        onEnd: () -> Unit
    ) {
        val closeHaptic = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.GESTURE_END
        } else {
            HapticFeedbackConstants.VIRTUAL_KEY
        }
        card.performHapticFeedback(closeHaptic)
        scrim.alpha = 0f
        scrim.visibility = View.GONE
        card.visibility = View.GONE
        FolderBlurCoordinator.restoreSourceIcon(canvas)
        onEnd()
    }
}