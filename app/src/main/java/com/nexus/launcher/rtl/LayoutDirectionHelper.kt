package com.nexus.launcher.rtl

import android.content.Context
import android.text.TextUtils
import android.view.View
import com.nexus.launcher.locale.LocaleObserver
import java.util.Locale

/**
 * Foundation helper for resolving layout direction (LTR vs RTL).
 * Evaluates the application's effective in-app locale (via [LocaleObserver])
 * rather than relying solely on the device's system configuration.
 */
object LayoutDirectionHelper {

    /**
     * Returns true if the given [locale] is Right-to-Left (RTL).
     * Uses Android's [TextUtils.getLayoutDirectionFromLocale].
     */
    fun isRtl(locale: Locale): Boolean {
        return TextUtils.getLayoutDirectionFromLocale(locale) == View.LAYOUT_DIRECTION_RTL
    }

    /**
     * Returns true if the application's current effective locale is Right-to-Left (RTL).
     */
    fun isRtl(context: Context): Boolean {
        val locale = try {
            LocaleObserver.getEffectiveLocale(context)
        } catch (_: Throwable) {
            Locale.getDefault()
        }
        return isRtl(locale)
    }
}
