package com.nexus.launcher.ui.canvas

import android.graphics.RectF

object RadialMenuGeometry {
    data class SweepResult(
        val sweepAngle: Float,
        val startAngle: Float,
        val invalidEdgeCount: Int,
        val cx: Float,
        val cy: Float,
        val baseR: Float,
        val canonical1x1Size: Float
    )

    fun computeFinalGeometry(
        anchorRect: RectF,
        itemsSize: Int,
        density: Float,
        w: Float,
        h: Float,
        statusBarH: Float,
        navBarH: Float,
        canonical1x1Size: Float,
        isFixedUpwardHalfRing: Boolean = false
    ): SweepResult {
        val baseR = Math.max(87f * density, canonical1x1Size / 2f + 16f * density + 37f * density)
        val menuRadius = baseR + 37f * density
        
        var cx = anchorRect.centerX()
        var cy = anchorRect.centerY()
        
        val padX = 16f * density
        val padTop = 16f * density + statusBarH
        val padBottom = 0f
        
        if (isFixedUpwardHalfRing) {
            // DOCK MODE: Fixed 180-degree upward half-ring.
            // Horizontal clamp only, to avoid clipping left/right edges.
            // Vertical position (cy) is strictly locked to the dock icon's true position.
            cx = cx.coerceIn(padX + menuRadius, w - padX - menuRadius)
            
            return SweepResult(
                sweepAngle = 180f,
                startAngle = 180f, // 180 to 360 is exactly the top half
                invalidEdgeCount = 0,
                cx = cx,
                cy = cy,
                baseR = baseR,
                canonical1x1Size = canonical1x1Size
            )
        }
        
        // MATHEMATICAL CLAMPING: Instantly snaps cx,cy into the closest valid screen position 
        // that guarantees a fully circular 360-degree menu.
        cx = cx.coerceIn(padX + menuRadius, w - padX - menuRadius)
        cy = cy.coerceIn(padTop + menuRadius, h - padBottom - menuRadius)
        
        return SweepResult(
            sweepAngle = 360f,
            startAngle = -(360f / itemsSize) / 2f,
            invalidEdgeCount = 0,
            cx = cx,
            cy = cy,
            baseR = baseR,
            canonical1x1Size = canonical1x1Size
        )
    }
}
