package com.nexus.launcher.feed

import android.content.Context
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.data.FeedArticle
import com.nexus.launcher.data.FeedDao
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob

class NexusFeedPage(
    context: Context,
    private val repository: NexusFeedRepository,
    private val feedDao: FeedDao,
    private val onFeedDismissProgress: (dx: Float, width: Float) -> Unit,
    private val onFeedDismissCommit: (commitDismiss: Boolean, velocityX: Float) -> Unit
) : FrameLayout(context) {

    private val dp = resources.displayMetrics.density
    private val activity = context as MainActivity
    private var pageJob = SupervisorJob()
    private var scope = CoroutineScope(Dispatchers.Main + pageJob)
    private val feedPrefs = context.getSharedPreferences(NexusFeedTimeRefreshHelper.PREFS_NAME, Context.MODE_PRIVATE)

    private val headerLayout: LinearLayout
    private val searchBarLayout: LinearLayout
    private val searchInput: EditText
    private val searchClearBtn: ImageView
    private val menuBtn: ImageView
    private val searchBtn: ImageView
    private val titleView: TextView
    private val categoryPills: NexusFeedCategoryPillsView
    private val scrollView: ScrollView
    private val tabHost: NexusFeedTabContentHost
    private val emptyView: TextView
    private val bottomBar: NexusFeedBottomBar
    private val gestureDetector: NexusFeedGestureDetector

    private val sourceManager = NexusFeedSourceManager(feedDao, repository)
    private val refreshController = NexusFeedRefreshController(context, repository, feedDao, feedPrefs)
    private val searchHelper = NexusFeedSearchHelper()
    private val tabManager: NexusFeedTabManager
    private var isHeadlineOnly = false
    private var activeTab = NexusFeedBottomBar.Tab.FEED
    private val displayManager: NexusFeedArticleDisplayManager
    private var currentTokens: NexusColorTokens = ThemeObserver.currentTokens(context)
    private var themeJob: Job? = null

    /** The UI Style flags this page's views were last built against. See [syncUiStyleIfChanged]. */
    private var styledForGlass = com.nexus.launcher.ui.glass.FrostedGlassEngine.isGlobalFrostedGlassEnabled
    private var styledForDefaultFlat = com.nexus.launcher.ui.glass.FrostedGlassEngine.isDefaultFlatStyleEnabled

    init {
        isClickable = true
        isFocusable = true
        val isEInk = NexusFeedEInkCoordinator.isEInkMode(context)
        if (isEInk) currentTokens = NexusFeedEInkCoordinator.getTokens(context)
        NexusFeedEInkCoordinator.clearRootHardwareLayer(this)
        applyBackgroundTokens(currentTokens, isEInk)
        isHeadlineOnly = feedPrefs.getBoolean("headline_only", false)

        val rootInsets = androidx.core.view.ViewCompat.getRootWindowInsets(activity.window.decorView)
        val initialSb = rootInsets?.getInsets(
            androidx.core.view.WindowInsetsCompat.Type.statusBars() or androidx.core.view.WindowInsetsCompat.Type.displayCutout()
        )?.top?.coerceAtLeast((24 * dp).toInt()) ?: NexusFeedThemeHelper.getStatusBarHeight(context, resources, dp)
        val initialNb = rootInsets?.getInsets(
            androidx.core.view.WindowInsetsCompat.Type.navigationBars()
        )?.bottom?.coerceAtLeast((16 * dp).toInt()) ?: (22 * dp).toInt()

        val header = NexusFeedHeaderBuilder.build(
            context = context,
            tokens = currentTokens,
            dp = dp,
            statusBarHeight = initialSb,
            onMenuClick = { showLeftCornerMenu() },
            onSearchClick = { toggleSearch() },
            onSearchTextChanged = { query -> searchHelper.onQueryChanged(query); observeArticles() }
        )
        headerLayout = header.headerLayout
        searchBarLayout = header.searchBarLayout
        searchInput = header.searchInput
        searchClearBtn = header.searchClearBtn
        menuBtn = header.menuBtn
        searchBtn = header.searchBtn
        titleView = header.titleView

        categoryPills = NexusFeedCategoryPillsView(context) { observeArticles() }

        tabHost = NexusFeedTabContentHost(context, dp)

        emptyView = NexusFeedThemeHelper.createEmptyView(context, dp, currentTokens)

        scrollView = ScrollView(context).apply {
            clipToPadding = false
            overScrollMode = OVER_SCROLL_NEVER
            setPadding(0, 0, 0, (88 * dp).toInt() + initialNb)
            addView(tabHost)
        }

        gestureDetector = NexusFeedGestureDetector(
            context = context,
            scrollView = scrollView,
            cardsContainer = tabHost,
            isRefreshingProvider = { refreshController.isRefreshing },
            onTriggerRefresh = { triggerRefresh() },
            onFeedDismissProgress = onFeedDismissProgress,
            onFeedDismissCommit = onFeedDismissCommit
        )
        // The pill row scrolls horizontally like any article strip; it just isn't produced by an
        // article render, so it has to be registered separately or a leftward drag across it is
        // read as a panel dismiss.
        gestureDetector.chromeStrips = listOf(categoryPills)

        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(headerLayout, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            addView(searchBarLayout)
            addView(categoryPills)
            addView(scrollView, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        }
        addView(contentLayout, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            NexusFeedInsetsLayout.apply(this, insets, headerLayout, contentLayout, scrollView, bottomBar, dp)
            insets
        }

        displayManager = NexusFeedArticleDisplayManager(
            context = context,
            dp = dp,
            tabHost = tabHost,
            emptyView = emptyView,
            onArticleLongPress = { showArticleMenu(it) },
            onStripsUpdated = { gestureDetector.horizontalStrips = it }
        )

        tabManager = NexusFeedTabManager(
            context = context,
            activity = activity,
            scope = scope,
            tabHost = tabHost,
            displayManager = displayManager,
            scrollView = scrollView,
            feedDao = feedDao,
            sourceManager = sourceManager,
            onEInkChanged = { onEInkChanged() }
        )

        bottomBar = NexusFeedBottomBar(context).apply {
            onTabSelected = { tab ->
                activeTab = tab
                gestureDetector.horizontalStrips = displayManager.stripsForTab(tab)
                tabHost.show(tab)
                updateTabHeader(tab)
                when (tab) {
                    NexusFeedBottomBar.Tab.SETTINGS -> tabManager.ensureSettingsTab { refreshController.cancelArticlesJob() }
                    NexusFeedBottomBar.Tab.LIBRARY -> tabManager.ensureLibraryTab { refreshController.cancelArticlesJob() }
                    else -> {
                        observeArticles()
                        if (tab == NexusFeedBottomBar.Tab.FEED) scrollView.scrollTo(0, 0)
                    }
                }
            }
        }
        addView(bottomBar, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.BOTTOM })
    }

    private fun updateTabHeader(tab: NexusFeedBottomBar.Tab) {
        titleView.text = context.getString(NexusFeedThemeHelper.getTabTitleRes(tab))
        val isFeedOrExplore = tab == NexusFeedBottomBar.Tab.FEED || tab == NexusFeedBottomBar.Tab.EXPLORE
        categoryPills.visibility = if (isFeedOrExplore) View.VISIBLE else View.GONE
        val isContentTab = tab != NexusFeedBottomBar.Tab.SETTINGS && tab != NexusFeedBottomBar.Tab.LIBRARY
        searchBtn.visibility = if (isContentTab) View.VISIBLE else View.GONE
        if (!isContentTab && searchBarLayout.visibility == View.VISIBLE) {
            toggleSearch()
        }
    }

    /** Re-applies everything that reads the UI Style mode (Default / Frosted Glass /
     *  Neumorphism) rather than the color tokens. */
    fun onUiStyleChanged() {
        styledForGlass = com.nexus.launcher.ui.glass.FrostedGlassEngine.isGlobalFrostedGlassEnabled
        styledForDefaultFlat = com.nexus.launcher.ui.glass.FrostedGlassEngine.isDefaultFlatStyleEnabled
        refreshContent()
    }

    fun onPanelOpening() {
        syncUiStyleIfChanged()
        if (!refreshController.isObservingSources) observeSources()
        refreshController.consumePendingRestoreRefresh(scope)
    }

    fun syncUiStyleIfChanged() {
        val isGlass = com.nexus.launcher.ui.glass.FrostedGlassEngine.isGlobalFrostedGlassEnabled
        val isDefaultFlat = com.nexus.launcher.ui.glass.FrostedGlassEngine.isDefaultFlatStyleEnabled
        if (isGlass == styledForGlass && isDefaultFlat == styledForDefaultFlat) return
        onUiStyleChanged()
    }

    fun onPanelClosed() {
        scrollView.scrollTo(0, 0)
        if (activeTab != NexusFeedBottomBar.Tab.FEED) {
            bottomBar.selectTab(NexusFeedBottomBar.Tab.FEED, notify = true)
        }
    }

    private fun showLeftCornerMenu() {
        val isEInk = NexusFeedEInkCoordinator.isEInkMode(context)
        NexusFeedMenuHelper.showMenu(
            context = context,
            menuBtn = menuBtn,
            currentTokens = currentTokens,
            dp = dp,
            isEInk = isEInk,
            chromeFillColor = NexusFeedThemeHelper.chromeFillColor(currentTokens.surfaceRaised),
            isHeadlineOnly = isHeadlineOnly,
            onHeadlineOnlyChanged = { enabled ->
                isHeadlineOnly = enabled
                feedPrefs.edit().putBoolean("headline_only", isHeadlineOnly).apply()
                refreshContent()
            },
            onOpenManageSources = {
                bottomBar.selectTab(NexusFeedBottomBar.Tab.SETTINGS, notify = true)
            },
            onEInkModeChanged = { onEInkChanged() }
        )
    }

    private fun onEInkChanged() {
        val isEInk = NexusFeedEInkCoordinator.isEInkMode(context)
        currentTokens = if (isEInk) NexusFeedEInkCoordinator.getTokens(context) else ThemeObserver.currentTokens(context)
        refreshContent()
    }

    private fun refreshContent() {
        applyTokens(currentTokens)
        displayManager.clearCache()
        renderArticles(refreshController.currentArticlesCache)
    }

    private fun toggleSearch() {
        searchHelper.toggleSearch(context, searchBarLayout, searchInput) {
            observeArticles()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (pageJob.isCancelled) {
            pageJob = SupervisorJob()
            scope = CoroutineScope(Dispatchers.Main + pageJob)
        }
        refreshController.resetTimeRefreshCheck()
        observeSources()
        observeArticles()
        themeJob?.cancel()
        themeJob = ThemeObserver.observe(context, scope) { tokens ->
            val isEInk = NexusFeedEInkCoordinator.isEInkMode(context)
            currentTokens = if (isEInk) NexusFeedEInkCoordinator.getTokens(context) else tokens
            refreshContent()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        themeJob?.cancel()
        refreshController.cancelActiveJobs()
        pageJob.cancel()
    }

    private val einkRefresh = NexusEInkRefreshGesture(this) {
        NexusFeedEInkCoordinator.isEInkMode(context)
    }

    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        einkRefresh.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    private fun applyBackgroundTokens(tokens: NexusColorTokens, isEInk: Boolean) {
        NexusFeedThemeHelper.applyBackground(this, tokens, isEInk)
    }

    private fun applyTokens(tokens: NexusColorTokens) {
        val isEInk = NexusFeedEInkCoordinator.isEInkMode(context)
        val effectiveTokens = if (isEInk) NexusFeedEInkCoordinator.getTokens(context) else tokens
        currentTokens = effectiveTokens
        NexusFeedEInkCoordinator.clearRootHardwareLayer(this)
        applyBackgroundTokens(effectiveTokens, isEInk)
        NexusFeedThemeHelper.applyHeaderTokens(menuBtn, searchBtn, titleView, emptyView, effectiveTokens, isEInk)
        searchHelper.applyTokens(
            searchBarLayout = searchBarLayout,
            searchInput = searchInput,
            searchClearBtn = searchClearBtn,
            tokens = effectiveTokens,
            isEInk = isEInk,
            dp = dp,
            chromeFillColor = { NexusFeedThemeHelper.chromeFillColor(it) }
        )
        categoryPills.applyTokens(effectiveTokens)
        bottomBar.applyTokens(effectiveTokens)
        tabManager.applyTokens(effectiveTokens)
    }

    private fun observeSources() {
        refreshController.observeSources(scope) { sources ->
            categoryPills.updateCategories(sources.map { it.category })
        }
    }

    private fun observeArticles() {
        if (activeTab == NexusFeedBottomBar.Tab.SETTINGS || activeTab == NexusFeedBottomBar.Tab.LIBRARY) return
        refreshController.observeArticles(
            scope = scope,
            searchQuery = searchHelper.searchQuery,
            activeTab = activeTab,
            selectedCategory = categoryPills.selectedCategory
        ) { filteredArticles, counts ->
            categoryPills.updateCounts(counts)
            renderArticles(filteredArticles)
        }
    }

    private fun renderArticles(articles: List<FeedArticle>) {
        displayManager.render(
            articles = articles,
            activeTab = activeTab,
            isHeadlineOnly = isHeadlineOnly,
            emptyText = searchHelper.getEmptyText(activity, activeTab, categoryPills.selectedCategory)
        )
    }

    private fun showArticleMenu(article: FeedArticle) {
        refreshController.showArticleMenu(activity, article) {
            observeArticles()
        }
    }

    fun triggerRefresh() {
        if (NexusFeedEInkCoordinator.isEInkMode(context)) {
            titleView.text = context.getString(R.string.nexus_feed_refreshing_text)
            refreshController.triggerRefresh(scope) {
                updateTabHeader(activeTab)
            }
        } else {
            refreshController.triggerRefresh(scope)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean =
        gestureDetector.onInterceptTouchEvent(ev) || super.onInterceptTouchEvent(ev)

    override fun onTouchEvent(ev: MotionEvent): Boolean =
        gestureDetector.onTouchEvent(ev, width.toFloat()) || super.onTouchEvent(ev)
}
