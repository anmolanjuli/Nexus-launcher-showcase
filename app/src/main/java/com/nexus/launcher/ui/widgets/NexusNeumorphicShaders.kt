package com.nexus.launcher.ui.widgets

import android.graphics.BlurMaskFilter
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.RectF
import android.graphics.Shader

/**
 * Pre-allocated shader and mask filter caches for silky smooth line-free Neumorphic rendering.
 * Eliminates allocations inside draw routines.
 */
internal object NexusNeumorphicShaders {

    private var cachedFillShader: LinearGradient? = null
    private var cachedFillBoundsKey = ""
    private var cachedBevelShader: LinearGradient? = null
    private var cachedBevelBoundsKey = ""
    private var cachedDebossShader: LinearGradient? = null
    private var cachedDebossBoundsKey = ""
    private var cachedPillShader: LinearGradient? = null
    private var cachedPillBoundsKey = ""

    private var cachedShadowBlur: BlurMaskFilter? = null
    private var cachedShadowBlurPx = 0f
    private var cachedAmbientBlur: BlurMaskFilter? = null
    private var cachedAmbientBlurPx = 0f
    private var cachedHighlightBlur: BlurMaskFilter? = null
    private var cachedHighlightBlurPx = 0f

    fun getOrCreateFillShader(bounds: RectF, c1: Int, c2: Int): LinearGradient {
        val key = "${bounds.left},${bounds.top},${bounds.right},${bounds.bottom},$c1,$c2"
        if (key != cachedFillBoundsKey || cachedFillShader == null) {
            cachedFillShader = LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom, c1, c2, Shader.TileMode.CLAMP)
            cachedFillBoundsKey = key
        }
        return cachedFillShader!!
    }

    fun getOrCreateBevelShader(bounds: RectF, c1: Int, c2: Int): LinearGradient {
        val key = "${bounds.left},${bounds.top},${bounds.right},${bounds.bottom},$c1,$c2"
        if (key != cachedBevelBoundsKey || cachedBevelShader == null) {
            cachedBevelShader = LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom, c1, c2, Shader.TileMode.CLAMP)
            cachedBevelBoundsKey = key
        }
        return cachedBevelShader!!
    }

    fun getOrCreateDebossShader(bounds: RectF, c1: Int, dp: Float): LinearGradient {
        val key = "${bounds.left},${bounds.top},${bounds.right},${bounds.bottom},$c1,$dp"
        if (key != cachedDebossBoundsKey || cachedDebossShader == null) {
            cachedDebossShader = LinearGradient(bounds.left, bounds.top, bounds.left, bounds.top + 8f * dp, c1, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            cachedDebossBoundsKey = key
        }
        return cachedDebossShader!!
    }

    fun getOrCreatePillShader(bounds: RectF, c1: Int): LinearGradient {
        val key = "${bounds.left},${bounds.top},${bounds.right},${bounds.bottom},$c1"
        if (key != cachedPillBoundsKey || cachedPillShader == null) {
            cachedPillShader = LinearGradient(bounds.left, bounds.top, bounds.left, bounds.bottom, c1, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            cachedPillBoundsKey = key
        }
        return cachedPillShader!!
    }

    fun getOrCreateShadowBlur(radiusPx: Float): BlurMaskFilter {
        if (cachedShadowBlur == null || cachedShadowBlurPx != radiusPx) {
            cachedShadowBlur = BlurMaskFilter(radiusPx.coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
            cachedShadowBlurPx = radiusPx
        }
        return cachedShadowBlur!!
    }

    fun getOrCreateAmbientBlur(radiusPx: Float): BlurMaskFilter {
        if (cachedAmbientBlur == null || cachedAmbientBlurPx != radiusPx) {
            cachedAmbientBlur = BlurMaskFilter(radiusPx.coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
            cachedAmbientBlurPx = radiusPx
        }
        return cachedAmbientBlur!!
    }

    fun getOrCreateHighlightBlur(radiusPx: Float): BlurMaskFilter {
        if (cachedHighlightBlur == null || cachedHighlightBlurPx != radiusPx) {
            cachedHighlightBlur = BlurMaskFilter(radiusPx.coerceAtLeast(1f), BlurMaskFilter.Blur.NORMAL)
            cachedHighlightBlurPx = radiusPx
        }
        return cachedHighlightBlur!!
    }
}
