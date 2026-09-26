package com.nexus.launcher.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/**
 * Phone-frame card that locks to [ManagePagesThumbSpec] aspect so page
 * thumbnails fill edge-to-edge under FIT_CENTER (no side letterbox bars).
 */
class ManagePagesPhoneFrame @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val mode = MeasureSpec.getMode(widthMeasureSpec)
        var width = MeasureSpec.getSize(widthMeasureSpec)
        if (mode == MeasureSpec.UNSPECIFIED || width <= 0) {
            width = measuredWidth.takeIf { it > 0 }
                ?: (ManagePagesThumbSpec.WIDTH_DP * resources.displayMetrics.density).toInt()
        }
        val ratio = ManagePagesThumbSpec.heightOverWidth(context)
        val height = (width * ratio).toInt().coerceAtLeast(1)
        val wSpec = MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY)
        val hSpec = MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
        super.onMeasure(wSpec, hSpec)
    }
}
