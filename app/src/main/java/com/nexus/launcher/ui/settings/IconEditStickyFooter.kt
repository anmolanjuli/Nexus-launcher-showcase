package com.nexus.launcher.ui.settings

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.ui.settings.views.NexusSettingsButtons

/** Sticky Reset + Apply footer matching the Settings-page chrome style. */
object IconEditStickyFooter {

    class Result(
        val container: LinearLayout,
        val resetBtn: TextView,
        val applyBtn: TextView
    )

    fun build(
        context: Context,
        density: Float,
        onReset: () -> Unit,
        onApply: () -> Unit
    ): Result {
        val tokens = try {
            ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            val padH = (20 * density).toInt()
            val padV = (12 * density).toInt()
            setPadding(padH, padV, padH, padV)
            setBackgroundColor(tokens.bg)
        }

        val resetBtn = NexusSettingsButtons.buildResetButton(context, tokens, density, onClick = onReset)
        val applyBtn = NexusSettingsButtons.buildApplyButton(context, tokens, density, onClick = onApply).also {
            it.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        container.addView(resetBtn)
        container.addView(applyBtn)
        return Result(container, resetBtn, applyBtn)
    }
}

