package com.nexus.launcher.ui.folder

import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.nexus.launcher.R

/** Positions the folder popup card near the source icon on screen. */
object FolderWindowPlacer {

    private const val TAG = "FolderPlace"

    fun statusBarInset(root: View, fallbackPx: Int = 0): Int {
        val insets = ViewCompat.getRootWindowInsets(root)
        return insets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: fallbackPx
    }

    fun navBarInset(root: View): Int {
        val insets = ViewCompat.getRootWindowInsets(root)
        return insets?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
    }

    fun positionCardNearIcon(
        cardHolder: View,
        root: View,
        iconScreenX: Float,
        iconScreenY: Float,
        iconSize: Float,
        cardWidthPx: Int
    ) {
        val density = root.resources.displayMetrics.density
        val edgePx = (16 * density).toInt()
        val gapPx = 8f * density
        val edgeMarginPx = 16f * density
        val screenW = root.resources.displayMetrics.widthPixels
        val screenH = root.resources.displayMetrics.heightPixels
        val statusBarHeight = statusBarInset(cardHolder, fallbackPx = 80).toFloat()
        val navBarHeight = navBarInset(root).toFloat()

        val cardW = resolveCardWidth(cardHolder, cardWidthPx)
        val cardH = cardHolder.measuredHeight.takeIf { it > 0 }
            ?: (screenH * 0.35f).toInt()

        val isWideCard = cardW >= screenW - edgePx * 2
        val left = if (isWideCard) {
            ((screenW - cardW) / 2).coerceAtLeast(edgePx)
        } else {
            val desiredLeft = (iconScreenX - cardW / 2f).toInt()
            val maxLeft = (screenW - cardW - edgePx).coerceAtLeast(edgePx)
            desiredLeft.coerceIn(edgePx, maxLeft)
        }

        val spaceAbove = iconScreenY - iconSize / 2f - statusBarHeight - edgeMarginPx
        val spaceBelow = screenH - iconScreenY - iconSize / 2f - navBarHeight - edgeMarginPx
        val placeAbove = spaceAbove >= cardH && spaceAbove >= spaceBelow

        val desiredTop = if (placeAbove) {
            iconScreenY - iconSize / 2f - cardH - gapPx
        } else {
            iconScreenY + iconSize / 2f + gapPx
        }

        val minTopClamp = statusBarHeight + gapPx
        val maxTopClamp = (screenH - navBarHeight - cardH - gapPx).coerceAtLeast(minTopClamp)
        val topMarginScreen = desiredTop.coerceIn(minTopClamp, maxTopClamp)

        val rootLoc = IntArray(2)
        root.getLocationOnScreen(rootLoc)
        val finalTopMargin = (topMarginScreen - rootLoc[1]).toInt().coerceAtLeast(0)

        Log.d(
            TAG,
            "iconScreenX=$iconScreenX iconScreenY=$iconScreenY iconSize=$iconSize " +
                "cardH=$cardH screenH=$screenH statusBar=$statusBarHeight navBar=$navBarHeight " +
                "spaceAbove=$spaceAbove spaceBelow=$spaceBelow placeAbove=$placeAbove " +
                "desiredTop=$desiredTop finalTopMargin=$finalTopMargin rootLocY=${rootLoc[1]}"
        )

        val rawLp = cardHolder.layoutParams
        Log.d(
            TAG,
            "layoutParams type: ${rawLp?.javaClass?.simpleName} " +
                "isMargin=${rawLp is ViewGroup.MarginLayoutParams}"
        )
        Log.d(
            TAG,
            "BEFORE set: topMargin=" +
                "${(cardHolder.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin} " +
                "translationY=${cardHolder.translationY} y=${cardHolder.y}"
        )

        val finalLeftMargin = (left - rootLoc[0]).coerceAtLeast(0)
        applyHolderMargins(cardHolder, cardW, finalLeftMargin, finalTopMargin)
        relayoutHolderNow(cardHolder)

        Log.d(
            TAG,
            "AFTER set: topMargin=" +
                "${(cardHolder.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin} " +
                "translationY=${cardHolder.translationY} y=${cardHolder.y}"
        )
        logHolderScreenPosition(cardHolder, "AFTER relayout")

        cardHolder.post {
            val currentTop = (cardHolder.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin
            if (currentTop != finalTopMargin) {
                Log.d(TAG, "POST_LAYOUT margin reset ($currentTop), re-applying $finalTopMargin")
                applyHolderMargins(cardHolder, cardW, finalLeftMargin, finalTopMargin)
                relayoutHolderNow(cardHolder)
            }
            logHolderScreenPosition(cardHolder, "POST_LAYOUT")
            Log.d(
                TAG,
                "POST_LAYOUT topMargin=" +
                    "${(cardHolder.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin}"
            )
        }

        root.findViewById<FolderWindowScrimView>(R.id.folder_scrim)?.setIconSpot(
            iconScreenX,
            iconScreenY,
            iconSize,
            shapeStyle = 0,
            accentColor = FolderScrimHighlight.resolvedAccentColor()
        )
    }

    private fun applyHolderMargins(
        cardHolder: View,
        cardW: Int,
        leftMargin: Int,
        topMargin: Int
    ) {
        val rawLp = cardHolder.layoutParams
        val lp = when (rawLp) {
            is ViewGroup.MarginLayoutParams -> rawLp
            else -> FrameLayout.LayoutParams(cardW, FrameLayout.LayoutParams.WRAP_CONTENT)
        }
        lp.width = cardW
        lp.height = FrameLayout.LayoutParams.WRAP_CONTENT
        if (lp is FrameLayout.LayoutParams) {
            lp.gravity = Gravity.TOP or Gravity.LEFT
        }
        lp.leftMargin = leftMargin
        lp.topMargin = topMargin
        lp.rightMargin = 0
        lp.bottomMargin = 0
        cardHolder.translationX = 0f
        cardHolder.translationY = 0f
        cardHolder.layoutParams = lp
        cardHolder.requestLayout()
        (cardHolder.parent as? View)?.requestLayout()
    }

    private fun relayoutHolderNow(cardHolder: View) {
        val parent = cardHolder.parent as? ViewGroup ?: return
        if (!parent.isLaidOut || parent.width <= 0 || parent.height <= 0) return
        val widthSpec = View.MeasureSpec.makeMeasureSpec(parent.width, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(parent.height, View.MeasureSpec.EXACTLY)
        parent.measure(widthSpec, heightSpec)
        parent.layout(parent.left, parent.top, parent.right, parent.bottom)
    }

    private fun logHolderScreenPosition(cardHolder: View, label: String) {
        val loc = IntArray(2)
        cardHolder.getLocationOnScreen(loc)
        Log.d(
            TAG,
            "$label screenY=${loc[1]} top=${cardHolder.top} " +
                "topMargin=${(cardHolder.layoutParams as? ViewGroup.MarginLayoutParams)?.topMargin}"
        )
    }

    private fun resolveCardWidth(card: View, cardWidthPx: Int): Int {
        val layoutWidth = card.layoutParams?.width?.takeIf { it > 0 } ?: 0
        card.measure(
            View.MeasureSpec.makeMeasureSpec(cardWidthPx, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        return maxOf(cardWidthPx, layoutWidth, card.measuredWidth)
    }

    fun applyGridWidth(scroll: View, grid: View, gridWidthPx: Int) {
        grid.layoutParams = grid.layoutParams.apply { width = gridWidthPx }
        scroll.layoutParams = scroll.layoutParams.apply { width = gridWidthPx }
    }

    fun capScrollHeight(scroll: View, maxHeightPx: Int) {
        val width = scroll.width.takeIf { it > 0 } ?: scroll.measuredWidth
        scroll.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val contentH = scroll.measuredHeight
        val lp = scroll.layoutParams
        lp.height = contentH.coerceAtMost(maxHeightPx)
        scroll.layoutParams = lp
    }
}
