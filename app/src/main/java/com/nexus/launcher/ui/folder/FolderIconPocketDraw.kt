package com.nexus.launcher.ui.folder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import com.nexus.launcher.data.FolderConfig

object FolderIconPocketDraw {

    fun drawBacking(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        config: FolderConfig,
        bgPaint: Paint,
        density: Float,
        bounds: RectF?,
        shapeStyle: Int = 8
    ) {
        val rect = bounds ?: RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        val path = FolderIconShapeDraw.getPocketFootprintPath(rect, radius, shapeStyle)
        fillDimmedPocketBacking(canvas, path, rect, bgPaint.color, density, config.backgroundOpacity)
    }

    /** Dim gradient of the folder bg — alpha follows Background Opacity slider. */
    fun fillDimmedPocketBacking(
        canvas: Canvas,
        path: Path,
        rect: RectF,
        baseColor: Int,
        density: Float,
        opacity: Float = 1f
    ) {
        val hsv = FloatArray(3)
        Color.colorToHSV(baseColor, hsv)
        hsv[2] = (hsv[2] * 0.78f).coerceIn(0f, 1f)
        val op = opacity.coerceIn(0f, 1f)
        val fillAlpha = (op * 255f).toInt().coerceIn(0, 255)
        val dark = Color.HSVToColor(fillAlpha, hsv)
        val lightHsv = hsv.copyOf()
        lightHsv[2] = (lightHsv[2] * 1.18f).coerceIn(0f, 1f)
        val light = Color.HSVToColor(fillAlpha, lightHsv)

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = android.graphics.LinearGradient(
                rect.left, rect.top, rect.left, rect.bottom,
                light, dark,
                android.graphics.Shader.TileMode.CLAMP
            )
            style = Paint.Style.FILL
        }
        canvas.drawPath(path, fillPaint)

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = Color.argb((45 * op).toInt().coerceIn(0, 255), 0, 0, 0)
            strokeWidth = 1.5f * density
        }
        canvas.drawPath(path, strokePaint)
    }

    fun drawFrontFlap(
        context: Context,
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        config: FolderConfig,
        density: Float,
        bounds: RectF?,
        customPaint: Paint? = null
    ) {
        val rect = bounds ?: RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        val corner = radius * 0.4f
        
        // Create the custom path for the front pouch
        val path = Path()
        
        // Start from bottom left corner arc end
        path.moveTo(rect.left + corner, rect.bottom)
        
        // Left edge up to ~40% from top
        path.lineTo(rect.left, rect.bottom - corner)
        path.lineTo(rect.left, rect.top + rect.height() * 0.4f)
        
        // Curve to create the cutout tab (like the App Pouch reference)
        val tabStartX = rect.left
        val tabStartY = rect.top + rect.height() * 0.4f
        
        val dipX = rect.left + rect.width() * 0.35f
        val dipY = rect.top + rect.height() * 0.45f
        
        val tabEndX = rect.left + rect.width() * 0.5f
        val tabEndY = rect.top + rect.height() * 0.6f
        
        path.cubicTo(
            tabStartX + rect.width() * 0.1f, tabStartY,
            dipX - rect.width() * 0.1f, dipY,
            dipX, dipY
        )
        
        path.cubicTo(
            dipX + rect.width() * 0.1f, dipY,
            tabEndX - rect.width() * 0.1f, tabEndY,
            tabEndX, tabEndY
        )
        
        // Right edge
        path.lineTo(rect.right - corner, tabEndY)
        path.quadTo(rect.right, tabEndY, rect.right, tabEndY + corner)
        path.lineTo(rect.right, rect.bottom - corner)
        
        // Bottom right corner
        path.quadTo(rect.right, rect.bottom, rect.right - corner, rect.bottom)
        
        // Bottom edge back to start
        path.lineTo(rect.left + corner, rect.bottom)
        path.quadTo(rect.left, rect.bottom, rect.left, rect.bottom - corner)
        path.close()
        
        if (customPaint != null) {
            canvas.drawPath(path, customPaint)
        } else {
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = FolderIconBallColor.resolve(context, config)
                style = Paint.Style.FILL
            }
            canvas.drawPath(path, fillPaint)
        }
        
        val op = config.backgroundOpacity.coerceIn(0f, 1f)
        // Add frosted top rim highlight
        val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((120 * op).toInt().coerceIn(0, 255), 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 2f * density
        }
        canvas.drawPath(path, rimPaint)
        
        // Add subtle inner shadow on the front flap
        val innerRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((40 * op).toInt().coerceIn(0, 255), 0, 0, 0)
            style = Paint.Style.STROKE
            strokeWidth = 1f * density
        }
        canvas.drawPath(path, innerRim)
    }

    fun drawLeatherWalletFlap(
        context: Context,
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        config: FolderConfig,
        density: Float,
        bounds: RectF?,
        customPaint: Paint? = null
    ) {
        val rect = bounds ?: RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        val corner = radius * 0.35f
        
        // Front flap covers bottom 55%
        val topEdge = rect.bottom - (rect.height() * 0.55f)
        
        val path = Path()
        path.moveTo(rect.left, rect.bottom - corner)
        // Left edge
        path.lineTo(rect.left, topEdge + corner)
        // Top-left corner
        path.quadTo(rect.left, topEdge, rect.left + corner, topEdge)
        // Dip slightly in the middle
        val midX = rect.centerX()
        val dipY = topEdge + (radius * 0.15f)
        path.quadTo(midX, dipY, rect.right - corner, topEdge)
        // Top-right corner
        path.quadTo(rect.right, topEdge, rect.right, topEdge + corner)
        // Right edge
        path.lineTo(rect.right, rect.bottom - corner)
        // Bottom-right corner
        path.quadTo(rect.right, rect.bottom, rect.right - corner, rect.bottom)
        // Bottom edge
        path.lineTo(rect.left + corner, rect.bottom)
        // Bottom-left corner
        path.quadTo(rect.left, rect.bottom, rect.left, rect.bottom - corner)
        path.close()
        
        // Fill
        if (customPaint != null) {
            canvas.drawPath(path, customPaint)
        } else {
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = FolderIconBallColor.resolve(context, config)
                style = Paint.Style.FILL
            }
            canvas.drawPath(path, fillPaint)
        }
        
        val op = config.backgroundOpacity.coerceIn(0f, 1f)
        // Stitching!
        val stitchPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((120 * op).toInt().coerceIn(0, 255), 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * density
            pathEffect = android.graphics.DashPathEffect(floatArrayOf(4f * density, 3f * density), 0f)
        }
        
        val stitchPath = Path()
        val inset = 4f * density
        val inL = rect.left + inset
        val inR = rect.right - inset
        val inB = rect.bottom - inset
        val inT = topEdge + inset
        val inC = (corner - inset).coerceAtLeast(0f)
        val inDipY = dipY + inset * 0.8f
        
        stitchPath.moveTo(inL, inB - inC)
        stitchPath.lineTo(inL, inT + inC)
        stitchPath.quadTo(inL, inT, inL + inC, inT)
        stitchPath.quadTo(cx, inDipY, inR - inC, inT)
        stitchPath.quadTo(inR, inT, inR, inT + inC)
        stitchPath.lineTo(inR, inB - inC)
        stitchPath.quadTo(inR, inB, inR - inC, inB)
        stitchPath.lineTo(inL + inC, inB)
        stitchPath.quadTo(inL, inB, inL, inB - inC)
        stitchPath.close()
        
        canvas.drawPath(stitchPath, stitchPaint)
        
        // Outer rim highlight for leather edge thickness
        val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((40 * op).toInt().coerceIn(0, 255), 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
        }
        canvas.drawPath(path, rimPaint)
        
        val innerShadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((60 * op).toInt().coerceIn(0, 255), 0, 0, 0)
            style = Paint.Style.STROKE
            strokeWidth = 2f * density
        }
        canvas.drawPath(path, innerShadow)
    }

    fun drawFileFolderBacking(
        canvas: Canvas, cx: Float, cy: Float, radius: Float, config: FolderConfig, bgPaint: Paint, density: Float, bounds: RectF?
    ) {
        val rect = bounds ?: RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        val path = FolderIconShapeDraw.getShapePath(rect, radius, 10)
        fillDimmedPocketBacking(canvas, path, rect, bgPaint.color, density, config.backgroundOpacity)
    }

    fun drawFileFolderFrontFlap(
        context: Context,
        canvas: Canvas, cx: Float, cy: Float, radius: Float, config: FolderConfig, density: Float, bounds: RectF?, customPaint: Paint? = null
    ) {
        val rect = bounds ?: RectF(cx - radius, cy - radius, cx + radius, cy + radius)
        val path = Path()
        FolderIconShapeDraw.appendFileFolderFrontFlap(path, rect, radius)
        val topEdge = rect.top + rect.height() * FolderIconShapeDraw.fileFolderFlapTopRatio()

        if (customPaint != null) {
            canvas.drawPath(path, customPaint)
        } else {
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = FolderIconBallColor.resolve(context, config)
                style = Paint.Style.FILL
            }
            canvas.drawPath(path, fillPaint)
        }

        val op = config.backgroundOpacity.coerceIn(0f, 1f)
        // Crease shadow where flap meets the peek zone.
        val creasePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((70 * op).toInt().coerceIn(0, 255), 0, 0, 0)
            style = Paint.Style.STROKE
            strokeWidth = 1.25f * density
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(rect.left + radius * 0.24f, topEdge, rect.right - radius * 0.24f, topEdge, creasePaint)

        val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((90 * op).toInt().coerceIn(0, 255), 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
        }
        canvas.drawPath(path, rimPaint)

        val innerRim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((45 * op).toInt().coerceIn(0, 255), 0, 0, 0)
            style = Paint.Style.STROKE
            strokeWidth = 1f * density
        }
        canvas.drawPath(path, innerRim)
    }
}
