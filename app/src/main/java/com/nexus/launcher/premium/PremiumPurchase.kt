package com.nexus.launcher.premium

import android.app.Activity
import android.widget.Toast
import com.nexus.launcher.premium.billing.PremiumBilling
import com.nexus.launcher.R

/**
 * The one place a purchase begins.
 *
 * [start] reports only whether Play's sheet opened. Whether anything was *bought* arrives later
 * through `PurchasesUpdatedListener` and lands on [PremiumManager], so callers must watch the
 * entitlement rather than this return value — the user is still deciding when this function
 * returns.
 *
 * A false answer means the purchase could not be attempted at all: no billing connection, or Play
 * has no offer for that product. Both are the normal state until the Console products exist and
 * the app is on a test track, which is why the message names that rather than blaming the device.
 */
object PremiumPurchase {

    fun start(activity: Activity, plan: PremiumPlan): Boolean {
        // Play warns that stale ProductDetails make launchBillingFlow fail, so the details are
        // refreshed and the purchase launched from the result rather than from whatever was
        // fetched at connect time.
        if (PremiumBilling.launchPurchase(activity, plan)) return true

        PremiumBilling.refreshProducts {
            if (!PremiumBilling.launchPurchase(activity, plan)) {
                Toast.makeText(
                    activity,
                    activity.getString(R.string.premium_billing_unavailable),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
        return false
    }
}
