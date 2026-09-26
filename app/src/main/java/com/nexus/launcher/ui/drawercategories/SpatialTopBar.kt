package com.nexus.launcher.ui.drawercategories

import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.typography.NexusTypeScale

class SpatialTopBar(context: Context) : LinearLayout(context) {
    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        isBaselineAligned = false
        val density = resources.displayMetrics.density
        val palette = CategoryPalette(context)
        val pad = (4f * density).toInt()
        setPadding(pad, pad, pad, pad)

        val title = TextView(context)
        title.text = context.getString(R.string.settings_section_app_drawer)
        title.layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            marginEnd = (12f * density).toInt()
        }
        NexusTypeScale.title.bindTo(title, palette.textPrimary)
        addView(title)

        val search = EditText(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            background = CategorySurfaces.card(palette, density, 20f)
            val hPad = (14f * density).toInt()
            val vPad = (10f * density).toInt()
            setPadding(hPad, vPad, hPad, vPad)
            hint = context.getString(R.string.drawer_category_search_hint)
            setHintTextColor(palette.textSecondary)
            setTextColor(palette.textPrimary)
            maxLines = 1
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO
        }
        NexusTypeScale.body.bindTo(search, palette.textPrimary)
        addView(search)
    }
}
