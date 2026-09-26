package com.nexus.launcher.ui.folder

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import com.nexus.launcher.data.FolderConfig
import com.nexus.launcher.ui.dock.DockFrostedGradients

/** Folder icon fill that mirrors the open-folder window background mode. */
object FolderIconFolderBgDraw {

    fun draw(
        context: Context,
        canvas: Canvas,
        cx: Float,
        cy: Float,
        folderRadius: Float,
        config: FolderConfig,
        density: Float,
        bounds: android.graphics.RectF? = null
    ) {
        if (config.shapeStyle == 6) return
        val alphaInt = (config.backgroundOpacity.coerceIn(0f, 1f) * 255f).toInt().coerceIn(0, 255)
        if (alphaInt <= 0) return

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        if (config.isExpressive) {
            when (config.windowBackgroundMode.uppercase()) {
                "FROSTED" -> {
                    val idx = DockFrostedGradients.clampIndex(config.frostedGradientIndex)
                    val preset = DockFrostedGradients.presets[idx]
                    val left = bounds?.left ?: (cx - folderRadius)
                    val top = bounds?.top ?: (cy - folderRadius)
                    val right = bounds?.right ?: (cx + folderRadius)
                    val bottom = bounds?.bottom ?: (cy + folderRadius)
                    paint.shader = LinearGradient(
                        left, top, right, bottom,
                        Color.argb(alphaInt, Color.red(preset.startRgb), Color.green(preset.startRgb), Color.blue(preset.startRgb)),
                        Color.argb(alphaInt, Color.red(preset.endRgb), Color.green(preset.endRgb), Color.blue(preset.endRgb)),
                        Shader.TileMode.CLAMP
                    )
                }
                "SOLID" -> {
                    val hex = config.solidBackgroundColor ?: config.backgroundColor ?: FolderAuroraTheme.BASE_BG
                    paint.color = try {
                        Color.parseColor(hex)
                    } catch (_: Exception) {
                        Color.parseColor(FolderAuroraTheme.BASE_BG)
                    }
                    paint.alpha = alphaInt
                }
                else -> {
                    paint.color = Color.argb(alphaInt, 255, 255, 255)
                }
            }
        } else {
            paint.color = FolderIconSurfaceColor.fillWithOpacity(context, config)
        }
        FolderIconShapeDraw.drawBackground(canvas, cx, cy, folderRadius, config, paint, density, bounds)
    }
}
