package com.nexus.launcher.ui.settings.views

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.lang.ref.WeakReference

/** Floating Aurora Glass option list for [AuroraDropdownRow]. */
object AuroraDropdownOverlay {

    private var activeOverlay: WeakReference<View>? = null

    fun show(
        host: ViewGroup,
        anchor: View,
        options: List<String>,
        selectedIndex: Int,
        accentArgb: Int,
        onSelected: (Int) -> Unit
    ) {
        dismiss(host)
        val dp = host.resources.displayMetrics.density
        val edge = (16 * dp).toInt()
        val gap = (4 * dp).toInt()
        val padH = (14 * dp).toInt()
        val padV = (11 * dp).toInt()

        val overlay = FrameLayout(host.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            elevation = 48f * dp
            setOnClickListener { dismiss(host) }
        }

        val card = LinearLayout(host.context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#F0121820"))
                cornerRadius = 14f * dp
                setStroke((1f * dp).toInt().coerceAtLeast(1), Color.parseColor("#667EB8D4"))
            }
            setOnClickListener { /* consume */ }
        }

        var maxRowWidth = 0
        options.forEachIndexed { index, label ->
            if (index > 0) {
                card.addView(View(host.context).apply {
                    tag = "divider"
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        (1f * dp).toInt().coerceAtLeast(1)
                    )
                    setBackgroundColor(Color.parseColor("#1AFFFFFF"))
                })
            }
            val row = TextView(host.context).apply {
                text = label
                textSize = 15f
                setTextColor(if (index == selectedIndex) accentArgb else Color.WHITE)
                gravity = Gravity.CENTER_VERTICAL
                setPadding(padH, padV, padH, padV)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
                setOnClickListener {
                    onSelected(index)
                    dismiss(host)
                }
            }
            row.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            )
            maxRowWidth = maxOf(maxRowWidth, row.measuredWidth)
            card.addView(row)
        }

        val cardWidth = maxRowWidth.coerceAtLeast((96 * dp).toInt())
        for (i in 0 until card.childCount) {
            val child = card.getChildAt(i)
            (child.layoutParams as? LinearLayout.LayoutParams)?.width = cardWidth
        }

        overlay.addView(card, FrameLayout.LayoutParams(
            cardWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.START
        ))

        host.addView(overlay)
        overlay.bringToFront()
        activeOverlay = WeakReference(overlay)

        card.measure(
            View.MeasureSpec.makeMeasureSpec(cardWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val cardH = card.measuredHeight

        val anchorLoc = IntArray(2)
        anchor.getLocationOnScreen(anchorLoc)
        val hostLoc = IntArray(2)
        host.getLocationOnScreen(hostLoc)
        val anchorBottom = anchorLoc[1] - hostLoc[1] + anchor.height
        val anchorRight = anchorLoc[0] - hostLoc[0] + anchor.width

        var left = anchorRight - cardWidth
        left = left.coerceIn(edge, (host.width - cardWidth - edge).coerceAtLeast(edge))
        var top = anchorBottom + gap
        if (top + cardH > host.height - edge) {
            top = anchorLoc[1] - hostLoc[1] - cardH - gap
        }
        top = top.coerceIn(edge, (host.height - cardH - edge).coerceAtLeast(edge))

        (card.layoutParams as FrameLayout.LayoutParams).apply {
            leftMargin = left
            topMargin = top
        }
        card.requestLayout()
    }

    fun dismiss(host: ViewGroup? = null) {
        val overlay = activeOverlay?.get() ?: return
        (overlay.parent as? ViewGroup)?.removeView(overlay)
        activeOverlay = null
    }
}
