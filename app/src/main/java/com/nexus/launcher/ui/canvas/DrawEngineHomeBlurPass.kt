package com.nexus.launcher.ui.canvas

import android.graphics.Canvas
import android.graphics.RenderNode
import android.graphics.Shader
import android.os.Build

/**
 * Drawer-open home blur pass via [RenderNode].
 * Settled home draws directly; when drawer is moving, home screen content blurs with drawer progress.
 */
class DrawEngineHomeBlurPass(private val view: LauncherCanvasView) {
    private var homeRenderNode: RenderNode? = null
    // What the recorded node shows. Compared field by field, not as a built string, so a frame
    // of the drawer transition allocates nothing. hasRecord = false forces the next record.
    private var hasRecord = false
    private var recordedGeneration = 0
    private var recordedPage = 0
    private var recordedScrollOffset = 0f
    private var lastHomeBlurStep = -1

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            homeRenderNode = RenderNode("HomeScreenNode")
        }
    }

    fun draw(canvas: Canvas, progress: Float, drawHomeAlpha: Int, drawHomeContent: (Canvas) -> Unit) {
        if (progress >= 1f || drawHomeAlpha <= 0) {
            if (hasRecord || lastHomeBlurStep != -1) {
                hasRecord = false
                lastHomeBlurStep = -1
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    homeRenderNode?.setRenderEffect(null)
                }
            }
            return
        }

        if (progress <= Drawer3DTransitionEngine.HOME_NODE_MIN_PROGRESS ||
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            homeRenderNode == null
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && lastHomeBlurStep != 0) {
                homeRenderNode?.setRenderEffect(null)
                lastHomeBlurStep = 0
            }
            hasRecord = false
            drawHomeContent(canvas)
            return
        }

        val node = homeRenderNode!!
        if (!hasRecord || !node.hasDisplayList() ||
            recordedGeneration != view.homeDrawGeneration ||
            recordedPage != view.currentPage ||
            recordedScrollOffset != view.dragScrollOffset
        ) {
            hasRecord = true
            recordedGeneration = view.homeDrawGeneration
            recordedPage = view.currentPage
            recordedScrollOffset = view.dragScrollOffset
            node.setPosition(0, 0, view.viewWidth, view.viewHeight)
            val recordCanvas = node.beginRecording()
            drawHomeContent(recordCanvas)
            node.endRecording()
        }
        node.alpha = (drawHomeAlpha / 255f).coerceIn(0f, 1f)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val maxBlur = 60f
            val blurStep = ((progress * maxBlur) / 3f).toInt()
            if (blurStep != lastHomeBlurStep) {
                lastHomeBlurStep = blurStep
                val blurRadius = blurStep * 3f
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
        Drawer3DTransitionEngine.applyHomeTransform(
            canvas,
            view.drawerTransition,
            progress,
            view.viewWidth.toFloat(),
            view.viewHeight.toFloat()
        )
        canvas.drawRenderNode(node)
        canvas.restoreToCount(saveCount)
    }
}
