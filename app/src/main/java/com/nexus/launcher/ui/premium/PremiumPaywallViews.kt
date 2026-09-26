package com.nexus.launcher.ui.premium

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.nexus.launcher.R
import com.nexus.launcher.premium.PremiumPlan
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.typography.NexusTypeScale

/**
 * The paywall's visual vocabulary — every piece a step is built from, and nothing about what the
 * steps say.
 *
 * Split out from [PremiumPaywallDialog] so the wording and the styling can move independently: a
 * re-voiced paywall touches [PremiumPaywallSteps], a re-styled one touches this. Both read their
 * colours from the [NexusColorTokens] captured once at construction, so a theme change while the
 * modal is open cannot half-repaint it.
 *
 * The button hierarchy is deliberate and only three deep. [filledButton] is the accent action and
 * there is at most one per step, so the recommended move is never ambiguous; [raisedButton] is the
 * same size and weight but muted, for a real choice that is not the recommendation; [textButton] is
 * for leaving. Anything that needs a fourth level is a sign the step is doing too much.
 */
internal class PaywallViews(
    private val host: Activity,
    val tokens: NexusColorTokens,
) {

    val dp: Float = host.resources.displayMetrics.density

    /** The rounded panel every step lives in. Sized for a [FrameLayout] step host. */
    fun card(): LinearLayout = LinearLayout(host).apply {
        orientation = LinearLayout.VERTICAL
        background = GradientDrawable().apply {
            cornerRadius = 30f * dp
            setColor(tokens.surface)
            setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
        }
        setPadding((24 * dp).toInt(), (28 * dp).toInt(), (24 * dp).toInt(), (22 * dp).toInt())
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
    }

    fun headline(text: String): TextView = TextView(host).apply {
        this.text = text
        gravity = Gravity.CENTER
        NexusTypeScale.title.bindTo(this, tokens.textPrimary)
    }

    fun body(text: String): TextView = TextView(host).apply {
        this.text = text
        gravity = Gravity.CENTER
        NexusTypeScale.body.bindTo(this, tokens.textSecondary)
        setLineSpacing(4 * dp, 1f)
    }

    /** A small accent label naming the feature that opened the modal. */
    fun eyebrow(text: String): TextView = TextView(host).apply {
        this.text = text
        gravity = Gravity.CENTER
        NexusTypeScale.sectionLabel.bindTo(this, tokens.accent)
    }

    fun spaced(topDp: Int): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply { topMargin = (topDp * dp).toInt() }

    /** The accent action: the move we are recommending on this step. */
    fun filledButton(text: String, onClick: () -> Unit): TextView =
        pill(text, tokens.accent, tokens.bg, tokens.accent, onClick)

    /** Same weight as [filledButton], deliberately less pull. */
    fun raisedButton(text: String, onClick: () -> Unit): TextView =
        pill(text, tokens.surfaceRaised, tokens.textPrimary, tokens.divider, onClick)

    private fun pill(
        text: String,
        fill: Int,
        textColor: Int,
        strokeColor: Int,
        onClick: () -> Unit,
    ): TextView = TextView(host).apply {
        this.text = text
        gravity = Gravity.CENTER
        NexusTypeScale.bodyStrong.bindTo(this, textColor)
        background = GradientDrawable().apply {
            cornerRadius = 999f
            setColor(fill)
            setStroke((1 * dp).toInt().coerceAtLeast(1), strokeColor)
        }
        setPadding((20 * dp).toInt(), (15 * dp).toInt(), (20 * dp).toInt(), (15 * dp).toInt())
        minHeight = (54 * dp).toInt()
        isClickable = true
        isFocusable = true
        setOnClickListener {
            tap(it)
            onClick()
        }
    }

    /** The way out of a step. Quiet, but a full-width tap target rather than a link. */
    fun textButton(text: String, onClick: () -> Unit): TextView = TextView(host).apply {
        this.text = text
        gravity = Gravity.CENTER
        NexusTypeScale.caption.bindTo(this, tokens.textSecondary)
        setPadding(0, (13 * dp).toInt(), 0, (13 * dp).toInt())
        isClickable = true
        isFocusable = true
        setOnClickListener {
            tap(it)
            onClick()
        }
    }

    /** One selectable plan. Paint it with [paintOption] — the row is built unstyled. */
    fun optionRow(plan: PremiumPlan, onClick: () -> Unit): LinearLayout = LinearLayout(host).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        addView(
            TextView(host).apply {
                text = offerLabel(plan)
                gravity = Gravity.CENTER
                NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
            },
        )
        setOnClickListener {
            tap(it)
            onClick()
        }
    }

    fun paintOption(row: LinearLayout, selected: Boolean) {
        row.background = GradientDrawable().apply {
            cornerRadius = 20f * dp
            setColor(
                if (selected) ColorUtils.blendARGB(tokens.surfaceRaised, tokens.accent, 0.16f)
                else tokens.surfaceRaised,
            )
            setStroke(
                ((if (selected) 2 else 1) * dp).toInt().coerceAtLeast(1),
                if (selected) tokens.accent else tokens.divider,
            )
        }
        // Assigning a background resets padding, so it is restored on every repaint.
        row.setPadding((18 * dp).toInt(), (16 * dp).toInt(), (18 * dp).toInt(), (16 * dp).toInt())
    }

    /** "Annually — $3.99 / year", built from the plan's own strings. */
    fun offerLabel(plan: PremiumPlan): String = host.getString(
        R.string.premium_paywall_offer,
        host.getString(plan.titleRes),
        host.getString(plan.fallbackPriceRes),
        host.getString(plan.periodRes),
    )

    fun string(resId: Int, vararg args: Any): String =
        if (args.isEmpty()) host.getString(resId) else host.getString(resId, *args)

    fun tap(view: View) {
        view.performHapticFeedback(
            HapticFeedbackConstants.VIRTUAL_KEY,
            HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
        )
    }

    fun withAlpha(color: Int, alpha: Float): Int =
        ColorUtils.setAlphaComponent(color, (alpha * 255f).toInt().coerceIn(0, 255))
}
