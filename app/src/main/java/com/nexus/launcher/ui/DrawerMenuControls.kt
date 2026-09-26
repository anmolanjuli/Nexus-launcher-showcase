package com.nexus.launcher.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.graphics.ColorUtils
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.typography.NexusTypeScale

/**
 * One segmented track: the shared control shape for every either/or choice in the menu.
 *
 * The menu used to draw each choice its own way — stroked glyph boxes for Layout, stroked bar
 * boxes for Style, flat text pills for placement — so three rows of the same kind of decision
 * looked like three different controls. They now share this: a single rounded track with the
 * selected option on a raised thumb, and no outlines.
 *
 * Icon segments use real vector drawables (`ic_drawer_layout_*`) rather than shapes painted in
 * code, so they share the Material Symbols format and weight of every other icon in the menu.
 * The unselected segment drops to the secondary text colour, which is what makes the current
 * choice legible at a glance.
 *
 * Selection repaints locally on tap before [onSelected] runs, so the control answers the finger
 * immediately even where the caller keeps the menu open (placement) rather than dismissing it.
 */
object MenuSegmentedTrack {

    /** An icon segment or a text segment. */
    sealed class Segment(val contentDescription: String) {
        class Icon(@DrawableRes val iconRes: Int, description: String) : Segment(description)
        class Text(val label: String) : Segment(label)
    }

    fun build(
        context: Context,
        dp: Float,
        tokens: NexusColorTokens,
        segments: List<Segment>,
        selectedIndex: Int,
        segmentWidthDp: Float = 46f,
        onSelected: (Int) -> Unit,
    ): LinearLayout {
        val thumb = ColorUtils.blendARGB(tokens.surfaceRaised, tokens.textPrimary, 0.14f)
        val inset = (3 * dp).toInt()

        val track = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(inset, inset, inset, inset)
            // Neumorphism: a sunken track with the selected segment raised out of it.
            background = com.nexus.launcher.ui.glass.NeumorphicSurfaces.sunkenOr(this, tokens, 12f * dp) {
                GradientDrawable().apply {
                    cornerRadius = 12f * dp
                    setColor(tokens.surfaceRaised)
                }
            }
        }

        val cells = segments.map { segment ->
            when (segment) {
                is Segment.Icon -> ImageView(context).apply {
                    setImageResource(segment.iconRes)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    val padH = (13 * dp).toInt()
                    val padV = (6 * dp).toInt()
                    setPadding(padH, padV, padH, padV)
                }
                is Segment.Text -> TextView(context).apply {
                    text = segment.label
                    gravity = Gravity.CENTER
                    NexusTypeScale.caption.bindTo(this, tokens.textSecondary)
                }
            }.apply {
                contentDescription = segment.contentDescription
                isClickable = true
                isFocusable = true
            }
        }

        fun paint(selected: Int) {
            cells.forEachIndexed { index, cell ->
                val on = index == selected
                cell.isSelected = on
                cell.background = if (on) {
                    if (com.nexus.launcher.ui.glass.NeumorphicSurfaces.isActive) {
                        com.nexus.launcher.ui.glass.NeumorphicSurfaces.thumb(cell, tokens, 9f * dp)
                    } else {
                        GradientDrawable().apply {
                            cornerRadius = 9f * dp
                            setColor(thumb)
                        }
                    }
                } else {
                    null
                }
                val ink = if (on) tokens.textPrimary else tokens.textSecondary
                (cell as? TextView)?.setTextColor(ink)
                (cell as? ImageView)?.imageTintList = ColorStateList.valueOf(ink)
            }
        }

        cells.forEachIndexed { index, cell ->
            track.addView(
                cell,
                LinearLayout.LayoutParams((segmentWidthDp * dp).toInt(), (32 * dp).toInt()),
            )
            cell.setOnClickListener {
                it.performHapticFeedback(
                    HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                )
                paint(index)
                onSelected(index)
            }
        }
        paint(selectedIndex)
        track.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        return track
    }
}
