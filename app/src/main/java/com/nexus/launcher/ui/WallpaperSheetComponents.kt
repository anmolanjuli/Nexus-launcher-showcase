@file:Suppress("DEPRECATION")
package com.nexus.launcher.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale

/** Shared component builders for Wallpaper Sheet UI. */
object WallpaperSheetComponents {

    fun buildApplyButton(
        context: Context,
        density: Float,
        tokens: NexusColorTokens = try { ThemeObserver.currentTokens(context) } catch (_: Exception) { NexusColorTokens.Dark },
        onClick: () -> Unit
    ): TextView {
        return TextView(context).apply {
            text = context.getString(R.string.action_apply)
            NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
            background = GradientDrawable().apply {
                setColor(tokens.surface)
                cornerRadius = 16f * density
                setStroke((1 * density).toInt().coerceAtLeast(1), tokens.divider)
            }
            setPadding((24 * density).toInt(), (10 * density).toInt(), (24 * density).toInt(), (10 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (32 * density).toInt()
            }
            alpha = 0f
            isEnabled = false
            setOnClickListener {
                it.performHapticFeedback(
                    HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
                onClick()
            }
        }
    }

    fun updateApplyButtonState(button: TextView, hasPending: Boolean) {
        if (hasPending && button.alpha == 0f) {
            button.animate().alpha(1f).setDuration(200).start()
        } else if (!hasPending && button.alpha > 0f) {
            button.animate().alpha(0f).setDuration(200).start()
        }
        button.isEnabled = hasPending
    }

    fun buildCard(
        context: Context,
        density: Float,
        tokens: NexusColorTokens = try { ThemeObserver.currentTokens(context) } catch (_: Exception) { NexusColorTokens.Dark }
    ): LinearLayout {
        val marginH = (10 * density).toInt()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(marginH, 0, marginH, 0) }
            val cardPadding = (16 * density).toInt()
            setPadding(cardPadding, cardPadding, cardPadding, cardPadding)
            background = GradientDrawable().apply {
                // Was flat opaque `tokens.surface` regardless of UI Style — converted to the
                // shared translucent-fill rule so this card actually reads as frosted glass over
                // the workspace blur WallpaperSheet activates behind it in Frosted Glass mode.
                val frostedTokens = com.nexus.launcher.ui.glass.FrostedGlassEngine.resolveFrostedTokens(tokens)
                val fillAlpha = (com.nexus.launcher.ui.glass.FrostedGlassEngine.sheetFillAlpha(1f, 0.70f) * 255f).toInt()
                setColor((frostedTokens.surface and 0x00FFFFFF) or (fillAlpha shl 24))
                cornerRadius = 24f * density
                setStroke((1 * density).toInt().coerceAtLeast(1), frostedTokens.border)
            }
            clipToOutline = true
        }
    }

    fun buildCloseButton(
        context: Context,
        density: Float,
        tokens: NexusColorTokens = try { ThemeObserver.currentTokens(context) } catch (_: Exception) { NexusColorTokens.Dark },
        onDismiss: () -> Unit
    ): ImageView {
        return ImageView(context).apply {
            setImageResource(R.drawable.ic_close)
            imageTintList = ColorStateList.valueOf(tokens.textSecondary)
            val pad = (6 * density).toInt()
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams((32 * density).toInt(), (32 * density).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(tokens.surfaceRaised)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                it.performHapticFeedback(
                    HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
                onDismiss()
            }
        }
    }
}
