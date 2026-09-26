package com.nexus.launcher.ui.settings

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.icons.IconPackInfo

/**
 * Horizontal tiles for choosing an icon pack, the sibling of [IconShapeTileRow].
 *
 * Lifted out of the old `IconCustomizationSheet` when the Icons settings page stopped hiding
 * these behind a "tap to customise" row. Selection applies immediately — there is no staged
 * Apply, matching every other control in Settings.
 */
object IconPackTileRow {

    /** The synthetic first tile, which means "no pack, use the system icons". */
    const val NO_PACK = "none"

    fun build(
        context: Context,
        density: Float,
        packs: List<IconPackInfo>,
        selectedPackage: String,
        tokens: NexusColorTokens,
        onSelected: (String) -> Unit,
    ): View {
        val dp = density
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val all = listOf(IconPackInfo(NO_PACK, context.getString(com.nexus.launcher.R.string.settings_icon_pack_system_default), null)) + packs

        fun paintTiles(selected: String) {
            for (i in 0 until row.childCount) {
                val tile = row.getChildAt(i) as LinearLayout
                val isSel = all[i].packageName == selected
                (tile.background as? GradientDrawable)?.apply {
                    if (isSel) {
                        setColor(tokens.surface)
                        setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
                    } else {
                        setColor(Color.TRANSPARENT)
                        setStroke(0, Color.TRANSPARENT)
                    }
                }
                (tile.getChildAt(tile.childCount - 1) as? TextView)
                    ?.setTextColor(if (isSel) tokens.textPrimary else tokens.textSecondary)
            }
        }

        for (pack in all) {
            val isSelected = pack.packageName == selectedPackage
            val tile = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    (80 * dp).toInt(),
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { setMargins((6 * dp).toInt(), 0, (6 * dp).toInt(), 0) }
                background = GradientDrawable().apply {
                    cornerRadius = 14f * dp
                    if (isSelected) {
                        setColor(tokens.surface)
                        setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
                    } else {
                        setColor(Color.TRANSPARENT)
                        setStroke(0, Color.TRANSPARENT)
                    }
                }
                setPadding(0, (10 * dp).toInt(), 0, (10 * dp).toInt())
                setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    paintTiles(pack.packageName)
                    onSelected(pack.packageName)
                }
            }

            val iconSize = (34 * dp).toInt()
            tile.addView(
                ImageView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
                    if (pack.icon != null) {
                        setImageDrawable(pack.icon)
                    } else {
                        setImageResource(com.nexus.launcher.R.drawable.outline_square_circle_24)
                        imageTintList = android.content.res.ColorStateList.valueOf(tokens.textSecondary)
                    }
                },
            )
            tile.addView(
                TextView(context).apply {
                    text = pack.label
                    gravity = Gravity.CENTER
                    setSingleLine()
                    ellipsize = TextUtils.TruncateAt.END
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply { topMargin = (4 * dp).toInt() }
                    NexusTypeScale.caption.bindTo(
                        this,
                        if (isSelected) tokens.textPrimary else tokens.textSecondary,
                    )
                },
            )
            row.addView(tile)
        }

        return HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
            setPadding((8 * dp).toInt(), 0, (8 * dp).toInt(), (12 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            addView(row)
        }
    }
}
