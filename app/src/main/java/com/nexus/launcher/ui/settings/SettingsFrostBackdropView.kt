package com.nexus.launcher.ui.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.RenderEffect
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.View
import com.nexus.launcher.ui.folder.HomeScreenFrameCache
import com.nexus.launcher.ui.glass.FrostedGlassEngine

/**
 * Full-bleed frosted glass wallpaper backdrop for Nexus Settings.
 * Renders hardware GPU-blurred wallpaper on API 31+ via RenderNode.
 */
class SettingsFrostBackdropView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var blurRenderNode: RenderNode? = null
    private var lastRecordedW = -1
    private var lastRecordedH = -1
    private var lastBackdropVersion = -1L
    private val blurRadiusPx = 50f * resources.displayMetrics.density

    init {
        setWillNotDraw(false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurRenderNode = RenderNode("SettingsFrostNode").apply {
                setRenderEffect(RenderEffect.createBlurEffect(blurRadiusPx, blurRadiusPx, Shader.TileMode.CLAMP))
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        if (!FrostedGlassEngine.isGlobalFrostedGlassEnabled) return
        if (width <= 0 || height <= 0) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val node = blurRenderNode
            if (node != null) {
                val currentVersion = HomeScreenFrameCache.getBackdropVersion()
                if (width != lastRecordedW || height != lastRecordedH ||
                    currentVersion != lastBackdropVersion || !node.hasDisplayList()
                ) {
                    lastRecordedW = width
                    lastRecordedH = height
                    lastBackdropVersion = currentVersion
                    node.setPosition(0, 0, width, height)
                    val nodeCanvas = node.beginRecording()
                    HomeScreenFrameCache.drawWallpaperOnly(nodeCanvas, context, 0, 0, width, height)
                    node.endRecording()
                }

                if (canvas.isHardwareAccelerated) {
                    canvas.drawRenderNode(node)
                    return
                }
            }
        }

        FrostedGlassEngine.drawBlurredWallpaper(canvas, context, 0, 0, width, height, divisor = 8)
    }

    fun invalidateBackdrop() {
        lastRecordedW = -1
        lastRecordedH = -1
        lastBackdropVersion = -1L
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurRenderNode?.discardDisplayList()
        }
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            blurRenderNode?.discardDisplayList()
        }
    }
}
