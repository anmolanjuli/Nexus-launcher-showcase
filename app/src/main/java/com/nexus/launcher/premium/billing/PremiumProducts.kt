package com.nexus.launcher.premium.billing

import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.QueryProductDetailsParams
import com.nexus.launcher.premium.PremiumPlan

/**
 * Translates between [PremiumPlan] and what Google Play calls the same thing.
 *
 * Play splits products two ways that the rest of the app has no reason to know about: a
 * subscription and a one-time purchase are different `ProductType`s fetched by different queries,
 * and the thing you actually buy is not the product but an *offer* inside it. Both of those live
 * here so [PremiumBilling] stays about connection and entitlement.
 *
 * ## Offer tokens
 *
 * `launchBillingFlow` needs an offer token, not just a product. A subscription's price lives on its
 * base plan, so a subscription with no active base plan comes back with no offers and cannot be
 * bought — which looks exactly like a mis-typed product id from inside the app. [offerToken] takes
 * the first offer Play returns, because Play only returns offers the user is actually eligible for
 * and lists them best-first.
 */
internal object PremiumProducts {

    /** Play's product type for a plan. Lifetime is a one-time purchase, the rest are subs. */
    fun typeOf(plan: PremiumPlan): String = when (plan) {
        PremiumPlan.LIFETIME -> BillingClient.ProductType.INAPP
        PremiumPlan.MONTHLY, PremiumPlan.YEARLY -> BillingClient.ProductType.SUBS
    }

    fun plansOfType(type: String): List<PremiumPlan> =
        PremiumPlan.entries.filter { typeOf(it) == type }

    fun planFor(productId: String): PremiumPlan? =
        PremiumPlan.entries.firstOrNull { it.productId == productId }

    /** One query's worth of products — Play will not mix types in a single request. */
    fun queryParams(type: String): QueryProductDetailsParams =
        QueryProductDetailsParams.newBuilder()
            .setProductList(
                plansOfType(type).map { plan ->
                    QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(plan.productId)
                        .setProductType(type)
                        .build()
                },
            )
            .build()

    /**
     * The token identifying which offer to buy, or null when the product has none.
     *
     * A null here is nearly always a Console problem — a subscription whose base plan was never
     * activated, or a one-time product still in draft — rather than anything wrong on the device.
     */
    fun offerToken(details: ProductDetails): String? =
        details.subscriptionOfferDetails?.firstOrNull()?.offerToken
            ?: details.oneTimePurchaseOfferDetailsList?.firstOrNull()?.offerToken

    /**
     * Play's own formatted price string — already localised, tax-inclusive where that applies, and
     * in the user's currency. This is what the paywall should show; the hardcoded dollar figures in
     * [PremiumPlan] are only a fallback for before this arrives.
     */
    fun formattedPrice(details: ProductDetails): String? =
        details.subscriptionOfferDetails
            ?.firstOrNull()
            ?.pricingPhases
            ?.pricingPhaseList
            ?.firstOrNull()
            ?.formattedPrice
            ?: details.oneTimePurchaseOfferDetailsList?.firstOrNull()?.formattedPrice
}
