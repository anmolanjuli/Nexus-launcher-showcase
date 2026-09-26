package com.nexus.launcher.ui.folder

import android.graphics.Canvas
import android.graphics.drawable.Drawable
import com.nexus.launcher.data.HomeScreenItem

object FolderIconSpecialPreviewDraw {

    fun drawWarpedPreview(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        folderRadius: Float,
        bitmap: android.graphics.Bitmap
    ) {
        val meshSize = 20
        val verts = FloatArray((meshSize + 1) * (meshSize + 1) * 2)
        var index = 0
        
        for (y in 0..meshSize) {
            val fy = y.toFloat() / meshSize
            for (x in 0..meshSize) {
                val fx = x.toFloat() / meshSize
                
                val nx = fx * 2 - 1
                val ny = fy * 2 - 1
                val r = Math.hypot(nx.toDouble(), ny.toDouble()).toFloat()
                
                var outX = nx
                var outY = ny
                if (r > 0f) {
                    val rNew = if (r <= 1f) {
                        Math.sin(r * Math.PI / 2).toFloat()
                    } else {
                        r
                    }
                    outX = (nx / r) * rNew
                    outY = (ny / r) * rNew
                }
                
                val destX = cx - folderRadius + (outX + 1) / 2 * (2 * folderRadius)
                val destY = cy - folderRadius + (outY + 1) / 2 * (2 * folderRadius)
                verts[index++] = destX
                verts[index++] = destY
            }
        }
        
        val filterPaint = android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG or android.graphics.Paint.ANTI_ALIAS_FLAG)
        canvas.drawBitmapMesh(bitmap, meshSize, meshSize, verts, 0, null, 0, filterPaint)
    }

    private fun getDominantColor(drawable: Drawable): Int {
        val bitmap = android.graphics.Bitmap.createBitmap(1, 1, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, 1, 1)
        drawable.draw(canvas)
        val color = bitmap.getPixel(0, 0)
        bitmap.recycle()
        return if (android.graphics.Color.alpha(color) < 128) android.graphics.Color.GRAY else color
    }

    fun drawSummaryCard(
        context: android.content.Context,
        canvas: Canvas, cx: Float, cy: Float,
        folderItem: HomeScreenItem,
        icons: List<Pair<HomeScreenItem, Drawable>>,
        folderRadius: Float, density: Float,
        contentsCount: Int,
        showLabels: Boolean
    ) {
        val count = icons.size
        if (count == 0) return
        
        val maxScale = 1.6f
        val scale = (folderRadius / (28f * density)).coerceAtMost(maxScale)
        
        // 1. Color Chips
        val chipRadius = 4f * density * scale
        val chipGap = 2f * density * scale
        val maxChips = 8
        val chipsToShow = icons.take(maxChips)
        val totalChipWidth = chipsToShow.size * (chipRadius * 2) + (chipsToShow.size - 1) * chipGap
        val startX = cx - totalChipWidth / 2f + chipRadius
        val chipY = cy - folderRadius * 0.4f
        
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        chipsToShow.forEachIndexed { i, (_, drawable) ->
            paint.color = getDominantColor(drawable)
            canvas.drawCircle(startX + i * (chipRadius * 2 + chipGap), chipY, chipRadius, paint)
        }
        
        // 2. Folder Name (only if global labels are OFF)
        if (!showLabels) {
            var nameTextSize = 14f * density * scale
            val namePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.WHITE
                textSize = nameTextSize
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                textAlign = android.graphics.Paint.Align.CENTER
            }
            val name = folderItem.folderTitle?.takeIf { it.isNotBlank() && it != "Folder" } ?: context.getString(com.nexus.launcher.R.string.folder_default_name)
            
            val maxTextWidth = folderRadius * 1.8f
            while (namePaint.measureText(name) > maxTextWidth && nameTextSize > 8f * density) {
                nameTextSize -= 0.5f * density
                namePaint.textSize = nameTextSize
            }
            
            canvas.drawText(name, cx, cy + 5f * density * scale, namePaint)
        }
        
        // 3. Stat Line
        val statPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.LTGRAY
            textSize = 11f * density * scale
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val statText = context.resources.getQuantityString(com.nexus.launcher.R.plurals.folder_app_count, contentsCount, contentsCount)
        canvas.drawText(statText, cx, cy + 20f * density * scale, statPaint)
    }

    /**
     * Summary Card for 3D Ball — orbital color chips + frosted center pill.
     * Avoids mesh-warp which turns flat text into a muddy smear on the sphere.
     */
    fun drawOrbitalSummary(
        context: android.content.Context,
        canvas: Canvas, cx: Float, cy: Float,
        folderItem: HomeScreenItem,
        icons: List<Pair<HomeScreenItem, Drawable>>,
        folderRadius: Float, density: Float,
        contentsCount: Int,
        showLabels: Boolean
    ) {
        if (icons.isEmpty()) return
        canvas.save()
        canvas.clipPath(android.graphics.Path().apply {
            addCircle(cx, cy, folderRadius, android.graphics.Path.Direction.CW)
        })

        val chipR = (folderRadius * 0.10f).coerceAtLeast(3f * density)
        // Sit chips on the sphere rim, well away from the center pill.
        val orbit = folderRadius - chipR * 0.35f
        val chips = icons.take(8)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        chips.forEachIndexed { i, (_, drawable) ->
            val angle = -Math.PI / 2 + (2 * Math.PI * i / chips.size.coerceAtLeast(1))
            val px = cx + orbit * Math.cos(angle).toFloat()
            val py = cy + orbit * Math.sin(angle).toFloat()
            paint.color = android.graphics.Color.argb(100, 0, 0, 0)
            canvas.drawCircle(px + density * 0.4f, py + density * 0.6f, chipR * 1.08f, paint)
            paint.color = getDominantColor(drawable)
            canvas.drawCircle(px, py, chipR, paint)
            paint.color = android.graphics.Color.argb(55, 255, 255, 255)
            paint.style = android.graphics.Paint.Style.STROKE
            paint.strokeWidth = 0.7f * density
            canvas.drawCircle(px, py, chipR, paint)
            paint.style = android.graphics.Paint.Style.FILL
        }

        val pillW = folderRadius * 0.95f
        val pillH = folderRadius * 0.48f
        val pill = android.graphics.RectF(cx - pillW / 2f, cy - pillH / 2f, cx + pillW / 2f, cy + pillH / 2f)
        paint.color = android.graphics.Color.argb(160, 8, 10, 16)
        canvas.drawRoundRect(pill, pillH / 2f, pillH / 2f, paint)
        paint.shader = android.graphics.LinearGradient(
            pill.left, pill.top, pill.left, pill.bottom,
            android.graphics.Color.argb(50, 255, 255, 255),
            android.graphics.Color.argb(0, 255, 255, 255),
            android.graphics.Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(pill, pillH / 2f, pillH / 2f, paint)
        paint.shader = null
        paint.color = android.graphics.Color.argb(40, 255, 255, 255)
        paint.style = android.graphics.Paint.Style.STROKE
        paint.strokeWidth = 1f * density
        canvas.drawRoundRect(pill, pillH / 2f, pillH / 2f, paint)
        paint.style = android.graphics.Paint.Style.FILL

        val titlePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = android.graphics.Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            textSize = (folderRadius * 0.24f).coerceIn(8f * density, 13f * density)
        }
        if (!showLabels) {
            val name = folderItem.folderTitle?.takeIf { it.isNotBlank() && it != "Folder" } ?: context.getString(com.nexus.launcher.R.string.folder_default_name)
            canvas.drawText(name, cx, cy - density * 0.5f, titlePaint)
        }
        val statPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(200, 200, 210, 220)
            textAlign = android.graphics.Paint.Align.CENTER
            textSize = (folderRadius * 0.18f).coerceIn(7f * density, 11f * density)
        }
        canvas.drawText(context.resources.getQuantityString(com.nexus.launcher.R.plurals.folder_app_count, contentsCount, contentsCount), cx, cy + folderRadius * 0.18f, statPaint)
        canvas.restore()
    }
}

