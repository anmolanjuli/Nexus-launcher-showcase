package com.nexus.launcher.ui.folder

import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog

/** Keeps bottom-anchored sheets pinned when inner content height changes. */
object FolderSheetBehaviorAnchor {

    private fun applyFlushBottom(sheet: FrameLayout) {
        (sheet.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            if (lp.bottomMargin != 0) {
                lp.bottomMargin = 0
                sheet.layoutParams = lp
            }
        }
    }

    fun anchorEditSheet(dialog: BottomSheetDialog) {
        val sheet = dialog.findViewById<FrameLayout>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return
        val applyPin = Runnable {
            val behavior = BottomSheetBehavior.from(sheet)
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
    }

    fun rePin(dialog: BottomSheetDialog?) {
        val sheet = dialog?.findViewById<FrameLayout>(
            com.google.android.material.R.id.design_bottom_sheet
        ) ?: return
        sheet.post {
            val behavior = BottomSheetBehavior.from(sheet)
            behavior.isFitToContents = true
            behavior.skipCollapsed = true
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            sheet.translationY = 0f
            applyFlushBottom(sheet)
            sheet.requestLayout()
        }
    }
}
