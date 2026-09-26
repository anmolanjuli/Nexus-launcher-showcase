package com.nexus.launcher.ui.widgets.mosaic

import android.view.HapticFeedbackConstants
import android.view.View

/** Shared haptics for Living Mosaic chrome / manage / settings / swipe. */
object LivingMosaicHaptics {

    fun tick(view: View?) {
        view ?: return
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    fun click(view: View?) {
        view ?: return
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    fun confirm(view: View?) {
        view ?: return
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }
}
