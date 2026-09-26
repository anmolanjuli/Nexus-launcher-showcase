package com.nexus.launcher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.widget.LinearLayout

class NotificationPermissionSheet(
    context: Context,
    private val density: Float,
    private val accentColor: Int,
    private val onEnable: () -> Unit,
    private val onDismiss: () -> Unit
) : LinearLayout(context) {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = android.graphics.Color.parseColor("#F00E0C18")
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
        color = android.graphics.Color.parseColor("#28FFFFFF")
    }

    init {
        setWillNotDraw(false)
        background = null
        orientation = VERTICAL
        val pad = (24 * density).toInt()
        setPadding(pad, (20 * density).toInt(), pad, (32 * density).toInt())

        // Handle bar
        val handle = android.view.View(context).apply {
            layoutParams = LayoutParams(
                (40 * density).toInt(), (4 * density).toInt()).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                bottomMargin = (16 * density).toInt()
            }
            background = android.graphics.drawable
                .GradientDrawable().apply {
                cornerRadius = 2 * density
                setColor(android.graphics.Color
                    .parseColor("#33FFFFFF"))
            }
        }

        // Icon
        val iconView = android.widget.ImageView(context).apply {
            layoutParams = LayoutParams(
                (48 * density).toInt(),
                (48 * density).toInt()).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                bottomMargin = (16 * density).toInt()
            }
            setImageResource(android.R.drawable.ic_dialog_info)
            imageTintList = android.content.res.ColorStateList
                .valueOf(accentColor)
        }

        // Title
        val title = android.widget.TextView(context).apply {
            text = context.getString(com.nexus.launcher.R.string.notif_perm_title)
            setTextColor(android.graphics.Color.WHITE)
            textSize = 18f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (8 * density).toInt()
            }
        }

        // Body
        val body = android.widget.TextView(context).apply {
            text = context.getString(com.nexus.launcher.R.string.notif_perm_body)
            setTextColor(android.graphics.Color
                .parseColor("#B3FFFFFF"))
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (24 * density).toInt()
            }
        }

        // Enable button
        val enableBtn = android.widget.TextView(context).apply {
            text = context.getString(com.nexus.launcher.R.string.music_enable_notification_access)
            setTextColor(android.graphics.Color.parseColor("#0E0C18"))
            textSize = 15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            gravity = android.view.Gravity.CENTER
            background = android.graphics.drawable
                .GradientDrawable().apply {
                cornerRadius = 12 * density
                setColor(accentColor)
            }
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                (48 * density).toInt()).apply {
                bottomMargin = (12 * density).toInt()
            }
            setOnClickListener { onEnable() }
        }

        // Not Now button
        val notNowBtn = android.widget.TextView(context).apply {
            text = context.getString(com.nexus.launcher.R.string.notif_perm_not_now)
            setTextColor(android.graphics.Color
                .parseColor("#66FFFFFF"))
            textSize = 14f
            gravity = android.view.Gravity.CENTER
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                (44 * density).toInt())
            setOnClickListener { onDismiss() }
        }

        // Center handle
        val handleContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = android.view.Gravity.CENTER
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (16 * density).toInt()
            }
            addView(handle)
        }

        addView(handleContainer)
        addView(iconView)
        addView(title)
        addView(body)
        addView(enableBtn)
        addView(notNowBtn)
    }

    override fun onDraw(canvas: Canvas) {
        val cornerRadius = 20 * density
        val strokeHalf = borderPaint.strokeWidth / 2f
        val rect = RectF(strokeHalf, strokeHalf,
            width.toFloat() - strokeHalf,
            height.toFloat() - strokeHalf)
        canvas.drawRoundRect(rect, cornerRadius,
            cornerRadius, fillPaint)
        canvas.drawRoundRect(rect, cornerRadius,
            cornerRadius, borderPaint)
        super.onDraw(canvas)
    }
}
