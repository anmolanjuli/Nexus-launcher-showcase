package com.nexus.launcher.ui.contextmenu

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import com.nexus.launcher.ui.glass.FrostedGlassEngine

class UniversalRadialMenu(
    context: Context,
    val geometry: com.nexus.launcher.ui.canvas.RadialMenuGeometry.SweepResult,
    val items: List<UniversalRadialMenuItem>,
    val folderTitle: String = "",
    private val onDismissRequest: () -> Unit
) : View(context) {

    private val density = context.resources.displayMetrics.density
    private var entranceProgress = 0f
    private var entranceAnimator: ValueAnimator? = null
    
    private val iconCache = mutableMapOf<Int, android.graphics.Bitmap>()
    
    private var cx = 0f
    private var cy = 0f
    private var sweepAngle = 360f
    private var startAngle = 0f
    private var baseR = 0f
    
    private val tokens: com.nexus.launcher.theme.NexusColorTokens = try {
        com.nexus.launcher.theme.ThemeObserver.currentTokens(context)
    } catch (_: Exception) {
        com.nexus.launcher.theme.NexusColorTokens.Dark
    }

    private val accentColor = tokens.accent
    
    private var touchController: com.nexus.launcher.ui.canvas.RadialMenuTouchController? = null
    
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tokens.textPrimary
        textAlign = Paint.Align.CENTER
    }
    
    init {
        baseR = geometry.baseR
        cx = geometry.cx
        cy = geometry.cy
        sweepAngle = geometry.sweepAngle
        startAngle = geometry.startAngle
        
        touchController = com.nexus.launcher.ui.canvas.RadialMenuTouchController(
            itemCount = items.size,
            density = density,
            totalSweepAngle = sweepAngle,
            startAngle = startAngle,
            baseR = baseR,
            onHoverChanged = { index -> 
                if (index != -1) performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                invalidate() 
            },
            onActionSelected = { index -> 
                post {
                    if (index in items.indices) {
                        items[index].action.invoke()
                    }
                    closeMenu()
                }
            },
            onDismiss = { closeMenu() }
        ).apply {
            initialize(cx, cy)
        }
        
        startEntranceAnimation()
    }
    
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
    }

    private fun startEntranceAnimation() {
        entranceAnimator?.cancel()
        entranceAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 200
            interpolator = OvershootInterpolator(1.2f)
            addUpdateListener { anim ->
                entranceProgress = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }
    
    fun closeMenu() {
        entranceAnimator?.cancel()
        entranceAnimator = ValueAnimator.ofFloat(entranceProgress, 0f).apply {
            duration = 200
            interpolator = AccelerateInterpolator()
            addUpdateListener { anim ->
                entranceProgress = anim.animatedValue as Float
                invalidate()
            }
            addListener(object : android.animation.AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: android.animation.Animator) {
                    onDismissRequest()
                }
            })
            start()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return touchController?.onTouchEvent(event) ?: true
    }

    override fun onDraw(canvas: Canvas) {
        val entranceScale = 0.8f + 0.2f * entranceProgress
        val activeIndex = touchController?.getActiveIndex() ?: -1
        
        val strokeW = 74f * density * entranceScale
        val rectRadius = baseR * entranceScale
        val rect = RectF(cx - rectRadius, cy - rectRadius, cx + rectRadius, cy + rectRadius)
        
        // Translucent, not opaque: FolderBlurCoordinator.setWorkspaceBlur already blurs the
        // workspace behind this menu (ContextMenuManager.show), so a semi-transparent ring
        // reads as frosted glass instead of a flat opaque panel sitting on top of that blur.
        val frostedTokens = FrostedGlassEngine.resolveFrostedTokens(tokens)
        val ringAlpha = (FrostedGlassEngine.sheetFillAlpha(1f, 0.70f) * 255f).toInt()
        val unselectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = strokeW
            color = (frostedTokens.surface and 0x00FFFFFF) or (ringAlpha shl 24)
        }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f * density
            color = tokens.divider
        }
        
        val sliceAngle = sweepAngle / items.size
        
        for (i in items.indices) {
            val drawStartAngle = startAngle + (i * sliceAngle)
            val isHovered = (i == activeIndex)
            
            if (isHovered) {
                val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    strokeWidth = strokeW
                    color = accentColor
                    setShadowLayer(15f * density, 0f, 0f, accentColor)
                }
                canvas.save()
                val cxScale = cx + baseR * Math.cos(Math.toRadians(drawStartAngle + sliceAngle / 2.0)).toFloat()
                val cyScale = cy + baseR * Math.sin(Math.toRadians(drawStartAngle + sliceAngle / 2.0)).toFloat()
                canvas.scale(0.96f, 0.96f, cxScale, cyScale)
                canvas.drawArc(rect, drawStartAngle, sliceAngle, false, selectedPaint)
            } else {
                canvas.drawArc(rect, drawStartAngle, sliceAngle, false, unselectedPaint)
            }
            
            if (sweepAngle == 360f || i > 0) {
                val divAngle = Math.toRadians(drawStartAngle.toDouble())
                val innerR = (baseR - 37f * density) * entranceScale
                val outerR = (baseR + 37f * density) * entranceScale
                val startXLine = cx + innerR * Math.cos(divAngle).toFloat()
                val startYLine = cy + innerR * Math.sin(divAngle).toFloat()
                val stopXLine = cx + outerR * Math.cos(divAngle).toFloat()
                val stopYLine = cy + outerR * Math.sin(divAngle).toFloat()
                canvas.drawLine(startXLine, startYLine, stopXLine, stopYLine, dividerPaint)
            }
            
            val sliceCenterAngle = drawStartAngle + sliceAngle / 2.0
            
            val itemR = baseR * entranceScale
            val itemCx = cx + itemR * Math.cos(Math.toRadians(sliceCenterAngle)).toFloat()
            val itemCy = cy + itemR * Math.sin(Math.toRadians(sliceCenterAngle)).toFloat()
            
            val item = items[i]
            val bmp = getIconBitmap(i, item.iconRes)
            if (bmp != null) {
                val iconAlpha = ((if (isHovered) 255 else 180) * entranceProgress).toInt().coerceIn(0, 255)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    alpha = iconAlpha
                    colorFilter = android.graphics.PorterDuffColorFilter(
                        if (isHovered) Color.WHITE else tokens.textPrimary, 
                        android.graphics.PorterDuff.Mode.SRC_IN
                    )
                }
                val iconScale = 1.2f
                val drawWidth = bmp.width * iconScale
                val drawHeight = bmp.height * iconScale
                canvas.drawBitmap(bmp, null, RectF(itemCx - drawWidth/2f, itemCy - 10f*density - drawHeight/2f, itemCx + drawWidth/2f, itemCy - 10f*density + drawHeight/2f), paint)
            }
            
            textPaint.textSize = 13f * density * entranceScale
            val txtColorAlpha = if (isHovered) 255 else 180
            textPaint.color = androidx.core.graphics.ColorUtils.setAlphaComponent(
                if (isHovered) Color.WHITE else tokens.textPrimary,
                (txtColorAlpha * entranceProgress).toInt().coerceIn(0, 255)
            )
            
            val currentTextWidth = textPaint.measureText(item.label)
            val maxTextWidth = (2f * baseR * Math.sin(Math.toRadians(sliceAngle / 2.0)).toFloat()) - 8f * density
            
            if (currentTextWidth > maxTextWidth && maxTextWidth > 0f) {
                val scale = maxTextWidth / currentTextWidth
                textPaint.textSize = Math.max(8f * density * entranceScale, 13f * density * entranceScale * scale)
            }
            
            canvas.drawText(item.label, itemCx, itemCy + 15f * density, textPaint)
            
            if (isHovered) canvas.restore()
        }
        
        if (sweepAngle < 360f) {
            val divAngle = Math.toRadians((startAngle + sweepAngle).toDouble())
            val innerR = (baseR - 37f * density) * entranceScale
            val outerR = (baseR + 37f * density) * entranceScale
            canvas.drawLine(
                cx + innerR * Math.cos(divAngle).toFloat(),
                cy + innerR * Math.sin(divAngle).toFloat(),
                cx + outerR * Math.cos(divAngle).toFloat(),
                cy + outerR * Math.sin(divAngle).toFloat(),
                dividerPaint
            )
        }
        
        if (folderTitle.isNotEmpty()) {
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textAlign = Paint.Align.CENTER
                textSize = 12f * density * entranceScale
                setShadowLayer(4f * density, 0f, 2f * density, Color.BLACK)
                alpha = (255 * entranceProgress).toInt().coerceIn(0, 255)
            }
            val gapTop = cy + geometry.canonical1x1Size / 2f
            val gapBottom = cy + baseR * entranceScale - 37f * density
            val textY = gapTop + (gapBottom - gapTop) / 2f + titlePaint.textSize / 3f
            canvas.drawText(folderTitle, cx, textY, titlePaint)
        }
    }
    
    private fun getIconBitmap(index: Int, drawableId: Int): android.graphics.Bitmap? {
        if (iconCache.containsKey(index)) return iconCache[index]
        val drawable = ContextCompat.getDrawable(context, drawableId) ?: return null
        val iconSize = (24f * density).toInt()
        val bitmap = android.graphics.Bitmap.createBitmap(iconSize, iconSize, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, iconSize, iconSize)
        drawable.draw(canvas)
        iconCache[index] = bitmap
        return bitmap
    }
}
