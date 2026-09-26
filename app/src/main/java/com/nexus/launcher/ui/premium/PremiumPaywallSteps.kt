package com.nexus.launcher.ui.premium

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.nexus.launcher.R
import com.nexus.launcher.premium.PremiumFeature
import com.nexus.launcher.premium.PremiumPlan

/** The four cards the paywall moves between. */
internal enum class PaywallStep { CHOICE, BROKE, RICH, CONFIRM }

/**
 * What each step of the paywall says, and which step each action leads to.
 *
 * This is the tone-bearing half of the modal. The jokes always land on us taking the money, never
 * on the user not having any — "I'm Broke Like You" puts the author in the group. That is the line
 * this file must not cross: a friend being straight with you and a company being clever at a broke
 * user's expense read completely differently, and every word here is one edit away from the second
 * one. All of it lives in strings so it can be re-voiced or translated without touching logic.
 *
 * Both routes stay reachable from wherever the user is: the Broke card still offers the
 * subscription, and the subscription card still offers the lifetime unlock. Picking a path never
 * removes the other one — the modal asks, it does not steer.
 *
 * Every step is rebuilt on entry rather than cached. They are small, and a fresh view means the
 * cross-fade in [PremiumPaywallDialog] never animates a view that is still parented somewhere.
 */
internal class PaywallSteps(
    private val views: PaywallViews,
    private val feature: PremiumFeature?,
    private val subscription: () -> PremiumPlan,
    private val onSubscriptionPicked: (PremiumPlan) -> Unit,
    private val goTo: (PaywallStep) -> Unit,
    private val onPurchase: (PremiumPlan) -> Unit,
    private val onDismiss: () -> Unit,
) {

    fun build(step: PaywallStep): View = when (step) {
        PaywallStep.CHOICE -> choice()
        PaywallStep.BROKE -> broke()
        PaywallStep.RICH -> rich()
        PaywallStep.CONFIRM -> confirm()
    }

    /** The question. Neither answer is the "wrong" one, so neither is styled as a decline. */
    private fun choice(): View = views.card().apply {
        // Names the feature that sent the user here. Not fine print — it is the answer to "why am
        // I looking at this?", and without it the modal arrives out of nowhere.
        feature?.let { locked ->
            addView(
                views.eyebrow(views.string(R.string.premium_locked_toast, views.string(locked.titleRes))),
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = (14 * views.dp).toInt() },
            )
        }
        addView(views.headline(views.string(R.string.premium_paywall_choice_title)))
        addView(views.body(views.string(R.string.premium_paywall_choice_subtitle)), views.spaced(10))
        addView(
            views.filledButton(views.string(R.string.premium_paywall_choice_broke)) {
                goTo(PaywallStep.BROKE)
            },
            views.spaced(26),
        )
        addView(
            views.raisedButton(views.string(R.string.premium_paywall_choice_rich)) {
                goTo(PaywallStep.RICH)
            },
            views.spaced(10),
        )
    }

    /**
     * The lifetime unlock, and the one we actually want people to take.
     *
     * The subscription is still offered underneath as a quiet third action, because picking this
     * path must not remove the other.
     */
    private fun broke(): View = views.card().apply {
        val plan = PremiumPlan.LIFETIME
        addView(views.headline(views.string(R.string.premium_paywall_broke_headline)))
        addView(views.body(views.string(R.string.premium_paywall_broke_body)), views.spaced(10))
        addView(
            views.filledButton(
                views.string(
                    R.string.premium_paywall_broke_cta,
                    views.string(plan.fallbackPriceRes),
                ),
            ) { onPurchase(plan) },
            views.spaced(26),
        )
        addView(
            views.textButton(views.string(R.string.premium_paywall_choice_rich)) {
                goTo(PaywallStep.RICH)
            },
            views.spaced(12),
        )
        addView(
            views.textButton(views.string(R.string.premium_paywall_broke_dismiss)) { onDismiss() },
            views.spaced(2),
        )
    }

    /**
     * The recurring options.
     *
     * Muted rather than accented on purpose — the accent belongs to the lifetime unlock, and this
     * path is meant to read as the indulgent choice the user insisted on.
     */
    private fun rich(): View = views.card().apply {
        addView(views.headline(views.string(R.string.premium_paywall_rich_headline)))
        addView(views.body(views.string(R.string.premium_paywall_rich_body)), views.spaced(10))

        val rows = mutableListOf<Pair<PremiumPlan, LinearLayout>>()
        fun repaint() = rows.forEach { (plan, row) -> views.paintOption(row, plan == subscription()) }

        listOf(PremiumPlan.MONTHLY, PremiumPlan.YEARLY).forEachIndexed { index, plan ->
            val row = views.optionRow(plan) {
                onSubscriptionPicked(plan)
                repaint()
            }
            rows.add(plan to row)
            addView(row, views.spaced(if (index == 0) 22 else 8))
        }
        repaint()

        addView(
            views.raisedButton(views.string(R.string.premium_paywall_rich_cta)) {
                goTo(PaywallStep.CONFIRM)
            },
            views.spaced(22),
        )
        addView(
            views.textButton(views.string(R.string.premium_paywall_rich_back)) {
                goTo(PaywallStep.BROKE)
            },
            views.spaced(10),
        )
    }

    /** One last look before a recurring charge. Delighted, not discouraging. */
    private fun confirm(): View = views.card().apply {
        val plan = subscription()
        addView(views.headline(views.string(R.string.premium_paywall_confirm_headline)))
        addView(views.body(views.string(R.string.premium_paywall_confirm_body)), views.spaced(10))
        addView(
            views.body(views.offerLabel(plan)).apply { setTextColor(views.tokens.textPrimary) },
            views.spaced(18),
        )
        addView(
            views.filledButton(views.string(R.string.premium_paywall_confirm_cta)) {
                onPurchase(plan)
            },
            views.spaced(24),
        )
        addView(
            views.textButton(views.string(R.string.premium_paywall_confirm_back)) {
                goTo(PaywallStep.RICH)
            },
            views.spaced(10),
        )
    }
}
