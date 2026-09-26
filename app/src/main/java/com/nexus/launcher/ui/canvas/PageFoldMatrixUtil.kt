package com.nexus.launcher.ui.canvas

import android.graphics.Camera
import android.graphics.Canvas
import android.graphics.Matrix

/**
 * Camera/Matrix pivot math for Gravity Fold. Camera rotates about its own origin,
 * so the hinge must be recentered with pre/post translates. Allocates Camera and
 * Matrix once; every call uses save()/restore() — never constructs in onDraw.
 */
object PageFoldMatrixUtil {
    private val camera = Camera()
    private val matrix = Matrix()

    /** Camera Z in camera-space units: Android's default is 8 at mdpi, scaled by density. */
    const val CAMERA_DEPTH_FACTOR = 8f

    fun applyYRotation(
        canvas: Canvas,
        hingeX: Float,
        pivotY: Float,
        rotationAngle: Float,
        density: Float
    ) {
        camera.save()
        camera.setLocation(0f, 0f, -CAMERA_DEPTH_FACTOR * density)
        camera.rotateY(rotationAngle)
        camera.getMatrix(matrix)
        camera.restore()
        matrix.preTranslate(-hingeX, -pivotY)
        matrix.postTranslate(hingeX, pivotY)
        canvas.concat(matrix)
    }
}
