@file:Suppress("DEPRECATION")
package com.nexus.launcher.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.settings.NexusHueWheelView

/** Full-screen frosted overlay for circular hue picker. */
class WallpaperColorPickerExpanded(
    context: Context,
    private val density: Float,
    initialHue: Float,
    private val onHueChanged: (Float) -> Unit,
    private val onDismiss: () -> Unit
) : FrameLayout(context) {

    private val tokens: NexusColorTokens = try {
        ThemeObserver.currentTokens(context)
    } catch (_: Exception) {
        NexusColorTokens.Dark
    }

    private var currentHue = initialHue

    private val colorWheel = NexusHueWheelView(
        context = context,
        density = density,
        initialHue = initialHue,
        onHueChanged = { hue ->
            currentHue = hue
            onHueChanged(hue)
        }
    )

    init {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        val blurBg = View(context).apply {
            layoutParams = LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        addView(blurBg, 0)

        post {
            val host = this.parent as? ViewGroup ?: return@post
            val w = host.width
            val h = host.height
            if (w <= 0 || h <= 0) return@post

            val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
            val c = android.graphics.Canvas(bmp)

            val oldAlpha = this.alpha
            this.alpha = 0f
            host.draw(c)
            this.alpha = oldAlpha

            val content = if (Build.VERSION.SDK_INT >= 31) bmp else WallpaperSheetFrost.fallbackBlur(bmp, 12)

            blurBg.background = LayerDrawable(arrayOf(
                BitmapDrawable(resources, content),
                ColorDrawable(Color.argb(184, 0, 0, 0))
            ))

            if (Build.VERSION.SDK_INT >= 31) {
                blurBg.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(30f * density, 30f * density, android.graphics.Shader.TileMode.CLAMP))
            }
        }

        isClickable = true
        isFocusable = true

        setOnClickListener {
            performHapticFeedback(
                HapticFeedbackConstants.VIRTUAL_KEY,
                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
            )
            onDismiss()
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable().apply {
                setColor(tokens.surface)
                cornerRadius = 24f * density
                setStroke((1 * density).toInt().coerceAtLeast(1), tokens.divider)
            }
            val pad = (24 * density).toInt()
            setPadding(pad, pad, pad, pad)
            isClickable = true
        }

        val title = TextView(context).apply {
            text = context.getString(com.nexus.launcher.R.string.wallpaper_select_tint)
            NexusTypeScale.title.bindTo(this, tokens.textPrimary)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (24 * density).toInt() }
        }

        val doneButton = TextView(context).apply {
            text = context.getString(com.nexus.launcher.R.string.action_done)
            NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(tokens.surfaceRaised)
                cornerRadius = 16f * density
                setStroke((1 * density).toInt().coerceAtLeast(1), tokens.divider)
            }
            setPadding((32 * density).toInt(), (12 * density).toInt(), (32 * density).toInt(), (12 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (24 * density).toInt() }

            setOnClickListener {
                it.performHapticFeedback(
                    HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
                onDismiss()
            }
        }

        container.addView(title)
        container.addView(colorWheel, LinearLayout.LayoutParams(
            (200 * density).toInt(), (200 * density).toInt()
        ))
        container.addView(doneButton)

        addView(container, LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.CENTER
        })
    }
}
