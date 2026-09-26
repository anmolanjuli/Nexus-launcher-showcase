package com.nexus.launcher.ui.icons

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import kotlin.math.abs
import kotlin.math.max

/**
 * Polarity-aware tonal glyph mapping — cache generation only, never in onDraw.
 */
internal object ThemedIconSilhouette {

    const val GLYPH_OPTICAL_FILL = 0.74f

    private const val SOURCE_ALPHA_MIN = 25
    private const val DELTA_FLOOR = 0.08f
    /** Delta at which a pixel becomes fully opaque — anything above reads as solid ink. */
    private const val DELTA_SOLID = 0.40f
    /** Floor for the color-distance span so a nearly-flat icon doesn't amplify noise. */
    private const val DIST_SPAN_MIN = 0.15f
    /** Below this max distance the foreground is one flat color — its alpha IS the glyph. */
    private const val FLAT_GLYPH_DIST = 0.12f
    /** Foreground covering this much of the safe zone is an opaque tile, not a cut-out logo. */
    private const val TILE_FILL_RATIO = 0.85f
    /** Share of opaque pixels the top color bin needs to count as a flat container. */
    private const val DOMINANT_MIN_SHARE = 0.30f
    /** Luma percentiles used to contrast-stretch photographic icons before duotoning. */
    private const val TONAL_LOW_PCT = 0.05f
    private const val TONAL_HIGH_PCT = 0.95f
    /** Raster/analyse at this multiple of the output size, then filter down. */
    const val ANALYSIS_SCALE = 2
    /** Used on the final fitted bitmap (canvas-relative) by [isGlyphEmpty]. */
    private const val MIN_GLYPH_FILL = 0.02f
    /** Ink pixels ÷ source opaque pixels — a thin wordmark on a tile is ~3-8%. */
    private const val MIN_INK_RATIO = 0.01f

    private val desatPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val tintPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val drawPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    fun renderNativeMonochromeLayer(monochrome: Drawable, sizePx: Int, fgColor: Int): Bitmap {
        // Supersample so the downscale into the 74% fit lands on crisp, filtered edges.
        val analysisPx = sizePx * ANALYSIS_SCALE
        val layer = Bitmap.createBitmap(analysisPx, analysisPx, Bitmap.Config.ARGB_8888)
        monochrome.mutate()
        monochrome.setBounds(0, 0, analysisPx, analysisPx)
        monochrome.draw(Canvas(layer))
        val tinted = tintMonochromeLayer(layer, fgColor)
        layer.recycle()
        val fitted = fitGlyphToCanvas(tinted, sizePx)
        tinted.recycle()
        return fitted
    }

    fun buildTonalGlyphOnPlate(
        drawable: Drawable,
        fgColor: Int,
        @Suppress("UNUSED_PARAMETER") isDarkTheme: Boolean,
        sizePx: Int,
        adaptiveViewport: Boolean = false
    ): Bitmap {
        // Analyse at 2× and let fitMappedToCanvas filter down — the old path cropped a small
        // logo out of a 1× raster and upscaled it, which is where the soft/bold look came from.
        val source = rasterizeDrawable(drawable, sizePx * ANALYSIS_SCALE, adaptiveViewport)
        val glyph = buildTonalGlyphOnPlate(source, fgColor, sizePx)
        source.recycle()
        return glyph
    }

    fun buildTonalGlyphOnPlate(source: Bitmap, fgColor: Int, sizePx: Int): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        val sourceOpaque = opaqueCount(pixels)

        var mapped = mapContainerDistance(pixels, w, h, fgColor, sourceOpaque)
        // Ink relative to the source's own opaque area — NOT the full canvas. A wordmark on a
        // tile is a few percent of the tile but well under 2% of the square, and the old
        // canvas-relative check sent every such icon to the edge-contour ghost path.
        val inkRatio = if (sourceOpaque == 0) 0f else opaqueCount(mapped).toFloat() / sourceOpaque
        if (inkRatio < MIN_INK_RATIO) {
            mapped = highPassEdgeContour(source, fgColor)
        }
        return fitMappedToCanvas(mapped, w, h, sizePx)
    }

    fun isGlyphEmpty(glyph: Bitmap): Boolean = opaqueFillRatio(glyph) < MIN_GLYPH_FILL

    /**
     * Three-way glyph classifier. Every branch outputs [fgColor] with a per-pixel alpha.
     *
     *  1. Flat glyph — opaque pixels are essentially one color and don't fill the safe zone
     *     (a cut-out logo): the alpha channel already is the silhouette; use it verbatim.
     *  2. Container icon — one color bin holds ≥ [DOMINANT_MIN_SHARE] of the opaque pixels
     *     (a tile, a badge, a colored disc): that *dominant* color is the container, and RGB
     *     distance from it is ink, snapped solid via [solidAlpha]. Using the dominant color
     *     rather than the perimeter is what keeps a bordered tile (ALDI's white rim around
     *     blue) from flipping the whole tile to ink.
     *  3. Photographic — no dominant color (Depth, Call of Duty, Fidelity): a binary ink rule
     *     can only over- or under-shoot, so contrast-stretch the luma between the
     *     5th/95th percentiles and keep it *continuous* as a duotone. Polarity follows the
     *     icon's mean luma: dark features on a bright image, bright on a dark one.
     */
    private fun mapContainerDistance(
        pixels: IntArray,
        w: Int,
        h: Int,
        fgColor: Int,
        sourceOpaque: Int
    ): IntArray {
        val out = IntArray(pixels.size)
        if (sourceOpaque == 0) return out
        val fgRgb = fgColor and 0xFFFFFF
        val isTile = sourceOpaque.toFloat() / (w * h) >= TILE_FILL_RATIO

        val (container, dominantShare) = dominantColor(pixels, sourceOpaque)

        val dist = FloatArray(pixels.size)
        var maxDist = 0f
        for (i in pixels.indices) {
            if (pixels[i] ushr 24 and 0xFF <= SOURCE_ALPHA_MIN) continue
            val d = colorDistance(pixels[i], container)
            dist[i] = d
            if (d > maxDist) maxDist = d
        }

        // 1. Flat glyph
        if (!isTile && maxDist < FLAT_GLYPH_DIST) {
            for (i in pixels.indices) {
                val a = pixels[i] ushr 24 and 0xFF
                if (a <= SOURCE_ALPHA_MIN) continue
                out[i] = (a shl 24) or fgRgb
            }
            return out
        }

        // 2. Container icon
        if (dominantShare >= DOMINANT_MIN_SHARE) {
            val span = maxDist.coerceAtLeast(DIST_SPAN_MIN)
            for (i in pixels.indices) {
                val a = pixels[i] ushr 24 and 0xFF
                if (a <= SOURCE_ALPHA_MIN) continue
                val delta = (dist[i] / span).coerceIn(0f, 1f)
                if (delta < DELTA_FLOOR) continue
                val finalAlpha = solidAlpha(delta, a)
                if (finalAlpha == 0) continue
                out[i] = (finalAlpha shl 24) or fgRgb
            }
            return out
        }

        // 3. Photographic → duotone
        mapTonalDuotone(pixels, fgRgb, out)
        return out
    }

    /**
     * Continuous-alpha duotone for icons with no flat container. Luma is percentile-stretched
     * so a low-contrast photo still uses the full ink range, and never snapped to solid.
     */
    private fun mapTonalDuotone(pixels: IntArray, fgRgb: Int, out: IntArray) {
        val hist = IntArray(256)
        var lumaSum = 0L
        var n = 0
        for (p in pixels) {
            if (p ushr 24 and 0xFF <= SOURCE_ALPHA_MIN) continue
            val l = luma255(p)
            hist[l]++
            lumaSum += l
            n++
        }
        if (n == 0) return
        val lo = percentile(hist, n, TONAL_LOW_PCT)
        val hi = percentile(hist, n, TONAL_HIGH_PCT).coerceAtLeast(lo + 1)
        val span = (hi - lo).toFloat()
        val darkFeatures = lumaSum.toFloat() / n > 127.5f

        for (i in pixels.indices) {
            val a = pixels[i] ushr 24 and 0xFF
            if (a <= SOURCE_ALPHA_MIN) continue
            val t = ((luma255(pixels[i]) - lo) / span).coerceIn(0f, 1f)
            val ink = if (darkFeatures) 1f - t else t
            val alpha = (ink * a + 0.5f).toInt().coerceIn(0, 255)
            if (alpha == 0) continue
            out[i] = (alpha shl 24) or fgRgb
        }
    }

    /**
     * Most-populated 4-bit-per-channel color bin among opaque pixels, returned as that bin's
     * mean color plus its share of the opaque area.
     */
    private fun dominantColor(pixels: IntArray, sourceOpaque: Int): Pair<Int, Float> {
        val counts = IntArray(4096)
        val sumR = LongArray(4096)
        val sumG = LongArray(4096)
        val sumB = LongArray(4096)
        for (p in pixels) {
            if (p ushr 24 and 0xFF <= SOURCE_ALPHA_MIN) continue
            val r = p shr 16 and 0xFF
            val g = p shr 8 and 0xFF
            val b = p and 0xFF
            val bin = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
            counts[bin]++
            sumR[bin] += r
            sumG[bin] += g
            sumB[bin] += b
        }
        var best = 0
        for (i in 1 until 4096) if (counts[i] > counts[best]) best = i
        val c = counts[best]
        if (c == 0) return Pair(0, 0f)
        val color = ((sumR[best] / c).toInt() shl 16) or
            ((sumG[best] / c).toInt() shl 8) or
            (sumB[best] / c).toInt()
        return Pair(color, c.toFloat() / sourceOpaque)
    }

    private fun percentile(hist: IntArray, n: Int, pct: Float): Int {
        val target = (n * pct).toInt()
        var acc = 0
        for (i in 0 until 256) {
            acc += hist[i]
            if (acc > target) return i
        }
        return 255
    }

    private fun luma255(argb: Int): Int {
        val r = argb shr 16 and 0xFF
        val g = argb shr 8 and 0xFF
        val b = argb and 0xFF
        return (0.299f * r + 0.587f * g + 0.114f * b + 0.5f).toInt().coerceIn(0, 255)
    }

    /** Euclidean RGB distance normalised to 0..1 (√3·255 = 441.67). */
    private fun colorDistance(a: Int, b: Int): Float {
        val dr = (a shr 16 and 0xFF) - (b shr 16 and 0xFF)
        val dg = (a shr 8 and 0xFF) - (b shr 8 and 0xFF)
        val db = (a and 0xFF) - (b and 0xFF)
        return kotlin.math.sqrt((dr * dr + dg * dg + db * db).toFloat()) / 441.67f
    }

    private fun opaqueCount(pixels: IntArray): Int {
        var count = 0
        for (p in pixels) if (p ushr 24 and 0xFF > SOURCE_ALPHA_MIN) count++
        return count
    }

    /**
     * Smoothstep ramp from [DELTA_FLOOR] to [DELTA_SOLID], scaled by the source pixel's own
     * alpha so anti-aliased edges stay soft while interiors are fully opaque. Replaces the old
     * gamma ramp with a 160 floor that left most glyph pixels semi-transparent ("low ink").
     */
    private fun solidAlpha(delta: Float, sourceAlpha: Int): Int {
        val t = ((delta - DELTA_FLOOR) / (DELTA_SOLID - DELTA_FLOOR)).coerceIn(0f, 1f)
        val s = t * t * (3f - 2f * t)
        return (s * sourceAlpha + 0.5f).toInt().coerceIn(0, 255)
    }

    private fun highPassEdgeContour(source: Bitmap, fgColor: Int): IntArray {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        val gray = desaturatePixels(pixels, w, h)
        val fgR = fgColor shr 16 and 0xFF
        val fgG = fgColor shr 8 and 0xFF
        val fgB = fgColor and 0xFF
        val out = IntArray(pixels.size)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val idx = y * w + x
                if (pixels[idx] ushr 24 and 0xFF <= SOURCE_ALPHA_MIN) continue
                val center = pixelLuma(gray[idx])
                val edge = max(
                    abs(center - pixelLuma(gray[idx - 1])),
                    max(
                        abs(center - pixelLuma(gray[idx + 1])),
                        max(
                            abs(center - pixelLuma(gray[idx - w])),
                            abs(center - pixelLuma(gray[idx + w]))
                        )
                    )
                )
                if (edge < DELTA_FLOOR) continue
                val finalAlpha = solidAlpha(edge, pixels[idx] ushr 24 and 0xFF)
                if (finalAlpha == 0) continue
                out[idx] = (finalAlpha shl 24) or (fgR shl 16) or (fgG shl 8) or fgB
            }
        }
        return out
    }

    private fun fitGlyphToCanvas(glyph: Bitmap, sizePx: Int): Bitmap {
        val w = glyph.width
        val h = glyph.height
        val pixels = IntArray(w * h)
        glyph.getPixels(pixels, 0, w, 0, 0, w, h)
        return fitMappedToCanvas(pixels, w, h, sizePx)
    }

    private fun fitMappedToCanvas(mapped: IntArray, w: Int, h: Int, sizePx: Int): Bitmap {
        val bounds = alphaBoundsFromPixels(mapped, w, h) ?: return emptyCanvas(sizePx)
        val glyphW = bounds.width().coerceAtLeast(1)
        val glyphH = bounds.height().coerceAtLeast(1)
        val cropPixels = IntArray(glyphW * glyphH)
        for (y in 0 until glyphH) {
            System.arraycopy(mapped, (bounds.top + y) * w + bounds.left, cropPixels, y * glyphW, glyphW)
        }
        val cropped = Bitmap.createBitmap(glyphW, glyphH, Bitmap.Config.ARGB_8888)
        cropped.setPixels(cropPixels, 0, glyphW, 0, 0, glyphW, glyphH)
        val maxDim = max(glyphW, glyphH).toFloat().coerceAtLeast(1f)
        val scale = (sizePx * GLYPH_OPTICAL_FILL) / maxDim
        val targetW = (glyphW * scale).toInt().coerceAtLeast(1)
        val targetH = (glyphH * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(cropped, targetW, targetH, true)
        cropped.recycle()
        val out = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(
            scaled,
            (sizePx - targetW) / 2f,
            (sizePx - targetH) / 2f,
            drawPaint
        )
        scaled.recycle()
        return out
    }

    private fun desaturatePixels(pixels: IntArray, w: Int, h: Int): IntArray {
        val src = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        src.setPixels(pixels, 0, w, 0, 0, w, h)
        val gray = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        desatPaint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
        Canvas(gray).drawBitmap(src, 0f, 0f, desatPaint)
        desatPaint.colorFilter = null
        src.recycle()
        val out = IntArray(pixels.size)
        gray.getPixels(out, 0, w, 0, 0, w, h)
        gray.recycle()
        return out
    }

    private fun rasterizeDrawable(drawable: Drawable, sizePx: Int, adaptiveViewport: Boolean): Bitmap {
        val source = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        drawable.mutate()
        if (adaptiveViewport && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val extra = AdaptiveIconDrawable.getExtraInsetFraction()
            val inset = (sizePx * extra).toInt()
            drawable.setBounds(-inset, -inset, sizePx + inset, sizePx + inset)
        } else {
            drawable.setBounds(0, 0, sizePx, sizePx)
        }
        drawable.draw(Canvas(source))
        return source
    }

    private fun tintMonochromeLayer(layer: Bitmap, fgColor: Int): Bitmap {
        val out = Bitmap.createBitmap(layer.width, layer.height, Bitmap.Config.ARGB_8888)
        tintPaint.colorFilter = PorterDuffColorFilter(fgColor, PorterDuff.Mode.SRC_IN)
        Canvas(out).drawBitmap(layer, 0f, 0f, tintPaint)
        tintPaint.colorFilter = null
        return out
    }

    private fun emptyCanvas(sizePx: Int): Bitmap =
        Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)

    private fun opaqueFillRatio(bitmap: Bitmap): Float {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return opaqueFillRatio(pixels, bitmap.width, bitmap.height)
    }

    private fun opaqueFillRatio(pixels: IntArray, w: Int, h: Int): Float {
        var count = 0
        for (p in pixels) {
            if (p ushr 24 and 0xFF > SOURCE_ALPHA_MIN) count++
        }
        return count.toFloat() / (w * h)
    }

    private fun alphaBoundsFromPixels(pixels: IntArray, w: Int, h: Int): Rect? {
        var minX = w
        var minY = h
        var maxX = -1
        var maxY = -1
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                if (pixels[row + x] ushr 24 and 0xFF > SOURCE_ALPHA_MIN) {
                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                }
            }
        }
        return if (maxX < minX || maxY < minY) null else Rect(minX, minY, maxX + 1, maxY + 1)
    }

    private fun pixelLuma(argb: Int): Float {
        val r = argb shr 16 and 0xFF
        val g = argb shr 8 and 0xFF
        val b = argb and 0xFF
        return (0.299f * r + 0.587f * g + 0.114f * b) / 255f
    }
}
