package com.nexus.launcher.ui.premium.showcase

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.nexus.launcher.R
import com.nexus.launcher.premium.PremiumFeature
import com.nexus.launcher.premium.PremiumManager
import com.nexus.launcher.premium.PremiumPurchase
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.WallpaperSheetComponents
import com.nexus.launcher.ui.premium.PremiumPaywallDialog

/**
 * A feature, full screen: its picture as large as the screen allows, what it does, and the way to
 * get it.
 *
 * The unlock button closes this and then opens [PremiumPaywallDialog] for the feature — the paywall
 * keeps its own voice and flow. [onClosed] runs on every close, so the page can bring the feature
 * back into view.
 */
internal object PremiumShowcasePreview {

    private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    fun show(
        context: Context,
        feature: PremiumFeature,
        tokens: NexusColorTokens,
        onClosed: (PremiumFeature) -> Unit,
    ) {
        val dp = context.resources.displayMetrics.density
        // Not a *Fullscreen* theme: its FLAG_FULLSCREEN reports a zero status bar inset, so the
        // title slid under the status bar. Same window setup as PremiumPaywallDialog.
        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar)
        dialog.window?.let { win ->
            win.setLayout(android.view.WindowManager.LayoutParams.MATCH_PARENT, android.view.WindowManager.LayoutParams.MATCH_PARENT)
            WindowCompat.setDecorFitsSystemWindows(win, false)
            win.statusBarColor = Color.TRANSPARENT
            win.navigationBarColor = Color.TRANSPARENT
            win.setBackgroundDrawable(ColorDrawable(tokens.bg))
        }
        var unlockChosen = false
        dialog.setOnDismissListener {
            onClosed(feature)
            // After this has closed, so the paywall never opens underneath it.
            if (unlockChosen) {
                PremiumPaywallDialog.show(context, feature) { activity, plan -> PremiumPurchase.start(activity, plan) }
            }
        }

        val side = (16 * dp).toInt()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(tokens.bg)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(side, bars.top + (8 * dp).toInt(), side, bars.bottom + side)
            insets
        }

        root.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(TextView(context).apply {
                text = context.getString(feature.titleRes)
                NexusTypeScale.title.bindTo(this, tokens.textPrimary)
            }, LinearLayout.LayoutParams(0, WRAP, 1f))
            addView(WallpaperSheetComponents.buildCloseButton(context, dp, tokens) { dialog.dismiss() })
        }, LinearLayout.LayoutParams(MATCH, WRAP))

        val art = PremiumShowcaseArt.view(context, feature, tokens)
        root.addView(PremiumShowcaseSections.pictureFrame(context, tokens, 24f).apply {
            addView(art, MATCH, MATCH)
        }, LinearLayout.LayoutParams(MATCH, 0, 1f).apply { topMargin = (12 * dp).toInt() })
        if (feature == PremiumFeature.EXTRA_THEMES && art is PremiumShowcaseMock) {
            root.addView(PremiumShowcaseThemeChips.build(context, tokens, art),
                LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = (12 * dp).toInt() })
        }

        root.addView(TextView(context).apply {
            text = context.getString(feature.descriptionRes)
            NexusTypeScale.body.bindTo(this, tokens.textSecondary)
        }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = side })

        val buttonParams = LinearLayout.LayoutParams(MATCH, (56 * dp).toInt()).apply { topMargin = side }
        if (PremiumManager.isLocked(feature)) {
            root.addView(PremiumShowcaseSections.accentButton(
                context, tokens, context.getString(R.string.premium_showcase_preview_cta),
            ) {
                unlockChosen = true
                dialog.dismiss()
            }, buttonParams)
        } else {
            root.addView(TextView(context).apply {
                text = context.getString(R.string.premium_showcase_preview_active)
                gravity = Gravity.CENTER
                NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
                background = GradientDrawable().apply {
                    cornerRadius = 16 * dp
                    setColor(tokens.surfaceRaised)
                }
            }, buttonParams)
        }

        dialog.setContentView(root)
        dialog.show()
    }
}
