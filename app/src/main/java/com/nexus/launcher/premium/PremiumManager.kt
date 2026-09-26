package com.nexus.launcher.premium

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The single source of truth for whether the user has Premium.
 *
 * Every gate in the app asks this and nothing else, so the entitlement can move from the local
 * flag below to Google Play Billing by rewriting this file alone.
 *
 * ## Two independent inputs
 *
 * [setBillingEntitled] is what Google Play says the user owns, refreshed on every app start. It
 * writes `false` as readily as `true`, because a refund or a cancelled subscription shows up as
 * Play no longer listing the purchase.
 *
 * [setPremiumForTesting] is the developer override, and it is deliberately *not* the same flag. If
 * the two shared one, the first entitlement refresh on a device with no purchases would silently
 * switch the override back off, and testing the unlocked state would become impossible on exactly
 * the build where it matters. The override wins while it is on; billing owns the rest.
 *
 * Both live in their own SharedPreferences file rather than in `NexusSettingsData` on purpose: the
 * settings snapshot is what backup export writes, and an entitlement living there would be granted
 * by editing a backup file. This is not security — a local flag never is — it just avoids building
 * the obvious bypass in by accident.
 */
object PremiumManager {

    private const val PREFS = "nexus_premium"

    /** What Play says the user owns. */
    private const val KEY_BILLING = "is_premium"

    /** The developer override, kept apart so an entitlement refresh cannot clear it. */
    private const val KEY_DEV_OVERRIDE = "dev_override"

    private val _isPremium = MutableStateFlow(false)
    val isPremium: StateFlow<Boolean> = _isPremium.asStateFlow()

    /** Call once on app start, before anything reads [isPremium]. */
    fun init(context: Context) {
        recompute(context)
    }

    fun has(feature: PremiumFeature): Boolean {
        // Every feature is covered by the one subscription today. The parameter is here so the
        // call sites already read correctly if tiers are ever split.
        @Suppress("UNUSED_PARAMETER")
        return _isPremium.value
    }

    fun isLocked(feature: PremiumFeature): Boolean = !has(feature)

    /**
     * The developer override. Grants Premium regardless of what Play says, and survives every
     * entitlement refresh — see the note on this object about why it is a separate key.
     */
    fun setPremiumForTesting(context: Context, premium: Boolean) {
        prefs(context).edit().putBoolean(KEY_DEV_OVERRIDE, premium).apply()
        recompute(context)
    }

    /**
     * What Google Play reports, from `PremiumBilling.refreshEntitlement`.
     *
     * Called with `false` too — that is how a refund or a lapsed subscription takes Premium away.
     */
    fun setBillingEntitled(context: Context, entitled: Boolean) {
        prefs(context).edit().putBoolean(KEY_BILLING, entitled).apply()
        recompute(context)
    }

    private fun recompute(context: Context) {
        val store = prefs(context)
        _isPremium.value =
            store.getBoolean(KEY_DEV_OVERRIDE, false) || store.getBoolean(KEY_BILLING, false)
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
