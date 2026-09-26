package com.nexus.launcher.ui.folder

import android.animation.ValueAnimator
import android.app.Activity
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout

/**
 * Adds a full-screen dark scrim over the launcher canvas
 * (home screen icons, dock, widgets) when a folder sheet opens.
 * This ensures the sheet background shows wallpaper only,
 * not home screen content bleeding through.
 */
object FolderSheetScrim {

    private const val SCRIM_TAG = "folder_sheet_scrim"
    private const val SCRIM_COLOR = 0xCC000000.toInt() // 80% black

    fun show(activity: Activity) {
        val decor = activity.window.decorView as? FrameLayout ?: return
        // Remove existing scrim if present
        dismiss(activity)
        val scrim = View(activity).apply {
            tag = SCRIM_TAG
            setBackgroundColor(Color.TRANSPARENT)
            isClickable = true // block touches to launcher below
        }
        decor.addView(
            scrim,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        // Animate scrim in
        ValueAnimator.ofArgb(Color.TRANSPARENT, SCRIM_COLOR).apply {
            duration = 200
            addUpdateListener {
                scrim.setBackgroundColor(it.animatedValue as Int)
            }
            start()
        }
    }

    fun dismiss(activity: Activity) {
        val decor = activity.window.decorView as? FrameLayout ?: return
        val scrim = decor.findViewWithTag<View>(SCRIM_TAG) ?: return
        // Animate scrim out then remove
        ValueAnimator.ofArgb(SCRIM_COLOR, Color.TRANSPARENT).apply {
            duration = 150
            addUpdateListener {
                scrim.setBackgroundColor(it.animatedValue as Int)
            }
            doOnEnd { decor.removeView(scrim) }
            start()
        }
    }
}

private fun ValueAnimator.doOnEnd(action: () -> Unit) {
    addListener(object : android.animation.AnimatorListenerAdapter() {
        override fun onAnimationEnd(animation: android.animation.Animator) {
            action()
        }
    })
}
