package com.nexus.launcher.reader.doc

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Continuous vertical scroll through a PDF, with viewport-based rendering and pinch zoom.
 *
 * ## What holds what
 *
 * A vertical [ScrollView] holds a [HorizontalScrollView] holding the column of pages. The second
 * axis exists for zoom: once a page is wider than the screen, panning across it has to be real
 * scrolling — with edges, with flings — and not a translation the reader has to fight.
 *
 * ## Memory
 *
 * One ImageView per page, but only the pages near the viewport hold a bitmap; the rest are handed
 * back ([releaseOutside]). A page is about 6MB at phone width and more when zoomed, and this view
 * runs inside the launcher's own process.
 */
class NexusPdfPageView(
    context: Context,
    private val scope: CoroutineScope,
    private val engine: NexusPdfRendererEngine,
    private val isEInk: Boolean,
    private val isDarkPaper: Boolean,
    private val backgroundColor: Int,
    private var statusBarHeight: Int = 0,
    private var navBarHeight: Int = 0,
    private val onPageChanged: (pageIndex: Int, totalPages: Int) -> Unit,
    private val onScrollProgress: (scrollY: Int, totalHeight: Int) -> Unit,
    private val onTap: () -> Unit,
    initialTheme: PdfThemeModeStore.ThemeMode = PdfThemeModeStore.ThemeMode.ORIGINAL,
    initialFitMode: PdfPageFitStore.FitMode = PdfPageFitStore.FitMode.SMART_CROP
) : ScrollView(context) {

    private val dp = resources.displayMetrics.density
    private var activeTheme = initialTheme
    private var activeFitMode = initialFitMode

    private val hScroll: HorizontalScrollView
    private val pagesContainer: LinearLayout
    private val pageViews = mutableListOf<ImageView>()

    /** Page heights at zoom 1; the laid-out height is this times the committed zoom. */
    private val baseHeights = mutableListOf<Int>()

    /** Which pages are holding bitmaps, and which have been measured — see [NexusPdfPageWindow]. */
    private var window: NexusPdfPageWindow? = null

    /** Page width at zoom 1, from the fit mode. */
    private var baseWidth = 0
    private var activePage = 0
    private var initialJumpPage: Int = -1
    private val gap get() = (12 * dp).toInt()

    private val zoomControl = NexusPdfScrollZoom(
        context = context,
        onPinchStart = { cancelOwnDrag() },
        onLiveScale = { scale, fx, fy -> applyLiveScale(scale, fx, fy) },
        onCommit = { previous, zoom, fx, fy -> commitZoom(previous, zoom, fx, fy) },
    )

    private val tapDetector = android.view.GestureDetector(
        context,
        object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onDoubleTap(e: MotionEvent): Boolean = true.also { zoomControl.toggle(e.x, e.y) }
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean = true.also { handleTapZone(e.x) }
        },
    )

    init {
        isFillViewport = true
        overScrollMode = OVER_SCROLL_NEVER
        clipToPadding = false
        applyPageSurface()

        pagesContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
        hScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            clipToPadding = false
            addView(pagesContainer)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }
        updatePaddingForFitMode()
        addView(hScroll)

        setOnScrollChangeListener { _, _, scrollY, _, _ ->
            onScrollProgress(scrollY, (pagesContainer.height - height).coerceAtLeast(1))
            checkVisiblePages(scrollY)
        }
    }

    /** The margins take the page's own paper colour, so page and frame read as one sheet. */
    private fun applyPageSurface() {
        setBackgroundColor(PdfThemeFilterApplier.surfaceColor(activeTheme, backgroundColor))
    }

    /** The pinch's live feedback: the column scales under the fingers until the zoom commits. */
    private fun applyLiveScale(scale: Float, focusX: Float, focusY: Float) {
        pagesContainer.pivotX = hScroll.scrollX + focusX
        pagesContainer.pivotY = scrollY + focusY
        pagesContainer.scaleX = scale
        pagesContainer.scaleY = scale
    }

    fun updateInsets(statusBar: Int, navBar: Int) {
        statusBarHeight = statusBar
        navBarHeight = navBar
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        zoomControl.onTouchEvent(ev)
        tapDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    // A pinch belongs to the zoom, not to either scroller: taking the stream here is what stops
    // the page panning sideways under the fingers while they are still deciding on a size.
    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean =
        if (zoomControl.isPinching) true else super.onInterceptTouchEvent(ev)

    override fun onTouchEvent(ev: MotionEvent): Boolean =
        if (zoomControl.isPinching) true else super.onTouchEvent(ev)

    /** Tells both scrollers the drag they started is over, so a pinch is not also a scroll. */
    private fun cancelOwnDrag() {
        val now = android.os.SystemClock.uptimeMillis()
        val cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0)
        super.onTouchEvent(cancel)
        hScroll.onTouchEvent(cancel)
        cancel.recycle()
    }

    private fun handleTapZone(touchX: Float) {
        val w = width.toFloat()
        if (w <= 0f) return
        val oneThird = w / 3f
        performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        when {
            touchX < oneThird -> if (activePage > 0) jumpToPage(activePage - 1)
            touchX > oneThird * 2f -> if (activePage < engine.pageCount - 1) jumpToPage(activePage + 1)
            else -> onTap()
        }
    }

    private fun updatePaddingForFitMode() {
        val hPad = when (activeFitMode) {
            PdfPageFitStore.FitMode.FIT_WIDTH -> 0
            PdfPageFitStore.FitMode.SMART_CROP -> (4 * dp).toInt()
            PdfPageFitStore.FitMode.FIT_PAGE -> (12 * dp).toInt()
        }
        pagesContainer.setPadding(hPad, (58 * dp).toInt(), hPad, (60 * dp).toInt())
    }

    suspend fun setupPages(startPage: Int) = withContext(Dispatchers.Main) {
        val totalPages = engine.pageCount
        if (totalPages <= 0) return@withContext

        initialJumpPage = startPage
        updatePaddingForFitMode()
        baseWidth = when (activeFitMode) {
            PdfPageFitStore.FitMode.FIT_WIDTH -> resources.displayMetrics.widthPixels
            PdfPageFitStore.FitMode.SMART_CROP -> resources.displayMetrics.widthPixels - (8 * dp).toInt()
            PdfPageFitStore.FitMode.FIT_PAGE -> resources.displayMetrics.widthPixels - (24 * dp).toInt()
        }.coerceIn(240, 2400)
        zoomControl.reset()

        pagesContainer.removeAllViews()
        pageViews.clear()
        baseHeights.clear()
        releaseAll()
        window = null

        // One page measured, not every page: asking for a page's size opens it, and a 500-page
        // document used to make 500 round trips before the first frame. The rest start at this
        // page's proportions and are corrected by the page window as they come near.
        val anchor = startPage.coerceIn(0, totalPages - 1)
        val (_, estimated) = engine.getPageDimensions(anchor, baseWidth)

        repeat(totalPages) { baseHeights.add(estimated) }
        pageViews.addAll(
            NexusPdfPageColumn.build(pagesContainer, totalPages, baseWidth, estimated, gap, activeTheme, isEInk)
        )
        window = NexusPdfPageWindow(
            scope = scope,
            engine = engine,
            pages = pageViews,
            baseHeights = baseHeights,
            baseWidth = baseWidth,
            dp = dp,
            fitMode = { activeFitMode },
            heightCorrected = { index, height -> onHeightCorrected(index, height) },
        )
        measuredAnchor(anchor)

        post {
            if (initialJumpPage in 0 until totalPages) jumpToPage(initialJumpPage)
            checkVisiblePages(scrollY)
        }
    }

    fun applyTheme(theme: PdfThemeModeStore.ThemeMode) {
        if (activeTheme == theme) return
        activeTheme = theme
        applyPageSurface()
        pageViews.forEach { PdfThemeFilterApplier.apply(it, activeTheme, isEInk) }
    }

    fun applyFitMode(mode: PdfPageFitStore.FitMode) {
        if (activeFitMode == mode) return
        activeFitMode = mode
        scope.launch { setupPages(activePage) }
    }

    fun jumpToPage(pageIndex: Int) {
        if (pageIndex !in baseHeights.indices) return
        scrollTo(0, pageTop(pageIndex))
        activePage = pageIndex
        onPageChanged(activePage, engine.pageCount)
    }

    /**
     * Re-lays the pages out at [zoom] and renders them again at that size, so the text is drawn at
     * the magnification rather than stretched to it.
     *
     * Whatever was under the fingers stays under them. The point being pinched is converted to
     * where it sits in the document at zoom 1, then converted back at the new zoom and scrolled to
     * — in both axes. Anchoring on a fraction of the total height instead, as this did, moves the
     * reader somewhere else in the document every time the zoom changes, which reads as the page
     * refusing to stay where it was put.
     */
    private fun commitZoom(previous: Float, zoom: Float, focusX: Float, focusY: Float) {
        if (baseHeights.isEmpty() || previous <= 0f) return
        val padTop = pagesContainer.paddingTop
        val anchorY = (scrollY + focusY - padTop) / previous
        val anchorX = (hScroll.scrollX + focusX) / previous
        val width = (baseWidth * zoom).roundToInt()

        for (i in pageViews.indices) {
            val view = pageViews[i]
            view.layoutParams = (view.layoutParams as LinearLayout.LayoutParams).apply {
                this.width = width
                this.height = (baseHeights[i] * zoom).roundToInt()
                bottomMargin = gap
            }
        }
        // The pages keep the bitmaps they have — stretched, until the sharper ones arrive a
        // moment later. Clearing them here left the reader looking at empty rectangles mid-pinch.
        window?.invalidateRenders()

        post {
            val maxY = (pagesContainer.height - height).coerceAtLeast(0)
            val maxX = (pagesContainer.width - hScroll.width).coerceAtLeast(0)
            scrollTo(0, (anchorY * zoom + padTop - focusY).roundToInt().coerceIn(0, maxY))
            hScroll.scrollTo((anchorX * zoom - focusX).roundToInt().coerceIn(0, maxX), 0)
            checkVisiblePages(scrollY)
        }
    }

    private fun checkVisiblePages(currentScrollY: Int) {
        if (pageViews.isEmpty()) return
        val margin = (300 * dp).toInt()
        val viewportTop = currentScrollY - margin
        val viewportBottom = currentScrollY + height + margin

        var currentY = pagesContainer.paddingTop
        var mostVisiblePage = 0
        var maxVisibleOverlap = -1
        var firstNear = -1
        var lastNear = -1

        for (i in pageViews.indices) {
            val pageH = laidOutHeight(i)
            val pageTop = currentY
            val pageBottom = pageTop + pageH

            val overlap = (pageBottom.coerceAtMost(currentScrollY + height) - pageTop.coerceAtLeast(currentScrollY))
                .coerceAtLeast(0)
            if (overlap > maxVisibleOverlap) {
                maxVisibleOverlap = overlap
                mostVisiblePage = i
            }
            if (pageBottom >= viewportTop && pageTop <= viewportBottom) {
                if (firstNear == -1) firstNear = i
                lastNear = i
                loadPageBitmap(i)
            }
            currentY = pageBottom + gap
        }
        releaseOutside(firstNear, lastNear)

        if (mostVisiblePage != activePage) {
            activePage = mostVisiblePage
            onPageChanged(activePage, engine.pageCount)
        }
    }

    private fun loadPageBitmap(pageIndex: Int) = window?.load(pageIndex, zoomControl.zoom)

    private fun releaseOutside(first: Int, last: Int) = window?.releaseOutside(first, last) ?: Unit

    private fun releaseAll() = window?.releaseAll() ?: Unit

    /**
     * A page turned out to be a different height than the estimate. The view it sits in is resized,
     * and if it was already above the viewport the scroll moves with it so the reader stays put.
     */
    private fun onHeightCorrected(index: Int, measured: Int) {
        val view = pageViews.getOrNull(index) ?: return
        val wasAbove = pageTop(index) + laidOutHeight(index) < scrollY
        val before = laidOutHeight(index)
        baseHeights[index] = measured
        val after = laidOutHeight(index)
        view.layoutParams = (view.layoutParams as LinearLayout.LayoutParams).apply { height = after }
        if (wasAbove) post { scrollBy(0, after - before) }
    }

    private fun measuredAnchor(index: Int) {
        window?.markMeasured(index)
    }

    private fun laidOutHeight(index: Int): Int =
        (baseHeights.getOrElse(index) { baseWidth } * zoomControl.zoom).roundToInt()

    /** Where [index] starts inside the container. */
    private fun pageTop(index: Int): Int {
        var y = pagesContainer.paddingTop
        for (i in 0 until index.coerceAtMost(baseHeights.size)) {
            y += laidOutHeight(i) + gap
        }
        return y
    }
}
