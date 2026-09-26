package com.nexus.launcher.ui.dock.settings

import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.ui.folder.FolderBlurCoordinator
import com.nexus.launcher.ui.glass.FrostedGlassEngine

/** Decorator handling window flags, blur behind, workspace blur, and bottom anchoring for Dock Settings. */
internal object DockSettingsDecor {

    const val CARD_CORNER_DP = 24f

    private fun applyFlushBottom(bottomSheet: FrameLayout) {
        (bottomSheet.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            if (lp.bottomMargin != 0) {
                lp.bottomMargin = 0
                bottomSheet.layoutParams = lp
            }
        }
    }

    fun applyWindowDecor(dialog: BottomSheetDialog) {
        val win = dialog.window ?: return
        WindowCompat.setDecorFitsSystemWindows(win, false)
        win.statusBarColor = Color.TRANSPARENT
        win.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            win.isNavigationBarContrastEnforced = false
            win.isStatusBarContrastEnforced = false
        }
        win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        FrostedGlassEngine.applyDialogWindowChrome(win)
        win.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        win.setDimAmount(0f)
    }

    fun applyBottomAnchor(dialog: BottomSheetDialog) {
        dialog.findViewById<View>(com.google.android.material.R.id.touch_outside)?.setBackgroundColor(Color.TRANSPARENT)
        dialog.findViewById<View>(com.google.android.material.R.id.coordinator)?.setBackgroundColor(Color.TRANSPARENT)
        val bottomSheet = dialog.findViewById<FrameLayout>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return
        bottomSheet.backgroundTintList = null
        bottomSheet.setBackgroundResource(android.R.color.transparent)
        bottomSheet.setBackgroundColor(Color.TRANSPARENT)
        applyFlushBottom(bottomSheet)
        val behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.isFitToContents = true
        behavior.skipCollapsed = true
        behavior.isDraggable = false
        behavior.state = BottomSheetBehavior.STATE_EXPANDED
        bottomSheet.translationY = 0f
        FolderBlurCoordinator.setWorkspaceBlur(dialog.context, true)
    }

    fun rePin(dialog: BottomSheetDialog?) {
        val bottomSheet = dialog?.findViewById<FrameLayout>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return
        bottomSheet.post {
            val behavior = BottomSheetBehavior.from(bottomSheet)
            behavior.isFitToContents = true
            behavior.skipCollapsed = true
            behavior.isDraggable = false
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            bottomSheet.translationY = 0f
            applyFlushBottom(bottomSheet)
        }
    }

    fun cardBackground(tokens: NexusColorTokens, dp: Float): GradientDrawable {
        return GradientDrawable().apply {
            val frostedTokens = FrostedGlassEngine.resolveFrostedTokens(tokens)
            val fillAlpha = (FrostedGlassEngine.sheetFillAlpha(1f, 0.70f) * 255f).toInt()
            setColor((frostedTokens.surface and 0x00FFFFFF) or (fillAlpha shl 24))
            cornerRadius = CARD_CORNER_DP * dp
            setStroke((1 * dp).toInt().coerceAtLeast(1), frostedTokens.border)
        }
    }

    fun applyCardOutline(view: View, dp: Float) {
        val cornerPx = CARD_CORNER_DP * dp
        view.clipToOutline = true
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) {
                if (v.width <= 0 || v.height <= 0) return
                outline.setRoundRect(0, 0, v.width, v.height, cornerPx)
            }
        }
    }

    fun wrapInRoot(card: View, dp: Float): FrameLayout {
        val marginH = (10 * dp).toInt()
        val baseMarginBottom = (8 * dp).toInt()
        val root = FrameLayout(card.context).apply {
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
        card.setPadding(0, 0, 0, (8 * dp).toInt())
        root.addView(card)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val bottomMargin = com.nexus.launcher.ui.LandscapeSheets.cardBottomMargin(root.context, navBars.bottom, baseMarginBottom)
            (card.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                if (lp.bottomMargin != bottomMargin) {
                    lp.setMargins(marginH, 0, marginH, bottomMargin)
                    card.layoutParams = lp
                }
            }
            insets
        }
        return root
    }
}
