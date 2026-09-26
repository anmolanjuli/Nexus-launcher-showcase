package com.nexus.launcher.ui.folder

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/** Dark frosted plate behind folder spot art. */
object FolderSpotPlateDraw {

    fun draw(
        canvas: Canvas,
        shapeStyle: Int,
        cx: Float,
        cy: Float,
        half: Float,
        density: Float
    ) {
        val platePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(240, 10, 14, 20)
            style = Paint.Style.FILL
        }
        val plateRect = RectF(cx - half * 0.92f, cy - half * 0.92f, cx + half * 0.92f, cy + half * 0.92f)
        val path = com.nexus.launcher.ui.folder.FolderIconShapeDraw.getShapePath(plateRect, half * 0.92f, shapeStyle)
        
        canvas.drawPath(path, platePaint)
        
        val gloss = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(35, 255, 255, 255)
            style = Paint.Style.STROKE
            strokeWidth = 1f * density
        }
        
        canvas.drawPath(path, gloss)
    }
}
