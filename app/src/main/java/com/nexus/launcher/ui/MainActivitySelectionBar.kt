package com.nexus.launcher.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

object MainActivitySelectionBar {
    fun create(context: Context, mainContainer: FrameLayout): LinearLayout {
        val dp = context.resources.displayMetrics.density
        val selectionActionBar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(Color.parseColor("#F0121820"))
            foreground = GradientDrawable().apply {
                setStroke(
                    (1 * dp).toInt(),
                    Color.parseColor("#337EB8D4")
                )
            }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                (56 * dp).toInt()
            ).apply {
                gravity = Gravity.TOP
            }
            setPadding((16 * dp).toInt(), 0, (16 * dp).toInt(), 0)
            visibility = View.GONE
            elevation = 8f * dp
        }
        mainContainer.addView(selectionActionBar)

        ViewCompat.setOnApplyWindowInsetsListener(selectionActionBar) { v, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()).top
            (v.layoutParams as FrameLayout.LayoutParams).topMargin = statusBarHeight
            v.requestLayout()
            insets
        }

        return selectionActionBar
    }
}
