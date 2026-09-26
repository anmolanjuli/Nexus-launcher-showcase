package com.nexus.launcher.ui.widgets.mosaic

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.ui.widgets.WidgetDrawerRowView

/**
 * Widget-style halo + drawer for Living Mosaic edit
 * (parity with [com.nexus.launcher.ui.widgets.WidgetContextMenuView]).
 */
class LivingMosaicContextMenuView(
    context: Context,
    private var widgetRect: Rect,
    private val accentColor: Int,
    private val initialZIndex: Int,
    private val underChildCap: Boolean,
    private val onMosaicSettings: () -> Unit,
    private val onAddWidget: () -> Unit,
    private val onRemoveClicked: () -> Unit,
    private val onZIndexChanged: (Int) -> Unit,
    private val onModeChanged: (isMoveMode: Boolean) -> Unit,
    private val onResizeNudgeModeChanged: (isResizeNudgeMode: Boolean) -> Unit,
    private val onDismissStarted: () -> Unit,
    private val onDismiss: () -> Unit
) : FrameLayout(context) {

    private val density = resources.displayMetrics.density
    private val tokens = com.nexus.launcher.ui.NexusContextMenuDesignHelper.resolveTokens(context)
    private var haloAlpha = 0f
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = tokens.textSecondary
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }

    private val drawerContainer = FrameLayout(context)
    private val drawerLayout = WidgetDrawerRowView.createDrawerPanel(context)
    private var isMoveMode = false
    private var isResizeNudgeMode = false
    private var moveRowView: View? = null
    private var resizeRowView: View? = null
    private var pulseAnimator: ValueAnimator? = null

    init {
        setWillNotDraw(false)
        setOnClickListener { dismiss() }
        isFocusable = true
        isFocusableInTouchMode = true
        buildDrawerRows()
        LivingMosaicContextMenuLayout.attachDrawer(
            context, this, drawerContainer, drawerLayout, widgetRect, density
        )
    }

    private fun buildDrawerRows() {
        val addLabel = if (underChildCap) context.getString(com.nexus.launcher.R.string.mosaic_add_widget) else context.getString(com.nexus.launcher.R.string.mosaic_add_widget_full)
        addActionRow(R.drawable.ic_add, addLabel, tokens.textPrimary) {
            onAddWidget(); dismiss()
        }
        addActionRow(R.drawable.ic_settings, context.getString(com.nexus.launcher.R.string.menu_settings), tokens.textPrimary) {
            onMosaicSettings(); dismiss()
        }
        moveRowView = addActionRow(R.drawable.ic_move, context.getString(com.nexus.launcher.R.string.menu_move), tokens.textPrimary) { toggleMoveMode() }
        resizeRowView = addActionRow(R.drawable.ic_resize, context.getString(com.nexus.launcher.R.string.action_resize), tokens.textPrimary) {
            toggleResizeNudgeMode()
        }
        addElevationRow()
        drawerLayout.addView(
            WidgetDrawerRowView.createRow(
                context,
                ImageView(context).apply {
                    setImageResource(R.drawable.ic_remove)
                    setColorFilter(tokens.danger)
                },
                context.getString(com.nexus.launcher.R.string.action_remove), tokens.danger, null
            ) {
                hapticClick()
                onRemoveClicked()
                dismiss()
            }
        )
    }

    private fun addActionRow(iconRes: Int, label: String, color: Int, onClick: () -> Unit): View {
        val row = WidgetDrawerRowView.createRow(
            context,
            ImageView(context).apply {
                setImageResource(iconRes)
                setColorFilter(color)
            },
            label, color, null
        ) {
            hapticClick()
            onClick()
        }
        drawerLayout.addView(row)
        drawerLayout.addView(WidgetDrawerRowView.createDivider(context))
        return row
    }

    private fun addElevationRow() {
        drawerLayout.addView(
            WidgetDrawerRowView.createElevationRow(context, initialZIndex, MAX_Z_INDEX, onZIndexChanged)
        )
        drawerLayout.addView(WidgetDrawerRowView.createDivider(context))
    }

    private fun hapticClick() {
        performHapticFeedback(
            if (android.os.Build.VERSION.SDK_INT >= 23)
                android.view.HapticFeedbackConstants.CONTEXT_CLICK
            else android.view.HapticFeedbackConstants.VIRTUAL_KEY
        )
    }

    private fun toggleMoveMode() {
        if (isMoveMode) return
        exitResizeNudgeMode()
        isMoveMode = true
        onModeChanged(true)
        moveRowView?.setBackgroundColor(tokens.surfaceRaised)
        hideDrawer()
    }

    fun exitMoveMode() {
        if (!isMoveMode) return
        isMoveMode = false
        onModeChanged(false)
        moveRowView?.setBackgroundColor(Color.TRANSPARENT)
        showDrawer()
    }

    private fun toggleResizeNudgeMode() {
        if (isResizeNudgeMode) return
        exitMoveMode()
        isResizeNudgeMode = true
        onResizeNudgeModeChanged(true)
        resizeRowView?.setBackgroundColor(tokens.surfaceRaised)
        hideDrawer()
    }

    fun exitResizeNudgeMode() {
        if (!isResizeNudgeMode) return
        isResizeNudgeMode = false
        onResizeNudgeModeChanged(false)
        resizeRowView?.setBackgroundColor(Color.TRANSPARENT)
        showDrawer()
    }

    private fun hideDrawer() {
        drawerLayout.animate()
            .alpha(0f)
            .translationY(drawerLayout.height.toFloat())
            .setDuration(200)
            .withEndAction { drawerContainer.visibility = View.GONE }
            .start()
    }

    private fun showDrawer() {
        drawerContainer.visibility = View.VISIBLE
        drawerLayout.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(200)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    fun updateForNewWidgetBounds(newRect: Rect) {
        widgetRect = newRect
        invalidate()
        LivingMosaicContextMenuLayout.reposition(context, drawerContainer, newRect, density)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        requestFocus()
        drawerLayout.translationY = -(100f * density)
        drawerLayout.post {
            drawerLayout.translationY = -drawerLayout.height.toFloat()
            drawerLayout.animate()
                .translationY(0f)
                .setDuration(250)
                .setInterpolator(DecelerateInterpolator())
                .start()
        }
        pulseAnimator = ValueAnimator.ofFloat(0.3f, 1f).apply {
            duration = 1200
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { anim ->
                haloAlpha = anim.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        pulseAnimator?.cancel()
    }

    fun dismiss() {
        pulseAnimator?.cancel()
        haloAlpha = 0f
        invalidate()
        onDismissStarted()
        drawerLayout.animate()
            .translationY(-drawerLayout.height.toFloat())
            .setDuration(200)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { onDismiss() }
            .start()
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.keyCode == android.view.KeyEvent.KEYCODE_BACK &&
            event.action == android.view.KeyEvent.ACTION_UP
        ) {
            dismiss()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        haloPaint.alpha = (haloAlpha * 255).toInt()
        val r = 16f * density
        canvas.drawRoundRect(
            RectF(
                widgetRect.left.toFloat(), widgetRect.top.toFloat(),
                widgetRect.right.toFloat(), widgetRect.bottom.toFloat()
            ),
            r, r, haloPaint
        )
    }

    private companion object {
        /** Highest elevation a Mosaic can be stepped to (widgets have no cap). */
        const val MAX_Z_INDEX = 50
    }
}
