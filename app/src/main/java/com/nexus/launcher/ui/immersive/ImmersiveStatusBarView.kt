package com.nexus.launcher.ui.immersive

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.nexus.launcher.R
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.CanvasTypographyHelper
import com.nexus.launcher.typography.TypefaceWeightMapper

/**
 * Immersive status row. Optional per-item glass pills around the camera, plus an optional fade.
 */
@SuppressLint("ViewConstructor")
class ImmersiveStatusBarView(
    private val activity: ComponentActivity,
    private val labelColor: () -> Int
) : View(activity) {

    private val density = resources.displayMetrics.density
    private var rowHeight = (ImmersiveStatus.ROW_HEIGHT_DP * density).toInt()
    private val wordGap = 6f * density
    private val hitSlop = 6f * density

    private val sources = StatusSources(activity) { invalidate() }
    private var items = ImmersiveStatus.Items()
    private var itemStyle = ImmersiveStatusStyle()

    private var cutoutLeft: Float? = null
    private var cutoutRight: Float? = null
    private var islandLeft: Float? = null
    private var islandRight: Float? = null
    private var cutoutBand = 0
    private var bandHeight = rowHeight

    private var barPadding = 0f
    private var barBackground = ImmersiveStatusStyle.BACKGROUND_TRANSPARENT

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 13f * resources.displayMetrics.scaledDensity
        typeface = CanvasTypographyHelper.getTypeface(activity, 600)
    }
    private val captionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f * resources.displayMetrics.scaledDensity
        typeface = CanvasTypographyHelper.getTypeface(activity, TypefaceWeightMapper.NORMAL)
    }
    private val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.3f * density
    }
    private val bell: Drawable? = ContextCompat.getDrawable(activity, R.drawable.ic_bell)?.mutate()
    private val wifi: Drawable? = ContextCompat.getDrawable(activity, R.drawable.sc_android_wifi)?.mutate()
    private val scratch = RectF()
    private val painters = ImmersiveStatusPainters(
        activity, density, 15f * density, textPaint, captionPaint, shapePaint, strokePaint, scratch,
    )
    private val glass = ImmersiveStatusGlass(this, density)
    private val pills = ImmersiveStatusPillLayout(density)

    private val clockRect = RectF()
    private val notificationRect = RectF()
    private val wifiRect = RectF()

    init {
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val cutout = insets.displayCutout
            val top = cutout?.boundingRects?.firstOrNull { it.top <= 0 && it.width() < it.height() * 4 }
            cutoutLeft = top?.left?.toFloat()
            cutoutRight = top?.right?.toFloat()
            cutoutBand = insets.getInsets(WindowInsetsCompat.Type.displayCutout()).top
            applyHeight()
            invalidate()
            insets
        }
    }

    fun setItems(value: ImmersiveStatus.Items) {
        if (value == items) return
        items = value
        invalidate()
    }

    fun setItemStyle(value: ImmersiveStatusStyle) {
        if (value == itemStyle) return
        itemStyle = value
        sources.wantsAppIcons = value.notifications == ImmersiveStatusStyle.NOTIFICATIONS_ICONS
        sources.wantsSeconds = value.clock == ImmersiveStatusStyle.CLOCK_SECONDS
        sources.maxIcons = value.maxIcons
        sources.countShape = StatusSources.CountShape(value.showSilent, value.groupByApp)
        sources.wantsNames = StatusSources.Names(
            carrier = value.showCarrier, dataType = value.showDataType,
            ssid = value.showSsid, band = value.showBand,
        )
        painters.datePattern = when (value.dateFormat) {
            ImmersiveStatusStyle.DATE_LONG -> "EEEE d MMMM"
            ImmersiveStatusStyle.DATE_WEEKDAY -> "EEEE"
            else -> "EEE d MMM"
        }
        applyFonts()
        applyHeight()
        invalidate()
    }

    private fun applyFonts() {
        textPaint.typeface = StatusBarFonts.typeface(activity, itemStyle.fontKey, 600)
        captionPaint.typeface = StatusBarFonts.typeface(
            activity, itemStyle.fontKey, TypefaceWeightMapper.NORMAL,
        )
    }

    fun setBar(heightDp: Int, paddingDp: Int, background: String) {
        barPadding = paddingDp * density
        barBackground = background
        val nextHeight = (heightDp * density).toInt()
        if (nextHeight != rowHeight) {
            rowHeight = nextHeight
            applyHeight()
        }
        invalidate()
    }

    fun setIslandCutout(left: Float?, right: Float?) {
        if (islandLeft == left && islandRight == right) return
        islandLeft = left
        islandRight = right
        invalidate()
    }

    private fun applyHeight() {
        val params = layoutParams as? android.widget.FrameLayout.LayoutParams
        var changed = false
        if (params != null && params.topMargin != 0) {
            params.topMargin = 0
            layoutParams = params
            changed = true
        }
        val next = resolvedBandHeight()
        if (bandHeight != next) {
            bandHeight = next
            changed = true
        }
        if (changed) requestLayout()
    }

    /** At the top, fill the camera cutout so pills can sit fully rounded inside it. */
    private fun resolvedBandHeight(): Int {
        if (itemStyle.barAtBottom) return rowHeight
        return if (cutoutBand > 0) maxOf(rowHeight, cutoutBand) else rowHeight
    }

    private fun itemCentreY(): Float = height / 2f

    /** Glyphs follow the height slider; the view itself may still fill the camera cutout. */
    private fun scaleGlyphsToPill() {
        val breathe = 3.5f * density
        val pillH = rowHeight.toFloat()
        val inner = (pillH - breathe * 2f).coerceAtLeast(10f * density)
        painters.iconSize = inner * 0.76f
        painters.insideMeter = (pillH - 4f * density).coerceAtLeast(inner)
        val sp = resources.displayMetrics.scaledDensity
        textPaint.textSize = (painters.iconSize * 0.82f).coerceIn(11f * sp, 15f * sp)
        captionPaint.textSize = textPaint.textSize
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        sources.start(activity)
        glass.attach()
        applyFonts()
        ViewCompat.requestApplyInsets(this)
    }

    override fun onDetachedFromWindow() {
        sources.stop()
        glass.detach()
        removeCallbacks(pulseTick)
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), bandHeight)
    }

    override fun onDraw(canvas: Canvas) {
        val tokens = try {
            ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            return
        }
        val ink = labelColor()
        textPaint.color = ink
        captionPaint.color = ink
        painters.captionColor = ink
        painters.alertColor = tokens.danger
        shapePaint.color = ink
        strokePaint.color = ink
        if (itemStyle.barFade) {
            glass.drawFade(canvas, width.toFloat(), height.toFloat(), itemStyle.barAtBottom)
        }

        scaleGlyphsToPill()
        val cy = itemCentreY()
        val baseline = cy - (textPaint.descent() + textPaint.ascent()) / 2f
        val capBaseline = cy - (captionPaint.descent() + captionPaint.ascent()) / 2f
        clockRect.setEmpty(); notificationRect.setEmpty(); wifiRect.setEmpty()
        pills.layout(
            painters, items, itemStyle, sources,
            width.toFloat(), height.toFloat(), rowHeight, barPadding,
            cutoutLeft, cutoutRight, islandLeft, islandRight,
        )

        val charging = sources.isCharging && itemStyle.chargingAnimation
        val opacity = itemStyle.pillOpacity
        if (itemStyle.segmentedPills) {
            glass.drawPill(canvas, pills.notifications, barBackground, opacity)
            glass.drawPill(canvas, pills.clock, barBackground, opacity)
            glass.drawPill(canvas, pills.wifi, barBackground, opacity)
            glass.drawPill(canvas, pills.signal, barBackground, opacity)
            glass.drawPill(canvas, pills.battery, barBackground, opacity)
        }

        drawNotifications(canvas, cy, capBaseline)
        drawClock(canvas, baseline)
        drawWifi(canvas, cy, capBaseline)
        drawSignal(canvas, cy, capBaseline)
        drawBattery(canvas, cy, capBaseline, charging)
    }

    private fun drawNotifications(canvas: Canvas, cy: Float, capBaseline: Float) {
        if (pills.notifications.isEmpty) return
        val x = pills.notifications.right - pills.pad
        val next = painters.drawNotifications(
            canvas, x, cy, capBaseline, pills.effectiveNotificationsStyle,
            sources.notificationCount, bell, sources.notificationIcons,
        )
        if (next < x) {
            notificationRect.set(
                pills.notifications.left - hitSlop, 0f,
                pills.notifications.right + hitSlop, height.toFloat(),
            )
        }
    }

    private fun drawClock(canvas: Canvas, baseline: Float) {
        if (pills.clock.isEmpty) return
        drawClockAt(canvas, pills.clock.right - pills.pad, baseline)
    }

    private fun drawWifi(canvas: Canvas, cy: Float, capBaseline: Float) {
        if (pills.wifi.isEmpty) return
        var next = painters.drawWifi(
            canvas, pills.wifi.left + pills.pad, cy, itemStyle.wifi, sources.wifiLevel, wifi,
        )
        wifiRect.set(pills.wifi.left - hitSlop, 0f, pills.wifi.right + hitSlop, height.toFloat())
        if (pills.showSsid) next = painters.drawWord(canvas, sources.ssid, next + wordGap, capBaseline)
        if (pills.showBand) painters.drawWord(canvas, sources.band, next + wordGap, capBaseline)
    }

    private fun drawSignal(canvas: Canvas, cy: Float, capBaseline: Float) {
        if (pills.signal.isEmpty) return
        var next = pills.signal.left + pills.pad
        if (sources.signalLevel >= 0) {
            next = painters.drawSignal(canvas, next, cy, itemStyle.signal, sources.signalLevel)
        }
        if (pills.showDataType) next = painters.drawWord(canvas, sources.dataType, next + wordGap, capBaseline)
        if (pills.showCarrier) painters.drawWord(canvas, sources.carrier, next + wordGap, capBaseline)
    }

    private fun drawBattery(canvas: Canvas, cy: Float, capBaseline: Float, charging: Boolean) {
        if (pills.battery.isEmpty) return
        schedulePulse(charging)
        painters.drawBattery(
            canvas, pills.battery.left + pills.pad, cy, capBaseline,
            itemStyle.battery, pills.effectiveBatteryPercent,
            sources.batteryPercent, itemStyle.lowBatteryPercent, charging, pulse(),
        )
    }

    private fun drawClockAt(canvas: Canvas, right: Float, baseline: Float) {
        painters.drawClock(canvas, right, baseline, pills.effectiveClockStyle)
        clockRect.set(
            pills.clock.left - hitSlop, 0f,
            pills.clock.right + hitSlop, height.toFloat(),
        )
    }

    private val pulseTick = object : Runnable {
        override fun run() {
            invalidate()
            postDelayed(this, PULSE_FRAME_MS)
        }
    }
    private var pulsing = false

    private fun schedulePulse(charging: Boolean) {
        if (pulsing == charging) return
        pulsing = charging
        removeCallbacks(pulseTick)
        if (charging) postDelayed(pulseTick, PULSE_FRAME_MS)
    }

    private fun pulse(): Float {
        if (!pulsing) return 1f
        val phase = (System.currentTimeMillis() % PULSE_CYCLE_MS) / PULSE_CYCLE_MS.toFloat()
        return 0.45f + 0.55f * kotlin.math.abs(1f - phase * 2f)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if ((activity as? com.nexus.launcher.ui.MainActivity)?.canvasView?.let {
                com.nexus.launcher.ui.canvas.SelectionModeTransform.isCardTrackActive(it)
            } == true) return false
        val target = targetAt(event.x, event.y) ?: return false
        if (event.action == MotionEvent.ACTION_UP) {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            when (target) {
                Target.NOTIFICATIONS -> openNotifications()
                Target.CLOCK -> launch(Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS))
                Target.WIFI -> launch(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
            }
        }
        return true
    }

    private enum class Target { NOTIFICATIONS, CLOCK, WIFI }

    private fun targetAt(x: Float, y: Float): Target? = when {
        notificationRect.contains(x, y) -> Target.NOTIFICATIONS
        clockRect.contains(x, y) -> Target.CLOCK
        wifiRect.contains(x, y) -> Target.WIFI
        else -> null
    }

    private fun openNotifications() {
        com.nexus.launcher.ui.notifications.NotificationSheet.show(context)
    }

    private fun launch(intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) { }
    }

    private companion object {
        const val PULSE_FRAME_MS = 60L
        const val PULSE_CYCLE_MS = 2000L
    }
}
