package com.nexus.launcher.reader.doc

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens

/**
 * Premium floating pill search bar for the Document Library.
 * Height: 48dp, corner radius: 24dp, surface token background, 16dp magnifying glass icon.
 */
class DocumentLibrarySearchBar(
    context: Context,
    private var tokens: NexusColorTokens,
    private var isEInk: Boolean,
    private val onQueryChanged: (String) -> Unit
) : LinearLayout(context) {

    private val dp = resources.displayMetrics.density
    private val searchIcon: ImageView
    val editText: EditText

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val hMargin = (16 * dp).toInt()
        val vMargin = (6 * dp).toInt()
        val lp = LayoutParams(LayoutParams.MATCH_PARENT, (48 * dp).toInt()).apply {
            setMargins(hMargin, vMargin, hMargin, (12 * dp).toInt())
        }
        layoutParams = lp

        // Background pill
        updateBackground()

        // 16dp Magnifying glass icon on the left
        searchIcon = ImageView(context).apply {
            setImageResource(R.drawable.ic_search)
            imageTintList = ColorStateList.valueOf(tokens.textSecondary)
            val iconSize = (16 * dp).toInt()
            val iconLp = LayoutParams(iconSize, iconSize).apply {
                marginStart = (16 * dp).toInt()
                marginEnd = (10 * dp).toInt()
            }
            layoutParams = iconLp
        }
        addView(searchIcon)

        // Text input
        editText = EditText(context).apply {
            hint = context.getString(R.string.nexus_doc_search_library_placeholder)
            textSize = 14f
            setTextColor(tokens.textPrimary)
            setHintTextColor(tokens.textSecondary)
            background = null
            setPadding(0, 0, (16 * dp).toInt(), 0)
            maxLines = 1
            isSingleLine = true
            typeface = if (isEInk) android.graphics.Typeface.MONOSPACE else android.graphics.Typeface.DEFAULT
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)

            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    onQueryChanged(s?.toString()?.trim() ?: "")
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        addView(editText)
    }

    fun applyTokens(newTokens: NexusColorTokens, eInk: Boolean) {
        tokens = newTokens
        isEInk = eInk
        updateBackground()
        searchIcon.imageTintList = ColorStateList.valueOf(tokens.textSecondary)
        editText.setTextColor(tokens.textPrimary)
        editText.setHintTextColor(tokens.textSecondary)
        editText.typeface = if (isEInk) android.graphics.Typeface.MONOSPACE else android.graphics.Typeface.DEFAULT
    }

    private fun updateBackground() {
        background = GradientDrawable().apply {
            setColor(tokens.surface)
            cornerRadius = if (isEInk) 0f else 24 * dp
            if (isEInk) {
                setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
            }
        }
    }
}
