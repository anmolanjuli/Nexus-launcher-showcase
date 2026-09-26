package com.nexus.launcher.ui.folder

import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import androidx.core.view.WindowCompat
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver

object FolderAppPickerDecor {

    private const val CORNER_DP = 24f

    fun apply(dialog: BottomSheetDialog) {
        com.nexus.launcher.ui.LandscapeSheets.apply(dialog)
        val density = dialog.context.resources.displayMetrics.density
        dialog.window?.let { win ->
            WindowCompat.setDecorFitsSystemWindows(win, false)
            win.statusBarColor = Color.TRANSPARENT
            win.navigationBarColor = Color.TRANSPARENT
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                win.isNavigationBarContrastEnforced = false
                win.isStatusBarContrastEnforced = false
            }
            win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            win.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            win.setDimAmount(0.72f)
            // Blurs only in Frosted Glass; the dim above carries the separation otherwise.
            com.nexus.launcher.ui.glass.FloatingSurfaces.applyBlurBehind(win)
        }
        dialog.findViewById<FrameLayout>(
            com.google.android.material.R.id.design_bottom_sheet
        )?.setBackgroundResource(android.R.color.transparent)
        dialog.findViewById<View>(R.id.folder_picker_root)?.setBackgroundColor(Color.TRANSPARENT)
        val panel = dialog.findViewById<View>(R.id.folder_picker_glass_panel) ?: return
        val tokens = try {
            ThemeObserver.currentTokens(panel.context)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }
        panel.background = com.nexus.launcher.ui.glass.FloatingSurfaces.sheetCard(tokens, CORNER_DP * density, density)
        applyRoundedClip(panel)
    }

    fun onDismiss(context: android.content.Context) {
    }

    private fun applyRoundedClip(view: View) {
        val cornerPx = CORNER_DP * view.resources.displayMetrics.density
        view.clipToOutline = true
        view.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, outline: Outline) {
                outline.setRoundRect(0, 0, v.width, v.height, cornerPx)
            }
        }
    }
}
