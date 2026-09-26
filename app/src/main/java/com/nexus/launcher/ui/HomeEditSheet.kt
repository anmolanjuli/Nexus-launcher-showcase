package com.nexus.launcher.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.CanvasTypographyHelper
import com.nexus.launcher.ui.canvas.LauncherCanvasView
import com.nexus.launcher.ui.canvas.RadialMenuTouchController
import com.nexus.launcher.ui.widgets.WidgetOverlayLayout

class HomeEditSheet(
    context: Context,
    private val density: Float,
    private var tokens: NexusColorTokens,
    private val touchX: Float,
    private val touchY: Float,
    private val onSettings: () -> Unit,
    private val onManagePages: () -> Unit,
    private val onAddPage: () -> Unit,
    private val onAddWidget: () -> Unit,
    private val onLockLayout: (Boolean) -> Unit,
    private val onAddFolder: () -> Unit,
    private val onAddIcon: () -> Unit,
    private val onAddShortcut: () -> Unit,
    private val onWallpaper: () -> Unit,
    private val onDismiss: () -> Unit,
    /** Icon-only shortcuts that fan out of a slice held under the finger; see [HomeEditBranches]. */
    private val branches: Map<Int, List<RadialBranchItem>> = emptyMap(),
) : View(context) {

    private val branch = HomeEditRadialBranch(context, density) { invalidate() }

    private val touchController = RadialMenuTouchController(
        itemCount = 8,
        density = density,
        totalSweepAngle = 360f,
        startAngle = -(360f / 8f) / 2f,
        baseR = 95f * density,
        onHoverChanged = { index ->
            if (index != -1) playHaptic()
            invalidate()
        },
        onActionSelected = { executeAction(it) },
        onDismiss = { closeSheet() }
    )

    private val startX get() = touchController.startX
    private val startY get() = touchController.startY
    private val activeIndex get() = touchController.getActiveIndex()

    private var isLayoutLocked = false
    var isModal = true

    private var entranceProgress = 0f
    private var entranceAnimator: ValueAnimator? = null

    private val labels by lazy {
        arrayOf(
            context.getString(R.string.home_edit_apps),
            context.getString(R.string.home_edit_widgets),
            context.getString(R.string.home_edit_wallpaper),
            context.getString(R.string.home_edit_pages),
            context.getString(R.string.home_edit_folder),
            context.getString(R.string.home_edit_shortcut),
            context.getString(R.string.home_edit_settings),
            context.getString(R.string.home_edit_lock_layout)
        )
    }

    private val lockedLabel by lazy {
        context.getString(R.string.home_edit_locked)
    }

    private val iconMap = mapOf(
        0 to R.drawable.ic_menu_add_app,
        1 to R.drawable.ic_menu_widget_add,
        2 to R.drawable.ic_menu_wallpaper,
        3 to R.drawable.ic_menu_addpages,
        4 to R.drawable.ic_menu_add_folder,
        5 to R.drawable.ic_menu_shortcut,
        6 to R.drawable.ic_menu_settings,
        7 to R.drawable.ic_menu_lockpage
    )
    private val iconCache = mutableMapOf<Int, Bitmap>()

    private fun getIconBitmap(index: Int): Bitmap? {
        if (iconCache.containsKey(index)) return iconCache[index]
        val drawableId = iconMap[index] ?: return null
        val drawable = ContextCompat.getDrawable(context, drawableId) ?: return null
        val iconSize = (24f * density).toInt()
        val bitmap = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, iconSize, iconSize)
        drawable.draw(canvas)
        iconCache[index] = bitmap
        return bitmap
    }

    // Pre-allocated reusable Paint & RectF objects (zero onDraw allocation)
    private val scrimPaint = Paint().apply {
        color = Color.TRANSPARENT
        style = Paint.Style.FILL
    }
    private val hubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val centerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        letterSpacing = 0.2f
    }
    private val unselectedRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val hoveredRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private val donutRect = RectF()
    private val iconDestRect = RectF()

    private var isDismissed = false
    private var canvasView: LauncherCanvasView? = null

    fun initialize(x: Float, y: Float) {
        val clampX = 150f * density
        val clampTop = 200f * density
        val clampBottom = 300f * density
        val cx = Math.max(clampX, Math.min(x, width - clampX))
        val cy = Math.max(clampTop, Math.min(y, height - clampBottom))
        touchController.initialize(cx, cy, x, y)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        initialize(touchX, touchY)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val p = parent as? ViewGroup
        if (p != null) {
            for (i in 0 until p.childCount) {
                val child = p.getChildAt(i)
                if (child is LauncherCanvasView) {
                    canvasView = child
                    break
                }
            }
        }

        val lifecycleOwner = context as? LifecycleOwner
        if (lifecycleOwner != null) {
            ThemeObserver.observe(context, lifecycleOwner.lifecycleScope) { newTokens ->
                tokens = newTokens
                invalidate()
            }
        }

        entranceAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 200
            interpolator = OvershootInterpolator(1.2f)
            addUpdateListener { anim ->
                entranceProgress = anim.animatedValue as Float
                invalidate()
            }
        }
        entranceAnimator?.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        entranceAnimator?.cancel()
    }

    private fun closeSheet() {
        if (isDismissed) return
        isDismissed = true
        branch.cancelPending(this)
        canvasView?.setBlurState(false)
        val mainContainer = (context as? MainActivity)?.findViewById<FrameLayout>(R.id.main_container)
        if (mainContainer != null) {
            for (i in 0 until mainContainer.childCount) {
                val child = mainContainer.getChildAt(i)
                if (child is WidgetOverlayLayout) {
                    child.setBlurState(false)
                }
                if (child is com.nexus.launcher.ui.dock.DockLayout) {
                    child.setBlurState(false)
                }
            }
        }
        animate().alpha(0f).setDuration(200).withEndAction {
            (parent as? ViewGroup)?.removeView(this)
            onDismiss()
        }.start()
    }

    override fun onDraw(canvas: Canvas) {
        // Full-screen scrim. Transparent in Frosted Glass, where the workspace blur behind is the
        // separation; the other styles have no blur, so it carries the context menu's 50% dim,
        // faded in with the menu rather than snapping on.
        scrimPaint.color = if (com.nexus.launcher.ui.glass.FrostedGlassEngine.isGlobalFrostedGlassEnabled) {
            Color.TRANSPARENT
        } else {
            Color.argb(
                (255 * com.nexus.launcher.ui.glass.FloatingSurfaces.WORKSPACE_DIM * entranceProgress).toInt(),
                0, 0, 0,
            )
        }
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrimPaint)

        val entranceScale = 0.8f + 0.2f * entranceProgress

        // Center Hub
        val hubRadius = 45f * density * entranceScale
        hubPaint.color = tokens.surface
        canvas.drawCircle(startX, startY, hubRadius, hubPaint)

        centerTextPaint.apply {
            color = tokens.textPrimary
            textSize = 12f * density * entranceScale
            typeface = CanvasTypographyHelper.getTypeface(context, Typeface.BOLD)
            alpha = (255 * entranceProgress).toInt().coerceIn(0, 255)
        }
        val centerOffset = (centerTextPaint.descent() + centerTextPaint.ascent()) / 2f
        canvas.drawText("NEXUS", startX, startY - centerOffset, centerTextPaint)

        // The Segmented Ring (The Donut)
        val strokeW = 90f * density * entranceScale
        val rectRadius = 95f * density * entranceScale
        donutRect.set(
            startX - rectRadius, startY - rectRadius,
            startX + rectRadius, startY + rectRadius
        )

        unselectedRingPaint.apply {
            strokeWidth = strokeW
            color = tokens.surface
        }
        hoveredRingPaint.strokeWidth = strokeW
        dividerPaint.apply {
            strokeWidth = 1f * density
            color = tokens.divider
        }

        for (i in 0 until 8) {
            val startAngle = (i * 45f) - 22.5f
            val sweepAngle = 45f
            val isHovered = (i == activeIndex)

            if (isHovered) {
                val highlightBgColor = if (i == 7 && isLayoutLocked) tokens.danger else tokens.textPrimary
                hoveredRingPaint.color = highlightBgColor
                canvas.save()
                val cxScale = startX + 95f * density * Math.cos(Math.toRadians(i * 45.0)).toFloat()
                val cyScale = startY + 95f * density * Math.sin(Math.toRadians(i * 45.0)).toFloat()
                canvas.scale(0.96f, 0.96f, cxScale, cyScale)
                canvas.drawArc(donutRect, startAngle, sweepAngle, false, hoveredRingPaint)
            } else {
                canvas.drawArc(donutRect, startAngle, sweepAngle, false, unselectedRingPaint)
            }

            // Draw Divider
            val divAngle = Math.toRadians(startAngle.toDouble())
            val innerR = (95f - 45f) * density * entranceScale
            val outerR = (95f + 45f) * density * entranceScale
            val startXLine = startX + innerR * Math.cos(divAngle).toFloat()
            val startYLine = startY + innerR * Math.sin(divAngle).toFloat()
            val stopXLine = startX + outerR * Math.cos(divAngle).toFloat()
            val stopYLine = startY + outerR * Math.sin(divAngle).toFloat()
            canvas.drawLine(startXLine, startYLine, stopXLine, stopYLine, dividerPaint)

            // Typography and Icon Alignment
            val textR = 95f * density * entranceScale
            val cx = startX + textR * Math.cos(Math.toRadians(i * 45.0)).toFloat()
            val cy = startY + textR * Math.sin(Math.toRadians(i * 45.0)).toFloat()

            val bmp = getIconBitmap(i)
            if (bmp != null) {
                val iconColor = if (isHovered) {
                    if (i == 7 && isLayoutLocked) Color.WHITE else tokens.surface
                } else {
                    tokens.textSecondary
                }
                iconPaint.alpha = (255 * entranceProgress).toInt().coerceIn(0, 255)
                iconPaint.colorFilter = PorterDuffColorFilter(iconColor, PorterDuff.Mode.SRC_IN)

                val iconScale = 1.2f
                val drawWidth = bmp.width * iconScale
                val drawHeight = bmp.height * iconScale
                iconDestRect.set(
                    cx - drawWidth / 2f,
                    cy - 16f * density - drawHeight / 2f,
                    cx + drawWidth / 2f,
                    cy - 16f * density + drawHeight / 2f
                )
                canvas.drawBitmap(bmp, null, iconDestRect, iconPaint)
            }

            val lbl = if (i == 7 && isLayoutLocked) lockedLabel else labels[i]
            val textColor = if (isHovered) {
                if (i == 7 && isLayoutLocked) Color.WHITE else tokens.surface
            } else {
                tokens.textSecondary
            }

            textPaint.apply {
                textSize = 13f * density * entranceScale
                typeface = CanvasTypographyHelper.getTypeface(context, Typeface.BOLD)
                color = textColor
                alpha = (255 * entranceProgress).toInt().coerceIn(0, 255)
            }
            canvas.drawText(lbl, cx, cy + 12f * density + 8f * density, textPaint)

            if (isHovered) {
                canvas.restore()
            }
        }
        branch.draw(canvas, tokens)
    }

    private fun playHaptic() {
        performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
    }

    private fun executeAction(index: Int) {
        when (index) {
            0 -> { onAddIcon(); closeSheet() } // Apps
            // Handoff like Wallpaper: clear sheet blur first, then picker owns blur
            1 -> { closeSheet(); onAddWidget() } // Widgets
            2 -> { closeSheet(); onWallpaper() } // close first so blur-off doesn't cancel the sheet's blur-on
            3 -> { closeSheet(); onManagePages() } // same handoff order as Wallpaper — blur must stay on
            4 -> { onAddFolder(); closeSheet() } // Folder
            5 -> { onAddShortcut(); closeSheet() } // Shortcut
            6 -> { onSettings(); closeSheet() } // Settings
            7 -> {
                isLayoutLocked = !isLayoutLocked
                onLockLayout(isLayoutLocked)
                invalidate()
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (branch.owns(event)) {
            if (event.actionMasked == MotionEvent.ACTION_UP) touchController.releaseGesture() else touchController.clearHover()
            val chosen = branch.onTouch(event) { playHaptic() }
            if (chosen != null) {
                closeSheet()
                chosen.action()
            }
            return true
        }
        val handled = touchController.onTouchEvent(event)
        branch.trackSlice(this, event, activeIndex, 45f, branches, startX, startY) { playHaptic() }
        return handled
    }
}

