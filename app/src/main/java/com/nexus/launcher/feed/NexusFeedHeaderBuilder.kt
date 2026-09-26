package com.nexus.launcher.feed

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.typography.NexusTypeScale

/** Builds Nexus Feed's header row (menu/title/search) and the collapsible search bar. */
object NexusFeedHeaderBuilder {

    class Result(
        val headerLayout: LinearLayout,
        val searchBarLayout: LinearLayout,
        val searchInput: EditText,
        val searchClearBtn: ImageView,
        val menuBtn: ImageView,
        val searchBtn: ImageView,
        val titleView: TextView,
    )

    fun build(
        context: Context,
        tokens: NexusColorTokens,
        dp: Float,
        statusBarHeight: Int,
        onMenuClick: () -> Unit,
        onSearchClick: () -> Unit,
        onSearchTextChanged: (String) -> Unit,
    ): Result {
        val headerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((20 * dp).toInt(), statusBarHeight + (12 * dp).toInt(), (20 * dp).toInt(), (10 * dp).toInt())
        }

        val menuBtn = ImageView(context).apply {
            setImageResource(R.drawable.ic_menu)
            imageTintList = ColorStateList.valueOf(tokens.textPrimary)
            layoutParams = LinearLayout.LayoutParams((31 * dp).toInt(), (31 * dp).toInt())
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onMenuClick()
            }
        }
        headerLayout.addView(menuBtn)

        val titleView = TextView(context).apply {
            text = context.getString(R.string.nexus_feed_title)
            NexusTypeScale.title.bindTo(this, tokens.textPrimary)
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        headerLayout.addView(titleView)

        val searchBtn = ImageView(context).apply {
            setImageResource(R.drawable.ic_search)
            imageTintList = ColorStateList.valueOf(tokens.textPrimary)
            layoutParams = LinearLayout.LayoutParams((31 * dp).toInt(), (31 * dp).toInt())
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onSearchClick()
            }
        }
        headerLayout.addView(searchBtn)

        val searchBarLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            background = GradientDrawable().apply {
                val alpha = (com.nexus.launcher.ui.glass.FrostedGlassEngine.sheetFillAlpha(1f, 0.70f) * 255).toInt()
                setColor((alpha shl 24) or (tokens.surfaceRaised and 0x00FFFFFF))
                cornerRadius = 14 * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
            }
            setPadding((14 * dp).toInt(), (2 * dp).toInt(), (10 * dp).toInt(), (2 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (44 * dp).toInt()).apply {
                setMargins((20 * dp).toInt(), 0, (20 * dp).toInt(), (8 * dp).toInt())
            }
        }

        val searchInput = EditText(context).apply {
            hint = context.getString(R.string.nexus_feed_search_hint)
            setHintTextColor(tokens.textSecondary)
            NexusTypeScale.body.bindTo(this, tokens.textPrimary)
            background = null
            isSingleLine = true
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val searchClearBtn = ImageView(context).apply {
            setImageResource(R.drawable.ic_close)
            imageTintList = ColorStateList.valueOf(tokens.textSecondary)
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams((20 * dp).toInt(), (20 * dp).toInt())
            setOnClickListener { searchInput.setText("") }
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim() ?: ""
                searchClearBtn.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                onSearchTextChanged(query)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        searchBarLayout.addView(searchInput)
        searchBarLayout.addView(searchClearBtn)

        return Result(headerLayout, searchBarLayout, searchInput, searchClearBtn, menuBtn, searchBtn, titleView)
    }
}
