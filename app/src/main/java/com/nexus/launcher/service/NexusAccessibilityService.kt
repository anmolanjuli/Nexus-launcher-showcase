package com.nexus.launcher.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent

class NexusAccessibilityService : AccessibilityService() {

    companion object {
        var instance: NexusAccessibilityService? = null
            private set
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No-op. We only need the service for performGlobalAction().
    }

    override fun onInterrupt() {
        // No-op.
    }
}
