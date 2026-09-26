package com.nexus.launcher.ui.folder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.TextPaint
import android.text.TextUtils
import com.nexus.launcher.data.FolderConfig
import com.nexus.launcher.data.HomeScreenItem

/** Draws a miniature open-folder card for the edit-sheet preview. */
object FolderEditMiniWindowDraw {

    fun draw(
        context: Context,
        canvas: Canvas,
        viewWidth: Int,
        viewHeight: Int,
        title: String,
        folderItem: HomeScreenItem,
        config: FolderConfig,
        contents: List<HomeScreenItem>,
        iconCache: Map<String, Drawable>,
        glass: com.nexus.launcher.ui.glass.GlassPreviewComposer? = null
    ) {
        val density = context.resources.displayMetrics.density
        val sorted = FolderContentsResolver.sortedForDisplay(contents)
        val metrics = FolderWindowGridMetrics.compute(context, config, sorted.size.coerceAtLeast(1), (56 * density).toInt())
        val pad = 12f * density
        val headerH = 22f * density
        val rowCount = ((sorted.size.coerceAtLeast(1) + metrics.widthCols - 1) / metrics.widthCols)
            .coerceIn(2, 4)
        val miniIcon = 26f * density
        val gap = 6f * density
        val gridH = rowCount * (miniIcon + gap) + gap
        val cardW = maxOf(3, metrics.widthCols) * (miniIcon + gap) + pad * 2
        val cardH = pad + headerH + gridH + pad
        val scale = minOf((viewWidth * 0.94f) / cardW, (viewHeight * 0.94f) / cardH)
        val drawW = cardW * scale
        val drawH = cardH * scale
        val left = (viewWidth - drawW) / 2f
        val top = (viewHeight - drawH) / 2f

        if (config.windowBackgroundOpacity < 0.99f) {
            drawMockBackdrop(canvas, left, top, drawW, drawH, density)
        }

        val corner = FolderGlassEdgeBuilder.cornerRadiusPx(context)
        val blurred = glass != null && FolderWallpaperBackdrop.isWindowGlass(config) &&
            config.windowBackgroundOpacity > 0.001f
        if (blurred) drawGlass(context, canvas, glass!!, config, left, top, drawW, drawH, corner * scale, cardW, cardH, density)

        canvas.save()
        canvas.translate(left, top)
        canvas.scale(scale, scale)
        val clip = android.graphics.Path().apply {
            addRoundRect(
                0f, 0f, cardW, cardH,
                corner, corner,
                android.graphics.Path.Direction.CW
            )
        }
        canvas.save()
        canvas.clipPath(clip)
        val bg = FolderWallpaperBackdrop.buildCardStack(
            context, config, cardW.toInt(), cardH.toInt(), includeWallpaper = !blurred,
        )
        bg.setBounds(0, 0, cardW.toInt(), cardH.toInt())
        bg.draw(canvas)

        val tokens = com.nexus.launcher.ui.widgets.NexusWidgetThemeResolver.resolve(context, config.themeMode)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (config.isExpressive) Color.WHITE else tokens.textPrimary
            textSize = 11f * density
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val titleClip = TextUtils.ellipsize(
            title.ifBlank { context.getString(com.nexus.launcher.R.string.folder_default_name) },
            titlePaint,
            cardW - pad * 2,
            TextUtils.TruncateAt.END
        )
        canvas.drawText(titleClip.toString(), pad, pad + titlePaint.textSize, titlePaint)

        val gridTop = pad + headerH
        sorted.take(rowCount * metrics.widthCols).forEachIndexed { index, item ->
            val col = index % metrics.widthCols
            val row = index / metrics.widthCols
            val ix = pad + col * (miniIcon + gap)
            val iy = gridTop + row * (miniIcon + gap)
            resolveIcon(context, item.packageName, iconCache)?.let { drawable ->
                drawable.setBounds(ix.toInt(), iy.toInt(), (ix + miniIcon).toInt(), (iy + miniIcon).toInt())
                drawable.draw(canvas)
            }
            if (config.showLabels) {
                val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (config.isExpressive) Color.parseColor(FolderAuroraTheme.TEXT_SECONDARY) else tokens.textSecondary
                    textSize = 7f * density
                }
                val label = labelFor(context, item)
                val clipped = TextUtils.ellipsize(label, labelPaint, miniIcon, TextUtils.TruncateAt.END)
                canvas.drawText(
                    clipped.toString(),
                    ix,
                    iy + miniIcon + 8f * density,
                    labelPaint
                )
            }
        }
        canvas.restore()
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = if (config.isExpressive) {
                Color.parseColor(FolderAuroraTheme.GLASS_BORDER)
            } else {
                tokens.divider
            }
            strokeWidth = 1.25f * density
        }
        canvas.drawRoundRect(0f, 0f, cardW, cardH, corner, corner, stroke)
        canvas.restore()
    }

    /**
     * The window's frost: the wallpaper where a real folder window opens — centred on screen, at
     * the size this miniature stands for (26dp icons for 56dp ones) — blurred by the same
     * refraction rule. The card stack then adds only the tint, as on the real window.
     */
    private fun drawGlass(
        context: Context, canvas: Canvas, glass: com.nexus.launcher.ui.glass.GlassPreviewComposer,
        config: FolderConfig, left: Float, top: Float, drawW: Float, drawH: Float, cornerPx: Float,
        cardW: Float, cardH: Float, density: Float,
    ) {
        val dm = context.resources.displayMetrics
        val real = 56f / 26f
        val srcW = (cardW * real).toInt().coerceAtMost(dm.widthPixels)
        val srcH = (cardH * real).toInt().coerceAtMost(dm.heightPixels)
        val dst = android.graphics.RectF(left, top, left + drawW, top + drawH)
        val clip = android.graphics.Path().apply {
            addRoundRect(dst, cornerPx, cornerPx, android.graphics.Path.Direction.CW)
        }
        glass.draw(
            canvas, dst, (dm.widthPixels - srcW) / 2, (dm.heightPixels - srcH) / 2, srcW, srcH,
            frameCornerPx = -1f, glassClip = clip, refraction = config.glassRefraction,
            opacity = config.windowBackgroundOpacity,
        )
    }

    private fun resolveIcon(
        context: Context,
        packageName: String,
        iconCache: Map<String, Drawable>
    ): Drawable? {
        iconCache[packageName]?.let {
            return it.constantState?.newDrawable()?.mutate() ?: it.mutate()
        }
        return try {
            context.packageManager.getApplicationIcon(packageName).mutate()
        } catch (_: Exception) {
            null
        }
    }

    private fun labelFor(context: Context, item: HomeScreenItem): String {
        return try {
            val pm = context.packageManager
            val label = pm.getApplicationLabel(pm.getApplicationInfo(item.packageName, 0))
            label?.toString()?.take(8) ?: item.packageName
        } catch (_: Exception) {
            item.packageName.take(8)
        }
    }

    private fun drawMockBackdrop(
        canvas: Canvas,
        cardLeft: Float,
        cardTop: Float,
        cardWidth: Float,
        cardHeight: Float,
        density: Float
    ) {
        val mockColors = intArrayOf(
            Color.parseColor("#3E7C9A"),
            Color.parseColor("#E06A3B"),
            Color.parseColor("#8E44AD"),
            Color.parseColor("#27AE60"),
            Color.parseColor("#2980B9"),
            Color.parseColor("#D35400")
        )
        val mockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.argb(200, 255, 255, 255)
        }
        val cols = 3
        val rows = 2
        val pad = 12f * density
        val availW = cardWidth - pad * 2
        val availH = cardHeight - pad * 2
        val gapX = 10f * density
        val gapY = 10f * density
        val iconW = (availW - (cols - 1) * gapX) / cols
        val iconH = (availH - (rows - 1) * gapY) / rows
        val mockIconSize = minOf(iconW, iconH).coerceAtLeast(16f * density)
        val corner = 8f * density

        val totalW = cols * mockIconSize + (cols - 1) * gapX
        val totalH = rows * mockIconSize + (rows - 1) * gapY
        val startX = cardLeft + (cardWidth - totalW) / 2f
        val startY = cardTop + (cardHeight - totalH) / 2f

        var colorIdx = 0
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val cx = startX + c * (mockIconSize + gapX)
                val cy = startY + r * (mockIconSize + gapY)
                val baseCol = mockColors[colorIdx % mockColors.size]
                colorIdx++
                mockPaint.color = androidx.core.graphics.ColorUtils.setAlphaComponent(baseCol, 230)
                canvas.drawRoundRect(cx, cy, cx + mockIconSize, cy + mockIconSize, corner, corner, mockPaint)
                canvas.drawCircle(cx + mockIconSize / 2f, cy + mockIconSize / 2f, mockIconSize * 0.20f, glyphPaint)
            }
        }
    }
}
