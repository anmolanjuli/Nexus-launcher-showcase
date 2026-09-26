package com.nexus.launcher.ui.dock

import android.graphics.Canvas
import android.graphics.drawable.Drawable

/** Draws a dock icon with optional center-anchored scale (no Paint alloc per frame). */
internal object DockIconPainter {

  fun drawIcon(
    canvas: Canvas,
    drawable: Drawable,
    left: Float,
    top: Float,
    iconSizePx: Float,
    scale: Float
  ) {
    if (scale == 1f) {
      drawable.setBounds(
        left.toInt(), top.toInt(),
        (left + iconSizePx).toInt(), (top + iconSizePx).toInt()
      )
      drawable.draw(canvas)
      return
    }
    val cx = left + iconSizePx / 2f
    val cy = top + iconSizePx / 2f
    canvas.save()
    canvas.translate(cx, cy)
    canvas.scale(scale, scale)
    val half = iconSizePx / 2f
    drawable.setBounds((-half).toInt(), (-half).toInt(), half.toInt(), half.toInt())
    drawable.draw(canvas)
    canvas.restore()
  }
}
