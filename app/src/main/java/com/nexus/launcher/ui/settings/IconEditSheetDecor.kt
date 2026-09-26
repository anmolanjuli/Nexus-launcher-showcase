package com.nexus.launcher.ui.settings

import android.content.Context
import android.graphics.Outline
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.nexus.launcher.ui.folder.FolderBlurCoordinator
import com.nexus.launcher.ui.folder.FolderContextMenuLauncher
import com.nexus.launcher.ui.folder.FolderPremiumGlassBuilder
import com.nexus.launcher.ui.folder.FolderSheetWallpaperDecor
import com.nexus.launcher.ui.folder.FolderWindowManager

/**
 * Icon Edit chrome — charcoal glass over workspace blur.
 * Uses fit-to-contents bottom anchoring (Icon Edit is short; folder's
 * fitToContents=false + offset 0 hangs short sheets under the status bar).
 */
object IconEditSheetDecor {

    private const val TOP_CORNER_DP = 24f

    private fun applyFlushBottom(sheet: FrameLayout) {
        (sheet.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            if (lp.bottomMargin != 0) {
                lp.bottomMargin = 0
                sheet.layoutParams = lp
            }
        }
    }

    fun apply(dialog: BottomSheetDialog, panelRoot: View) {
        FolderWindowManager.setOpenFolderChromeHidden(true)
        val sheet = dialog.findViewById<FrameLayout>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return
        sheet.layoutParams = sheet.layoutParams?.apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = ViewGroup.LayoutParams.WRAP_CONTENT
        }
        sheet.setBackgroundResource(android.R.color.transparent)
        val density = sheet.resources.displayMetrics.density
        val tokens = try {
            com.nexus.launcher.theme.ThemeObserver.currentTokens(sheet.context)
        } catch (_: Exception) {
            com.nexus.launcher.theme.NexusColorTokens.Dark
        }
        val radiusPx = TOP_CORNER_DP * density
        panelRoot.background = android.graphics.drawable.GradientDrawable().apply {
            val frostedTokens = com.nexus.launcher.ui.glass.FrostedGlassEngine.resolveFrostedTokens(tokens)
            val fillAlpha = (com.nexus.launcher.ui.glass.FrostedGlassEngine.sheetFillAlpha(1f, 0.70f) * 255f).toInt()
            setColor((frostedTokens.surface and 0x00FFFFFF) or (fillAlpha shl 24))
            cornerRadius = radiusPx
            setStroke((1 * density).toInt().coerceAtLeast(1), frostedTokens.border)
        }
        applyRoundedClip(panelRoot)
        sheet.elevation = 8f * density
        dialog.window?.let { win ->
            com.nexus.launcher.ui.glass.FrostedGlassEngine.applyDialogWindowChrome(win)
            com.nexus.launcher.ui.glass.FloatingSurfaces.applyEditSheetDim(win)
        }
        pinToBottom(dialog)
    }

    fun onStart(context: Context, dialog: BottomSheetDialog) {
        FolderBlurCoordinator.setWorkspaceBlur(context, true)
        pinToBottom(dialog)
    }

    fun rePin(dialog: BottomSheetDialog?) {
        if (dialog != null) pinToBottom(dialog)
    }

    fun onDismiss(context: Context, dialog: BottomSheetDialog?) {
        FolderWindowManager.setOpenFolderChromeHidden(false)
        FolderSheetWallpaperDecor.removeWindowBlur(dialog, context)
        if (!FolderContextMenuLauncher.isShowing()) {
            FolderBlurCoordinator.setWorkspaceBlur(context, false)
        }
    }

    private fun pinToBottom(dialog: BottomSheetDialog) {
        val sheet = dialog.findViewById<FrameLayout>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return
        val applyPin = Runnable {
            val behavior = BottomSheetBehavior.from(sheet)
            // Content-height sheet, bottom-aligned (correct for short Icon Edit).
            // Do not set expandedOffset — with fitToContents it is derived from
            // parentHeight - childHeight; forcing 0 top-anchors short sheets.
            behavior.isFitToContents = true
            behavior.skipCollapsed = true
            behavior.isDraggable = false
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            sheet.translationY = 0f
            applyFlushBottom(sheet)
            sheet.requestLayout()
        }
        applyPin.run()
        sheet.post(applyPin)
        sheet.viewTreeObserver.addOnGlobalLayoutListener(
            object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    sheet.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    applyPin.run()
                }
            }
        )
    }

    private fun applyRoundedClip(view: View) {
        val cornerPx = TOP_CORNER_DP * view.resources.displayMetrics.density
        view.clipToOutline = true
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) {
                if (v.width <= 0 || v.height <= 0) return
                outline.setRoundRect(0, 0, v.width, v.height, cornerPx)
            }
        }
    }
}
