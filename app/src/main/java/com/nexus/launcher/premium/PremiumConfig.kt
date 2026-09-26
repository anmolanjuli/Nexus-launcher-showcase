package com.nexus.launcher.premium

/**
 * Whether Premium exists as far as the user is concerned.
 *
 * Play can only sell a product that has been created in the Console and activated, and the Console
 * will not let you create one until a build carrying the billing library has been uploaded. So
 * there is a window — starting now — where the paywall is complete, correct and unable to take
 * money. Shipping *that* to strangers is the bad outcome: a locked feature with no way to unlock it
 * is worse than a feature that was never advertised as paid.
 *
 * [BILLING_LIVE] is the one line that changes when the Console side is finished. While it is false:
 *
 * - `PremiumGate` lets everything through. Nothing is locked that cannot be bought.
 * - The Premium page is hidden from the settings hub and its search.
 *
 * It is deliberately not derived from `BuildConfig.DEBUG`. The builds actually being daily-driven
 * here are release builds signed with the debug key, so a DEBUG check would switch Premium off in
 * exactly the build used to test it.
 */
object PremiumConfig {

    /**
     * Flip to `true` once all three products are live in the Play Console and the app is on a
     * track that can complete a purchase.
     *
     * Left `true` for now so the paywall and the gates stay testable on the current builds. **It
     * must be `false` in the first build that reaches a public track**, because that build is
     * necessarily uploaded before the products it sells can be created.
     */
    const val BILLING_LIVE: Boolean = true
}
