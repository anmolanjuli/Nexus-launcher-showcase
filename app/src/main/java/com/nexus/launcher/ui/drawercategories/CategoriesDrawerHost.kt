package com.nexus.launcher.ui.drawercategories

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import com.nexus.launcher.data.prefs.DrawerLayoutModes
import com.nexus.launcher.ui.canvas.LauncherCanvasView
import com.nexus.launcher.ui.glass.ChromeBackdrop

class CategoriesDrawerHost(context: Context) : FrameLayout(context) {
    var onAppClick: ((CategoryApp) -> Unit)? = null
    var onAppLongPress: ((CategoryApp, View) -> Unit)? = null
    var canvasView: LauncherCanvasView? = null
    val isForwarding: Boolean get() = dismissRelay.isForwarding

    private val dismissRelay = CategoriesDrawerDismissRelay(this) { canvasView }
    private var blurAnimator: ValueAnimator? = null
    private var blurRadius = 0f

    init {
        layoutDirection = LAYOUT_DIRECTION_LTR
        clipChildren = true
        clipToPadding = true
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_DOWN && isInChromeInset(ev.y)) {
            return false
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (isInChromeInset(ev.y)) return false
        return dismissRelay.onIntercept(ev) || super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isInChromeInset(event.y)) return false
        return dismissRelay.onTouch(event) || super.onTouchEvent(event)
    }

    override fun onDetachedFromWindow() {
        dismissRelay.cancel()
        blurAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    fun setBlurState(blurred: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val end = if (blurred) ChromeBackdrop.RADIUS_PX else 0f
        if (kotlin.math.abs(end - blurRadius) < 0.5f) {
            ChromeBackdrop.applyTo(this, end)
            return
        }
        blurAnimator?.cancel()
        blurAnimator = ValueAnimator.ofFloat(blurRadius, end).apply {
            duration = if (blurred) 250L else 150L
            addUpdateListener { animator ->
                blurRadius = animator.animatedValue as Float
                ChromeBackdrop.applyTo(this@CategoriesDrawerHost, blurRadius)
            }
            start()
        }
    }

    /** Takes the view to [categoryId], for the drawer dropdown's category picks. */
    fun jumpToCategory(categoryId: Int) {
        when (val child = getChildAt(0)) {
            is SpatialCardStack -> child.pageOf(categoryId)?.let { child.setPage(it) }
            is CategoryListView -> child.scrollToCategory(categoryId)
            is CategoryStripView -> child.scrollToCategory(categoryId)
        }
    }

    fun bind(
        style: String,
        categories: List<CategoryGroup>,
        frequent: List<CategoryApp>,
        untaggedFolders: List<CategoryApp>,
        showRail: Boolean,
        bottomChrome: Boolean,
    ) {
        val keptScroll = (getChildAt(0) as? android.widget.ScrollView)?.scrollY ?: 0
        val keptPage = (getChildAt(0) as? SpatialCardStack)?.currentPage() ?: -1
        removeAllViews()
        if (categories.isEmpty() && untaggedFolders.isEmpty()) return
        val click: (CategoryApp) -> Unit = { app -> onAppClick?.invoke(app) }
        val longPress: (CategoryApp, View) -> Unit = { app, view ->
            onAppLongPress?.invoke(app, view)
        }
        when (style) {
            DrawerLayoutModes.STRIP -> {
                CategoriesFolderTag.currentCategoryId = null
                addView(
                    CategoryStripView(
                        context = context,
                        categories = categories,
                        frequent = frequent,
                        untaggedFolders = untaggedFolders,
                        showSearch = false,
                        onAppClick = click,
                        onAppLongPress = longPress,
                    ),
                    LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
                )
            }
            DrawerLayoutModes.CAT_LIST -> bindList(
                categories, untaggedFolders, click, longPress, showRail,
            )
            else -> bindSpatial(
                withFolders(categories, untaggedFolders), click, longPress, bottomChrome,
            )
        }
        restorePosition(keptScroll, keptPage)
    }

    /**
     * A rebind rebuilds the whole tree (an app installed, renamed, recategorised), which would
     * otherwise drop the reader back at the first category.
     */
    private fun restorePosition(scrollY: Int, page: Int) {
        val child = getChildAt(0) ?: return
        if (child is SpatialCardStack) {
            if (page >= 0) child.setPage(page)
        } else if (child is android.widget.ScrollView && scrollY > 0) {
            child.post { child.scrollTo(0, scrollY) }
        }
    }

    private fun bindList(
        categories: List<CategoryGroup>,
        untaggedFolders: List<CategoryApp>,
        click: (CategoryApp) -> Unit,
        longPress: (CategoryApp, View) -> Unit,
        showRail: Boolean,
    ) {
        CategoriesFolderTag.currentCategoryId = null
        val list = CategoryListView(
            context = context,
            categories = categories,
            untaggedFolders = untaggedFolders,
            onAppClick = click,
            onAppLongPress = longPress,
            reserveRail = showRail,
        )
        addView(list, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        if (!showRail) return
        val rail = StripAlphabetRail(context)
        rail.onLetter = { list.scrollToLetter(it) }
        rail.canvasView = canvasView
        val labels = buildList {
            addAll(untaggedFolders.map { it.label })
            categories.forEach { cat -> addAll(cat.apps.map { it.label }) }
        }
        rail.bindSources(labels)
        addView(rail, StripAlphabetRail.endParams())
        // The blocks below carry elevation, and elevation beats child order — so does the rail's.
        rail.elevation = 16f * resources.displayMetrics.density
        rail.bringToFront()
    }

    /** Folders nobody filed away get their own card, the way the list drawer gives them a block. */
    private fun withFolders(
        categories: List<CategoryGroup>,
        untaggedFolders: List<CategoryApp>,
    ): List<CategoryGroup> {
        if (untaggedFolders.isEmpty()) return categories
        val folders = CategoryGroup(
            name = context.getString(com.nexus.launcher.R.string.drawer_category_untagged_folders),
            subtitle = context.resources.getQuantityString(
                com.nexus.launcher.R.plurals.drawer_category_app_count,
                untaggedFolders.size, untaggedFolders.size,
            ),
            accent = CategoryPalette(context).mistBlue,
            apps = untaggedFolders,
            quickActions = emptyList(),
            recents = emptyList(),
            badgeIcon = com.nexus.launcher.R.drawable.ic_folder_solid,
        )
        return listOf(folders) + categories
    }

    private fun bindSpatial(
        categories: List<CategoryGroup>,
        click: (CategoryApp) -> Unit,
        longPress: (CategoryApp, View) -> Unit,
        bottomChrome: Boolean,
    ) {
        if (categories.isEmpty()) return
        addView(
            SpatialCardStack(
                context = context,
                categories = categories,
                showTopBar = false,
                showColorPicker = false,
                showPageDots = !bottomChrome,
                onAppClick = click,
                onAppLongPress = longPress,
                onPageChanged = { page ->
                    CategoriesFolderTag.currentCategoryId =
                        categories.getOrNull(page)?.id?.takeIf { it > 0 }
                },
            ),
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
    }

    private fun isInChromeInset(y: Float): Boolean {
        return y < paddingTop || y > height - paddingBottom
    }
}
