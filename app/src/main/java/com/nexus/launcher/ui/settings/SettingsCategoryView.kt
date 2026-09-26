package com.nexus.launcher.ui.settings

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeEntryPoint
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.glass.FrostedPanelLayout
import com.nexus.launcher.ui.glass.FrostedGlassEngine
import com.nexus.launcher.ui.settings.views.NexusNavRow
import com.nexus.launcher.ui.settings.views.NexusSection
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Settings landing hub: title, live search, grouped pages, version footer.
 */
class SettingsCategoryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr) {

    var onCategoryTapped: ((Int) -> Unit)? = null

    /** A setting found by search: its page, and the row label to scroll to on it. */
    var onControlTapped: ((Int, String) -> Unit)? = null

    private val density = resources.displayMetrics.density
    private val searchPill: FrostedPanelLayout
    private val titleView: TextView
    private val searchField: EditText
    private val searchIcon: ImageView
    /** One section per [SettingsHubGroup], built from the enum rather than named individually,
     *  so regrouping the hub is a change to the catalog alone. */
    private val groupSections: List<NexusSection>
    private val pagesResultSection: NexusSection
    private val controlsResultSection: NexusSection
    private val emptyView: TextView
    private val footerView: TextView
    private val defaultLauncherBanner: SettingsDefaultLauncherBanner
    private var currentTokens: NexusColorTokens = NexusColorTokens.Dark
    private var attachListener: ThemeAttachListener? = null

    init {
        orientation = VERTICAL
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        val pad = dp(16)
        setPadding(pad, 0, pad, dp(24))

        titleView = TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(8)
                bottomMargin = dp(12)
            }
            minHeight = dp(56)
            text = context.getString(R.string.settings_title)
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
        }
        NexusTypeScale.title.bindTo(titleView, NexusColorTokens.Dark.textPrimary)
        addView(titleView)

        val frostedTokens = FrostedGlassEngine.resolveFrostedTokens(NexusColorTokens.Dark)
        val fillAlpha = (FrostedGlassEngine.sheetFillAlpha(1f, 0.70f) * 255f).toInt()
        searchPill = FrostedPanelLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, dp(48)).apply {
                bottomMargin = dp(4)
            }
            setCornerRadiusPx(24 * density)
            setTint((frostedTokens.surface and 0x00FFFFFF) or (fillAlpha shl 24))
            setBorder(frostedTokens.border, (1 * density).coerceAtLeast(1f))
            val hPad = dp(16)
            setPadding(hPad, 0, hPad, 0)
        }
        searchIcon = ImageView(context).apply {
            layoutParams = LayoutParams(dp(18), dp(18)).apply { marginEnd = dp(10) }
            setImageResource(R.drawable.ic_search)
            imageTintList = ColorStateList.valueOf(NexusColorTokens.Dark.textSecondary)
        }
        searchField = EditText(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            background = null
            setPadding(0, 0, 0, 0)
            hint = context.getString(R.string.settings_search_hint)
            setHintTextColor(NexusColorTokens.Dark.textSecondary)
            setTextColor(NexusColorTokens.Dark.textPrimary)
            maxLines = 1
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }
        NexusTypeScale.body.bindTo(searchField, NexusColorTokens.Dark.textPrimary)
        searchField.doAfterTextChanged { text -> applyQuery(text?.toString().orEmpty()) }
        searchField.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchField.clearFocus()
                true
            } else {
                false
            }
        }
        searchPill.setOnClickListener { searchField.requestFocus() }
        searchPill.addView(searchIcon)
        searchPill.addView(searchField)
        addView(searchPill)

        defaultLauncherBanner = SettingsDefaultLauncherBanner(context)
        addView(defaultLauncherBanner.view)

        groupSections = SettingsHubGroup.entries.map { section(it) }
        groupSections.forEach { addView(it) }

        pagesResultSection = NexusSection(context).apply {
            setTitle(context.getString(R.string.settings_search_pages))
            visibility = GONE
        }
        controlsResultSection = NexusSection(context).apply {
            setTitle(context.getString(R.string.settings_search_controls))
            visibility = GONE
        }
        addView(pagesResultSection)
        addView(controlsResultSection)

        emptyView = TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(28)
            }
            gravity = Gravity.CENTER
            visibility = GONE
        }
        NexusTypeScale.body.bindTo(emptyView, NexusColorTokens.Dark.textSecondary)
        addView(emptyView)

        addView(View(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        })

        footerView = TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(32)
                bottomMargin = dp(8)
            }
            gravity = Gravity.CENTER
            text = context.getString(
                R.string.settings_hub_footer,
                context.getString(R.string.app_name),
                installedVersionName(),
                context.getString(R.string.settings_release_year_placeholder),
            )
        }
        NexusTypeScale.iconLabel.bindTo(footerView, NexusColorTokens.Dark.textSecondary)
        addView(footerView)

        setupThemeBinding()
    }

    fun applyTokens(tokens: NexusColorTokens) {
        currentTokens = tokens
        // Was unconditionally opaque `tokens.bg` on itself AND its parent NestedScrollView AND
        // (redundantly, and in a straight race with SettingsActivity's own collector) on
        // settings_category_container / settings_hub_inset directly — completely hiding whatever
        // frosted-wallpaper background SettingsActivity.applyChromeBackground paints behind this
        // view, and fighting that same call for ownership of the outer containers' background.
        // SettingsActivity now owns settings_category_container/settings_hub_inset exclusively;
        // this view and its immediate scroll parent just need to go transparent so that shows
        // through in Frosted Glass mode, same as HomeScreenSettingsFragment's own root/preview/
        // scroll layers do for the fragment side of the same screen.
        if (com.nexus.launcher.ui.glass.FrostedGlassEngine.isGlobalFrostedGlassEnabled) {
            setBackgroundColor(android.graphics.Color.TRANSPARENT)
            (parent as? View)?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        } else {
            setBackgroundColor(tokens.bg)
            (parent as? View)?.setBackgroundColor(tokens.bg)
        }
        titleView.setTextColor(tokens.textPrimary)
        val frostedTokens = FrostedGlassEngine.resolveFrostedTokens(tokens)
        // The pill sits on the frosted pane, not on the wallpaper, so matching the pane's own
        // fill alpha made it dissolve into its background. It reads as an interactive control
        // only if it is lighter and more opaque than the surface under it: surfaceRaised rather
        // than surface, a floor under the alpha so it stays present at any refraction, and the
        // canonical glass edge at the same weight every other glass surface uses.
        val paneAlpha = FrostedGlassEngine.sheetFillAlpha(1f, 0.70f)
        val pillAlpha = ((paneAlpha + 0.22f).coerceAtMost(1f) * 255f).toInt().coerceIn(0, 255)
        searchPill.setTint((frostedTokens.surfaceRaised and 0x00FFFFFF) or (pillAlpha shl 24))
        searchPill.setBorder(
            if (FrostedGlassEngine.isGlobalFrostedGlassEnabled) {
                FrostedGlassEngine.CANONICAL_GLASS_BORDER_ARGB
            } else {
                frostedTokens.border
            },
            (1.5f * density).coerceAtLeast(1f)
        )
        searchIcon.imageTintList = ColorStateList.valueOf(tokens.textSecondary)
        searchField.setHintTextColor(tokens.textSecondary)
        searchField.setTextColor(tokens.textPrimary)
        emptyView.setTextColor(tokens.textSecondary)
        footerView.setTextColor(tokens.textSecondary)
        groupSections.forEach { it.applyTokens(tokens) }
        pagesResultSection.applyTokens(tokens)
        controlsResultSection.applyTokens(tokens)
        defaultLauncherBanner.applyTokens(tokens)
        defaultLauncherBanner.refreshVisibility(searchField.text?.isNotEmpty() == true)
    }

    private fun section(group: SettingsHubGroup): NexusSection {
        return NexusSection(context).apply {
            setTitle(context.getString(group.titleRes))
            setRows(SettingsHubCatalog.pagesIn(group).map { hubRow(it) })
        }
    }

    private fun hubRow(page: SettingsHubPage, subtitleOverride: String? = null, onClick: (() -> Unit)? = null): NexusNavRow {
        return NexusNavRow(
            context,
            title = context.getString(page.titleRes),
            subtitle = subtitleOverride ?: context.getString(page.subtitleRes),
            iconRes = page.iconRes,
        ) {
            if (onClick != null) onClick() else onCategoryTapped?.invoke(page.pagerIndex)
        }.apply { setLandingTitle() }
    }

    private fun applyQuery(raw: String) {
        val query = raw.trim()
        if (query.isEmpty()) {
            defaultLauncherBanner.refreshVisibility(isSearching = false)
            groupSections.forEach { it.visibility = VISIBLE }
            pagesResultSection.visibility = GONE
            controlsResultSection.visibility = GONE
            emptyView.visibility = GONE
            return
        }
        defaultLauncherBanner.refreshVisibility(isSearching = true)
        groupSections.forEach { it.visibility = GONE }
        val result = SettingsHubCatalog.search(context, query)
        if (result.isEmpty) {
            pagesResultSection.visibility = GONE
            controlsResultSection.visibility = GONE
            emptyView.visibility = VISIBLE
            emptyView.text = context.getString(R.string.settings_search_empty, query)
            return
        }
        emptyView.visibility = GONE
        if (result.pages.isNotEmpty()) {
            pagesResultSection.setRows(result.pages.map { hubRow(it) })
            pagesResultSection.applyTokens(currentTokens)
            pagesResultSection.visibility = VISIBLE
        } else {
            pagesResultSection.visibility = GONE
        }
        if (result.controls.isNotEmpty()) {
            controlsResultSection.setRows(
                result.controls.map { control ->
                    val page = SettingsHubCatalog.pageAt(control.pagerIndex)
                    val label = context.getString(control.titleRes)
                    hubRow(page, subtitleOverride = context.getString(page.titleRes)) {
                        onControlTapped?.invoke(control.pagerIndex, label)
                    }.apply { setTitle(label) }
                },
            )
            controlsResultSection.applyTokens(currentTokens)
            controlsResultSection.visibility = VISIBLE
        } else {
            controlsResultSection.visibility = GONE
        }
    }

    private fun installedVersionName(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
    }

    private fun setupThemeBinding() {
        val listener = ThemeAttachListener(this)
        attachListener = listener
        addOnAttachStateChangeListener(listener)
        if (ViewCompat.isAttachedToWindow(this)) {
            listener.subscribe(this)
        }
    }

    private fun dp(value: Int): Int = (value * density).toInt()

    private class ThemeAttachListener(private val view: SettingsCategoryView) : OnAttachStateChangeListener {
        private var job: Job? = null

        override fun onViewAttachedToWindow(v: View) {
            subscribe(v as? SettingsCategoryView ?: view)
        }

        override fun onViewDetachedFromWindow(v: View) {
            cleanup()
        }

        fun cleanup() {
            job?.cancel()
            job = null
        }

        fun subscribe(target: SettingsCategoryView) {
            cleanup()
            val owner = target.findViewTreeLifecycleOwner() ?: (target.context as? LifecycleOwner)
            if (owner != null) {
                try {
                    val controller = EntryPointAccessors.fromApplication(
                        target.context.applicationContext,
                        ThemeEntryPoint::class.java,
                    ).themeController()
                    job = owner.lifecycleScope.launch {
                        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                            controller.currentTokens.collect { tokens ->
                                target.applyTokens(tokens)
                            }
                        }
                    }
                } catch (_: Exception) {
                    target.applyTokens(NexusColorTokens.Dark)
                }
            } else {
                target.applyTokens(NexusColorTokens.Dark)
            }
        }
    }

    fun refreshDefaultLauncherState() {
        defaultLauncherBanner.refreshVisibility(searchField.text?.isNotEmpty() == true)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        refreshDefaultLauncherState()
    }

    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus) {
            refreshDefaultLauncherState()
        }
    }
}
