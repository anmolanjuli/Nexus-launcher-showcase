package com.nexus.launcher.ui.widgets

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.ui.glass.FrostedGlassEngine

/**
 * Standardized creator and decor helper for bottom-anchored edit sheets.
 * Ensures consistent flush bottom anchoring, transparent system bars,
 * rounded frosted card chrome, and eliminates navigation bar letterboxing.
 */
object NexusEditBottomSheetHelper {

    const val TOP_CORNER_DP = 24f

    fun createDragHandle(context: Context, tokens: NexusColorTokens, dp: Float): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                (40 * dp).toInt(), (4 * dp).toInt()
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = (11 * dp).toInt()
                bottomMargin = (10 * dp).toInt()
            }
            background = GradientDrawable().apply {
                setColor(tokens.divider)
                cornerRadius = 2f * dp
            }
        }
    }

    fun create(
        activity: Activity,
        card: View,
        onDismissRequested: () -> Unit
    ): BottomSheetDialog {
        val dp = activity.resources.displayMetrics.density
        applyCardOutline(card, dp)

        val dialog = object : BottomSheetDialog(activity, com.google.android.material.R.style.Theme_Design_BottomSheetDialog) {
            override fun onBackPressed() {
                onDismissRequested()
            }
        }
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)
        com.nexus.launcher.ui.LandscapeSheets.apply(dialog)
        dialog.setOnCancelListener {
            onDismissRequested()
        }

        val marginH = (10 * dp).toInt()
        val baseMarginBottom = (8 * dp).toInt()
        val root = FrameLayout(activity).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val cardLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(marginH, 0, marginH, baseMarginBottom)
        }
        card.layoutParams = cardLp
        root.addView(card)

        dialog.setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            (card.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                val newBottom = com.nexus.launcher.ui.LandscapeSheets.cardBottomMargin(activity, navBars.bottom, baseMarginBottom)
                if (lp.bottomMargin != newBottom || lp.leftMargin != marginH || lp.rightMargin != marginH) {
                    lp.setMargins(marginH, 0, marginH, newBottom)
                    card.layoutParams = lp
                }
            }
            insets
        }

        dialog.setOnShowListener {
            dialog.findViewById<View>(com.google.android.material.R.id.touch_outside)?.setBackgroundColor(Color.TRANSPARENT)
            dialog.findViewById<View>(com.google.android.material.R.id.coordinator)?.setBackgroundColor(Color.TRANSPARENT)

            val bottomSheet = dialog.findViewById<FrameLayout>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            if (bottomSheet != null) {
                bottomSheet.backgroundTintList = null
                bottomSheet.setBackgroundResource(android.R.color.transparent)
                bottomSheet.setBackgroundColor(Color.TRANSPARENT)
                (bottomSheet.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                    if (lp.bottomMargin != 0) {
                        lp.bottomMargin = 0
                        bottomSheet.layoutParams = lp
                    }
                }
                val behavior = BottomSheetBehavior.from(bottomSheet)
                behavior.isFitToContents = true
                behavior.skipCollapsed = true
                behavior.isDraggable = false
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                bottomSheet.translationY = 0f
                bottomSheet.requestLayout()
            }

            dialog.window?.let { win ->
                WindowCompat.setDecorFitsSystemWindows(win, false)
                win.statusBarColor = Color.TRANSPARENT
                win.navigationBarColor = Color.TRANSPARENT
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    win.isNavigationBarContrastEnforced = false
                    win.isStatusBarContrastEnforced = false
                }
                win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                FrostedGlassEngine.applyDialogWindowChrome(win)
                com.nexus.launcher.ui.glass.FloatingSurfaces.applyEditSheetDim(win)
            }
        }

        return dialog
    }

    fun applyCardOutline(card: View, dp: Float) {
        val radiusPx = TOP_CORNER_DP * dp
        card.clipToOutline = true
        card.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                if (view.width <= 0 || view.height <= 0) return
                outline.setRoundRect(
                    0, 0, view.width, view.height, radiusPx
                )
            }
        }
    }

    fun buildCardBackground(tokens: NexusColorTokens, dp: Float): GradientDrawable {
        return GradientDrawable().apply {
            val frostedTokens = FrostedGlassEngine.resolveFrostedTokens(tokens)
            val fillAlpha = (FrostedGlassEngine.sheetFillAlpha(1f, 0.70f) * 255f).toInt()
            val radiusPx = TOP_CORNER_DP * dp
            cornerRadius = radiusPx
            setColor((frostedTokens.surface and 0x00FFFFFF) or (fillAlpha shl 24))
            setStroke((1 * dp).toInt().coerceAtLeast(1), frostedTokens.border)
        }
    }
}
