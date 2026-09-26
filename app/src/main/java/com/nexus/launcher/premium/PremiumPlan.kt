package com.nexus.launcher.premium

import androidx.annotation.StringRes
import com.nexus.launcher.R

/**
 * The three things a user can buy, and nothing about how they are sold.
 *
 * The paywall's wording lives in strings and [com.nexus.launcher.ui.premium.PremiumPaywallDialog];
 * this file is only the product identity plus the figures shown before Play answers. Keeping the
 * jokes out of here means the tone can be rewritten without touching anything a purchase depends
 * on.
 *
 * ## Prices here are placeholders
 *
 * [fallbackPriceRes] is only what the page shows before Google Play has answered. The real price
 * is whatever Play returns for [productId] in the user's country and currency — it handles local
 * pricing, tax and currency formatting, and hardcoding a dollar figure would be wrong everywhere
 * outside the US. Once billing is wired, the fallback should only ever appear if the product
 * query fails.
 *
 * The product ids must match what is created in the Play Console exactly, and they cannot be
 * renamed afterwards: an id is the identity of a purchase, so changing one orphans everybody who
 * already bought it.
 * */
enum class PremiumPlan(
    val productId: String,
    @StringRes val titleRes: Int,
    @StringRes val fallbackPriceRes: Int,
    @StringRes val periodRes: Int,
) {
    MONTHLY(
        productId = "nexus_premium_monthly",
        titleRes = R.string.premium_plan_monthly,
        fallbackPriceRes = R.string.premium_plan_monthly_price,
        periodRes = R.string.premium_plan_period_monthly,
    ),
    YEARLY(
        productId = "nexus_premium_yearly",
        titleRes = R.string.premium_plan_yearly,
        fallbackPriceRes = R.string.premium_plan_yearly_price,
        periodRes = R.string.premium_plan_period_yearly,
    ),
    LIFETIME(
        productId = "nexus_premium_lifetime",
        titleRes = R.string.premium_plan_lifetime,
        fallbackPriceRes = R.string.premium_plan_lifetime_price,
        periodRes = R.string.premium_plan_period_once,
    ),
}
