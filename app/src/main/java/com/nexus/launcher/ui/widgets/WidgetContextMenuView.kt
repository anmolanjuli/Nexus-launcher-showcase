package com.nexus.launcher.ui.widgets

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import com.nexus.launcher.R

class WidgetContextMenuView(
    context: Context,
    private var widgetRect: Rect,
    private val accentColor: Int,
    private val initialZIndex: Int,
    private val initialPaddingEnabled: Boolean,
    private val onZIndexChanged: (Int) -> Unit,
    private val onReplaceClicked: () -> Unit,
    private val onRemoveClicked: () -> Unit,
    private val onModeChanged: (isMoveMode: Boolean) -> Unit,
    private val onResizeNudgeModeChanged: (isResizeNudgeMode: Boolean) -> Unit,
    private val onPaddingToggled: (Boolean) -> Unit,
    private val onSettingsClicked: (() -> Unit)?,
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
    private var isNearBottom = false

    init {
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        setWillNotDraw(false)
        setOnClickListener {
            dismiss()
        }
        isFocusable = true
        isFocusableInTouchMode = true

        buildDrawerRows()

        isNearBottom = WidgetContextMenuLayout.attachDrawer(
            context, this, drawerContainer, drawerLayout, widgetRect, density
        )
    }

    private fun buildDrawerRows() {
        moveRowView = WidgetDrawerRowView.createRow(
            context,
            ImageView(context).apply {
                setImageResource(R.drawable.ic_move)
                setColorFilter(tokens.textPrimary)
            },
            context.getString(com.nexus.launcher.R.string.menu_move), tokens.textPrimary, null, {
                performHapticFeedback(if (android.os.Build.VERSION.SDK_INT >= 23) android.view.HapticFeedbackConstants.CONTEXT_CLICK else android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                toggleMoveMode()
            }
        )
        drawerLayout.addView(moveRowView)
        drawerLayout.addView(WidgetDrawerRowView.createDivider(context))

        resizeRowView = WidgetDrawerRowView.createRow(
            context,
            ImageView(context).apply {
                setImageResource(R.drawable.ic_resize)
                setColorFilter(tokens.textPrimary)
            },
            context.getString(com.nexus.launcher.R.string.action_resize), tokens.textPrimary, null, {
                performHapticFeedback(if (android.os.Build.VERSION.SDK_INT >= 23) android.view.HapticFeedbackConstants.CONTEXT_CLICK else android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                toggleResizeNudgeMode()
            }
        )
        drawerLayout.addView(resizeRowView)
        drawerLayout.addView(WidgetDrawerRowView.createDivider(context))

        // Widgets have never had an upper limit; Mosaic caps at 50.
        drawerLayout.addView(
            WidgetDrawerRowView.createElevationRow(context, initialZIndex, Int.MAX_VALUE, onZIndexChanged)
        )
        drawerLayout.addView(WidgetDrawerRowView.createDivider(context))

        val replaceRowView = WidgetDrawerRowView.createRow(
            context,
            ImageView(context).apply {
                setImageResource(R.drawable.ic_replace)
                setColorFilter(tokens.textPrimary)
            },
            context.getString(com.nexus.launcher.R.string.menu_replace), tokens.textPrimary, null, {
                performHapticFeedback(if (android.os.Build.VERSION.SDK_INT >= 23) android.view.HapticFeedbackConstants.CONTEXT_CLICK else android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                onReplaceClicked()
                dismiss()
            }
        )
        drawerLayout.addView(replaceRowView)
        drawerLayout.addView(WidgetDrawerRowView.createDivider(context))

        val paddingIcon = ImageView(context).apply {
            setImageResource(R.drawable.ic_resize)
            setColorFilter(tokens.textPrimary)
        }
        val paddingSwitch = Switch(context).apply {
            isChecked = initialPaddingEnabled
            setOnCheckedChangeListener { _, isChecked ->
                onPaddingToggled(isChecked)
            }
        }
        val paddingRow = WidgetDrawerRowView.createRow(
            context,
            paddingIcon,
            context.getString(com.nexus.launcher.R.string.menu_padding),
            tokens.textPrimary,
            paddingSwitch,
            {
                paddingSwitch.isChecked = !paddingSwitch.isChecked
            }
        )
        drawerLayout.addView(paddingRow)
        drawerLayout.addView(WidgetDrawerRowView.createDivider(context))

        if (onSettingsClicked != null) {
            val settingsRow = WidgetDrawerRowView.createRow(
                context,
                ImageView(context).apply {
                    setImageResource(R.drawable.ic_settings)
                    setColorFilter(tokens.textPrimary)
                },
                context.getString(com.nexus.launcher.R.string.menu_settings), tokens.textPrimary, null, {
                    performHapticFeedback(if (android.os.Build.VERSION.SDK_INT >= 23) android.view.HapticFeedbackConstants.CONTEXT_CLICK else android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    onSettingsClicked.invoke()
                    dismiss()
                }
            )
            drawerLayout.addView(settingsRow)
            drawerLayout.addView(WidgetDrawerRowView.createDivider(context))
        }

        drawerLayout.addView(WidgetDrawerRowView.createRow(
            context,
            ImageView(context).apply {
                setImageResource(R.drawable.ic_remove)
                setColorFilter(tokens.danger)
            },
            context.getString(com.nexus.launcher.R.string.action_remove), tokens.danger, null, {
                performHapticFeedback(if (android.os.Build.VERSION.SDK_INT >= 23) android.view.HapticFeedbackConstants.CONTEXT_CLICK else android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                onRemoveClicked()
                dismiss()
            }
        ))
    }

    private fun toggleMoveMode() {
        if (isMoveMode) return
        exitResizeNudgeMode()
        isMoveMode = true; onModeChanged(isMoveMode)
        moveRowView?.setBackgroundColor(tokens.surfaceRaised)
        drawerLayout.animate().alpha(0f).translationY(drawerLayout.height.toFloat()).setDuration(200).withEndAction { drawerContainer.visibility = View.GONE }.start()
    }

    fun exitMoveMode() {
        if (!isMoveMode) return
        isMoveMode = false; onModeChanged(isMoveMode)
        moveRowView?.setBackgroundColor(Color.TRANSPARENT)
        drawerContainer.visibility = View.VISIBLE
        drawerLayout.animate().alpha(1f).translationY(0f).setDuration(200).setInterpolator(DecelerateInterpolator()).start()
    }

    private fun toggleResizeNudgeMode() {
        if (isResizeNudgeMode) return
        exitMoveMode()
        isResizeNudgeMode = true; onResizeNudgeModeChanged(isResizeNudgeMode)
        resizeRowView?.setBackgroundColor(tokens.surfaceRaised)
        drawerLayout.animate().alpha(0f).translationY(drawerLayout.height.toFloat()).setDuration(200).withEndAction { drawerContainer.visibility = View.GONE }.start()
    }

    fun exitResizeNudgeMode() {
        if (!isResizeNudgeMode) return
        isResizeNudgeMode = false; onResizeNudgeModeChanged(isResizeNudgeMode)
        resizeRowView?.setBackgroundColor(Color.TRANSPARENT)
        drawerContainer.visibility = View.VISIBLE
        drawerLayout.animate().alpha(1f).translationY(0f).setDuration(200).setInterpolator(DecelerateInterpolator()).start()
    }

    fun updateForNewWidgetBounds(newRect: android.graphics.Rect) {
        widgetRect = newRect
        invalidate() // redraws halo at new bounds
        isNearBottom = WidgetContextMenuLayout.reposition(context, drawerContainer, newRect, density)
    }

    private var pulseAnimator: ValueAnimator? = null

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        requestFocus()
        
        drawerLayout.translationY = if (isNearBottom) (100f * density) else -(100f * density)
        drawerLayout.post {
            drawerLayout.translationY = if (isNearBottom) drawerLayout.height.toFloat() else -drawerLayout.height.toFloat()
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
        
        val targetY = if (isNearBottom) drawerLayout.height.toFloat() else -drawerLayout.height.toFloat()
        drawerLayout.animate()
            .translationY(targetY)
            .setDuration(200)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                onDismiss()
            }
            .start()
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if (event.keyCode == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_UP) {
            dismiss()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        
        haloPaint.alpha = (haloAlpha * 255).toInt()
        val r = 16f * density
        val rectF = RectF(
            widgetRect.left.toFloat(),
            widgetRect.top.toFloat(),
            widgetRect.right.toFloat(),
            widgetRect.bottom.toFloat()
        )
        canvas.drawRoundRect(rectF, r, r, haloPaint)
    }
}
