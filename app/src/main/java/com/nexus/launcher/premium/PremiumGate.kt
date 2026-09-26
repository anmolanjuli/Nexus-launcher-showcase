package com.nexus.launcher.premium

import android.content.Context
import android.content.Intent
import android.widget.Toast
import com.nexus.launcher.R

/**
 * The one place a locked feature is turned away.
 *
 * Locked controls stay visible and selectable — that was the agreed behaviour, because a free user
 * who cannot see what Premium offers has no reason to want it. What changes is the outcome: the
 * selection does not take effect, and the Premium page opens instead.
 *
 * Every gate is a single [allow] call, so adding a feature is an entry in [PremiumFeature] plus one
 * line at the control. Nothing here knows which features exist.
 */
object PremiumGate {

    /**
     * True when [feature] may be used. When it may not, this opens the Premium page as a side
     * effect — so the caller only has to bail out:
     *
     * ```
     * if (!PremiumGate.allow(context, PremiumFeature.EXTRA_THEMES)) return
     * ```
     *
     * Callers that have already changed a control's visual state (a segmented row paints its
     * selection before reporting it) must also put that state back; [allow] cannot know about it.
     */
    fun allow(context: Context, feature: PremiumFeature): Boolean {
        // Never lock a feature that cannot be bought. Until the Play products are live there is no
        // way out of a refusal, so a gate here would be a dead end rather than an offer.
        if (!PremiumConfig.BILLING_LIVE) return true
        if (PremiumManager.has(feature)) return true
        openPremiumPage(context, feature)
        return false
    }

    /**
     * The paywall, or the Premium page if there is no activity to host a dialog.
     *
     * A widget host or service context cannot show a [android.app.Dialog], so the settings deep
     * link stays as the fallback rather than the turn-away failing silently.
     */
    private fun openPremiumPage(context: Context, feature: PremiumFeature) {
        val shown = com.nexus.launcher.ui.premium.PremiumPaywallDialog.show(
            context,
            feature,
        ) { activity, plan -> PremiumPurchase.start(activity, plan) }
        if (shown) return
        Toast.makeText(
            context,
            context.getString(R.string.premium_locked_toast, context.getString(feature.titleRes)),
            Toast.LENGTH_SHORT,
        ).show()
        runCatching {
            context.startActivity(
                Intent(context, com.nexus.launcher.ui.settings.SettingsActivity::class.java).apply {
                    putExtra(
                        com.nexus.launcher.ui.settings.SettingsActivity.EXTRA_SECTION,
                        SECTION_PREMIUM,
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
            )
        }
    }

    /** Matches the deep-link key `SettingsActivity.sectionIndex` maps to the Premium page. */
    private const val SECTION_PREMIUM = "premium"
}
