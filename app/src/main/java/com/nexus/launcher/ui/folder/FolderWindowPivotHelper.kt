package com.nexus.launcher.ui.folder

import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.OvershootInterpolator

object FolderWindowPivotHelper {

    private const val TAG = "FolderWindow"

    fun applyPivotAndAnimateCard(
        folderCard: View,
        iconScreenX: Float,
        iconScreenY: Float
    ) {
        val listener = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                folderCard.viewTreeObserver.removeOnPreDrawListener(this)
                val cardLoc = IntArray(2)
                folderCard.getLocationOnScreen(cardLoc)
                folderCard.pivotX = iconScreenX - cardLoc[0]
                folderCard.pivotY = iconScreenY - cardLoc[1]
                Log.d(
                    TAG,
                    "pivot final: ${folderCard.pivotX}, ${folderCard.pivotY} " +
                        "cardLoc: ${cardLoc[0]}, ${cardLoc[1]} " +
                        "iconScreen: $iconScreenX, $iconScreenY"
                )
                folderCard.visibility = View.VISIBLE
                folderCard.scaleX = 0f
                folderCard.scaleY = 0f
                folderCard.alpha = 0f
                folderCard.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(220)
                    .setInterpolator(OvershootInterpolator(0.8f))
                    .start()
                return true
            }
        }
        folderCard.viewTreeObserver.addOnPreDrawListener(listener)
        folderCard.requestLayout()
    }
}
