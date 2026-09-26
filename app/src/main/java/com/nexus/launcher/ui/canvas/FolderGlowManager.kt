package com.nexus.launcher.ui.canvas

import android.animation.ValueAnimator
import android.graphics.Color

class FolderGlowManager(private val view: LauncherCanvasView) {
    var folderGlowId: Long? = null
    var folderGlowColor: Int = Color.GREEN
    var folderGlowAlpha: Float = 0f

    fun triggerFolderGlow(folderId: Long, success: Boolean) {
        folderGlowId = folderId
        folderGlowColor = if (success) Color.parseColor("#44FF44") else Color.parseColor("#FF4444")

        ValueAnimator.ofFloat(0f, 1f, 0f).apply {
            duration = 600
            addUpdateListener {
                folderGlowAlpha = it.animatedValue as Float
                view.invalidate()
            }
            start()
        }
    }
}
