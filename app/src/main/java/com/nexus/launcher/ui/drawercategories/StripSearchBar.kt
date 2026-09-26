package com.nexus.launcher.ui.drawercategories

import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import com.nexus.launcher.R
import com.nexus.launcher.typography.NexusTypeScale

class StripSearchBar(context: Context) : LinearLayout(context) {
    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val density = resources.displayMetrics.density
        val palette = CategoryPalette(context)
        val padH = (14f * density).toInt()
        val padV = (10f * density).toInt()
        setPadding(padH, padV, padH, padV)
        background = CategorySurfaces.card(palette, density, 22f)
        val field = EditText(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            background = null
            setPadding(0, 0, 0, 0)
            hint = context.getString(R.string.drawer_category_search_hint)
            setHintTextColor(palette.textSecondary)
            setTextColor(palette.textPrimary)
            maxLines = 1
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO
        }
        NexusTypeScale.body.bindTo(field, palette.textPrimary)
        addView(field)
    }
}
