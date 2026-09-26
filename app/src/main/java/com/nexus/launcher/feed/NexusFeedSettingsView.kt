package com.nexus.launcher.feed

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.data.FeedDao
import com.nexus.launcher.data.FeedSource
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.settings.views.SettingsSectionGroupView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Dedicated full-page view for Feed Sources & Settings, embedded directly in NexusFeedPage.
 */
class NexusFeedSettingsView(
    context: Context,
    private val scope: CoroutineScope,
    private val feedDao: FeedDao,
    private val sourceManager: NexusFeedSourceManager
) : LinearLayout(context) {

    private val dp = resources.displayMetrics.density
    private var tokens: NexusColorTokens = if (NexusFeedEInkCoordinator.isEInkMode(context)) {
        NexusFeedEInkCoordinator.getTokens(context)
    } else {
        ThemeObserver.currentTokens(context)
    }

    private val sourcesHost = FrameLayout(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    }
    private val emptySourcesView = TextView(context)
    private val curatedSection = NexusFeedCuratedSection(context, scope, sourceManager)
    private val urlInput: EditText
    private val addProgress: ProgressBar
    private val addStatusText: TextView
    private val addBtn: TextView
    private val addCardBg = GradientDrawable()
    var onEInkModeChanged: (() -> Unit)? = null
    private val einkCardBg = GradientDrawable()
    private val inputRowBg = GradientDrawable()
    private val addBtnBg = GradientDrawable()
    private val sectionHeaders = mutableListOf<TextView>()

    private var isDragging = false
    private var lastRenderedKeys = listOf<String>()
    private var currentSourcesList = listOf<FeedSource>()

    init {
        orientation = VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

        // 1. Reading Experience (E-Ink Paper Mode)
        addView(buildSectionHeader(context.getString(R.string.nexus_feed_eink_reading_section)))
        val isEInk = NexusFeedEInkCoordinator.isEInkMode(context)
        einkCardBg.setColor(tokens.surface)
        einkCardBg.cornerRadius = if (isEInk) 0f else 16 * dp
        einkCardBg.setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
        val einkCard = LinearLayout(context).apply {
            orientation = VERTICAL
            background = einkCardBg
            val padH = (14 * dp).toInt()
            val padV = (8 * dp).toInt()
            setPadding(padH, padV, padH, padV)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            addView(
                NexusFeedEInkMenuRow.build(
                    context = context,
                    tokens = tokens,
                    dp = dp,
                    onModeChanged = { onEInkModeChanged?.invoke() }
                )
            )
        }
        addView(einkCard)

        // 2. Add Source Section
        addView(buildSectionHeader(context.getString(R.string.nexus_feed_add_custom_source).uppercase()))
        addCardBg.setColor(tokens.surface)
        addCardBg.cornerRadius = if (isEInk) 0f else 16 * dp
        addCardBg.setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
        val addCard = LinearLayout(context).apply {
            orientation = VERTICAL
            background = addCardBg
            val pad = (14 * dp).toInt()
            setPadding(pad, pad, pad, pad)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        inputRowBg.setColor(tokens.surfaceRaised)
        inputRowBg.cornerRadius = if (isEInk) 0f else 12 * dp
        inputRowBg.setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
        val inputRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = inputRowBg
            setPadding((12 * dp).toInt(), (4 * dp).toInt(), (6 * dp).toInt(), (4 * dp).toInt())
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, (48 * dp).toInt())
        }

        urlInput = EditText(context).apply {
            hint = context.getString(R.string.nexus_feed_add_source_hint)
            setHintTextColor(tokens.textSecondary)
            typeface = if (isEInk) android.graphics.Typeface.MONOSPACE else null
            NexusTypeScale.body.bindTo(this, tokens.textPrimary)
            background = null
            isSingleLine = true
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        }
        inputRow.addView(urlInput)

        addProgress = ProgressBar(context).apply {
            layoutParams = LayoutParams((22 * dp).toInt(), (22 * dp).toInt()).apply { marginEnd = (6 * dp).toInt() }
            indeterminateDrawable?.setTint(tokens.textPrimary)
            visibility = View.GONE
        }
        inputRow.addView(addProgress)

        addBtnBg.setColor(tokens.surface)
        addBtnBg.cornerRadius = if (isEInk) 0f else 10 * dp
        addBtnBg.setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
        addBtn = TextView(context).apply {
            text = context.getString(R.string.action_add)
            typeface = if (isEInk) android.graphics.Typeface.MONOSPACE else null
            NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
            gravity = Gravity.CENTER
            background = addBtnBg
            setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                handleAddSource()
            }
        }
        inputRow.addView(addBtn)
        addCard.addView(inputRow)

        addStatusText = TextView(context).apply {
            NexusTypeScale.caption.bindTo(this, tokens.textPrimary)
            visibility = View.GONE
            setPadding(0, (6 * dp).toInt(), 0, 0)
        }
        addCard.addView(addStatusText)
        addView(addCard)

        // 2. Your Sources Section
        addView(buildSectionHeader(context.getString(R.string.nexus_feed_your_sources).uppercase()))
        emptySourcesView.apply {
            text = context.getString(R.string.nexus_feed_no_sources_yet)
            NexusTypeScale.body.bindTo(this, tokens.textSecondary)
            gravity = Gravity.CENTER
            val pad = (16 * dp).toInt()
            setPadding(pad, pad, pad, pad)
            visibility = View.GONE
        }
        addView(sourcesHost)

        // 3. Discover Sources Section
        addView(buildSectionHeader(context.getString(R.string.nexus_feed_discover_sources).uppercase()))
        addView(curatedSection)

        observeSources()
    }

    fun horizontalScrollStrips(): List<android.widget.HorizontalScrollView> =
        listOf(curatedSection.categoryTabsScroll)

    private fun buildSectionHeader(title: String): TextView {
        val isEInk = NexusFeedEInkCoordinator.isEInkMode(context)
        return TextView(context).apply {
            text = title
            typeface = if (isEInk) android.graphics.Typeface.MONOSPACE else null
            NexusTypeScale.labelSmall.bindTo(this, tokens.textSecondary)
            setPaddingRelative(0, (14 * dp).toInt(), 0, (6 * dp).toInt())
        }.also { sectionHeaders.add(it) }
    }

    private fun handleAddSource() {
        val query = urlInput.text.toString().trim()
        if (query.isBlank()) return
        addProgress.visibility = View.VISIBLE
        addStatusText.visibility = View.GONE

        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(urlInput.windowToken, 0)

        scope.launch {
            val result = sourceManager.addSource(query)
            addProgress.visibility = View.GONE
            addStatusText.visibility = View.VISIBLE
            result.onSuccess { createdSource: FeedSource ->
                urlInput.setText("")
                addStatusText.text = context.getString(R.string.nexus_feed_source_added, createdSource.title)
                addStatusText.setTextColor(tokens.textPrimary)
            }.onFailure { err ->
                addStatusText.text = err.message ?: context.getString(R.string.nexus_feed_source_add_failed)
                addStatusText.setTextColor(tokens.danger)
            }
        }
    }

    private fun observeSources() {
        scope.launch {
            feedDao.getAllSourcesFlow().collectLatest { sources ->
                if (!isDragging) renderSources(sources)
                curatedSection.updateExistingUrls(sources.map { it.url }.toSet())
            }
        }
    }

    private fun renderSources(sources: List<FeedSource>) {
        currentSourcesList = sources
        val currentKeys = sources.map { "${it.id}_${it.title}_${it.isEnabled}" }
        if (currentKeys == lastRenderedKeys && sourcesHost.childCount > 0) return
        lastRenderedKeys = currentKeys
        sourcesHost.removeAllViews()

        if (sources.isEmpty()) {
            emptySourcesView.visibility = View.VISIBLE
            sourcesHost.addView(emptySourcesView)
            return
        }
        emptySourcesView.visibility = View.GONE

        val isEInk = NexusFeedEInkCoordinator.isEInkMode(context)
        val effectiveTokens = if (isEInk) NexusFeedEInkCoordinator.getTokens(context) else tokens
        tokens = effectiveTokens

        val group = SettingsSectionGroupView(context).apply { paperMode = isEInk }
        sources.forEach { source ->
            group.addChildRow(
                NexusFeedSourceRowBinder.buildSourceRow(
                    context = context,
                    source = source,
                    dp = dp,
                    scope = scope,
                    sourceManager = sourceManager,
                    sourcesContainer = group,
                    tokens = effectiveTokens,
                    isEInk = isEInk,
                    onDragStateChanged = { isDragging = it },
                    onReorderCommitted = { newIds ->
                        lastRenderedKeys = emptyList()
                        scope.launch { sourceManager.reorderSources(newIds) }
                    }
                )
            )
        }
        group.applyTokens(effectiveTokens)
        sourcesHost.addView(group)
    }

    fun applyTokens(newTokens: NexusColorTokens) {
        val isEInk = NexusFeedEInkCoordinator.isEInkMode(context)
        tokens = if (isEInk) NexusFeedEInkCoordinator.getTokens(context) else newTokens
        val strokeWidth = (1 * dp).toInt().coerceAtLeast(1)
        einkCardBg.setColor(tokens.surface)
        einkCardBg.cornerRadius = if (isEInk) 0f else 16 * dp
        einkCardBg.setStroke(strokeWidth, tokens.divider)
        addCardBg.setColor(tokens.surface)
        addCardBg.cornerRadius = if (isEInk) 0f else 16 * dp
        addCardBg.setStroke(strokeWidth, tokens.divider)
        inputRowBg.setColor(tokens.surfaceRaised)
        inputRowBg.cornerRadius = if (isEInk) 0f else 12 * dp
        inputRowBg.setStroke(strokeWidth, tokens.divider)
        addBtnBg.setColor(tokens.surface)
        addBtnBg.cornerRadius = if (isEInk) 0f else 10 * dp
        addBtnBg.setStroke(strokeWidth, tokens.divider)
        addBtn.typeface = if (isEInk) android.graphics.Typeface.MONOSPACE else null
        NexusTypeScale.bodyStrong.bindTo(addBtn, tokens.textPrimary)
        urlInput.setHintTextColor(tokens.textSecondary)
        urlInput.typeface = if (isEInk) android.graphics.Typeface.MONOSPACE else null
        NexusTypeScale.body.bindTo(urlInput, tokens.textPrimary)
        addProgress.indeterminateDrawable?.setTint(tokens.textPrimary)
        NexusTypeScale.caption.bindTo(addStatusText, tokens.textPrimary)
        NexusTypeScale.body.bindTo(emptySourcesView, tokens.textSecondary)
        sectionHeaders.forEach {
            it.typeface = if (isEInk) android.graphics.Typeface.MONOSPACE else null
            NexusTypeScale.labelSmall.bindTo(it, tokens.textSecondary)
        }
        curatedSection.applyTokens(tokens, isEInk)
        lastRenderedKeys = emptyList()
        renderSources(currentSourcesList)
    }
}
