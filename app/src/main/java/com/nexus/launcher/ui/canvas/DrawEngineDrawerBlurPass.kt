package com.nexus.launcher.ui.canvas

import android.graphics.Canvas
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build

/**
 * Blur and fade pass for drawer content during Smart transition.
 * Uses cached RenderNode display list so drawer items are drawn only ONCE per gesture,
 * ensuring 120 FPS smooth GPU hardware compositing without CPU recording stalls.
 */
class DrawEngineDrawerBlurPass(private val view: LauncherCanvasView) {
    private var drawerRenderNode: RenderNode? = null
    // What the recorded node shows, compared field by field so a transition frame allocates
    // nothing. The list is compared by identity: a new list with the same count (an icon pack
    // applied, an app updated) still has to re-record.
    private var hasRecord = false
    private var recordedItems: List<*>? = null
    private var recordedScrollY = 0
    private var recordedSearchMode = false
    private var lastBlurStep = -1

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            drawerRenderNode = RenderNode("DrawerBlurPassNode")
        }
    }

    fun draw(
        canvas: Canvas,
        progress: Float,
        drawerAlpha: Int,
        drawDrawerContent: (Canvas, Int) -> Unit
    ) {
        if (drawerAlpha <= 0) {
            if (lastBlurStep != -1 || hasRecord) {
                lastBlurStep = -1
                dropRecord()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    drawerRenderNode?.setRenderEffect(null)
                }
            }
            return
        }

        val shouldBlur = progress < 1f &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && drawerRenderNode != null

        if (!shouldBlur) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && lastBlurStep != 0) {
                drawerRenderNode?.setRenderEffect(null)
                lastBlurStep = 0
            }
            dropRecord()
            drawDrawerContent(canvas, 255)
            return
        }

        val node = drawerRenderNode!!
        // Cache display list: record with full contentAlpha (255) so node.alpha drives fade smoothly on GPU
        if (!hasRecord || !node.hasDisplayList() ||
            recordedItems !== view.drawerItems ||
            recordedScrollY != view.scrollY.toInt() ||
            recordedSearchMode != view.isSearchMode
        ) {
            hasRecord = true
            recordedItems = view.drawerItems
            recordedScrollY = view.scrollY.toInt()
            recordedSearchMode = view.isSearchMode
            node.setPosition(0, 0, view.viewWidth, view.viewHeight)
            val nodeCanvas = node.beginRecording()
            drawDrawerContent(nodeCanvas, 255)
            node.endRecording()
        }

        node.alpha = (drawerAlpha / 255f).coerceIn(0f, 1f)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val maxBlur = 28f
            // Quantize blur in 4px steps to avoid per-frame Skia shader regeneration
            val blurStep = (((1f - progress) * maxBlur) / 4f).toInt()
            if (blurStep != lastBlurStep) {
                lastBlurStep = blurStep
                val blurRadius = blurStep * 4f
                if (blurRadius > 0.5f) {
                    node.setRenderEffect(
                        com.nexus.launcher.ui.glass.BlurEffectCache.get(blurRadius, Shader.TileMode.CLAMP)
                    )
                } else {
                    node.setRenderEffect(null)
                }
            }
        }

        val saveCount = canvas.save()
        Drawer3DTransitionEngine.applyDrawerTransform(
            canvas,
            view.drawerTransition,
            progress,
            view.viewWidth.toFloat(),
            view.viewHeight.toFloat()
        )
        canvas.drawRenderNode(node)
        canvas.restoreToCount(saveCount)
    }

    // Forget the recorded list too, so the node does not keep an old drawer list alive.
    private fun dropRecord() {
        hasRecord = false
        recordedItems = null
    }
}
