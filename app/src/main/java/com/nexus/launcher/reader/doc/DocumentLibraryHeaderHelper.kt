package com.nexus.launcher.reader.doc

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens

/**
 * Helper to build and dynamically update the top header row of [DocumentLibraryView],
 * keeping the main view well under the 400-line ceiling while ensuring E-Ink and theme parity.
 */
object DocumentLibraryHeaderHelper {

    class HeaderComponents(
        val rootLayout: LinearLayout,
        val sectionTitle: TextView,
        val viewModeToggleBtn: ImageView,
        val importBtn: LinearLayout,
        val importIcon: ImageView,
        val importLabel: TextView
    )

    fun buildHeader(
        context: Context,
        tokens: NexusColorTokens,
        isEInk: Boolean,
        activeViewMode: LibrarySortFilterStore.ViewMode,
        dp: Float,
        onToggleViewMode: () -> Unit,
        onImportClick: () -> Unit
    ): HeaderComponents {
        val rootLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (8 * dp).toInt(), 0, (6 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val sectionTitle = TextView(context).apply {
            text = context.getString(R.string.nexus_feed_tab_library)
            textSize = 28f
            typeface = if (isEInk) Typeface.MONOSPACE else Typeface.DEFAULT_BOLD
            setTextColor(tokens.textPrimary)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        rootLayout.addView(sectionTitle)

        val viewModeToggleBtn = ImageView(context).apply {
            val iconRes = if (activeViewMode == LibrarySortFilterStore.ViewMode.LIST) {
                R.drawable.ic_drawer_layout_grid
            } else {
                R.drawable.ic_drawer_layout_list
            }
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(tokens.textPrimary)
            val p = (7 * dp).toInt()
            setPadding(p, p, p, p)
            background = GradientDrawable().apply {
                setColor(tokens.surfaceRaised)
                cornerRadius = if (isEInk) 0f else 16 * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
            }
            layoutParams = LinearLayout.LayoutParams((32 * dp).toInt(), (32 * dp).toInt()).apply {
                marginEnd = (8 * dp).toInt()
            }
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onToggleViewMode()
            }
        }
        rootLayout.addView(viewModeToggleBtn)

        val importBtn = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(tokens.surfaceRaised)
                cornerRadius = if (isEInk) 0f else 16 * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
            }
            setPadding((12 * dp).toInt(), (6 * dp).toInt(), (12 * dp).toInt(), (6 * dp).toInt())
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onImportClick()
            }
        }

        val importIcon = ImageView(context).apply {
            setImageResource(R.drawable.ic_add)
            imageTintList = ColorStateList.valueOf(tokens.textPrimary)
            layoutParams = LinearLayout.LayoutParams((14 * dp).toInt(), (14 * dp).toInt()).apply {
                marginEnd = (4 * dp).toInt()
            }
        }
        importBtn.addView(importIcon)

        val importLabel = TextView(context).apply {
            text = context.getString(R.string.nexus_doc_import)
            textSize = 12f
            typeface = if (isEInk) Typeface.MONOSPACE else Typeface.DEFAULT_BOLD
            setTextColor(tokens.textPrimary)
        }
        importBtn.addView(importLabel)
        rootLayout.addView(importBtn)

        return HeaderComponents(
            rootLayout = rootLayout,
            sectionTitle = sectionTitle,
            viewModeToggleBtn = viewModeToggleBtn,
            importBtn = importBtn,
            importIcon = importIcon,
            importLabel = importLabel
        )
    }

    fun updateHeader(
        components: HeaderComponents,
        tokens: NexusColorTokens,
        isEInk: Boolean,
        activeViewMode: LibrarySortFilterStore.ViewMode,
        dp: Float
    ) {
        components.sectionTitle.setTextColor(tokens.textPrimary)
        components.sectionTitle.typeface = if (isEInk) Typeface.MONOSPACE else Typeface.DEFAULT_BOLD

        val iconRes = if (activeViewMode == LibrarySortFilterStore.ViewMode.LIST) {
            R.drawable.ic_drawer_layout_grid
        } else {
            R.drawable.ic_drawer_layout_list
        }
        components.viewModeToggleBtn.setImageResource(iconRes)
        components.viewModeToggleBtn.imageTintList = ColorStateList.valueOf(tokens.textPrimary)
        components.viewModeToggleBtn.background = GradientDrawable().apply {
            setColor(tokens.surfaceRaised)
            cornerRadius = if (isEInk) 0f else 16 * dp
            setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
        }

        components.importBtn.background = GradientDrawable().apply {
            setColor(tokens.surfaceRaised)
            cornerRadius = if (isEInk) 0f else 16 * dp
            setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
        }
        components.importIcon.imageTintList = ColorStateList.valueOf(tokens.textPrimary)
        components.importLabel.setTextColor(tokens.textPrimary)
        components.importLabel.typeface = if (isEInk) Typeface.MONOSPACE else Typeface.DEFAULT_BOLD
    }
}
