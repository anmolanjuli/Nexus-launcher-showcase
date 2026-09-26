package com.nexus.launcher.ui.canvas

object RadialGeometryCalculator {
    fun getTargetSliceIndex(
        dx: Float, dy: Float, density: Float, itemCount: Int,
        totalSweepAngle: Float = 360f, startAngle: Float = 0f
    ): Int {
        val distance = Math.sqrt((dx * dx + dy * dy).toDouble()).toFloat()
        
        if (distance < 50f * density) return -1
        if (distance > 150f * density) return -1
        
        val thetaRad = Math.atan2(dy.toDouble(), dx.toDouble())
        var normalized = thetaRad
        if (normalized < 0) normalized += 2 * Math.PI
        val degrees = Math.toDegrees(normalized).toFloat()
        
        val sliceAngle = totalSweepAngle / itemCount
        
        var relativeDegrees = degrees - startAngle
        while (relativeDegrees < 0) relativeDegrees += 360f
        while (relativeDegrees >= 360f) relativeDegrees -= 360f

        if (relativeDegrees > totalSweepAngle && totalSweepAngle < 360f) {
            return -1
        }
        
        return (relativeDegrees / sliceAngle).toInt().coerceIn(0, itemCount - 1)
    }
}
