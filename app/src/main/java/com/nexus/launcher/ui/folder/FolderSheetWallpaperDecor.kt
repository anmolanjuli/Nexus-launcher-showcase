package com.nexus.launcher.ui.folder

import android.app.Activity
import android.graphics.Color
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.WindowCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

object FolderSheetWallpaperDecor {

    private const val TAG = "FolderSheetDecor"

    /**
     * Apply native window blur (API 31+) behind the sheet dialog.
     * Also anchors the sheet to the bottom with correct behavior state.
     * Falls back to FolderSheetScrim on older APIs.
     */
    fun applyWindowBlur(dialog: BottomSheetDialog) {
        val window = dialog.window ?: return
        val wm = dialog.context
            .getSystemService(android.content.Context.WINDOW_SERVICE)
                as android.view.WindowManager

        // Set window transparent regardless of blur support
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setBackgroundDrawable(
            android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        dialog.findViewById<View>(
            com.google.android.material.R.id.touch_outside)
            ?.setBackgroundColor(Color.TRANSPARENT)
        dialog.findViewById<View>(
            com.google.android.material.R.id.coordinator)
            ?.setBackgroundColor(Color.TRANSPARENT)

        // Its heavier blur (80 behind, 40 background) is a deliberate look for this sheet and stays;
        // what it lacked was the UI Style check, so it blurred in Neumorphism and Default too.
        if (android.os.Build.VERSION.SDK_INT >= 31
            && wm.isCrossWindowBlurEnabled
            && com.nexus.launcher.ui.glass.FrostedGlassEngine.isGlobalFrostedGlassEnabled) {
            // Approach C: blur behind + background blur
            window.addFlags(
                android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            val attr = window.attributes
            attr.blurBehindRadius = 80
            window.attributes = attr
            window.setBackgroundBlurRadius(40)
            window.setDimAmount(0.2f)
            Log.d(TAG, "applyWindowBlur: native blur applied")
        } else {
            // Fallback: dark scrim
            window.setDimAmount(0.7f)
            Log.d(TAG, "applyWindowBlur: fallback dim applied")
        }

        // Make sheet background transparent so blur shows through
        val sheet = dialog.findViewById<FrameLayout>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return
        sheet.backgroundTintList = null
        sheet.setBackgroundResource(android.R.color.transparent)

        // Anchor sheet to bottom — must run after layout
        sheet.viewTreeObserver.addOnGlobalLayoutListener(
            object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    sheet.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    val behavior = BottomSheetBehavior.from(sheet)
                    behavior.skipCollapsed = true
                    behavior.isDraggable = false
                    behavior.isFitToContents = true
                    if (behavior.state != BottomSheetBehavior.STATE_EXPANDED) {
                        behavior.state = BottomSheetBehavior.STATE_EXPANDED
                        Log.d(TAG, "applyWindowBlur: sheet anchored state=EXPANDED")
                    }
                }
            })
    }

    /**
     * Remove window blur when sheet dismisses.
     */
    fun removeWindowBlur(
        dialog: BottomSheetDialog?,
        context: android.content.Context
    ) {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            dialog?.window?.apply {
                clearFlags(
                    android.view.WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                setBackgroundBlurRadius(0)
                setDimAmount(0f)
            }
        }
        Log.d(TAG, "removeWindowBlur: blur cleared")
    }

    /**
     * Legacy applyEdgeToEdge — kept for any callers that still use it.
     * Delegates to applyWindowBlur.
     */
    fun applyEdgeToEdge(
        dialog: BottomSheetDialog,
        fullScreen: Boolean = true
    ) {
        applyWindowBlur(dialog)
    }
}
