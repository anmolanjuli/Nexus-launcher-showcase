package com.nexus.launcher.ui.island

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.CanvasTypographyHelper
import com.nexus.launcher.typography.NexusTypeSlot
import com.nexus.launcher.typography.TypefaceWeightMapper

@SuppressLint("ViewConstructor")
class IslandView(host: com.nexus.launcher.ui.MainActivity) : View(host) {

    var animWidth: Float = 0f
    var animHeight: Float = 0f
    var contentAlpha: Float = 1f
    var dangerPulse: Float = 0f
    var animSpeed: Float = 1f
    var position: String = "center"
    var compactSize: String = "medium"
    var bandHeight: Float = 0f
    var cutoutCenterX: Float = -1f
    var cutoutCenterY: Float = -1f
    var cutoutWidth: Float = 0f
    var cutoutHeight: Float = 0f
    var calibratedWidthDp: Int = 110
    var calibratedHeightDp: Int = 28
    var xOffsetDp: Int = 0
    var yOffsetDp: Int = 0
    var expandedPage: Int = 0
    /** What the open island can show, in order. */
    var pages: List<IslandPage> = listOf(IslandPage.GLANCE)

    /** Everything live right now: one page each, in the same order. */
    var live: List<IslandPayload> = emptyList()
    var onWidthChanged: (() -> Unit)? = null

    fun currentPage(): IslandPage = pages.getOrNull(expandedPage) ?: IslandPage.GLANCE

    /** The payload the open card is showing — the page's own, which may not be the primary. */
    fun pagePayload(): IslandPayload? =
        if (currentPage() == IslandPage.ACTIVITY) live.getOrNull(expandedPage) ?: primary else null

    var shape: IslandShape = IslandShape.DORMANT
        private set
    var primary: IslandPayload? = null
        private set
    var secondary: IslandPayload? = null
        private set

    val primaryRect = RectF()
    val secondaryRect = RectF()
    val capRect = RectF()
    val playRect = RectF()
    val prevRect = RectF()
    val nextRect = RectF()
    val actionRect = RectF()
    val artRect = RectF()

    var onTap: (() -> Unit)? = null
    var onLaunch: (() -> Unit)? = null
    var onDoubleTap: (() -> Unit)? = null
    var onLongPress: (() -> Unit)? = null
    var onSwipeUp: (() -> Unit)? = null
    var onSwipeLeft: (() -> Unit)? = null
    var onSwipeRight: (() -> Unit)? = null
    var onPlayToggle: (() -> Unit)? = null
    var onPrev: (() -> Unit)? = null
    var onNext: (() -> Unit)? = null
    var onAction: (() -> Unit)? = null
    /** One of the live activity's own buttons, by position. */
    var onActivityAction: ((Int) -> Unit)? = null
    var settingsData: com.nexus.launcher.data.prefs.NexusSettingsData? = null

    private val density = resources.displayMetrics.density
    private var tokens: NexusColorTokens = try {
        ThemeObserver.currentTokens(context)
    } catch (_: Exception) {
        NexusColorTokens.Dark
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        isSubpixelText = true
        textAlign = Paint.Align.LEFT
    }
    private val subPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        isSubpixelText = true
        textAlign = Paint.Align.LEFT
    }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val touch = IslandTouchHandler(this)
    private val clipPath = android.graphics.Path()

    init {
        layoutDirection = LAYOUT_DIRECTION_LTR
        isClickable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_YES
        animWidth = 0f
        animHeight = 0f
        applyTokens(tokens)
    }

    fun applyTokens(next: NexusColorTokens) {
        tokens = next
        titlePaint.color = next.textPrimary
        subPaint.color = next.textSecondary
        accentPaint.color = next.accent
        CanvasTypographyHelper.applyTypeface(context, titlePaint, TypefaceWeightMapper.BOLD)
        CanvasTypographyHelper.applyTypeface(context, subPaint, TypefaceWeightMapper.NORMAL)
        val title = CanvasTypographyHelper.getPaint(context, NexusTypeSlot.ICON_LABEL)
        titlePaint.textSize = title.textSize
        val cap = CanvasTypographyHelper.getPaint(context, NexusTypeSlot.CAPTION)
        subPaint.textSize = cap.textSize
        invalidate()
    }

    fun bind(shape: IslandShape, primary: IslandPayload?, secondary: IslandPayload?) {
        this.shape = shape
        this.primary = primary
        this.secondary = secondary
        contentDescription = primary?.title
        invalidate()
    }

    /** The page moved: same shape, new content. */
    fun bindPage() {
        contentDescription = pagePayload()?.title ?: primary?.title
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val specW = MeasureSpec.getSize(widthMeasureSpec)
        val w = specW.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val centerY = cutoutCenterY.takeIf { it > 0f } ?: (bandHeight / 2f)
        val h = IslandGeometry.viewHeightPx(centerY, density)
        setMeasuredDimension(w, h)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        if (w != oldw && w > 0) onWidthChanged?.invoke()
    }

    override fun onDraw(canvas: Canvas) {
        val centerY = cutoutCenterY.takeIf { it > 0f }
            ?: ((bandHeight.takeIf { it > 0f } ?: (24f * density)) / 2f)
        IslandGeometry.layoutPills(
            capRect, primaryRect, secondaryRect, width.toFloat(), animWidth, animHeight, shape, position, density,
            centerY, cutoutCenterX, calibratedWidthDp, calibratedHeightDp, xOffsetDp, yOffsetDp,
        )
        val islandLeft = if (shape != IslandShape.EXPANDED && !primaryRect.isEmpty) {
            primaryRect.left
        } else if (!capRect.isEmpty) {
            capRect.left
        } else null
        val islandRight = if (shape != IslandShape.EXPANDED && !primaryRect.isEmpty) {
            primaryRect.right
        } else if (!capRect.isEmpty) {
            capRect.right
        } else null
        com.nexus.launcher.ui.immersive.ImmersiveStatus.setIslandBounds(islandLeft, islandRight)

        val title = CanvasTypographyHelper.getPaint(context, NexusTypeSlot.ICON_LABEL)
        titlePaint.textSize = title.textSize
        titlePaint.typeface = title.typeface
        if (!capRect.isEmpty && (primaryRect.isEmpty || shape == IslandShape.EXPANDED)) {
            drawCap(canvas)
        }
        if (!primaryRect.isEmpty) {
            drawOne(canvas, primaryRect, primary, isPrimary = true)
        }
        if (shape == IslandShape.MULTI && secondary != null && !secondaryRect.isEmpty) {
            drawOne(canvas, secondaryRect, secondary, isPrimary = false)
        }
        // The equaliser is a handful of bars: 15 frames a second is plenty, and a full-width
        // view repainting every frame of every song is not.
        if (primary?.kind == IslandKind.MUSIC && primary?.playing == true && !primaryRect.isEmpty &&
            shape != IslandShape.DORMANT
        ) {
            postInvalidateDelayed(EQ_FRAME_MS)
        }
        // A running clock or a sliding "working" bar needs its own repaint; a settled progress
        // bar does not, and is redrawn when the app next updates its notification.
        val ticking = pagePayload() ?: primary
        if (ticking?.usesActivityCard == true && shape != IslandShape.DORMANT &&
            (ticking.chronoBaseMs > 0L || ticking.busy)
        ) {
            postInvalidateDelayed(ACTIVITY_FRAME_MS)
        }
    }

    private fun drawOne(canvas: Canvas, rect: RectF, payload: IslandPayload?, isPrimary: Boolean) {
        if (rect.width() <= 0f || rect.height() <= 0f) return
        IslandRenderer.drawPill(canvas, rect, tokens, fillPaint, strokePaint, if (isPrimary) dangerPulse else 0f, density)
        clipPath.reset()
        clipPath.addRoundRect(rect, rect.height() / 2f, rect.height() / 2f, android.graphics.Path.Direction.CW)
        val save = canvas.save()
        canvas.clipPath(clipPath)
        when {
            payload == null || shape == IslandShape.DORMANT -> {
                accentPaint.color = tokens.accent
                accentPaint.alpha = (contentAlpha * 255f).toInt()
                IslandContentDraw.drawDormant(canvas, rect, accentPaint)
            }
            shape == IslandShape.EXPANDED && isPrimary -> {
                playRect.setEmpty()
                prevRect.setEmpty()
                nextRect.setEmpty()
                actionRect.setEmpty()
                artRect.setEmpty()
                val shown = pagePayload()
                if (shown?.kind == IslandKind.CALL) {
                    IslandContentDraw.drawExpanded(
                        canvas, rect, shown, tokens, glyphPaint, titlePaint, subPaint, fillPaint,
                        density, contentAlpha, playRect, prevRect, nextRect, actionRect, artRect, context,
                    )
                } else {
                    drawPage(canvas, rect, shown)
                }
            }
            payload?.usesActivityCard == true -> IslandActivityDraw.drawCompact(
                canvas, rect, payload, tokens, titlePaint, density, contentAlpha,
                IslandCompactZones.of(rect, cutoutCenterX, cutoutWidth, density),
            )
            else -> IslandContentDraw.drawCompact(
                canvas, rect, payload, tokens, glyphPaint, titlePaint, density, contentAlpha,
                IslandCompactZones.of(rect, cutoutCenterX, cutoutWidth, density),
            )
        }
        canvas.restoreToCount(save)
    }

    /** One page of the open island: whatever is live, the widgets, or the dock. */
    private fun drawPage(canvas: Canvas, rect: RectF, payload: IslandPayload?) {
        val pad = 10f * density
        when (currentPage()) {
            IslandPage.ACTIVITY -> {
                if (payload?.usesActivityCard == true) {
                    IslandActivityDraw.drawExpanded(
                        canvas, rect, payload, tokens, titlePaint, subPaint, density, pad,
                    )
                } else if (payload != null) {
                    IslandContentDraw.drawExpanded(
                        canvas, rect, payload, tokens, glyphPaint, titlePaint, subPaint, fillPaint,
                        density, contentAlpha, playRect, prevRect, nextRect, actionRect, artRect, context,
                    )
                } else {
                    IslandCardDraw.drawMediaEmpty(canvas, rect, tokens, density, pad, context)
                }
            }
            IslandPage.GLANCE ->
                IslandGlanceDraw.draw(canvas, rect, density, pad, context)
        }
        if (pages.size > 1) {
            IslandCardDraw.drawPageIndicators(canvas, rect, expandedPage, pages.size, tokens, density)
        }
    }

    private fun drawCap(canvas: Canvas) {
        if (capRect.isEmpty) return
        IslandRenderer.drawPill(canvas, capRect, tokens, fillPaint, strokePaint, 0f, density)
        accentPaint.color = tokens.accent
        accentPaint.alpha = (contentAlpha * 255f).toInt()
        val payload = primary
        if (payload == null) {
            IslandContentDraw.drawDormant(canvas, capRect, accentPaint)
        } else {
            IslandGlyphs.draw(
                canvas, payload.kind, capRect.centerX(), capRect.centerY(),
                capRect.height() * 0.62f, accentPaint,
            )
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = touch.onTouch(event)

    private companion object {
        /** ~15fps for the playing equaliser. */
        const val EQ_FRAME_MS = 66L

        /** A live activity's clock only moves once a second. */
        const val ACTIVITY_FRAME_MS = 1000L
    }
}
