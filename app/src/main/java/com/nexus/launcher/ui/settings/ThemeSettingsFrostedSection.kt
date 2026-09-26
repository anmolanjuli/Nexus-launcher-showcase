package com.nexus.launcher.ui.settings

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.nexus.launcher.R
import com.nexus.launcher.premium.PremiumFeature
import com.nexus.launcher.premium.PremiumGate
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.ui.glass.FrostedGlassEngine
import com.nexus.launcher.ui.settings.views.NexusNavRow
import com.nexus.launcher.ui.settings.views.NexusSegmentedRow

/**
 * "UI Style" section — Neumorphism / Default / Frosted Glass, presented as three named options
 * rather than an on/off toggle (per explicit request: these are distinct visual styles, not a
 * feature being switched on/off).
 * - Neumorphism: raised Soft UI plates with shadow/highlight/bevel.
 * - Default: the same outer shape/margins as Neumorphism, but a flat solid fill in the same color
 *   — no raised shadow effect, no glass.
 * - Frosted Glass: hardware blur and acrylic glassmorphism.
 * Also hosts — when Frosted Glass is selected — the entry point for the one-time wallpaper
 * capture that frost needs on an external/live wallpaper. Modular component to keep
 * [ThemeSettingsFragment] under the 400-line hard limit.
 */
class ThemeSettingsFrostedSection(
    context: Context,
    onUiStyleModeChanged: (String) -> Unit,
    /** The actual capture only runs inside MainActivity's own window (it hides that activity's
     *  canvas/widget-overlay/dock and captures the composite) — this settings screen can only
     *  hand off to it, not perform the capture itself. */
    onCaptureWallpaperRequested: () -> Unit
) {
    val container: LinearLayout

    /** Last accepted style, so a refused selection can be painted back. */
    private var currentStyleValue = FrostedGlassEngine.UI_STYLE_DEFAULT
    private val pillBackground = GradientDrawable()
    private val styleRow: NexusSegmentedRow
    private val previewView: UiStylePreviewView
    private val captureDivider: View
    private val captureRow: NexusNavRow

    init {
        val dp = context.resources.displayMetrics.density
        pillBackground.cornerRadius = 16f * dp

        styleRow = NexusSegmentedRow(context).apply {
            configure(
                label = context.getString(R.string.settings_ui_option),
                options = listOf(
                    FrostedGlassEngine.UI_STYLE_DEFAULT to context.getString(R.string.ui_option_default),
                    FrostedGlassEngine.UI_STYLE_FROSTED_GLASS to context.getString(R.string.ui_option_frosted_glass),
                    FrostedGlassEngine.UI_STYLE_NEUMORPHISM to context.getString(R.string.ui_option_neumorphism)
                ),
                initialValue = FrostedGlassEngine.UI_STYLE_DEFAULT,
                subtitle = context.getString(R.string.settings_ui_option_subtitle)
            )
            setPremiumOptions(PremiumFeature.FROSTED_GLASS) { it == FrostedGlassEngine.UI_STYLE_FROSTED_GLASS }
            onValueChanged = { value ->
                // Frosted Glass is the gated style; Default and Neumorphism stay free. The row has
                // already painted the new selection by now, so a refusal has to paint it back.
                if (value == FrostedGlassEngine.UI_STYLE_FROSTED_GLASS &&
                    !PremiumGate.allow(context, PremiumFeature.FROSTED_GLASS)
                ) {
                    styleRow.setSelectedValue(currentStyleValue)
                } else {
                    currentStyleValue = value
                    onUiStyleModeChanged(value)
                    previewView.setMode(value)
                    updateCaptureRowVisibility(value)
                }
            }
        }

        previewView = UiStylePreviewView(context).apply {
            setMode(FrostedGlassEngine.UI_STYLE_DEFAULT)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (64 * dp).toInt()
            ).apply {
                marginStart = (16 * dp).toInt()
                marginEnd = (16 * dp).toInt()
                bottomMargin = (12 * dp).toInt()
            }
        }

        captureDivider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (1 * dp).toInt()
            ).apply {
                marginStart = (16 * dp).toInt()
                marginEnd = (16 * dp).toInt()
            }
            visibility = View.GONE
        }

        captureRow = NexusNavRow(
            context,
            title = context.getString(com.nexus.launcher.R.string.frost_capture_title),
            subtitle = context.getString(com.nexus.launcher.R.string.frost_capture_subtitle),
            iconRes = R.drawable.ic_menu_wallpaper,
            showChevron = true
        ) { onCaptureWallpaperRequested() }.apply {
            visibility = View.GONE
        }

        container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (4 * dp).toInt()
                bottomMargin = (12 * dp).toInt()
            }
            background = pillBackground
            clipToOutline = true
            addView(styleRow)
            addView(previewView)
            addView(captureDivider)
            addView(captureRow)
        }
    }

    fun applyTokens(tokens: NexusColorTokens) {
        pillBackground.setColor(tokens.surface)
        styleRow.applyTokens(tokens)
        previewView.setTokens(tokens)
        captureRow.applyTokens(tokens)
        captureDivider.setBackgroundColor(tokens.divider)
    }

    fun setSelectedMode(mode: String) {
        // Keeps the revert target in step with the stored value; without this a refusal would
        // paint back whatever was last chosen in this session rather than what is actually saved.
        currentStyleValue = mode
        if (styleRow.getSelectedValue() != mode) {
            styleRow.setSelectedValue(mode)
        }
        previewView.setMode(mode)
        updateCaptureRowVisibility(mode)
    }

    private fun updateCaptureRowVisibility(mode: String) {
        val isGlass = mode == FrostedGlassEngine.UI_STYLE_FROSTED_GLASS
        captureDivider.visibility = if (isGlass) View.VISIBLE else View.GONE
        captureRow.visibility = if (isGlass) View.VISIBLE else View.GONE
    }
}
