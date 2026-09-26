package com.nexus.launcher.ui.folder

import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver

/**
 * Edit sheet chrome: themed surface over live workspace blur.
 * Clip only the root panel (not the sheet FrameLayout) to avoid a false
 * “inner column” from outline/elevation against a translucent fill.
 */
object FolderEditSheetDecor {

    private const val TOP_CORNER_DP = 24f

    fun apply(dialog: BottomSheetDialog) {
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
            ThemeObserver.currentTokens(sheet.context)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }

        val root = sheet.findViewById<LinearLayout>(R.id.folder_edit_sheet_root)
        if (root != null) {
            val radiusPx = TOP_CORNER_DP * density
            root.background = GradientDrawable().apply {
                val frostedTokens = com.nexus.launcher.ui.glass.FrostedGlassEngine.resolveFrostedTokens(tokens)
                val fillAlpha = (com.nexus.launcher.ui.glass.FrostedGlassEngine.sheetFillAlpha(1f, 0.70f) * 255f).toInt()
                setColor((frostedTokens.surface and 0x00FFFFFF) or (fillAlpha shl 24))
                cornerRadius = radiusPx
                setStroke((1 * density).toInt().coerceAtLeast(1), frostedTokens.border)
            }
            applyRoundedClip(root)
        }
        sheet.elevation = 8f * density
        dialog.window?.let { win ->
            com.nexus.launcher.ui.glass.FrostedGlassEngine.applyDialogWindowChrome(win)
            com.nexus.launcher.ui.glass.FloatingSurfaces.applyEditSheetDim(win)
        }
        FolderSheetBehaviorAnchor.anchorEditSheet(dialog)
    }

    fun rePin(dialog: BottomSheetDialog?) {
        FolderSheetBehaviorAnchor.rePin(dialog)
    }

    fun onDismiss(context: android.content.Context) {
        FolderWindowManager.setOpenFolderChromeHidden(false)
        if (!FolderContextMenuLauncher.isShowing()) {
            FolderBlurCoordinator.setWorkspaceBlur(context, false)
        }
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
