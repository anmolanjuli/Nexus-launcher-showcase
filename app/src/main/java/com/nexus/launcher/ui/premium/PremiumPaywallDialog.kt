package com.nexus.launcher.ui.premium

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.nexus.launcher.R
import com.nexus.launcher.premium.PremiumFeature
import com.nexus.launcher.premium.PremiumManager
import com.nexus.launcher.premium.PremiumPlan
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * The paywall: a full-screen modal that asks how the user wants to pay before it shows a price.
 *
 * ## Why it is shaped as a question
 *
 * A conventional paywall opens with three columns and lets the user work out which one insults
 * them least. This one asks first, because the two honest answers — *buy it once* and *pay us
 * every month* — want different pitches, and showing both at full volume makes the cheap one look
 * like a trick. Splitting them lets each be written plainly. What each step says lives in
 * [PaywallSteps]; how it looks lives in [PaywallViews]; this class is only the window, the step
 * host, and the transitions between them.
 *
 * One [Dialog] holds all four steps in a single host, swapped with a cross-fade rather than four
 * separate dialogs — the window chrome and theme tokens are then resolved once, and a step change
 * cannot flash the wallpaper between two windows.
 *
 * ## The modal closes on the entitlement, not on the tap
 *
 * [onPurchase] only opens Google Play's sheet; the user is still deciding when it returns. So this
 * dialog never dismisses itself on a purchase action — it watches [PremiumManager.isPremium] and
 * closes when Premium actually arrives, whether that is thirty seconds later or never. Dismissing
 * on the tap would be indistinguishable, from the user's side, from a purchase that silently
 * failed.
 *
 * Nothing in this file can grant an entitlement of its own.
 */
class PremiumPaywallDialog private constructor(
    private val host: Activity,
    feature: PremiumFeature?,
    private val onPurchase: (Activity, PremiumPlan) -> Unit,
) : Dialog(host, android.R.style.Theme_Translucent_NoTitleBar) {

    private val tokens: NexusColorTokens = runCatching { ThemeObserver.currentTokens(host) }
        .getOrDefault(NexusColorTokens.Dark)
    private val views = PaywallViews(host, tokens)
    private val dp = views.dp

    private lateinit var stepHost: FrameLayout
    private var step = PaywallStep.CHOICE
    private var scope: CoroutineScope? = null
    private var entitlementJob: Job? = null

    /** Which subscription the Rich path would buy. Ignored on the Broke path. */
    private var chosenSubscription = PremiumPlan.YEARLY

    private val steps = PaywallSteps(
        views = views,
        feature = feature,
        subscription = { chosenSubscription },
        onSubscriptionPicked = { chosenSubscription = it },
        goTo = { showStep(it) },
        onPurchase = { plan -> onPurchase(host, plan) },
        onDismiss = { dismiss() },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setCanceledOnTouchOutside(false)
        window?.let(::applyWindowChrome)
        setContentView(buildRoot())
        showStep(PaywallStep.CHOICE, animate = false)
    }

    /**
     * Closes the moment Premium is actually granted.
     *
     * `drop(1)` skips the flow's current value: the Premium page can open this while already
     * subscribed, and without the drop it would close again immediately.
     */
    override fun onStart() {
        super.onStart()
        val active = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope = active
        entitlementJob = active.launch {
            PremiumManager.isPremium.drop(1).collect { premium -> if (premium) dismiss() }
        }
    }

    override fun onStop() {
        super.onStop()
        entitlementJob?.cancel()
        entitlementJob = null
        scope?.cancel()
        scope = null
    }

    /**
     * Back walks the steps rather than closing the modal.
     *
     * Each step was reached by a deliberate tap, so collapsing the whole thing on the first Back
     * would throw away a choice the user just made. Only the opening question closes.
     */
    @Deprecated("Dialog has no OnBackPressedDispatcher; this is the supported override.")
    override fun onBackPressed() {
        when (step) {
            PaywallStep.CHOICE -> @Suppress("DEPRECATION") super.onBackPressed()
            PaywallStep.BROKE, PaywallStep.RICH -> showStep(PaywallStep.CHOICE)
            PaywallStep.CONFIRM -> showStep(PaywallStep.RICH)
        }
    }

    private fun applyWindowChrome(window: Window) {
        window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window.setLayout(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
        )
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        // The scrim is painted by the root view rather than FLAG_DIM_BEHIND: this window already
        // covers the screen, so a platform dim would only darken it a second time.
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }

    private fun buildRoot(): View {
        stepHost = FrameLayout(host)

        val column = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(
                stepHost,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        val scroll = ScrollView(host).apply {
            // Fills the viewport so the column's CENTER gravity actually centres the card, and
            // scrolls instead of clipping once a step is taller than the screen.
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(
                column,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ),
            )
        }

        val close = buildCloseButton()

        return FrameLayout(host).apply {
            // Scrim, not a solid fill: whatever is behind stays faintly readable, which keeps this
            // feeling like a layer over the app rather than a new screen.
            setBackgroundColor(views.withAlpha(tokens.bg, 0.94f))
            addView(
                scroll,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            addView(close)
            ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
                val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                column.setPadding(
                    (22 * dp).toInt(),
                    bars.top + (24 * dp).toInt(),
                    (22 * dp).toInt(),
                    bars.bottom + (24 * dp).toInt(),
                )
                (close.layoutParams as FrameLayout.LayoutParams).topMargin =
                    bars.top + (10 * dp).toInt()
                close.requestLayout()
                insets
            }
        }
    }

    /**
     * The escape hatch.
     *
     * The opening question offers no decline of its own — both answers move forward — so without
     * this the first step would be a trap for anyone who opened it by accident.
     */
    private fun buildCloseButton(): TextView = TextView(host).apply {
        text = "✕"
        contentDescription = host.getString(R.string.premium_paywall_close)
        gravity = Gravity.CENTER
        NexusTypeScale.body.bindTo(this, tokens.textSecondary)
        val size = (40 * dp).toInt()
        layoutParams = FrameLayout.LayoutParams(size, size).apply {
            gravity = Gravity.TOP or Gravity.END
            marginEnd = (12 * dp).toInt()
        }
        background = GradientDrawable().apply {
            cornerRadius = 999f
            setColor(views.withAlpha(tokens.surfaceRaised, 0.65f))
        }
        isClickable = true
        isFocusable = true
        setOnClickListener {
            views.tap(it)
            dismiss()
        }
    }

    private fun showStep(next: PaywallStep, animate: Boolean = true) {
        step = next
        val view = steps.build(next)
        if (!animate) {
            stepHost.removeAllViews()
            stepHost.addView(view)
            return
        }
        val outgoing = stepHost.getChildAt(0)
        view.alpha = 0f
        view.translationY = 14 * dp
        stepHost.addView(view)
        view.animate().alpha(1f).translationY(0f).setDuration(190L).start()
        outgoing?.animate()?.alpha(0f)?.setDuration(120L)?.withEndAction {
            stepHost.removeView(outgoing)
        }?.start()
    }

    companion object {

        /**
         * Shows the paywall over whatever activity [context] belongs to.
         *
         * Returns false when there is no activity to host a dialog — a widget host or service
         * context — so the caller can fall back to opening the Premium page instead.
         */
        fun show(
            context: Context,
            feature: PremiumFeature? = null,
            onPurchase: (Activity, PremiumPlan) -> Unit,
        ): Boolean {
            val activity = context.findActivity() ?: return false
            if (activity.isFinishing || activity.isDestroyed) return false
            return runCatching {
                PremiumPaywallDialog(activity, feature, onPurchase).show()
                true
            }.getOrDefault(false)
        }

        private fun Context.findActivity(): Activity? {
            var current: Context? = this
            while (current is ContextWrapper) {
                if (current is Activity) return current
                current = current.baseContext
            }
            return null
        }
    }
}
