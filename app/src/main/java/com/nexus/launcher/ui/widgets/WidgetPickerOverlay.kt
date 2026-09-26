package com.nexus.launcher.ui.widgets

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.MainActivity
import com.nexus.launcher.ui.picker.PickerOverlayShell
import com.nexus.launcher.ui.widgets.mosaic.NexusWidgetCatalog
import kotlin.math.roundToInt

class WidgetPickerOverlay(context: Context) : PickerOverlayShell(context) {

    private val scroll: ScrollView
    private val contentContainer: LinearLayout
    private val tabBar: WidgetPickerTabBar

    var onWidgetSelected: ((WidgetAppGroup, WidgetProviderEntry) -> Unit)? = null
    var onWidgetDropped: ((WidgetProviderEntry, Float, Float) -> Unit)? = null
    var excludeNexusMosaic = false

    private var allSystemGroups: List<WidgetAppGroup> = emptyList()
    private var nexusGroup: WidgetAppGroup? = null
    private var currentTab: WidgetPickerTabBar.Tab = WidgetPickerTabBar.Tab.NEXUS
    private var lastQuery: String = ""

    /**
     * Nexus widgets are Premium; Living Mosaic is not, and neither is anything from another app.
     *
     * Checked at placement rather than by hiding entries, so a free user can still see what the
     * subscription includes — a picker that silently omits them advertises nothing. Both routes
     * into placement funnel through here: tapping an entry and dragging one onto the grid.
     */
    private fun allowPlacement(entry: WidgetProviderEntry): Boolean {
        if (entry.isLivingMosaic) return true
        val isNexusWidget = entry.nexusKind != null ||
            entry.info?.provider?.packageName == context.packageName
        if (!isNexusWidget) return true
        return com.nexus.launcher.premium.PremiumGate.allow(
            context,
            com.nexus.launcher.premium.PremiumFeature.NEXUS_WIDGETS,
        )
    }

    private val mainActivity: MainActivity? get() {
        var c: Context? = context
        while (c is android.content.ContextWrapper) {
            if (c is MainActivity) return c
            c = c.baseContext
        }
        return null
    }

    private val dragHelper by lazy {
        val act = mainActivity
        if (act != null) {
            WidgetPickerDragHelper(this, act.canvasView) { entry, x, y ->
                if (allowPlacement(entry)) onWidgetDropped?.invoke(entry, x, y)
            }.also { helper ->
                helper.onDragCompleted = {
                    animatePickerChrome(1f)
                    setBackgroundColor(tokens.bg)
                }
            }
        } else null
    }

    init {
        setTitle(context.getString(com.nexus.launcher.R.string.menu_widgets))
        setSearchHint(context.getString(com.nexus.launcher.R.string.widget_picker_search))

        tabBar = WidgetPickerTabBar(context) { tab ->
            currentTab = tab
            filterGroups(lastQuery)
        }
        setCustomHeaderView(tabBar)

        scroll = ScrollView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            isVerticalScrollBarEnabled = false
            clipToPadding = false
        }

        contentContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            setPadding(0, (8 * dp).toInt(), 0, (24 * dp).toInt())
        }

        scroll.addView(contentContainer)
        setContent(scroll)

        setOnSearchQueryChanged { query ->
            filterGroups(query)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (dragHelper?.isDragging == true) return true
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent): Boolean {
        if (dragHelper?.isDragging == true) {
            dragHelper?.handleTouchEvent(ev)
            return true
        }
        return super.onTouchEvent(ev)
    }

    fun bind(groups: List<WidgetAppGroup>) {
        hideSpinner()
        NexusWidgetPreviewCache.clear()
        com.nexus.launcher.ui.widgets.performance.PerformancePreviewBuilder.clearCache()
        com.nexus.launcher.ui.widgets.mosaic.NexusCatalogPreviewDrawers.clearCache()
        val realNexusGroup = groups.find { it.packageName == context.packageName }
        val fullNexusGroup = NexusWidgetCatalog.buildGroup(context, realNexusGroup?.widgets)
        allSystemGroups = groups.filterNot { it.packageName == context.packageName }
        nexusGroup = fullNexusGroup
        tabBar.setAllTabVisible(true)
        currentTab = WidgetPickerTabBar.Tab.NEXUS
        tabBar.select(currentTab, notify = false)
        setSearchHint(context.getString(com.nexus.launcher.R.string.widget_picker_search))
        filterGroups(lastQuery)
    }

    private fun filterGroups(query: String) {
        lastQuery = query
        val base = groupsForCurrentTab()
        if (query.isBlank()) {
            renderGroups(base)
            return
        }
        val q = query.lowercase()
        val refined = base.mapNotNull { group ->
            val widgets = group.widgets.filter {
                it.label.lowercase().contains(q) || group.appLabel.lowercase().contains(q)
            }
            if (widgets.isEmpty()) null else group.copy(widgets = widgets)
        }
        renderGroups(refined)
    }

    private fun groupsForCurrentTab(): List<WidgetAppGroup> {
        return when (currentTab) {
            WidgetPickerTabBar.Tab.NEXUS -> listOfNotNull(nexusGroup)
            WidgetPickerTabBar.Tab.ALL -> allSystemGroups
        }
    }

    private fun renderGroups(groups: List<WidgetAppGroup>) {
        contentContainer.removeAllViews()
        val visible = groups.map { group ->
            if (!excludeNexusMosaic) group
            else group.copy(widgets = group.widgets.filterNot { it.isLivingMosaic })
        }.filter { it.widgets.isNotEmpty() }

        if (visible.isEmpty()) {
            val emptyMsg = if (excludeNexusMosaic && currentTab == WidgetPickerTabBar.Tab.NEXUS) {
                context.getString(com.nexus.launcher.R.string.widget_picker_system_only)
            } else {
                context.getString(com.nexus.launcher.R.string.widget_picker_empty)
            }
            showEmptyState(emptyMsg)
            return
        }

        hideEmptyState()
        for (group in visible) {
            val isNexusTab = currentTab == WidgetPickerTabBar.Tab.NEXUS
            val row = WidgetGroupRow(
                context, group,
                onWidgetSelected = { grp, entry ->
                    if (allowPlacement(entry)) onWidgetSelected?.invoke(grp, entry)
                },
                onWidgetLongPressed = { view, grp, entry ->
                    startDrag(view, entry)
                },
                startExpanded = isNexusTab,
                showHeader = !isNexusTab,
                spanOf = ::computeSpanOf
            )
            contentContainer.addView(row)
        }
    }

    private fun computeSpanOf(entry: WidgetProviderEntry): Pair<Int, Int> {
        if (entry.isLivingMosaic || entry.isShortcutBox || entry.isAppBox) return NexusWidgetKinds.mosaicSpan(entry.nexusKind)
        val canvas = (context as? MainActivity)?.canvasView ?: return 1 to 1
        val density = canvas.resources.displayMetrics.density
        val metrics = com.nexus.launcher.ui.canvas.GridMetrics.compute(
            availableWidthPx = canvas.gridAreaWidth.toFloat(),
            availableHeightPx = (canvas.height - canvas.topInset - canvas.dockBottomReserve)
                .toFloat().coerceAtLeast(0f),
            columns = canvas.effectiveHomeColumns,
            rows = canvas.effectiveHomeRows,
            paddingLeftRightDp = canvas.homePaddingLeftRightDp,
            paddingTopBottomDp = canvas.homePaddingTopBottomDp,
            gapHorizontalDp = canvas.homeGapHorizontalDp,
            gapVerticalDp = canvas.homeGapVerticalDp,
            density = density
        )
        val cellWDp = (metrics.cellWidthPx / density).coerceAtLeast(1f)
        val cellHDp = (metrics.cellHeightPx / density).coerceAtLeast(1f)
        val sx = if (android.os.Build.VERSION.SDK_INT >= 31 && entry.info != null && entry.info.targetCellWidth > 0) {
            entry.info.targetCellWidth.coerceIn(1, canvas.effectiveHomeColumns)
        } else {
            (entry.minWidthDp / cellWDp).roundToInt().coerceIn(1, canvas.effectiveHomeColumns)
        }
        val sy = if (android.os.Build.VERSION.SDK_INT >= 31 && entry.info != null && entry.info.targetCellHeight > 0) {
            entry.info.targetCellHeight.coerceIn(1, canvas.effectiveHomeRows)
        } else {
            (entry.minHeightDp / cellHDp).roundToInt().coerceIn(1, canvas.effectiveHomeRows)
        }
        return sx to sy
    }

    private fun startDrag(view: View, entry: WidgetProviderEntry) {
        val helper = dragHelper ?: return
        val activity = mainActivity ?: return
        val mainContainer = activity.findViewById<FrameLayout>(R.id.main_container) ?: return

        var widgetOverlay: WidgetOverlayLayout? = null
        for (i in 0 until mainContainer.childCount) {
            val child = mainContainer.getChildAt(i)
            if (child is WidgetOverlayLayout) {
                widgetOverlay = child
                break
            }
        }

        helper.startDrag(view, entry, widgetOverlay)

        // Hide all picker chrome so the user can see the home screen grid under the drag shadow
        animatePickerChrome(0f)
        val bgAnim = android.animation.ValueAnimator.ofArgb(tokens.bg, Color.TRANSPARENT)
        bgAnim.addUpdateListener { setBackgroundColor(it.animatedValue as Int) }
        bgAnim.duration = 200
        bgAnim.start()
    }
}
