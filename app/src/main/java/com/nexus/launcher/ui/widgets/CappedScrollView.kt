package com.nexus.launcher.ui.widgets

import android.content.Context
import android.widget.ScrollView

/** ScrollView that never measures taller than [capPx], so a bottom sheet cannot cover the widget. */
class CappedScrollView(context: Context) : ScrollView(context) {
    var capPx: Int = Int.MAX_VALUE

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val capped = MeasureSpec.makeMeasureSpec(capPx.coerceAtLeast(0), MeasureSpec.AT_MOST)
        super.onMeasure(widthMeasureSpec, capped)
    }
}
