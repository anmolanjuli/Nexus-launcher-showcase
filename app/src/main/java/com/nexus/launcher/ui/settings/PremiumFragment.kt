package com.nexus.launcher.ui.settings

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nexus.launcher.R
import com.nexus.launcher.premium.PremiumFeature
import com.nexus.launcher.premium.PremiumManager
import com.nexus.launcher.premium.PremiumPurchase
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeEntryPoint
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.premium.PremiumPaywallDialog
import com.nexus.launcher.ui.premium.showcase.PremiumShowcaseGrid
import com.nexus.launcher.ui.premium.showcase.PremiumShowcasePreview
import com.nexus.launcher.ui.premium.showcase.PremiumShowcaseSections
import com.nexus.launcher.ui.settings.views.NexusNavRow
import com.nexus.launcher.ui.settings.views.NexusSection
import com.nexus.launcher.ui.settings.views.NexusToggleRow
import com.nexus.launcher.ui.settings.views.SettingsSectionGroupView
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

/**
 * The Premium page: a showcase of what Premium feels like, then one way to get it.
 *
 * Top to bottom: a heading, the flagship feature as a hero, four features as picture tiles, every
 * other feature behind an expander, and a footer whose button opens [PremiumPaywallDialog] — the
 * paywall and its tone are unchanged. Any feature opens [PremiumShowcasePreview] full screen, and
 * closing that returns here scrolled to the feature with a brief accent ring.
 *
 * The showcase is rebuilt on a theme or entitlement change rather than re-tinted: it is small, and
 * almost all of it is pictures drawn from the tokens.
 */
class PremiumFragment : Fragment() {

    private lateinit var scrollView: ScrollView
    private lateinit var content: LinearLayout
    private lateinit var showcase: LinearLayout
    private lateinit var mainSection: NexusSection
    private lateinit var headerDev: NexusNavRow
    private lateinit var devGroup: SettingsSectionGroupView
    private lateinit var devToggleRow: NexusToggleRow

    private var currentTokens: NexusColorTokens = NexusColorTokens.Dark
    private var isDevExpanded = true
    private var isMoreExpanded = false
    private val featureViews = mutableMapOf<PremiumFeature, View>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val dp = resources.displayMetrics.density
        val padH = (16 * dp).toInt()
        currentTokens = runCatching { ThemeObserver.currentTokens(requireContext()) }
            .getOrDefault(NexusColorTokens.Dark)

        scrollView = ScrollView(requireContext()).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            clipToPadding = false
        }
        content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setPadding(padH, 0, padH, (80 * dp).toInt())
            clipChildren = false
        }

        showcase = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            clipChildren = false
        }
        content.addView(showcase)
        renderShowcase()

        mainSection = NexusSection(requireContext()).apply { isTransparentCard = true }
        // Developer override: removed before shipping.
        devToggleRow = NexusToggleRow(requireContext()).apply {
            configure(
                label = getString(R.string.premium_dev_unlock),
                checked = PremiumManager.isPremium.value,
                subtitle = getString(R.string.premium_dev_unlock_desc),
            )
            setBadge(getString(R.string.settings_badge_beta))
            onCheckedChanged = { checked ->
                PremiumManager.setPremiumForTesting(requireContext(), checked)
            }
            applyTokens(currentTokens)
        }
        devGroup = SettingsSectionGroupView(requireContext()).apply {
            addChildRow(devToggleRow)
            applyTokens(currentTokens)
        }
        headerDev = sectionHeader(currentTokens)
        mainSection.addRow(LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(headerDev)
            addView(devGroup)
        })
        mainSection.applyTokens(currentTokens)
        content.addView(mainSection)

        scrollView.addView(content)
        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            content.setPadding(padH, (4 * dp).toInt(), padH, bars.bottom + (80 * dp).toInt())
            insets
        }
        return scrollView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    val controller = EntryPointAccessors.fromApplication(
                        requireContext().applicationContext,
                        ThemeEntryPoint::class.java,
                    ).themeController()
                    controller.currentTokens.collect { tokens -> updateTokens(tokens) }
                }
                launch {
                    PremiumManager.isPremium.collect { premium ->
                        renderShowcase()
                        devToggleRow.setChecked(premium)
                    }
                }
            }
        }
    }

    private fun updateTokens(tokens: NexusColorTokens) {
        currentTokens = tokens
        renderShowcase()
        headerDev.applyTokens(tokens)
        devGroup.applyTokens(tokens)
        devToggleRow.applyTokens(tokens)
        mainSection.applyTokens(tokens)
    }

    private fun renderShowcase() {
        val context = context ?: return
        val dp = resources.displayMetrics.density
        val tokens = currentTokens
        showcase.removeAllViews()
        featureViews.clear()
        val register: (PremiumFeature, View) -> Unit = { feature, view -> featureViews[feature] = view }

        showcase.addView(TextView(context).apply {
            text = getString(R.string.premium_showcase_title)
            NexusTypeScale.title.bindTo(this, tokens.textPrimary)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = (8 * dp).toInt()
        })
        showcase.addView(TextView(context).apply {
            text = getString(R.string.premium_showcase_subtitle)
            NexusTypeScale.caption.bindTo(this, tokens.textSecondary)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = (4 * dp).toInt()
            bottomMargin = (16 * dp).toInt()
        })

        showcase.addView(PremiumShowcaseSections.hero(context, HERO, tokens, ::openPreview).also { register(HERO, it) })
        showcase.addView(PremiumShowcaseGrid.tiles(context, GRID, tokens, ::openPreview, register))
        showcase.addView(PremiumShowcaseGrid.more(
            context, rest(), tokens, isMoreExpanded, { isMoreExpanded = it }, ::openPreview, register,
        ))
        showcase.addView(PremiumShowcaseSections.footer(
            context, tokens, PremiumManager.isPremium.value, PremiumFeature.entries.size,
        ) {
            PremiumPaywallDialog.show(context, feature = null) { activity, plan ->
                PremiumPurchase.start(activity, plan)
            }
        })
    }

    private fun rest(): List<PremiumFeature> = PremiumFeature.entries.filter { it != HERO && it !in GRID }

    /** Where the page was when a preview opened; closing it puts the page back there. */
    private var scrollBeforePreview = 0

    private fun openPreview(feature: PremiumFeature) {
        scrollBeforePreview = scrollView.scrollY
        PremiumShowcasePreview.show(requireContext(), feature, currentTokens, ::highlight)
    }

    /**
     * Returns the page to where it was before the preview, and rings the feature just previewed.
     *
     * Closing the full-screen preview could leave the page scrolled to its end. Rather than race
     * whatever moves it, this waits until the page's window has focus again and its layout has
     * settled, restores the saved position outright, and scrolls further only when the feature is
     * not already on screen.
     */
    private fun highlight(feature: PremiumFeature) {
        if (!isAdded || view == null) return
        if (feature in rest() && !isMoreExpanded) {
            isMoreExpanded = true
            renderShowcase()
        }
        if (scrollView.hasWindowFocus()) {
            scrollView.post { settleAfterPreview(feature) }
        } else {
            scrollView.viewTreeObserver.addOnWindowFocusChangeListener(object : android.view.ViewTreeObserver.OnWindowFocusChangeListener {
                override fun onWindowFocusChanged(hasFocus: Boolean) {
                    if (!hasFocus) return
                    scrollView.viewTreeObserver.removeOnWindowFocusChangeListener(this)
                    scrollView.post { settleAfterPreview(feature) }
                }
            })
        }
    }

    private fun settleAfterPreview(feature: PremiumFeature) {
        if (!isAdded || view == null) return
        val target = featureViews[feature] ?: return
        val dp = resources.displayMetrics.density
        scrollView.scrollTo(0, scrollBeforePreview)
        run {
            var y = 0
            var v: View? = target
            while (v != null && v !== content) {
                y += v.top
                v = v.parent as? View
            }
            val margin = (24 * dp).toInt()
            val visibleTop = scrollView.scrollY
            val visibleBottom = visibleTop + scrollView.height
            if (y - margin < visibleTop || y + target.height + margin > visibleBottom) {
                scrollView.smoothScrollTo(0, (y - margin).coerceAtLeast(0))
            }

            val previous = target.foreground
            val ring = GradientDrawable().apply {
                cornerRadius = 22 * dp
                setStroke((2 * dp).toInt().coerceAtLeast(1), currentTokens.textPrimary)
            }
            target.foreground = ring
            ValueAnimator.ofInt(255, 0).apply {
                startDelay = 700
                duration = 900
                addUpdateListener { ring.alpha = it.animatedValue as Int }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        target.foreground = previous
                    }
                })
            }.start()
        }
    }

    private fun sectionHeader(tokens: NexusColorTokens): NexusNavRow {
        val dp = resources.displayMetrics.density
        lateinit var header: NexusNavRow
        header = NexusNavRow(
            requireContext(),
            title = getString(R.string.premium_section_developer),
            subtitle = getString(R.string.premium_section_developer_subtitle),
            iconRes = R.drawable.ic_admin,
            showChevron = true,
        ) {
            isDevExpanded = !isDevExpanded
            devGroup.visibility = if (isDevExpanded) View.VISIBLE else View.GONE
            header.setChevronRotation(if (isDevExpanded) 180f else 0f)
        }.apply {
            setContentPadding(0, (12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt())
            setChevronRotation(180f, animate = false)
            applyTokens(tokens)
        }
        return header
    }

    private companion object {
        /** The flagship: the look people see in screenshots of Nexus. */
        val HERO = PremiumFeature.FROSTED_GLASS

        val GRID = listOf(
            PremiumFeature.NEXUS_WIDGETS,
            PremiumFeature.EXTRA_THEMES,
            PremiumFeature.EINK_FEED,
            PremiumFeature.SMART_SEARCH,
        )
    }
}
