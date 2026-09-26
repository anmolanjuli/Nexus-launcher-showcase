package com.nexus.launcher.ui.picker

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

/**
 * Handles system-level Home navigation and system dialog close events for [PickerOverlayShell],
 * ensuring that Home gestures / button taps dismiss the overlay immediately.
 */
class PickerOverlaySystemNavHelper(
    private val onDismiss: () -> Unit
) {
    private var receiver: BroadcastReceiver? = null
    private var registeredContext: Context? = null

    @Suppress("DEPRECATION")
    fun register(activity: Activity) {
        unregister()
        val filter = IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
        val closeReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                onDismiss()
            }
        }
        receiver = closeReceiver
        registeredContext = activity
        try {
            ContextCompat.registerReceiver(
                activity,
                closeReceiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED
            )
        } catch (_: Exception) {
            // In case of security or registration issues
        }
    }

    fun unregister() {
        receiver?.let { r ->
            try {
                registeredContext?.unregisterReceiver(r)
            } catch (_: Exception) {
                // Ignore if already unregistered
            }
        }
        receiver = null
        registeredContext = null
    }
}
