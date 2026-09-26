package com.nexus.launcher.ui.premium

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.nexus.launcher.R
import com.nexus.launcher.premium.PremiumConfig
import com.nexus.launcher.premium.PremiumFeature
import com.nexus.launcher.premium.PremiumManager
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.typography.NexusTypeScale
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

/**
 * The mark on a control that Premium unlocks.
 *
 * Locked controls stay visible and selectable on purpose ([com.nexus.launcher.premium.PremiumGate]):
 * a free user who cannot see what Premium offers has no reason to want it. Until now the only sign
 * was the paywall opening *after* the tap. The badge says it before.
 *
 * ## Two shapes, one rule
 *
 * A **row** — a whole setting — carries a PREMIUM pill beside its title, drawn exactly like the
 * BETA pill already used in Settings so the two read as one family. An **option** inside a
 * segmented control carries a small lock beside its label instead: a word pill does not fit inside
 * a segment, and a lock reads the same in every language.
 *
 * Both follow [isShown], and both update while on screen: buying Premium from the paywall takes the
 * marks off the controls behind it without reopening anything.
 */
object PremiumBadges {

    /**
     * Whether [feature] should be marked. Never while billing is off: then every gate is open, and a
     * lock on something the user can freely use would be a lie.
     */
    fun isShown(feature: PremiumFeature): Boolean =
        PremiumConfig.BILLING_LIVE && PremiumManager.isLocked(feature)

    /**
     * Calls [onChange] now if [view] is on screen, and again whenever Premium is gained or lost,
     * for as long as it stays attached.
     */
    fun bind(view: View, onChange: () -> Unit) {
        var job: Job? = null
        val listener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                job?.cancel()
                // A StateFlow delivers its current value on collection, so this also draws the
                // initial state as soon as the view arrives.
                job = MainScope().launch { PremiumManager.isPremium.collect { onChange() } }
            }

            override fun onViewDetachedFromWindow(v: View) {
                job?.cancel()
                job = null
            }
        }
        view.addOnAttachStateChangeListener(listener)
        if (view.isAttachedToWindow) listener.onViewAttachedToWindow(view)
    }

    /**
     * Puts the lock beside [option]'s label, or takes it away. [ink] is the label's own colour, so
     * the lock follows the option through selected and unselected.
     */
    fun decorateOption(option: TextView, locked: Boolean, ink: Int) {
        if (!locked) {
            option.setCompoundDrawablesRelative(null, null, null, null)
            return
        }
        val size = (option.textSize * 0.9f).toInt().coerceAtLeast(1)
        val lock = ContextCompat.getDrawable(option.context, R.drawable.ic_premium_lock)?.mutate()?.apply {
            setBounds(0, 0, size, size)
        }
        option.setCompoundDrawablesRelative(null, null, lock, null)
        option.compoundDrawablePadding = (4 * option.resources.displayMetrics.density).toInt()
        option.compoundDrawableTintList = ColorStateList.valueOf(ink)
    }

    /** The PREMIUM pill for a row, styled like the BETA pill. Starts hidden; see [bindPill]. */
    fun pill(context: Context, tokens: NexusColorTokens): TextView {
        val density = context.resources.displayMetrics.density
        return TextView(context).apply {
            text = context.getString(R.string.premium_badge)
            NexusTypeScale.caption.bindTo(this)
            textSize = 10f
            isAllCaps = true
            letterSpacing = 0.06f
            setTextColor(tokens.accent)
            background = GradientDrawable().apply {
                cornerRadius = 999f * density
                setColor(tokens.accentMuted)
            }
            setPadding((7 * density).toInt(), (2 * density).toInt(), (7 * density).toInt(), (2 * density).toInt())
            visibility = View.GONE
        }
    }

    /** Drives a settings row's own title pill ([setBadge]) from [feature]'s lock. */
    fun bindRowBadge(row: View, feature: PremiumFeature, setBadge: (String?) -> Unit) {
        bind(row) { setBadge(if (isShown(feature)) row.context.getString(R.string.premium_badge) else null) }
    }

    /** Shows [pill] while [feature] is locked, following entitlement while it is on screen. */
    fun bindPill(pill: TextView, feature: PremiumFeature) {
        bind(pill) { pill.visibility = if (isShown(feature)) View.VISIBLE else View.GONE }
    }
}
