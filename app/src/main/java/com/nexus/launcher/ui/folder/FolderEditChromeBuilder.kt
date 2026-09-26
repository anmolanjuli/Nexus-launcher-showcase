package com.nexus.launcher.ui.folder

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.typography.NexusTypeScale

/** Builds the Folder Edit sheet's outer chrome: scrim root, glass panel, drag handle, title row + close button. */
object FolderEditChromeBuilder {

    class Result(
        val root: FrameLayout,
        val panelRoot: LinearLayout,
        val closeBtn: ImageView,
    )

    fun build(context: Context, tokens: NexusColorTokens, dp: Float): Result {
        val root = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val panelRoot = LinearLayout(context).apply {
            id = R.id.folder_edit_sheet_root
            orientation = LinearLayout.VERTICAL
            val marginH = (10 * dp).toInt()
            val baseMarginBottom = (8 * dp).toInt()
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(marginH, 0, marginH, baseMarginBottom)
            }
            background = GradientDrawable().apply {
                setColor(tokens.bg)
                cornerRadius = 24f * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
            }
            clipToOutline = true
        }

        panelRoot.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                (40 * dp).toInt(), (4 * dp).toInt()
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = (11 * dp).toInt()
                bottomMargin = (10 * dp).toInt()
            }
            background = GradientDrawable().apply {
                setColor(tokens.divider)
                cornerRadius = 2f * dp
            }
        })

        val headerTitleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padH = (16 * dp).toInt()
            setPadding(padH, 0, padH, (8 * dp).toInt())
        }
        val titleText = TextView(context).apply {
            text = context.getString(com.nexus.launcher.R.string.folder_edit_title)
            NexusTypeScale.title.bindTo(this, tokens.textPrimary)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeBtn = ImageView(context).apply {
            id = R.id.folder_rename_cancel
            setImageResource(R.drawable.ic_close)
            imageTintList = ColorStateList.valueOf(tokens.textSecondary)
            val pad = (6 * dp).toInt()
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams((32 * dp).toInt(), (32 * dp).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(tokens.surfaceRaised)
            }
        }
        headerTitleRow.addView(titleText)
        headerTitleRow.addView(closeBtn)
        panelRoot.addView(headerTitleRow)

        return Result(root, panelRoot, closeBtn)
    }
}
