package com.nexus.launcher.ui.immersive

import android.graphics.RectF

/** One stadium pill per status module, laid out around the camera. */
internal class ImmersiveStatusPillLayout(private val density: Float) {
    val clock = RectF()
    val notifications = RectF()
    val wifi = RectF()
    val signal = RectF()
    val battery = RectF()
    var pad = 8f * density
        private set
    private var pillGap = 4f * density
    private val wordGap = 6f * density
    private val cutoutGap = 6f * density

    var effectiveClockStyle: String = ImmersiveStatusStyle.CLOCK_SYSTEM
        private set
    var effectiveNotificationsStyle: String = ImmersiveStatusStyle.NOTIFICATIONS_COUNT
        private set
    var effectiveBatteryPercent: String = ImmersiveStatusStyle.PERCENT_BESIDE
        private set
    var showBand: Boolean = true
        private set
    var showCarrier: Boolean = true
        private set
    var showSsid: Boolean = true
        private set
    var showDataType: Boolean = true
        private set

    fun layout(
        painters: ImmersiveStatusPainters,
        items: ImmersiveStatus.Items,
        style: ImmersiveStatusStyle,
        sources: StatusSources,
        viewWidth: Float,
        viewHeight: Float,
        rowHeight: Int,
        barPadding: Float,
        cutoutLeft: Float?,
        cutoutRight: Float?,
        islandLeft: Float? = null,
        islandRight: Float? = null,
    ) {
        clock.setEmpty()
        notifications.setEmpty()
        wifi.setEmpty()
        signal.setEmpty()
        battery.setEmpty()
        val inset = 2f * density
        val h = rowHeight.toFloat()
            .coerceAtMost((viewHeight - inset * 2f).coerceAtLeast(16f * density))
            .coerceAtLeast(16f * density)
        if (style.segmentedPills) {
            pad = (h * 0.20f).coerceIn(4f * density, 9f * density)
            pillGap = 6f * density
        } else {
            pad = 4f * density
            pillGap = 8f * density
        }
        val top = ((viewHeight - h) / 2f).coerceAtLeast(inset)
        val bottom = (top + h).coerceAtMost(viewHeight - inset)
        val centre = viewWidth / 2f
        val cutL = cutoutLeft ?: (centre - cutoutGap / 2f)
        val cutR = cutoutRight ?: (centre + cutoutGap / 2f)
        val holeL = if (islandLeft != null && islandLeft > 0f) minOf(cutL, islandLeft) else cutL
        val holeR = if (islandRight != null && islandRight > 0f) maxOf(cutR, islandRight) else cutR

        // --- Left side layout & auto-compaction ---
        val availL = ((holeL - cutoutGap) - barPadding).coerceAtLeast(0f)
        effectiveClockStyle = style.clock
        effectiveNotificationsStyle = style.notifications

        var notifW = if (items.notifications) {
            painters.notificationsWidth(effectiveNotificationsStyle, sources.notificationCount, sources.notificationIcons)
        } else 0f
        var clockW = if (items.clock && style.clockPosition == ImmersiveStatusStyle.POSITION_LEFT) {
            painters.clockWidth(effectiveClockStyle)
        } else 0f

        fun calcLeftTotal(): Float {
            var sum = 0f
            var count = 0
            if (notifW > 0f) { sum += notifW + pad * 2f; count++ }
            if (clockW > 0f) { sum += clockW + pad * 2f; count++ }
            if (count > 1) sum += pillGap
            return sum
        }

        // Compaction Tier 1: date/seconds to standard clock
        if (calcLeftTotal() > availL && (effectiveClockStyle == ImmersiveStatusStyle.CLOCK_DATE || effectiveClockStyle == ImmersiveStatusStyle.CLOCK_SECONDS)) {
            effectiveClockStyle = ImmersiveStatusStyle.CLOCK_SYSTEM
            clockW = painters.clockWidth(effectiveClockStyle)
        }
        // Compaction Tier 2: notifications icons down to count
        if (calcLeftTotal() > availL && effectiveNotificationsStyle == ImmersiveStatusStyle.NOTIFICATIONS_ICONS) {
            effectiveNotificationsStyle = ImmersiveStatusStyle.NOTIFICATIONS_COUNT
            notifW = painters.notificationsWidth(effectiveNotificationsStyle, sources.notificationCount, sources.notificationIcons)
        }

        var leftCursor = holeL - cutoutGap
        if (items.notifications && notifW > 0f) {
            leftCursor = placeLeft(notifications, notifW, leftCursor, top, bottom, barPadding)
        }
        if (items.clock && style.clockPosition == ImmersiveStatusStyle.POSITION_LEFT && clockW > 0f) {
            leftCursor = placeLeft(clock, clockW, leftCursor, top, bottom, barPadding)
        }

        // --- Right side layout & auto-compaction ---
        val maxRight = viewWidth - barPadding
        val availR = (maxRight - (holeR + cutoutGap)).coerceAtLeast(0f)
        showBand = style.showBand
        showCarrier = style.showCarrier
        showSsid = style.showSsid
        showDataType = style.showDataType
        effectiveBatteryPercent = style.batteryPercent

        val clockRight = items.clock && (
            style.clockPosition == ImmersiveStatusStyle.POSITION_RIGHT ||
                (style.clockPosition == ImmersiveStatusStyle.POSITION_CENTER && cutoutLeft != null)
        )

        fun calcRightTotal(): Float {
            var sum = 0f
            var count = 0
            if (items.wifi && sources.wifiLevel >= 0) {
                val w = wifiWidth(painters, style, sources, showSsid, showBand)
                if (w > 0f) { sum += w + pad * 2f; count++ }
            }
            if (items.signal) {
                val w = signalWidth(painters, style, sources, showDataType, showCarrier)
                if (w > 0f) { sum += w + pad * 2f; count++ }
            }
            if (items.battery && sources.batteryPercent >= 0) {
                val w = painters.batteryWidth(style.battery, effectiveBatteryPercent, sources.batteryPercent)
                if (w > 0f) { sum += w + pad * 2f; count++ }
            }
            if (clockRight) {
                val w = painters.clockWidth(effectiveClockStyle)
                if (w > 0f) { sum += w + pad * 2f; count++ }
            }
            if (count > 1) sum += pillGap * (count - 1)
            return sum
        }

        // Compaction Tier 1: drop band (5GHz) & carrier (AT&T)
        if (calcRightTotal() > availR) {
            showBand = false
            showCarrier = false
        }
        // Compaction Tier 2: drop SSID & data type
        if (calcRightTotal() > availR) {
            showSsid = false
            showDataType = false
        }
        // Compaction Tier 3: compact battery percentage
        if (calcRightTotal() > availR && effectiveBatteryPercent == ImmersiveStatusStyle.PERCENT_BESIDE) {
            effectiveBatteryPercent = ImmersiveStatusStyle.PERCENT_OFF
        }

        var rightCursor = holeR + cutoutGap
        if (items.wifi && sources.wifiLevel >= 0) {
            val w = wifiWidth(painters, style, sources, showSsid, showBand)
            if (w > 0f) rightCursor = placeRight(wifi, w, rightCursor, top, bottom, maxRight)
        }
        if (items.signal) {
            val w = signalWidth(painters, style, sources, showDataType, showCarrier)
            if (w > 0f) rightCursor = placeRight(signal, w, rightCursor, top, bottom, maxRight)
        }
        if (items.battery && sources.batteryPercent >= 0) {
            val w = painters.batteryWidth(style.battery, effectiveBatteryPercent, sources.batteryPercent)
            if (w > 0f) rightCursor = placeRight(battery, w, rightCursor, top, bottom, maxRight)
        }
        if (clockRight) {
            val w = painters.clockWidth(effectiveClockStyle)
            if (w > 0f) placeRight(clock, w, rightCursor, top, bottom, maxRight)
        }
        if (items.clock && style.clockPosition == ImmersiveStatusStyle.POSITION_CENTER && cutoutLeft == null) {
            val w = painters.clockWidth(effectiveClockStyle)
            clock.set(centre - w / 2f - pad, top, centre + w / 2f + pad, bottom)
        }
    }

    private fun wifiWidth(
        painters: ImmersiveStatusPainters,
        style: ImmersiveStatusStyle,
        sources: StatusSources,
        includeSsid: Boolean,
        includeBand: Boolean,
    ): Float {
        var w = painters.wifiGlyphWidth(style.wifi)
        val s = if (includeSsid) painters.wordWidth(sources.ssid) else 0f
        val b = if (includeBand) painters.wordWidth(sources.band) else 0f
        val extra = s + b
        if (extra > 0f) w += wordGap + extra
        return w
    }

    private fun signalWidth(
        painters: ImmersiveStatusPainters,
        style: ImmersiveStatusStyle,
        sources: StatusSources,
        includeData: Boolean,
        includeCarrier: Boolean,
    ): Float {
        var w = 0f
        if (sources.signalLevel >= 0) w += painters.signalGlyphWidth(style.signal)
        val d = if (includeData) painters.wordWidth(sources.dataType) else 0f
        val c = if (includeCarrier) painters.wordWidth(sources.carrier) else 0f
        val extra = d + c
        if (extra > 0f) w += wordGap + extra
        return w
    }

    private fun placeLeft(
        dest: RectF, content: Float, cursor: Float, top: Float, bottom: Float, minLeft: Float,
    ): Float {
        val r = cursor
        val l = (r - content - pad * 2f).coerceAtLeast(minLeft)
        if (l >= r) return cursor
        dest.set(l, top, r, bottom)
        return l - pillGap
    }

    private fun placeRight(
        dest: RectF, content: Float, cursor: Float, top: Float, bottom: Float, maxRight: Float,
    ): Float {
        val l = cursor
        val r = (l + content + pad * 2f).coerceAtMost(maxRight)
        if (l >= r) return cursor
        dest.set(l, top, r, bottom)
        return r + pillGap
    }
}
