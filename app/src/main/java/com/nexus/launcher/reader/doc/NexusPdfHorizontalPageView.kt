package com.nexus.launcher.reader.doc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.GestureDetector
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

/**
 * Single-page horizontal view for PDF reading with swipe gestures, RTL awareness,
 * 3-zone tap navigation, and instant E-Ink transitions.
 */
class NexusPdfHorizontalPageView(
    context: Context,
    private val scope: CoroutineScope,
    private val engine: NexusPdfRendererEngine,
    private val isEInk: Boolean,
    private val isDarkPaper: Boolean,
    private val backgroundColor: Int,
    private var statusBarHeight: Int = 0,
    private var navBarHeight: Int = 0,
    private val onPageChanged: (pageIndex: Int, totalPages: Int) -> Unit,
    private val onCenterTap: () -> Unit,
    initialTheme: PdfThemeModeStore.ThemeMode = PdfThemeModeStore.ThemeMode.ORIGINAL,
    initialFitMode: PdfPageFitStore.FitMode = PdfPageFitStore.FitMode.SMART_CROP
) : FrameLayout(context) {

    private val dp = resources.displayMetrics.density
    private var activeTheme = initialTheme
    private var activeFitMode = initialFitMode
    private var currentPage = 0
    private var isTransitioning = false

    private val activeImageView = ImageView(context)
    private val incomingImageView = ImageView(context)
    private val zoomHelper = NexusPdfPageZoomHelper(
        context,
        { activeImageView },
        onZoomSettled = { scale -> renderActivePage(scale) },
    )
    private val dragTurn = NexusPdfPageDragTurn(activeImageView, incomingImageView, dp)
    private val touchSlop = android.view.ViewConfiguration.get(context).scaledTouchSlop
    private var velocityTracker: android.view.VelocityTracker? = null

    /** Neighbouring pages, rendered and fit-processed ahead of time so a drag can start at once. */
    private var preparedNext: Bitmap? = null
    private var preparedPrev: Bitmap? = null
    private val gestureDetector: GestureDetector
    private val prefetch = NexusPdfPagePrefetch(scope, engine, dp)

    private var twoFingerStartX = 0f
    private var downX = 0f
    private var downY = 0f
    private var lastTouchY = 0f
    private var maxFitWidthPanY = 0f
    private var isSwipeConsumed = false

    /** A gesture that ever had two fingers on it is a zoom, never a page turn. */
    private var multiTouchGesture = false

    init {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        applyPageSurface()
        updatePaddingForFitMode()

        configureImageView(activeImageView)
        configureImageView(incomingImageView)
        incomingImageView.visibility = GONE

        addView(incomingImageView)
        addView(activeImageView)

        gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (zoomHelper.isZoomed) {
                    zoomHelper.resetZoom()
                } else {
                    zoomHelper.setZoom(2.0f, e.x, e.y)
                }
                return true
            }

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (!isSwipeConsumed && !zoomHelper.isZoomed) {
                    handleTapZone(e.x)
                }
                return true
            }

            // No onFling: a flick is the end of a drag, and NexusPdfPageDragTurn decides what it
            // meant from the same distance and velocity the finger actually produced.
        })
    }

    /** The margins take the page's own paper colour, so page and frame read as one sheet. */
    private fun applyPageSurface() {
        setBackgroundColor(PdfThemeFilterApplier.surfaceColor(activeTheme, backgroundColor))
    }

    private fun updatePaddingForFitMode() {
        val hPad = when (activeFitMode) {
            PdfPageFitStore.FitMode.FIT_WIDTH -> 0
            PdfPageFitStore.FitMode.SMART_CROP -> (4 * dp).toInt()
            PdfPageFitStore.FitMode.FIT_PAGE -> (12 * dp).toInt()
        }
        setPadding(
            hPad,
            statusBarHeight + (58 * dp).toInt(),
            hPad,
            navBarHeight + (60 * dp).toInt()
        )
    }

    private fun configureImageView(view: ImageView) {
        view.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT, Gravity.CENTER)
        view.scaleType = ImageView.ScaleType.FIT_CENTER
        view.background = null
        PdfThemeFilterApplier.apply(view, activeTheme, isEInk)
    }

    fun applyTheme(theme: PdfThemeModeStore.ThemeMode) {
        if (activeTheme == theme) return
        activeTheme = theme
        applyPageSurface()
        PdfThemeFilterApplier.apply(activeImageView, activeTheme, isEInk)
        PdfThemeFilterApplier.apply(incomingImageView, activeTheme, isEInk)
    }

    fun applyFitMode(mode: PdfPageFitStore.FitMode) {
        if (activeFitMode == mode) return
        activeFitMode = mode
        updatePaddingForFitMode()
        renderActivePage()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = true

    override fun onTouchEvent(event: MotionEvent): Boolean {
        zoomHelper.scaleDetector.onTouchEvent(event)

        if (zoomHelper.isZoomed) {
            val took = zoomHelper.onZoomedTouch(event, width, height) { forward ->
                if (!isTransitioning) {
                    val isRtl = layoutDirection == LAYOUT_DIRECTION_RTL
                    if (forward != isRtl) nextPage() else previousPage()
                }
            }
            gestureDetector.onTouchEvent(event)
            if (took) return true
        }

        // Normal 1x page navigation: the page follows the finger (see NexusPdfPageDragTurn).
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastTouchY = event.y
                isSwipeConsumed = false
                multiTouchGesture = false
                velocityTracker?.recycle()
                velocityTracker = android.view.VelocityTracker.obtain().apply { addMovement(event) }
            }
            MotionEvent.ACTION_MOVE -> {
                velocityTracker?.addMovement(event)
                val dx = event.x - downX
                val dy = event.y - downY
                if (!dragTurn.isDragging && !isTransitioning && !multiTouchGesture &&
                    !zoomHelper.scaleDetector.isInProgress &&
                    abs(dx) > touchSlop && abs(dx) > abs(dy)
                ) {
                    beginDragTurn(dx)
                }
                if (dragTurn.isDragging) {
                    dragTurn.drag(dx)
                } else if (activeFitMode == PdfPageFitStore.FitMode.FIT_WIDTH && maxFitWidthPanY > 0f) {
                    val step = event.y - lastTouchY
                    lastTouchY = event.y
                    activeImageView.translationY = (activeImageView.translationY + step).coerceIn(-maxFitWidthPanY, maxFitWidthPanY)
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // A second finger has landed: this is a zoom. Any turn already under way is put
                // back, and no new one starts until every finger has lifted.
                multiTouchGesture = true
                if (dragTurn.isDragging) {
                    dragTurn.reset()
                    isSwipeConsumed = true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragTurn.isDragging) {
                    velocityTracker?.addMovement(event)
                    velocityTracker?.computeCurrentVelocity(1000)
                    val vx = velocityTracker?.xVelocity ?: 0f
                    val goingForward = dragTurn.forward == true
                    val target = if (goingForward) currentPage + 1 else currentPage - 1
                    isSwipeConsumed = true
                    dragTurn.finish(event.x - downX, vx) { committed ->
                        if (committed) commitDraggedPage(target) else prebufferAdjacentPages()
                    }
                }
                velocityTracker?.recycle()
                velocityTracker = null
            }
        }
        if (!isSwipeConsumed) {
            gestureDetector.onTouchEvent(event)
        }
        return true
    }

    fun updateInsets(statusBar: Int, navBar: Int) {
        statusBarHeight = statusBar
        navBarHeight = navBar
        updatePaddingForFitMode()
    }

    fun setup(initialPage: Int) {
        val total = engine.pageCount
        currentPage = initialPage.coerceIn(0, (total - 1).coerceAtLeast(0))
        renderActivePage()
        onPageChanged(currentPage, total)
        prebufferAdjacentPages()
    }

    fun jumpToPage(pageIndex: Int) {
        val total = engine.pageCount
        val target = pageIndex.coerceIn(0, (total - 1).coerceAtLeast(0))
        if (target == currentPage && activeImageView.drawable != null) return
        currentPage = target
        renderActivePage()
        onPageChanged(currentPage, total)
        prebufferAdjacentPages()
    }

    private fun handleTapZone(touchX: Float) {
        val width = width.toFloat()
        if (width <= 0f) return

        val isRtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val oneThird = width / 3f

        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

        when {
            touchX < oneThird -> {
                if (isRtl) nextPage() else previousPage()
            }
            touchX > oneThird * 2f -> {
                if (isRtl) previousPage() else nextPage()
            }
            else -> {
                onCenterTap()
            }
        }
    }

    /**
     * Lifts the neighbouring page into view so the drag can move it. Only starts when that page is
     * already rendered — a drag that had to wait for a render would jump.
     */
    private fun beginDragTurn(dx: Float) {
        val isRtl = layoutDirection == LAYOUT_DIRECTION_RTL
        val goingForward = if (isRtl) dx > 0f else dx < 0f
        val target = if (goingForward) currentPage + 1 else currentPage - 1
        if (target !in 0 until engine.pageCount) return
        val neighbour = if (goingForward) preparedNext else preparedPrev
        if (dragTurn.begin(goingForward, neighbour, width)) {
            applyFitScale(incomingImageView, neighbour)
        }
    }

    /** The dragged page has arrived: it becomes the page being read. */
    private fun commitDraggedPage(target: Int) {
        val bmp = if (target > currentPage) preparedNext else preparedPrev
        currentPage = target.coerceIn(0, (engine.pageCount - 1).coerceAtLeast(0))
        if (bmp != null) {
            activeImageView.setImageBitmap(bmp)
            applyFitScale(activeImageView, bmp)
        } else {
            renderActivePage()
        }
        onPageChanged(currentPage, engine.pageCount)
        prebufferAdjacentPages()
    }

    fun nextPage() {
        if (currentPage >= engine.pageCount - 1) return
        transitionToPage(currentPage + 1, forward = true)
    }

    fun previousPage() {
        if (currentPage <= 0) return
        transitionToPage(currentPage - 1, forward = false)
    }

    private fun transitionToPage(targetPage: Int, forward: Boolean) {
        if (isTransitioning) return
        if (targetPage !in 0 until engine.pageCount) return
        isTransitioning = true
        dragTurn.reset()
        NexusPdfPageSlideAnimator.slideToPage(
            scope = scope,
            engine = engine,
            active = activeImageView,
            incoming = incomingImageView,
            page = targetPage,
            forward = forward,
            fitMode = activeFitMode,
            dp = dp,
            targetWidth = contentWidth(),
            targetHeight = contentHeight(),
            onBitmapReady = { bmp -> applyFitScale(incomingImageView, bmp) },
        ) { bmp ->
            currentPage = targetPage
            isTransitioning = false
            applyFitScale(activeImageView, bmp)
            onPageChanged(currentPage, engine.pageCount)
            prebufferAdjacentPages()
        }
    }

    private fun contentWidth(): Int =
        (width - paddingLeft - paddingRight).coerceAtLeast(resources.displayMetrics.widthPixels - (24 * dp).toInt())

    private fun contentHeight(): Int =
        (height - paddingTop - paddingBottom).coerceAtLeast(resources.displayMetrics.heightPixels - (100 * dp).toInt())

    /**
     * Renders the page being read. [renderScale] is the zoom it has to stand up to: the page is
     * rendered that many times larger and then fitted back into the view, so zooming in reveals
     * detail that was rendered rather than a magnified version of screen-sized pixels. Capped at
     * [MAX_RENDER_SCALE], past which one page's bitmap costs more memory than the view is worth.
     */
    private fun renderActivePage(renderScale: Float = 1f) {
        prefetch.render(
            page = currentPage,
            width = contentWidth(),
            height = contentHeight(),
            scale = renderScale.coerceIn(1f, MAX_RENDER_SCALE),
            fit = activeFitMode,
        ) { bmp ->
            if (bmp != null) {
                activeImageView.setImageBitmap(bmp)
                // A zoomed page keeps its zoom: the fit pass would reset the view's scale, and this
                // render is usually the answer to a pinch asking for a sharper page.
                if (zoomHelper.isZoomed) zoomHelper.reapply() else applyFitScale(activeImageView, bmp)
            }
        }
    }

    private fun applyFitScale(view: ImageView, bmp: Bitmap?) {
        val vW = (width - paddingLeft - paddingRight).toFloat()
        val vH = (height - paddingTop - paddingBottom).toFloat()
        maxFitWidthPanY = NexusPdfFitScaler.applyFitScale(view, bmp, activeFitMode, vW, vH)
    }

    private fun prebufferAdjacentPages() {
        prefetch.prebuffer(currentPage, contentWidth(), contentHeight(), activeFitMode) { previous, next ->
            preparedPrev = previous
            preparedNext = next
        }
    }

    private companion object {
        /** A page rendered beyond this costs more than the sharpness is worth. */
        const val MAX_RENDER_SCALE = 2.5f
    }
}
