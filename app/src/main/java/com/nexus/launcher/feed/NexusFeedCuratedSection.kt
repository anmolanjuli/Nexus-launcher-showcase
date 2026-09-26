package com.nexus.launcher.feed

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeEntryPoint
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.settings.views.SettingsSectionGroupView
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Locale

/** Curated-source rows are pillow-grouped via [SettingsSectionGroupView] — same one card, hairline
 *  -separated rows every Settings screen uses — not an independent per-row card design. */
class NexusFeedCuratedSection(
    context: Context,
    private val scope: CoroutineScope,
    private val sourceManager: NexusFeedSourceManager
) : LinearLayout(context) {

    private val dp = resources.displayMetrics.density
    private var selectedCategory = "All"
    private var existingUrls = setOf<String>()
    private var tokens: NexusColorTokens = if (NexusFeedEInkCoordinator.isEInkMode(context)) {
        NexusFeedEInkCoordinator.getTokens(context)
    } else {
        try {
            ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }
    }

    private val titleView: TextView
    private val tabsContainer: LinearLayout
    /** Exposed so the page-level dismiss gesture yields to category-tab scrolling. */
    val categoryTabsScroll: HorizontalScrollView
    private val emptyView: TextView
    /** Holds at most one [SettingsSectionGroupView] — rebuilt fresh on every render since that
     *  component only exposes an additive addChildRow() API, with no way to clear stale rows. */
    private val groupHost: android.widget.FrameLayout
    private var themeJob: Job? = null

    init {
        orientation = VERTICAL

        titleView = TextView(context).apply {
            text = context.getString(R.string.nexus_feed_curated_for_you)
            typeface = if (NexusFeedEInkCoordinator.isEInkMode(context)) android.graphics.Typeface.MONOSPACE else null
            NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            setPadding(0, (22 * dp).toInt(), 0, (12 * dp).toInt())
        }
        addView(titleView)

        categoryTabsScroll = HorizontalScrollView(context).apply {
            overScrollMode = View.OVER_SCROLL_NEVER
            isHorizontalScrollBarEnabled = false
        }
        tabsContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            setPadding(0, (4 * dp).toInt(), 0, (14 * dp).toInt())
        }
        categoryTabsScroll.addView(tabsContainer)
        addView(categoryTabsScroll)

        emptyView = TextView(context).apply {
            text = context.getString(R.string.nexus_feed_all_curated_added)
            NexusTypeScale.body.bindTo(this, tokens.textSecondary)
            gravity = Gravity.CENTER
            setPadding((16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt())
            visibility = View.GONE
        }
        addView(emptyView)

        groupHost = android.widget.FrameLayout(context)
        addView(groupHost)

        renderTabs()
        renderCuratedList()
        setupThemeBinding()
    }

    fun updateExistingUrls(urls: Set<String>) {
        if (existingUrls != urls) {
            existingUrls = urls
            renderCuratedList()
        }
    }

    private fun renderTabs() {
        tabsContainer.removeAllViews()
        val isEInk = NexusFeedEInkCoordinator.isEInkMode(context)
        val allCats = listOf("All") + CuratedFeedSources.CATEGORIES
        for (cat in allCats) {
            val isSelected = cat.equals(selectedCategory, ignoreCase = true)
            val tab = TextView(context).apply {
                text = if (cat == "All") "All" else cat.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
                typeface = if (isEInk) android.graphics.Typeface.MONOSPACE else null
                NexusTypeScale.bodyStrong.bindTo(this, if (isSelected) tokens.textPrimary else tokens.textSecondary)
                background = GradientDrawable().apply {
                    setColor(if (isSelected) tokens.surfaceRaised else tokens.surface)
                    cornerRadius = if (isEInk) 0f else 9999f
                    setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
                }
                setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
                layoutParams = LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginEnd = (8 * dp).toInt()
                }
                setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                    selectedCategory = cat
                    renderTabs()
                    renderCuratedList()
                }
            }
            tabsContainer.addView(tab)
        }
    }

    private fun renderCuratedList() {
        val list = (if (selectedCategory.equals("All", ignoreCase = true)) {
            CuratedFeedSources.ALL_SOURCES
        } else {
            CuratedFeedSources.ALL_SOURCES.filter { it.category.equals(selectedCategory, ignoreCase = true) }
        }).filter { !existingUrls.contains(it.url) }

        groupHost.removeAllViews()
        if (list.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            return
        }
        emptyView.visibility = View.GONE

        val isEInk = NexusFeedEInkCoordinator.isEInkMode(context)
        val effectiveTokens = if (isEInk) NexusFeedEInkCoordinator.getTokens(context) else tokens
        tokens = effectiveTokens

        val group = SettingsSectionGroupView(context).apply {
            paperMode = NexusFeedEInkCoordinator.isEInkMode(context)
        }
        for (item in list) {
            group.addChildRow(buildCuratedRow(item))
        }
        group.applyTokens(effectiveTokens)
        groupHost.addView(group)
    }

    private fun buildCuratedRow(item: CuratedFeedSource): View {
        val isEInk = NexusFeedEInkCoordinator.isEInkMode(context)
        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
        }

        row.addView(TextView(context).apply {
            text = NexusFeedImageLoader.getMonogram(item.name)
            typeface = if (isEInk) android.graphics.Typeface.MONOSPACE else null
            NexusTypeScale.labelSmall.bindTo(this, tokens.textPrimary)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(tokens.surfaceRaised)
                if (isEInk) {
                    cornerRadius = 0f
                    setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
                } else {
                    shape = GradientDrawable.OVAL
                }
            }
            layoutParams = LinearLayout.LayoutParams((44 * dp).toInt(), (44 * dp).toInt()).apply {
                marginEnd = (14 * dp).toInt()
            }
        })

        row.addView(LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(context).apply {
                text = item.name
                typeface = if (isEInk) android.graphics.Typeface.MONOSPACE else null
                NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            })
            addView(TextView(context).apply {
                text = item.category.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
                typeface = if (isEInk) android.graphics.Typeface.MONOSPACE else null
                NexusTypeScale.caption.bindTo(this, tokens.textSecondary)
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = (2 * dp).toInt()
                }
            })
        })

        row.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_add)
            imageTintList = ColorStateList.valueOf(tokens.textPrimary)
            layoutParams = LinearLayout.LayoutParams((36 * dp).toInt(), (36 * dp).toInt())
            setPadding((8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                scope.launch { sourceManager.addCuratedSource(item.name, item.category, item.url) }
            }
        })

        return row
    }

    fun applyTokens(newTokens: NexusColorTokens, isEInk: Boolean = NexusFeedEInkCoordinator.isEInkMode(context)) {
        tokens = if (isEInk) NexusFeedEInkCoordinator.getTokens(context) else newTokens
        titleView.typeface = if (isEInk) android.graphics.Typeface.MONOSPACE else null
        NexusTypeScale.bodyStrong.bindTo(titleView, tokens.textPrimary)
        NexusTypeScale.body.bindTo(emptyView, tokens.textSecondary)
        renderTabs()
        renderCuratedList()
    }

    private fun setupThemeBinding() {
        addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = subscribe()
            override fun onViewDetachedFromWindow(v: View) {
                themeJob?.cancel(); themeJob = null
            }
        })
        if (ViewCompat.isAttachedToWindow(this)) subscribe()
    }

    private fun subscribe() {
        val owner: LifecycleOwner = findViewTreeLifecycleOwner() ?: return
        themeJob?.cancel()
        themeJob = owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    val controller = EntryPointAccessors.fromApplication(
                        context.applicationContext, ThemeEntryPoint::class.java
                    ).themeController()
                    controller.currentTokens.collect { themeTokens ->
                        val isEInk = NexusFeedEInkCoordinator.isEInkMode(context)
                        val activeTokens = if (isEInk) NexusFeedEInkCoordinator.getTokens(context) else themeTokens
                        applyTokens(activeTokens, isEInk)
                    }
                } catch (_: Exception) {}
            }
        }
    }
}
