package com.nexus.launcher.ui

import android.content.res.ColorStateList
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nexus.launcher.data.prefs.NexusSettingsData
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.ui.canvas.LauncherCanvasView
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Owns the drawer's floating chrome: the Split Search Pill and the standalone category bar.
 *
 * The two are independent — either can be hidden, and each docks to the top or the bottom edge on
 * its own — so this class re-parents the shared overflow (☰) trigger to whichever surface is
 * currently on screen and keeps the layout reserve in
 * [LauncherCanvasView.drawerChrome] in step, so the app grid and the A–Z rail never sit
 * underneath either bar. [DrawerChromeLayout] resolves the preferences into the arrangement to
 * render; this class applies it.
 *
 * Home-vs-drawer visibility is layered on top by [MainActivityDrawerChromeBinder] via [chromeViews].
 */
class DrawerChromeController(
    private val activity: MainActivity,
    private val canvasView: LauncherCanvasView,
    private val viewModel: MainViewModel
) {
    private val dp = activity.resources.displayMetrics.density

    private var mainContainer: FrameLayout? = null
    private var pillContainer: FrameLayout? = null
        set(v) { field = v; chromeViews = listOfNotNull(v, categoryContainer) }
    private var categoryContainer: FrameLayout? = null
        set(v) { field = v; chromeViews = listOfNotNull(pillContainer, v) }
    private var pillViews: DrawerSearchPillBuilder.Views? = null
    private var categoryBar: DrawerCategoryBar? = null
    private var overflowIcon: ImageView? = null
    private var railOverflowButton: View? = null
    private var categoryDropdown: DrawerCategoryDropdown? = null
    private var applied: DrawerChromeLayout.Resolved? = null
    private var lastSettings: NexusSettingsData? = null

    private val pillBarHeightPx = ((48 + 12) * dp).toInt() // pill height + vertical padding

    /** True when neither bar is on screen, so the legacy rail-edge button is the overflow
     *  trigger's only remaining home. */
    val railOverflowIsHome: Boolean
        get() = applied?.overflowHome == DrawerChromeLayout.OverflowHome.RAIL

    /** Every chrome view the drawer-open animation has to reveal and slide. */
    var chromeViews: List<View> = emptyList() // kept, not built per read: read every drawer frame
        private set

    private fun tokens(): NexusColorTokens = try {
        ThemeObserver.currentTokens(activity)
    } catch (_: Exception) {
        NexusColorTokens.Dark
    }

    fun attach(
        container: FrameLayout,
        railOverflowBtn: View,
        onOverflowIconReady: (ImageView) -> Unit
    ) {
        if (mainContainer != null) return
        mainContainer = container
        railOverflowButton = railOverflowBtn

        // One trigger instance for the whole drawer, re-parented as the arrangement changes —
        // OverflowMenuController binds its click listener exactly once, here.
        val icon = ImageView(activity).apply {
            setImageResource(DrawerCategoryBar.OVERFLOW_ICON_RES)
            imageTintList = ColorStateList.valueOf(tokens().textPrimary)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
        overflowIcon = icon
        onOverflowIconReady(icon)

        ThemeObserver.observe(activity, activity.lifecycleScope) { liveTokens ->
            pillViews?.let { DrawerSearchPillBuilder.applyTokens(it, liveTokens, dp) }
            categoryBar?.applyTokens(liveTokens, viewModel.selectedCategory.value)
            icon.imageTintList = ColorStateList.valueOf(liveTokens.textPrimary)
        }

        activity.lifecycleScope.launch {
            activity.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // The bars are built from the categories, so adding, deleting, renaming or
                // re-iconing one rebuilds them.
                launch {
                    viewModel.drawerCategories.revision.drop(1).collect {
                        applied = null
                        lastSettings?.let { applySettings(it) }
                    }
                }
                viewModel.selectedCategory.collect { category ->
                    val label = DrawerSearchPillBuilder.categoryLabelText(activity, category)
                    pillViews?.categoryLabel?.text = label
                    pillViews?.dropdownLabel?.text = label
                    categoryBar?.setSelectedCategory(tokens(), category)
                }
            }
        }
    }

    /**
     * Rebuilds the chrome for [settings]. Called from
     * [com.nexus.launcher.ui.canvas.CanvasSettingsApplier] on every settings emission, so the
     * arrangement is already correct before the drawer is ever drawn.
     */
    fun applySettings(settings: NexusSettingsData) {
        lastSettings = settings
        val container = mainContainer ?: return
        val resolved = DrawerChromeLayout.resolve(settings)
        if (resolved == applied) return
        applied = resolved

        val tokens = tokens()

        // Torn down and rebuilt rather than mutated: the two bars can swap edges, swap which one
        // hosts the overflow trigger, and swap the category picker between three presentations.
        // Reconciling that in place would be far more state than rebuilding two small rows.
        pillContainer?.let { container.removeView(it) }
        categoryContainer?.let { container.removeView(it) }
        (overflowIcon?.parent as? ViewGroup)?.removeView(overflowIcon)
        pillContainer = null
        categoryContainer = null
        pillViews = null
        categoryBar = null

        if (resolved.pillVisible) buildPill(container, tokens, resolved)
        if (resolved.categoryBarVisible) buildCategoryBar(container, tokens, resolved)

        railOverflowButton?.visibility =
            if (resolved.overflowHome == DrawerChromeLayout.OverflowHome.RAIL) View.VISIBLE
            else View.GONE

        val state = canvasView.drawerChrome
        state.pillVisible = resolved.pillVisible
        state.pillHeightPx = if (resolved.pillVisible) pillBarHeightPx else 0
        state.pillAtBottom = resolved.pillAtBottom
        state.categoryBarVisible = resolved.categoryBarVisible
        state.categoryBarHeightPx =
            if (resolved.categoryBarVisible) ((36 + 12) * dp).toInt() else 0
        state.categoryBarAtBottom = resolved.categoryBarAtBottom

        syncToDrawerState()
        canvasView.recalculateLayout()
        canvasView.invalidate()
    }

    /**
     * Adopts the drawer's current open/closed state. Rebuilt bars are born hidden, and the
     * animation binder only flips them on a state or progress change — so changing a setting
     * while the drawer is open (from the overflow menu, or Drawer Settings reached through it)
     * left the new chrome invisible until the drawer was closed and reopened.
     */
    private fun syncToDrawerState() {
        val open = canvasView.uiState != com.nexus.launcher.ui.model.LauncherState.HOME
        chromeViews.forEach {
            it.translationY = 0f
            it.visibility = if (open) View.VISIBLE else View.GONE
        }
    }

    private fun buildPill(
        container: FrameLayout, tokens: NexusColorTokens, resolved: DrawerChromeLayout.Resolved
    ) {
        val built = DrawerSearchPillBuilder.build(activity, dp, tokens)
        pillViews = built
        built.categoryLabel.text =
            DrawerSearchPillBuilder.categoryLabelText(activity, viewModel.selectedCategory.value)

        val showSegment = resolved.categoryInPill
        built.categorySegment.visibility = if (showSegment) View.VISIBLE else View.GONE
        built.divider.visibility = if (showSegment) View.VISIBLE else View.GONE
        (built.searchIcon.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
            lp.marginStart = ((if (showSegment) 12 else 16) * dp).toInt()
            built.searchIcon.layoutParams = lp
        }

        if (resolved.overflowHome == DrawerChromeLayout.OverflowHome.PILL) {
            overflowIcon?.let { icon ->
                val pad = (12 * dp).toInt()
                icon.background = null
                icon.setPadding(pad, pad, pad, pad)
                icon.layoutParams = LinearLayout.LayoutParams(
                    (48 * dp).toInt(), ViewGroup.LayoutParams.MATCH_PARENT
                ).apply { marginStart = (4 * dp).toInt() }
                built.pill.addView(icon)
            }
        }

        val pillHeight = (48 * dp).toInt()
        val content: View = if (resolved.categoryBesidePill) {
            DrawerSearchPillBuilder.buildDropdownChip(activity, dp, tokens, built)
            built.dropdownLabel?.text =
                DrawerSearchPillBuilder.categoryLabelText(activity, viewModel.selectedCategory.value)
            built.dropdownChip?.setOnClickListener { anchor -> showCategoryDropdown(anchor) }
            LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                // Chip leads the row so the category reads as the filter the search runs
                // within. The pill yields the width; the chip takes only what its label needs.
                addView(
                    built.dropdownChip,
                    LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, pillHeight)
                )
                addView(
                    built.pill,
                    LinearLayout.LayoutParams(0, pillHeight, 1f)
                        .apply { marginStart = (8 * dp).toInt() }
                )
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, pillHeight
                )
            }
        } else {
            built.pill.also {
                it.layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, pillHeight
                )
            }
        }

        // Tapping the input hands off to the full search overlay — no direct typing in this bar.
        //
        // On a tap, not on focus. This used to open search whenever the field *gained focus*, and
        // focus is not only given by tapping: when any other text field on the screen lets go of
        // it — the app picker's own search, closed with Done or Back — Android hands focus to the
        // next focusable view, which was this one. The picker closed and Nexus search opened in its
        // place. The bar never accepts typing, so it has no business holding focus at all.
        built.editText.apply {
            isFocusable = false
            isFocusableInTouchMode = false
            isCursorVisible = false
            setOnClickListener {
                com.nexus.launcher.search.ui.NexusSearchOverlay.show(
                    activity, com.nexus.launcher.search.SearchContext.APP_DRAWER
                )
            }
        }
        built.categorySegment.setOnClickListener { anchor -> showCategoryDropdown(anchor) }

        pillContainer = addBar(container, content, resolved.pillAtBottom)
    }

    private fun buildCategoryBar(
        container: FrameLayout, tokens: NexusColorTokens, resolved: DrawerChromeLayout.Resolved
    ) {
        val bar = DrawerCategoryBar(activity)
        categoryBar = bar
        bar.onCategorySelected = { category -> selectCategory(category) }
        bar.onDropdownRequested = { anchor -> showCategoryDropdown(anchor) }

        val row = bar.build(
            tokens = tokens,
            categories = viewModel.categories,
            selectedCategory = viewModel.selectedCategory.value,
            isStrip = resolved.categoryBarIsStrip,
            showCategories = resolved.categoryBarHasCategories
        )
        if (resolved.overflowHome == DrawerChromeLayout.OverflowHome.CATEGORY_BAR) {
            overflowIcon?.let { bar.attachOverflow(it, tokens) }
        }
        row.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, bar.rowHeightPx
        )
        // When both bars share an edge the pill sits outermost, so the category bar has to
        // clear its full height on top of the system inset.
        val stackOffset =
            if (resolved.pillVisible && resolved.pillAtBottom == resolved.categoryBarAtBottom)
                pillBarHeightPx
            else 0
        categoryContainer = addBar(container, row, resolved.categoryBarAtBottom, stackOffset)
    }

    /**
     * Wraps [content] in an edge-docked container. When both bars share an edge the pill sits
     * outermost and the category bar inboard of it, so the chips always read as belonging to the
     * grid side of the search control.
     */
    private fun addBar(
        container: FrameLayout, content: View, atBottom: Boolean, stackOffset: Int = 0
    ): FrameLayout {
        val h = (16 * dp).toInt()
        val v = (12 * dp).toInt()
        val bar = FrameLayout(activity).apply {
            addView(content)
            if (atBottom) setPadding(h, v, h, 0) else setPadding(h, 0, h, v)
            visibility = View.GONE
        }
        ViewCompat.setOnApplyWindowInsetsListener(bar) { view, insets ->
            DrawerBarInsets.apply(view, insets, atBottom, stackOffset)
            insets
        }
        container.addView(
            bar,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { gravity = if (atBottom) Gravity.BOTTOM else Gravity.TOP }
        )
        ViewCompat.getRootWindowInsets(canvasView)?.let { DrawerBarInsets.apply(bar, it, atBottom, stackOffset) }
        return bar
    }

    private fun showCategoryDropdown(anchor: View, editing: Boolean = false) {
        if (categoryDropdown?.isShowing == true) return
        if (!editing) anchor.performHapticFeedback(
            android.view.HapticFeedbackConstants.VIRTUAL_KEY,
            android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
        )
        categoryDropdown = DrawerCategoryDropdown(
            context = activity,
            anchorView = anchor,
            categories = viewModel.categories,
            selectedCategory = viewModel.selectedCategory.value,
            onCategorySelected = { category -> selectCategory(category) },
            onDismissed = { categoryDropdown = null },
            onAddCategory = { DrawerCategoryActions.add(activity, viewModel) { selectCategory(it) } },
            onSortApps = {
                com.nexus.launcher.ui.drawercategories.CategorySortSheet.show(activity, viewModel)
            },
            onDeleteCategory = { DrawerCategoryActions.delete(activity, viewModel, it, ::reopenEditing) },
            onChangeIcon = { DrawerCategoryActions.changeIcon(activity, viewModel, it, ::reopenEditing) },
            onRename = { DrawerCategoryActions.rename(activity, viewModel, it, ::reopenEditing) },
            canRestoreDefaults = viewModel.drawerCategories.hasHiddenBuiltIns,
            onRestoreDefaults = { viewModel.restoreDefaultCategories(); reopenEditing() },
            startEditing = editing,
        ).also { it.show() }
    }

    /**
     * Brings the dropdown back in edit mode once a manage dialog closes, saved or not. Posted
     * past the rebuild a change triggers, which replaces the anchor; nothing reopens if the
     * drawer was closed meanwhile.
     */
    private fun reopenEditing() {
        canvasView.postDelayed({
            val anchor = listOfNotNull(pillViews?.dropdownChip, pillViews?.categorySegment, categoryBar?.dropdownAnchor)
                .firstOrNull { it.isShown }
            anchor?.let { showCategoryDropdown(it, editing = true) }
        }, REOPEN_DELAY_MS)
    }

    private fun selectCategory(category: String) {
        viewModel.onCategorySelected(category)
        // In the Categories drawer there is nothing to filter — go to the category instead.
        com.nexus.launcher.ui.drawercategories.CategoriesDrawerJump.to(
            viewModel.drawerCategories.idOf(category)
        )
        // A sparse category has less content than the scroll position the user was at, so without
        // this the drawer can land past the end of the new list and read as "the filter did
        // nothing". Search already resets scroll this way.
        canvasView.resetScrollY()
    }

    private companion object {
        const val REOPEN_DELAY_MS = 120L
    }
}
