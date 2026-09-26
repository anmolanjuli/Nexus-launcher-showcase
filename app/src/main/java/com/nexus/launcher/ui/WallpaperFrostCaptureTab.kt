package com.nexus.launcher.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.nexus.launcher.theme.BackgroundLayerMode
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import kotlinx.coroutines.launch

/**
 * "System" wallpaper tab content: explains why frost needs a one-time capture for an
 * external/live wallpaper (see [WallpaperFrostCapture]'s doc comment) and lets the user trigger
 * it or clear an existing one.
 */
object WallpaperFrostCaptureTab {

    fun build(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        density: Float,
        tokens: NexusColorTokens,
        /** The enclosing sheet itself — must be hidden during capture too, since it's part of
         *  the same window and would otherwise get baked into the "clean" wallpaper capture. */
        sheetView: () -> View?
    ): View {
        // MediaProjection's consent flow needs a ComponentActivity (for activityResultRegistry) —
        // matches WallpaperGallerySource.launchPicker's own launcher pattern.
        val activity = lifecycleOwner as? androidx.activity.ComponentActivity

        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (16 * density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        val explainer = TextView(context).apply {
            text = context.getString(com.nexus.launcher.R.string.frost_tab_explainer)
            NexusTypeScale.body.bindTo(this, tokens.textSecondary)
            setPadding(0, 0, 0, (12 * density).toInt())
        }
        column.addView(explainer)

        // Independent of whether a capture exists at all: if Background Layer is set to
        // "Theme Color" (in Theme Settings), HomeScreenFrameCache.drawWallpaperBackdrop returns
        // a flat theme-derived gradient/color for EVERY frosted surface before it ever looks at
        // wallpaperType — the capture below would be saved and correct, but silently never used,
        // which reads as "frost isn't working" with no visible explanation why. Surface that
        // here directly rather than leaving it as a silent, non-obvious interaction between two
        // separate settings.
        val backgroundLayerWarning = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            background = GradientDrawable().apply {
                setColor((tokens.danger and 0x00FFFFFF) or (0x22 shl 24))
                cornerRadius = 12 * density
                setStroke((1 * density).toInt().coerceAtLeast(1), tokens.danger)
            }
            val pad = (12 * density).toInt()
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (12 * density).toInt() }
        }
        backgroundLayerWarning.addView(TextView(context).apply {
            text = context.getString(com.nexus.launcher.R.string.frost_tab_theme_color_warning)
            NexusTypeScale.body.bindTo(this, tokens.textPrimary)
            setPadding(0, 0, 0, (8 * density).toInt())
        })
        val switchLayerButton = TextView(context).apply {
            text = context.getString(com.nexus.launcher.R.string.frost_tab_switch_layer)
            gravity = Gravity.CENTER
            NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
            background = GradientDrawable().apply {
                setColor(tokens.surfaceRaised)
                cornerRadius = 10 * density
                setStroke((1 * density).toInt().coerceAtLeast(1), tokens.divider)
            }
            val padH = (14 * density).toInt()
            val padV = (10 * density).toInt()
            setPadding(padH, padV, padH, padV)
            isClickable = true
            isFocusable = true
        }
        backgroundLayerWarning.addView(switchLayerButton)
        column.addView(backgroundLayerWarning)

        // Independent gate #2: the global "Frosted Glass" toggle (Nexus Settings > Appearance).
        // With it off, widgets/boxes/folders never enter glass mode at all regardless of any
        // wallpaper capture — same "silently does nothing" trap as the Background Layer setting.
        val globalToggleWarning = TextView(context).apply {
            text = context.getString(com.nexus.launcher.R.string.frost_tab_disabled_warning)
            NexusTypeScale.body.bindTo(this, tokens.danger)
            visibility = View.GONE
            setPadding(0, 0, 0, (12 * density).toInt())
        }
        column.addView(globalToggleWarning)

        fun refreshBackgroundLayerWarning() {
            val isThemeColor = ThemeObserver.currentBackgroundLayerMode(context) == BackgroundLayerMode.THEME_COLOR
            backgroundLayerWarning.visibility = if (isThemeColor) View.VISIBLE else View.GONE
            globalToggleWarning.visibility =
                if (!com.nexus.launcher.ui.glass.FrostedGlassEngine.isGlobalFrostedGlassEnabled) View.VISIBLE else View.GONE
        }
        refreshBackgroundLayerWarning()
        switchLayerButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
            (lifecycleOwner as? androidx.activity.ComponentActivity)?.lifecycleScope?.launch {
                ThemeObserver.setBackgroundLayerMode(context, BackgroundLayerMode.WALLPAPER)
                refreshBackgroundLayerWarning()
            }
        }

        val statusText = TextView(context).apply {
            NexusTypeScale.caption.bindTo(this, tokens.textSecondary)
            setPadding(0, 0, 0, (10 * density).toInt())
        }
        column.addView(statusText)

        fun refreshStatus() {
            statusText.text = if (activity != null && WallpaperFrostCapture.hasCapture(activity)) {
                context.getString(com.nexus.launcher.R.string.frost_capture_status_set)
            } else {
                context.getString(com.nexus.launcher.R.string.frost_capture_status_unset)
            }
        }
        refreshStatus()

        val captureButton = TextView(context).apply {
            text = context.getString(com.nexus.launcher.R.string.frost_capture_title)
            gravity = Gravity.CENTER
            NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
            background = GradientDrawable().apply {
                setColor(tokens.surfaceRaised)
                cornerRadius = 14 * density
                setStroke((1 * density).toInt().coerceAtLeast(1), tokens.divider)
            }
            val padH = (16 * density).toInt()
            val padV = (12 * density).toInt()
            setPadding(padH, padV, padH, padV)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (8 * density).toInt() }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                if (activity == null) return@setOnClickListener
                text = context.getString(com.nexus.launcher.R.string.frost_capturing)
                isEnabled = false
                val extra = listOfNotNull(sheetView())
                WallpaperFrostCapture.captureCleanWallpaper(activity, extra) { success ->
                    text = context.getString(com.nexus.launcher.R.string.frost_capture_title)
                    isEnabled = true
                    statusText.text = if (success) {
                        context.getString(com.nexus.launcher.R.string.frost_capture_status_set)
                    } else {
                        context.getString(com.nexus.launcher.R.string.frost_capture_failed)
                    }
                }
            }
        }
        column.addView(captureButton)

        val clearButton = TextView(context).apply {
            text = context.getString(com.nexus.launcher.R.string.frost_capture_clear)
            gravity = Gravity.CENTER
            NexusTypeScale.body.bindTo(this, tokens.textSecondary)
            setPadding(0, (8 * density).toInt(), 0, 0)
            isClickable = true
            isFocusable = true
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                if (activity == null) return@setOnClickListener
                WallpaperFrostCapture.clearCapture(activity)
                refreshStatus()
            }
        }
        column.addView(clearButton)

        return column
    }
}
