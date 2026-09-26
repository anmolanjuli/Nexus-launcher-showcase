package com.nexus.launcher.ui.island

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.hardware.camera2.CameraManager
import android.os.BatteryManager
import android.text.TextPaint
import com.nexus.launcher.R
import com.nexus.launcher.service.NexusNotificationService
import com.nexus.launcher.ui.widgets.weather.NexusWeatherRepository

/**
 * The island's second page: a glance, not a widget board.
 *
 * Three small tiles — the weather where the phone is, the battery, and the torch — plus a way
 * into the notification page. Anything larger belongs on the home screen or, later, the side
 * panel; a capsule under the camera is the wrong place for a full widget.
 *
 * The weather here is the real reading the weather widget already fetches
 * ([NexusWeatherRepository.getCachedTemperature]); when there is none — no location permission,
 * nothing fetched yet — the tile says so rather than inventing a number, which is what the page
 * it replaces used to do.
 *
 * The tiles are drawn for the island, not for the theme: the card is black whatever the theme
 * is, so light theme tokens turned it into a pale panel sitting in a black hole. They are white
 * text floating on the card, with no card of their own.
 */
object IslandGlanceDraw {

    private val tileRects = mutableListOf<Pair<RectF, Tile>>()
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val valuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val labelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }

    private enum class Tile { WEATHER, BATTERY, TORCH, NOTIFICATIONS }

    private var torchOn = false

    /**
     * Which notifications to count, so the tile and the immersive status row never disagree —
     * both follow the same two settings (`Show silent`, `Group by app`).
     */
    var countShape: CountShape = CountShape()

    data class CountShape(val includeSilent: Boolean = true, val byApp: Boolean = false)

    private fun notificationCount(): Int {
        val counts = NexusNotificationService.counts.value
        return when {
            countShape.byApp && countShape.includeSilent -> counts.apps
            countShape.byApp -> counts.loudApps
            countShape.includeSilent -> counts.all
            else -> counts.loud
        }
    }

    fun draw(
        canvas: Canvas,
        pill: RectF,
        density: Float,
        pad: Float,
        context: Context,
    ) {
        tileRects.clear()
        val tiles = listOf(Tile.WEATHER, Tile.BATTERY, Tile.TORCH, Tile.NOTIFICATIONS)
        val gap = 8f * density
        val top = pill.top + pad + 2f * density
        val height = (pill.height() - pad * 2f - 18f * density).coerceAtLeast(40f * density)
        val width = (pill.width() - pad * 2f - gap * (tiles.size - 1)) / tiles.size
        var x = pill.left + pad

        dividerPaint.color = HAIRLINE
        dividerPaint.strokeWidth = 1f * density
        valuePaint.textSize = 15f * density
        labelPaint.textSize = 9f * density

        for ((index, tile) in tiles.withIndex()) {
            val rect = RectF(x, top, x + width, top + height)
            if (index > 0) {
                val lineX = rect.left - gap / 2f
                canvas.drawLine(
                    lineX, rect.top + height * 0.22f, lineX, rect.bottom - height * 0.22f,
                    dividerPaint,
                )
            }
            drawTile(canvas, rect, tile, density, context)
            tileRects.add(rect to tile)
            x += width + gap
        }
    }

    private fun drawTile(
        canvas: Canvas,
        rect: RectF,
        tile: Tile,
        density: Float,
        context: Context,
    ) {
        valuePaint.color = VALUE
        labelPaint.color = LABEL
        val cx = rect.centerX()
        val valueY = rect.centerY() + 1f * density
        val labelY = rect.bottom - 8f * density
        when (tile) {
            Tile.WEATHER -> {
                val reading = NexusWeatherRepository.getCachedTemperature(context)
                val value = reading?.let { "${it.first}${it.second}" }
                    ?: context.getString(R.string.island_glance_no_reading)
                canvas.drawText(value, cx, valueY, valuePaint)
                canvas.drawText(context.getString(R.string.island_glance_weather), cx, labelY, labelPaint)
            }
            Tile.BATTERY -> {
                val manager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
                val percent = manager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
                val value = if (percent in 0..100) {
                    com.nexus.launcher.locale.LocaleDigitUtils.formatPercent(percent, context)
                } else {
                    context.getString(R.string.island_glance_no_reading)
                }
                canvas.drawText(value, cx, valueY, valuePaint)
                canvas.drawText(context.getString(R.string.island_glance_battery), cx, labelY, labelPaint)
            }
            Tile.TORCH -> {
                // On is the only state worth lighting up; off stays as quiet as its neighbours.
                valuePaint.color = if (torchOn) VALUE else LABEL
                canvas.drawText(
                    context.getString(
                        if (torchOn) R.string.island_glance_on else R.string.island_glance_off
                    ),
                    cx, valueY, valuePaint,
                )
                valuePaint.color = VALUE
                canvas.drawText(context.getString(R.string.island_glance_torch), cx, labelY, labelPaint)
            }
            Tile.NOTIFICATIONS -> {
                val count = notificationCount()
                canvas.drawText(
                    com.nexus.launcher.locale.LocaleDigitUtils.formatNumber(
                        count, com.nexus.launcher.locale.LocaleObserver.getEffectiveLocale(context),
                    ),
                    cx, valueY, valuePaint,
                )
                canvas.drawText(
                    context.getString(R.string.island_glance_notifications), cx, labelY, labelPaint,
                )
            }
        }
    }

    /** True when the tap landed on a tile and was dealt with. */
    fun hitTest(x: Float, y: Float, context: Context): Boolean {
        val hit = tileRects.firstOrNull { it.first.contains(x, y) }?.second ?: return false
        when (hit) {
            Tile.WEATHER -> launch(
                context,
                Intent(Intent.ACTION_VIEW, android.net.Uri.parse("geo:0,0?q=weather")),
            )
            Tile.BATTERY -> launch(context, Intent(Intent.ACTION_POWER_USAGE_SUMMARY))
            Tile.TORCH -> toggleTorch(context)
            Tile.NOTIFICATIONS -> com.nexus.launcher.ui.notifications.NotificationSheet.show(context)
        }
        return true
    }

    private fun toggleTorch(context: Context) {
        runCatching {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = manager.cameraIdList.firstOrNull { cameraId ->
                manager.getCameraCharacteristics(cameraId)
                    .get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return
            torchOn = !torchOn
            manager.setTorchMode(id, torchOn)
        }
    }

    private fun launch(context: Context, intent: Intent) {
        runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** White on the island's black, at three weights. The card ignores the theme, so do these. */
    private val VALUE = android.graphics.Color.WHITE
    private val LABEL = android.graphics.Color.argb(150, 255, 255, 255)
    private val HAIRLINE = android.graphics.Color.argb(38, 255, 255, 255)
}
