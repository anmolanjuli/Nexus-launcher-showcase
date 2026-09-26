package com.nexus.launcher.ui.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

object WidgetDrawerRowView {

    fun createRow(
        context: Context,
        iconView: View,
        labelText: String,
        labelColor: Int,
        rightView: View? = null,
        onClick: (() -> Unit)? = null
    ): LinearLayout {
        val density = context.resources.displayMetrics.density
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                (com.nexus.launcher.ui.ContextMenuMetrics.ROW_HEIGHT_DP * density).toInt()
            )
            isClickable = onClick != null
            isFocusable = onClick != null
            val pad = (com.nexus.launcher.ui.ContextMenuMetrics.ROW_PADDING_H_DP * density).toInt()
            setPadding(pad, 0, pad, 0)
            if (onClick != null) {
                setOnClickListener { onClick() }
            }
        }
        iconView.layoutParams = LinearLayout.LayoutParams(
            (com.nexus.launcher.ui.ContextMenuMetrics.ICON_DP * density).toInt(),
            (com.nexus.launcher.ui.ContextMenuMetrics.ICON_DP * density).toInt()
        )
        row.addView(iconView)
        
        val label = TextView(context).apply {
            text = labelText
            com.nexus.launcher.typography.NexusTypeScale.menuItem.bindTo(this, labelColor)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (com.nexus.launcher.ui.ContextMenuMetrics.ICON_TEXT_GAP_DP * density).toInt()
            }
        }
        row.addView(label)
        
        if (rightView != null) {
            row.addView(rightView)
        }
        
        return row
    }
    
    fun createDivider(context: Context): View {
        val tokens = com.nexus.launcher.ui.NexusContextMenuDesignHelper.resolveTokens(context)
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 
                (1f * context.resources.displayMetrics.density).toInt()
            ).apply {
                marginStart = (16f * context.resources.displayMetrics.density).toInt()
                marginEnd = (16f * context.resources.displayMetrics.density).toInt()
            }
            setBackgroundColor(tokens.divider)
        }
    }

    fun createTextBtn(context: Context, label: String, onClick: () -> Unit): TextView {
        val density = context.resources.displayMetrics.density
        val tokens = try {
            com.nexus.launcher.theme.ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            com.nexus.launcher.theme.NexusColorTokens.Dark
        }
        return TextView(context).apply {
            text = label
            textSize = 18f
            setTextColor(tokens.textPrimary)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                (32f * density).toInt(), 
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
    }

    fun createArrow(context: Context, dx: Float, dy: Float, label: String, onClick: (Float, Float) -> Unit): View {
        val density = context.resources.displayMetrics.density
        val tokens = try {
            com.nexus.launcher.theme.ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            com.nexus.launcher.theme.NexusColorTokens.Dark
        }
        return TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            setTextColor(tokens.textPrimary)
            textSize = 24f
            background = object : android.graphics.drawable.Drawable() {
                val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tokens.surfaceRaised }
                override fun draw(canvas: Canvas) {
                    canvas.drawCircle(bounds.exactCenterX(), bounds.exactCenterY(), bounds.width() / 2f, p)
                }
                override fun setAlpha(alpha: Int) {}
                override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
                @Suppress("OVERRIDE_DEPRECATION")
                override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
            }
            isClickable = true
            isFocusable = true
            
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            var delay = 400L
            val runnable = object : Runnable {
                override fun run() {
                    val nudgeX = dx * 2f * density // 2dp nudge!
                    val nudgeY = dy * 2f * density // 2dp nudge!
                    performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    onClick(nudgeX, nudgeY)
                    delay = 80L // Accelerate
                    handler.postDelayed(this, delay)
                }
            }

            setOnTouchListener { v, event ->
                when (event.actionMasked) {
                    android.view.MotionEvent.ACTION_DOWN -> {
                        delay = 400L
                        val nudgeX = dx * 2f * density
                        val nudgeY = dy * 2f * density
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                        onClick(nudgeX, nudgeY) // Fire initial nudge immediately
                        handler.postDelayed(runnable, delay)
                        true
                    }
                    android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                        handler.removeCallbacks(runnable)
                        true
                    }
                    else -> false
                }
            }
        }
    }

    fun createLayersIcon(context: Context): View {
        val tokens = try {
            com.nexus.launcher.theme.ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            com.nexus.launcher.theme.NexusColorTokens.Dark
        }
        return object : View(context) {
            val density = context.resources.displayMetrics.density
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = tokens.textPrimary
                style = Paint.Style.STROKE
                strokeWidth = 1.5f * density
                strokeJoin = Paint.Join.ROUND
            }
            override fun onDraw(canvas: Canvas) {
                val w = width.toFloat(); val h = height.toFloat()
                val cx = w / 2f; val cy = h / 2f
                val rw = w * 0.6f; val rh = h * 0.35f
                val path = android.graphics.Path()
                path.moveTo(cx, cy - rh * 0.5f)
                path.lineTo(cx + rw * 0.5f, cy)
                path.lineTo(cx, cy + rh * 0.5f)
                path.lineTo(cx - rw * 0.5f, cy)
                path.close()
                path.moveTo(cx - rw * 0.5f, cy + rh * 0.5f)
                path.lineTo(cx, cy + rh)
                path.lineTo(cx + rw * 0.5f, cy + rh * 0.5f)
                canvas.drawPath(path, p)
            }
        }
    }

    fun createGridIcon(context: Context): View {
        val tokens = try {
            com.nexus.launcher.theme.ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            com.nexus.launcher.theme.NexusColorTokens.Dark
        }
        return object : View(context) {
            val density = context.resources.displayMetrics.density
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = tokens.textPrimary
                style = Paint.Style.STROKE
                strokeWidth = 1.5f * density
                strokeJoin = Paint.Join.ROUND
            }
            override fun onDraw(canvas: Canvas) {
                val s = 14f * density
                val cx = width / 2f; val cy = height / 2f
                val r = android.graphics.RectF(cx - s/2, cy - s/2, cx + s/2, cy + s/2)
                canvas.drawRect(r, p)
                canvas.drawLine(r.left, cy, r.right, cy, p)
                canvas.drawLine(cx, r.top, cx, r.bottom, p)
            }
        }
    }

    /**
     * The Elevation row both long-press menus show: layers icon, label, and a ▼ Z ▲ stepper.
     *
     * One builder for both menus — they had drifted (only Mosaic capped at [maxZ]). The value is
     * caption-size with tight margins so the stepper stays narrow enough for "Elevation" to sit
     * on one line beside it; at body size the label was squeezed onto two.
     */
    fun createElevationRow(
        context: Context,
        initialZ: Int,
        maxZ: Int,
        onChanged: (Int) -> Unit,
    ): LinearLayout {
        val tokens = com.nexus.launcher.ui.NexusContextMenuDesignHelper.resolveTokens(context)
        val density = context.resources.displayMetrics.density
        var z = initialZ
        val value = TextView(context).apply {
            text = context.getString(com.nexus.launcher.R.string.menu_layer_value, z)
            com.nexus.launcher.typography.NexusTypeScale.caption.bindTo(this, tokens.textSecondary)
            maxLines = 1
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                leftMargin = (2f * density).toInt()
                rightMargin = (2f * density).toInt()
            }
        }
        fun step(to: Int) {
            if (to < 0 || to > maxZ) return
            value.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
            z = to
            value.text = context.getString(com.nexus.launcher.R.string.menu_layer_value, z)
            onChanged(z)
        }
        val stepper = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT,
            )
            addView(createTextBtn(context, "▼") { step(z - 1) })
            addView(value)
            addView(createTextBtn(context, "▲") { step(z + 1) })
        }
        return createRow(
            context, createLayersIcon(context),
            context.getString(com.nexus.launcher.R.string.menu_elevation), tokens.textPrimary, stepper,
        )
    }

    /**
     * The panel both long-press menus (a widget's and a Mosaic's) sit in.
     *
     * It frosts itself — a [com.nexus.launcher.ui.glass.FrostedPanelLayout] blurs the wallpaper
     * slice under its own bounds, over an opaque base — instead of being a translucent fill that
     * relied on the whole workspace being blurred behind it. That workspace blur is gone
     * (2026-09-24): during a move or resize the surrounding widgets, folders and icons have to
     * stay sharp, because comparing sizes against them is the point. One function, so the two
     * menus cannot drift apart.
     */
    fun createDrawerPanel(context: Context): com.nexus.launcher.ui.glass.FrostedPanelLayout {
        val tokens = com.nexus.launcher.ui.NexusContextMenuDesignHelper.resolveTokens(context)
        val density = context.resources.displayMetrics.density
        return com.nexus.launcher.ui.glass.FrostedPanelLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setCornerRadiusPx(16f * density)
            // Frost the real icons and widgets behind the menu, not only the wallpaper — so it
            // reads like the folder and icon menus, whose fill sits over a blurred workspace.
            frostWorkspace = true
            applyStyle(tokens, com.nexus.launcher.ui.glass.FrostedPanelLayout.Density.SHEET)
            foreground = drawerEdge(tokens, density)
        }
    }

    /** The short rule along the panel's top edge, which the old drawn background carried. */
    private fun drawerEdge(
        tokens: com.nexus.launcher.theme.NexusColorTokens,
        density: Float,
    ): android.graphics.drawable.Drawable = object : android.graphics.drawable.Drawable() {
        private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = tokens.textSecondary
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
            strokeCap = Paint.Cap.ROUND
        }
        private val inset = 32f * density

        override fun draw(canvas: Canvas) {
            val r = bounds
            canvas.drawLine(r.left + inset, r.top.toFloat(), r.right - inset, r.top.toFloat(), edgePaint)
        }
        override fun setAlpha(alpha: Int) {}
        override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {}
        @Suppress("OVERRIDE_DEPRECATION")
        override fun getOpacity() = android.graphics.PixelFormat.TRANSLUCENT
    }
}
