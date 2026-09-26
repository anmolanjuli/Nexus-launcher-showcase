package com.nexus.launcher.premium.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryPurchasesParams
import com.nexus.launcher.premium.PremiumManager
import com.nexus.launcher.premium.PremiumPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The app's one connection to Google Play Billing.
 *
 * A single client, held for the process lifetime, because two clients means two
 * [PurchasesUpdatedListener] callbacks for the same purchase. Auto-reconnection is on, so a
 * dropped service connection is re-established by the library on the next call rather than by
 * retry logic here.
 *
 * ## Entitlement is queried, never remembered
 *
 * [refreshEntitlement] asks Play what the user owns and writes the answer — *including* writing
 * `false`. That direction matters as much as the other one: a refund, a cancelled subscription or
 * a chargeback all show up as Play no longer listing the purchase, and an app that only ever
 * grants keeps handing out Premium to someone who stopped paying for it. The local flag in
 * [PremiumManager] is a cache of this answer, not the answer.
 *
 * ## Acknowledgement is not optional
 *
 * Play automatically refunds any purchase that is not acknowledged within three days. Every
 * granted purchase goes through [acknowledge] for that reason, and a failure there is worth a log
 * line even in release.
 *
 * ## Nothing works until the Console side exists
 *
 * Until the products are created and the app is on a test track, every query returns its products
 * as unfetched and every purchase attempt fails to launch. That is the expected state of this code
 * on a locally-signed build, not a bug in it.
 */
object PremiumBilling {

    private const val TAG = "NexusBilling"

    private var client: BillingClient? = null
    private var appContext: Context? = null

    private val _prices = MutableStateFlow<Map<PremiumPlan, String>>(emptyMap())

    /** Play's localised price per plan, empty until a query succeeds. */
    val prices: StateFlow<Map<PremiumPlan, String>> = _prices.asStateFlow()

    private val _ready = MutableStateFlow(false)

    /** Whether a purchase could be launched right now. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /** Product details are re-queried before each purchase — Play warns that stale ones fail. */
    private val details = mutableMapOf<PremiumPlan, ProductDetails>()

    private val purchasesUpdated = PurchasesUpdatedListener { result, purchases ->
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK ->
                purchases?.forEach(::handlePurchase)
            // Backing out of Play's sheet is a decision, not a failure. Say nothing.
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> Log.w(TAG, "Purchase update failed: ${result.responseCode} ${result.debugMessage}")
        }
    }

    /**
     * Builds the client and connects. Safe to call repeatedly — later calls only reconnect.
     *
     * `enableOneTimeProducts` on the pending-purchase params is required by the library whenever
     * one-time products are sold at all, which the lifetime unlock is.
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        if (client == null) {
            client = BillingClient.newBuilder(context.applicationContext)
                .setListener(purchasesUpdated)
                .enablePendingPurchases(
                    PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
                )
                .enableAutoServiceReconnection()
                .build()
        }
        connect()
    }

    private fun connect() {
        val billing = client ?: return
        if (billing.isReady) {
            onConnected()
            return
        }
        billing.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(billingResult: BillingResult) {
                if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                    onConnected()
                } else {
                    _ready.value = false
                    Log.w(TAG, "Billing setup failed: ${billingResult.debugMessage}")
                }
            }

            // Auto-reconnection is enabled, so the library retries on the next call. Nothing to do
            // here beyond recording that a purchase cannot be launched at this moment.
            override fun onBillingServiceDisconnected() {
                _ready.value = false
            }
        })
    }

    private fun onConnected() {
        _ready.value = true
        refreshProducts()
        refreshEntitlement()
    }

    /** Refreshes prices for both product types. Called on connect and before each purchase. */
    fun refreshProducts(onDone: (() -> Unit)? = null) {
        val billing = client
        if (billing == null) {
            onDone?.invoke()
            return
        }
        val types = listOf(BillingClient.ProductType.SUBS, BillingClient.ProductType.INAPP)
        var outstanding = types.size
        types.forEach { type ->
            billing.queryProductDetailsAsync(PremiumProducts.queryParams(type)) { result, found ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    found.productDetailsList.forEach { product ->
                        val plan = PremiumProducts.planFor(product.productId) ?: return@forEach
                        details[plan] = product
                        PremiumProducts.formattedPrice(product)?.let { price ->
                            _prices.value = _prices.value + (plan to price)
                        }
                    }
                    found.unfetchedProductList.forEach {
                        Log.w(TAG, "Product not fetched: ${it.productId}")
                    }
                } else {
                    Log.w(TAG, "Product query failed: ${result.debugMessage}")
                }
                if (--outstanding == 0) onDone?.invoke()
            }
        }
    }

    /**
     * Asks Play what the user owns and writes it through to [PremiumManager].
     *
     * Call this on every app start. It is the only thing that restores Premium on a new device or
     * after a reinstall, and the only thing that takes it away after a refund.
     */
    fun refreshEntitlement() {
        val billing = client ?: return
        val owned = mutableSetOf<String>()
        var outstanding = 2
        var allSucceeded = true

        fun finish() {
            if (--outstanding > 0) return
            // Only a complete answer may be written. A failed query means Play could not be
            // reached — offline, or Play Services unavailable — which is not the same as owning
            // nothing. Writing `false` there would revoke Premium from a paying user the moment
            // they opened the app on a plane, so the cached entitlement stands instead.
            if (!allSucceeded) {
                Log.w(TAG, "Entitlement unknown; keeping the cached answer")
                return
            }
            appContext?.let { PremiumManager.setBillingEntitled(it, owned.isNotEmpty()) }
        }

        listOf(BillingClient.ProductType.SUBS, BillingClient.ProductType.INAPP).forEach { type ->
            billing.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder().setProductType(type).build(),
            ) { result, purchases ->
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    purchases.filter { it.purchaseState == Purchase.PurchaseState.PURCHASED }
                        .forEach { purchase ->
                            owned.addAll(purchase.products)
                            acknowledge(purchase)
                        }
                } else {
                    allSucceeded = false
                    Log.w(TAG, "Purchase query failed: ${result.debugMessage}")
                }
                finish()
            }
        }
    }

    /**
     * Starts Play's purchase sheet for [plan].
     *
     * Returns false when the flow could not even be launched — no connection, or Play has no
     * offer for that product. It does not report whether the user went on to buy anything: that
     * arrives later through [purchasesUpdated], which is why the paywall waits on the entitlement
     * rather than on this answer.
     */
    fun launchPurchase(activity: Activity, plan: PremiumPlan): Boolean {
        val billing = client ?: return false
        if (!billing.isReady) {
            connect()
            return false
        }
        val product = details[plan] ?: return false
        val token = PremiumProducts.offerToken(product) ?: run {
            // Almost always a Console problem: a base plan that was never activated has no offer.
            Log.w(TAG, "No offer for ${plan.productId}; is its base plan active?")
            return false
        }
        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(product)
                        .setOfferToken(token)
                        .build(),
                ),
            )
            .build()
        val result = billing.launchBillingFlow(activity, params)
        return result.responseCode == BillingClient.BillingResponseCode.OK
    }

    /** A purchase state is not an entitlement: only PURCHASED counts, PENDING waits. */
    private fun handlePurchase(purchase: Purchase) {
        if (purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return
        acknowledge(purchase)
        appContext?.let { PremiumManager.setBillingEntitled(it, true) }
    }

    /** Three days unacknowledged and Play refunds it automatically. */
    private fun acknowledge(purchase: Purchase) {
        if (purchase.isAcknowledged) return
        val billing = client ?: return
        billing.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(purchase.purchaseToken)
                .build(),
        ) { result ->
            if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                Log.w(TAG, "Acknowledge failed, Play will refund: ${result.debugMessage}")
            }
        }
    }
}
