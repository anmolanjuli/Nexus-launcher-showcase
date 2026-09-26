package com.nexus.launcher.ui.widgets.mosaic

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.NexusDesignSystem
import com.nexus.launcher.ui.folder.FolderGlassEdgeBuilder
import kotlin.math.abs

/**
 * Living Mosaic container — glass box hosting packed / single-focus child widgets.
 * Mosaic mode: swipe pages. Focus mode: swipe widgets on the current page only.
 */
class LivingMosaicView(context: Context) : FrameLayout(context) {

    private val dp = resources.displayMetrics.density
    private val inset = (2 * dp).toInt()
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var glassStroke: GradientDrawable? = null
    private var glassBackdrop: LivingMosaicGlassBackdropView? = null
    private var neumorphicBackdrop: LivingMosaicNeumorphicBackdropView? = null
    private val contentHost: FrameLayout = MosaicContentFrame(context) // draws the cells' raised shadows
    private val contentClip = FrameLayout(context) // clips the content when the tile draws its shadow
    private var drawsOwnShadow = false
    internal val childHost = LivingMosaicChildHost(contentHost, dp)
    private val swipeAnimator = LivingMosaicSwipeAnimator(contentHost, dp)
    internal val swipeAnimatorInternal get() = swipeAnimator
    private val emptyHint: TextView
    internal val modeButton: TextView
    internal val dotsRow: LinearLayout
    private val editGestures = LivingMosaicEditGestures(this)
    private val modeSwipe = LivingMosaicModeSwipe(contentHost, dp)

    private var boundItem: HomeScreenItem? = null
    // Tracks the global toggle's value as of the last applySurfaceBackground() call — bind()
    // only re-runs that (which decides whether the glass backdrop child exists at all) when the
    // MosaicConfig itself changed, so a live flip of the toggle with no other config change would
    // otherwise never be picked up (the backdrop would keep showing its last-recorded frame,
    // read as a Glass tile "stuck" on whatever wallpaper it last captured).
    private var lastAppliedGlassToggle: Boolean? = null
    private var lastAppliedFlatStyle: Boolean? = null
    internal var config: MosaicConfig = MosaicConfig()
    private var appWidgetHost: AppWidgetHost? = null
    private var appWidgetManager: AppWidgetManager? = null
    private var animateNext = false

    private var onChromeLongPress: (() -> Unit)? = null

    var onLongPressDetected: (() -> Unit)?
        get() = onChromeLongPress
        set(value) { onChromeLongPress = value }
    var onDragStarted: (() -> Unit)?
        get() = editGestures.onDragStarted
        set(value) { editGestures.onDragStarted = value }
    var onDragMoved: ((Float, Float) -> Unit)?
        get() = editGestures.onDragMoved
        set(value) { editGestures.onDragMoved = value }
    var onDropDetected: ((Float, Float) -> Unit)?
        get() = editGestures.onDropDetected
        set(value) { editGestures.onDropDetected = value }
    var onBodyTappedInMoveMode: (() -> Unit)?
        get() = editGestures.onBodyTappedInMoveMode
        set(value) { editGestures.onBodyTappedInMoveMode = value }

    var onPlaceholderTapped: ((Int) -> Unit)?
        get() = childHost.onPlaceholderTapped
        set(value) { childHost.onPlaceholderTapped = value }

    /** Mosaic-tile long press opens the large, temporary Focus Window. */
    var onTileFocusRequested: ((Int) -> Unit)? = null

    var onConfigChanged: ((HomeScreenItem, MosaicConfig) -> Unit)? = null

    private val touchRouter by lazy {
        LivingMosaicTouchRouter(this, swipeAnimator, editGestures, modeSwipe)
    }

    init {
        clipChildren = true
        clipToPadding = true
        clipToOutline = true
        contentHost.clipChildren = true
        contentHost.clipToPadding = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                val r = when (config.shapeStyle) {
                    2 -> 0f
                    11 -> Math.min(view.width, view.height) / 2f
                    else -> FolderGlassEdgeBuilder.cornerRadiusPx(context)
                }
                outline.setRoundRect(0, 0, view.width, view.height, r)
            }
        }
        applySurfaceBackground(config)

        contentHost.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        contentClip.addView(contentHost)
        addView(contentClip, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        emptyHint = TextView(context).apply {
            text = context.getString(com.nexus.launcher.R.string.mosaic_empty_swipe_or_add)
            setTextColor(Color.parseColor(NexusDesignSystem.COLOR_TEXT_SECONDARY))
            textSize = 13f
            gravity = Gravity.CENTER
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        addView(emptyHint)

        modeButton = LivingMosaicChrome.modeButton(context, dp) { toggleMode() }
        addView(modeButton)
        dotsRow = LivingMosaicChrome.dots(context, dp)
        addView(dotsRow)
        editGestures.onLongPressDetected = { onChromeLongPress?.invoke() }
    }

    fun applyThemeTokens() {
        applySurfaceBackground(config)
        emptyHint.setTextColor(Color.parseColor(NexusDesignSystem.COLOR_TEXT_SECONDARY))
        rebuildDots()
        invalidate()
    }

    fun setMoveModeActive(active: Boolean) = editGestures.setMoveModeActive(active)
    fun setEditChromeActive(active: Boolean) = editGestures.setEditChromeActive(active)

    fun dispatchToChildren(ev: MotionEvent) {
        super.dispatchTouchEvent(ev)
    }

    fun bind(
        item: HomeScreenItem,
        host: AppWidgetHost,
        manager: AppWidgetManager,
        animate: Boolean = false
    ) {
        boundItem = item
        appWidgetHost = host
        appWidgetManager = manager
        val next = MosaicConfig.parse(item.folderConfigJson)
        if (surfaceChanged(config, next) || styleChangedSinceLastApply()) applySurfaceBackground(next)
        config = next
        animateNext = animate
        tag = "mosaic:${item.id}"
        refreshLayout()
    }

    /** True if EITHER the master Glass toggle OR the Default-flat-style flag has moved since the
     *  last [applySurfaceBackground] call — a Neumorphism<->Default transition never changes the
     *  Glass toggle at all (both are "glass off"), so checking that alone missed it entirely. */
    private fun styleChangedSinceLastApply(): Boolean =
        com.nexus.launcher.ui.glass.FrostedGlassEngine.isGlobalFrostedGlassEnabled != lastAppliedGlassToggle ||
            com.nexus.launcher.ui.glass.FrostedGlassEngine.isDefaultFlatStyleEnabled != lastAppliedFlatStyle

    fun currentItem(): HomeScreenItem? = boundItem
    fun currentConfig(): MosaicConfig = config

    fun applyDraftSurface(next: MosaicConfig) {
        config = next
        applySurfaceBackground(next)
    }

    fun pulseAndRelayout(newConfig: MosaicConfig) {
        if (surfaceChanged(config, newConfig) || styleChangedSinceLastApply()) applySurfaceBackground(newConfig)
        config = newConfig
        glassStroke?.let { LivingMosaicBreathPulse.pulse(this, it, dp) }
        animateNext = true
        refreshLayout()
    }

    private fun surfaceChanged(a: MosaicConfig, b: MosaicConfig): Boolean =
        a.surfaceOpacity != b.surfaceOpacity ||
            a.backgroundMode != b.backgroundMode ||
            a.frostedGradientIndex != b.frostedGradientIndex ||
            a.glassRefraction != b.glassRefraction ||
            a.shapeStyle != b.shapeStyle

    private fun applySurfaceBackground(cfg: MosaicConfig) {
        val result = LivingMosaicSurfaceApplier.apply(this, cfg, glassBackdrop, neumorphicBackdrop)
        glassStroke = result.stroke
        glassBackdrop = result.glassBackdrop
        neumorphicBackdrop = result.neumorphicBackdrop
        lastAppliedGlassToggle = result.glassToggle
        lastAppliedFlatStyle = result.flatStyle
        drawsOwnShadow = MosaicRaisedShadow.applyTileClip(this, contentClip, neumorphicBackdrop?.raisedPalette() != null)
    }

    private fun refreshLayout() {
        val host = appWidgetHost ?: return
        val manager = appWidgetManager ?: return
        // Defensive: force the outer Glass backdrop to re-record its wallpaper crop on every
        // bind pass rather than trusting its own position/version staleness check alone — a
        // Mosaic tile that isn't actively moving/resizing could otherwise keep replaying
        // whatever it first recorded (reported as a "fixed"/frozen background). This runs once
        // per bind, not per frame, so the cost is negligible next to the correctness it buys.
        glassBackdrop?.invalidateBackdrop()
        val kids = config.currentChildren()
        editGestures.childInputEnabled = true
        emptyHint.visibility = if (kids.isEmpty()) View.VISIBLE else View.GONE
        emptyHint.text = if (config.pages.size > 1) context.getString(com.nexus.launcher.R.string.mosaic_empty_swipe_or_add) else context.getString(com.nexus.launcher.R.string.mosaic_empty_long_press)
        // Temporarily disabled by product decision; retain the control for future re-enable.
        modeButton.isEnabled = false
        modeButton.visibility = View.GONE
        rebuildDots()

        val w = width.takeIf { it > 0 } ?: measuredWidth
        val h = height.takeIf { it > 0 } ?: measuredHeight
        if (w <= 0 || h <= 0) {
            post { refreshLayout() }
            return
        }
        childHost.sync(
            children = kids,
            mode = config.mode,
            templateId = config.currentPage().layoutTemplate,
            focusIndex = config.focusIndex.coerceIn(0, (kids.size - 1).coerceAtLeast(0)),
            contentW = w,
            contentH = h,
            inset = inset,
            host = host,
            manager = manager,
            animate = animateNext
        )
        animateNext = false
    }

    /** Applies a temporary child appearance while its Studio editor is open. */
    internal fun previewChildStyle(index: Int, child: MosaicChild) {
        if (index !in config.currentChildren().indices) return
        config = config.withCurrentChildren(config.currentChildren().toMutableList().also { it[index] = child })
        refreshLayout()
    }

    private fun rebuildDots() {
        when {
            config.mode == MosaicConfig.MODE_MOSAIC && config.pages.size > 1 -> {
                LivingMosaicPageChrome.rebuildDots(
                    dotsRow, config.pages.size, config.pageIndex, dp
                ) { setPage(it) }
            }
            config.mode == MosaicConfig.MODE_SINGLE && config.currentChildren().size >= 2 -> {
                LivingMosaicPageChrome.rebuildDots(
                    dotsRow, config.currentChildren().size, config.focusIndex, dp
                ) { setFocus(it) }
            }
            else -> {
                dotsRow.removeAllViews()
                dotsRow.visibility = View.GONE
            }
        }
    }

    internal fun toggleMode() {
        LivingMosaicHaptics.click(this)
        val nextMode = if (config.mode == MosaicConfig.MODE_SINGLE) {
            MosaicConfig.MODE_MOSAIC
        } else {
            MosaicConfig.MODE_SINGLE
        }
        setMode(nextMode, useConfirmHaptic = false)
    }

    internal fun setModeExternal(nextMode: String) = setMode(nextMode, useConfirmHaptic = true)

    private fun setMode(nextMode: String, useConfirmHaptic: Boolean = true) {
        val item = boundItem ?: return
        if (config.mode == nextMode) return
        if (useConfirmHaptic) LivingMosaicHaptics.confirm(this)
        config = config.copy(mode = nextMode, focusIndex = 0)
        glassStroke?.let { LivingMosaicBreathPulse.pulse(this, it, dp) }
        animateNext = true
        onConfigChanged?.invoke(item, config)
        refreshLayout()
    }

    private fun setFocus(index: Int) = applyFocusIndex(index)

    internal fun applyFocusIndex(index: Int) {
        val item = boundItem ?: return
        val last = config.currentChildren().lastIndex.coerceAtLeast(0)
        val wrapped = when {
            config.currentChildren().isEmpty() -> return
            else -> ((index % (last + 1)) + (last + 1)) % (last + 1)
        }
        if (wrapped == config.focusIndex) return
        LivingMosaicHaptics.confirm(this)
        config = config.copy(focusIndex = wrapped)
        glassStroke?.let { LivingMosaicBreathPulse.pulse(this, it, dp) }
        animateNext = true
        onConfigChanged?.invoke(item, config)
        refreshLayout()
    }

    fun setPage(index: Int) {
        val item = boundItem ?: return
        if (index !in config.pages.indices || index == config.pageIndex) return
        LivingMosaicHaptics.confirm(this)
        config = config.copy(pageIndex = index, focusIndex = 0)
        glassStroke?.let { LivingMosaicBreathPulse.pulse(this, it, dp) }
        animateNext = true
        onConfigChanged?.invoke(item, config)
        refreshLayout()
    }

    /** Mosaic mode only — never while in focus/single mode. */
    internal fun canSwipeMosaicPages(): Boolean =
        config.mode == MosaicConfig.MODE_MOSAIC && config.pages.size > 1

    /** Focus mode only — widgets on the current page; never changes pageIndex. */
    internal fun canSwipeMosaicFocus(): Boolean =
        config.mode == MosaicConfig.MODE_SINGLE && config.currentChildren().size >= 2

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0 && boundItem != null) refreshLayout()
    }

    override fun onDetachedFromWindow() {
        swipeAnimator.reset()
        editGestures.onDetached()
        super.onDetachedFromWindow()
    }

    override fun dispatchDraw(canvas: android.graphics.Canvas) {
        // Belt-and-suspenders: applySurfaceBackground() is meant to be re-run whenever the global
        // toggle changes via bind()'s glassToggleChangedSinceLastApply() check, driven externally
        // by WidgetOverlayLayout.rebindCachedWidgets(). In practice that external trigger chain
        // did not reliably reach every live Mosaic tile (reported: switching to Default/
        // Neumorphism left the tile showing its old Glass-mode blur/transparency indefinitely,
        // with no further interaction fixing it). Checking it here instead means it's re-verified
        // on every actual draw pass of this specific view — which WILL happen (any scroll,
        // invalidate, or animation touches it) — regardless of whether any external rebind logic
        // successfully found and re-bound this tile.
        // Wrapped defensively: this runs on every draw pass of this view, so any exception here
        // would crash rendering outright rather than just a background settings collector.
        if (boundItem != null) {
            try {
                if (styleChangedSinceLastApply()) {
                    applySurfaceBackground(config)
                    childHost.refreshBackgrounds()
                }
            } catch (e: Exception) {
                android.util.Log.e("LivingMosaicView", "style re-check failed in dispatchDraw", e)
            }
        }
        if (drawsOwnShadow) MosaicRaisedShadow.drawOwn(canvas, this) // before the backdrop's face
        super.dispatchDraw(canvas)
    }

    fun invalidateBackdropsForScroll() {
        glassBackdrop?.invalidate()
        childHost.invalidateBackdropsForScroll()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        return touchRouter.dispatch(ev)
    }
}

