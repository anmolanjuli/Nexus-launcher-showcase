package com.nexus.launcher.ui.folder

import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import com.nexus.launcher.R
import com.nexus.launcher.data.FolderConfig
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.ui.glass.FrostedGlassEngine

/** Layered wallpaper frost with rounded beveled edge on blurBg. */
object FolderCardFrostApplier {

    private const val BLUR_TAG = "folder_blur_bg"
    private val latestConfig = java.util.WeakHashMap<ViewGroup, FolderConfig>()

    fun apply(overlayRoot: View, config: FolderConfig) {
        val cardContainer = overlayRoot.findViewById<ViewGroup>(R.id.folder_card_container) ?: return
        val holder = overlayRoot.findViewById<View>(R.id.folder_card_holder)
        val density = overlayRoot.resources.displayMetrics.density
        holder?.elevation = 22f * density
        cardContainer.clipChildren = false
        cardContainer.clipToPadding = false
        latestConfig[cardContainer] = config

        var blurBg = cardContainer.findViewWithTag<View>(BLUR_TAG)
        if (blurBg == null) {
            val newBlurBg = View(cardContainer.context).apply {
                id = View.generateViewId()
                tag = BLUR_TAG
                layoutParams = android.widget.LinearLayout.LayoutParams(0, 0)
            }
            cardContainer.addView(newBlurBg, 0)
            cardContainer.addOnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
                newBlurBg.layout(0, 0, right - left, bottom - top)
                val latest = latestConfig[cardContainer] ?: config
                refreshBlurBackground(newBlurBg, latest)
            }
            blurBg = newBlurBg
        }

        val cornerPx = FolderGlassEdgeBuilder.cornerRadiusPx(cardContainer.context)
        refreshBlurBackground(blurBg, config)
        blurBg.clipToOutline = true
        blurBg.outlineProvider = roundedOutline(cornerPx)

        val tokens = try {
            ThemeObserver.currentTokens(cardContainer.context)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }
        val frostedTokens = FrostedGlassEngine.resolveFrostedTokens(tokens)
        val borderPx = (1.25f * density).toInt().coerceAtLeast(1)
        val isGlass = FolderIconPlateDraw.isGlass(config)
        // Flat border, matching widgets (no refraction-driven white blend / glass-shadow rim;
        // removed per explicit request, see NexusWidgetRenderer's equivalent removal).
        val strokeColor = if (isGlass) frostedTokens.border else tokens.divider
        cardContainer.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerPx
            setColor(Color.TRANSPARENT)
            setStroke(borderPx, strokeColor)
        }
        cardContainer.clipToOutline = true
        cardContainer.outlineProvider = roundedOutline(cornerPx)
    }

    private val screenLoc = IntArray(2)

    private fun refreshBlurBackground(blurBg: View, config: FolderConfig) {
        if (blurBg.width <= 0 || blurBg.height <= 0) return
        blurBg.getLocationOnScreen(screenLoc)
        blurBg.background = FolderWallpaperBackdrop.buildCardStack(
            blurBg.context, config, blurBg.width, blurBg.height, screenLoc[0], screenLoc[1]
        )
        blurBg.invalidate()
    }

    private fun roundedOutline(cornerPx: Float) = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, cornerPx)
        }
    }

    /**
     * @param refraction 0..1 — lower is softer/frostier, higher is sharper and lets more of the
     * wallpaper's detail bleed through, matching the "Glass Refraction" slider elsewhere.
     */
    fun applyBlur(cardContainer: ViewGroup, refraction: Float = 0.70f, opacity: Float = 1f) {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            val blurBg = cardContainer.findViewWithTag<View>(BLUR_TAG) ?: return
            // Same range as WidgetGlassLiveBackdropView.setRefraction — this was previously much
            // weaker (34-18*refr, topping out ~21px at the 0.70 default), leaving the wallpaper
            // sharp and saturated enough to read as "too colorful" instead of frosted next to
            // the widgets' heavier blur.
            val radius = FrostedGlassEngine.glassBlurRadius(opacity, refraction)
            blurBg.setRenderEffect(
                android.graphics.RenderEffect.createBlurEffect(
                    radius, radius,
                    android.graphics.Shader.TileMode.CLAMP
                )
            )
            cardContainer.setRenderEffect(null)
        }
    }

    fun removeBlur(cardContainer: ViewGroup) {
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            val blurBg = cardContainer.findViewWithTag<View>(BLUR_TAG) ?: return
            blurBg.setRenderEffect(null)
            cardContainer.setRenderEffect(null)
        }
    }
}
