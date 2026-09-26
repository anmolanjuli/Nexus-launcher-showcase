package com.nexus.launcher.ui.widgets.weather

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.ui.widgets.NexusNeumorphicDraw
import com.nexus.launcher.ui.widgets.music.NexusMusicWidgetProvider

class NexusWeatherQuickSettingsActivity : Activity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        window?.setGravity(Gravity.CENTER)

        NexusMusicWidgetProvider.triggerHaptic(this)

        appWidgetId = intent.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        )

        val dp = resources.displayMetrics.density
        val palette = NexusNeumorphicDraw.resolvePalette(this)

        val root = FrameLayout(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundColor(Color.argb(135, 0, 0, 0))
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                finish()
            }
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val cardBg = GradientDrawable().apply {
                setColor(palette.surfaceLight)
                cornerRadius = 24f * dp
                setStroke((1.5f * dp).toInt(), palette.highlight)
            }
            background = cardBg
            val p = (22 * dp).toInt()
            setPadding(p, p, p, p)
            elevation = 16f * dp
            isClickable = true
        }

        val sourceBounds = intent.sourceBounds
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val cardW = (290 * dp).toInt()

        val cardLp = FrameLayout.LayoutParams(
            cardW,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        if (sourceBounds != null && !sourceBounds.isEmpty) {
            val desiredLeft = (sourceBounds.centerX() - cardW / 2).coerceIn((16 * dp).toInt(), (screenW - cardW - 16 * dp).toInt().coerceAtLeast(0))
            val desiredTop = (sourceBounds.centerY() - (110 * dp).toInt()).coerceIn((48 * dp).toInt(), (screenH - (240 * dp).toInt()).coerceAtLeast(0))
            cardLp.gravity = Gravity.TOP or Gravity.START
            cardLp.leftMargin = desiredLeft
            cardLp.topMargin = desiredTop
        } else {
            cardLp.gravity = Gravity.CENTER
        }
        root.addView(card, cardLp)

        // Title
        val titleView = TextView(this).apply {
            text = getString(R.string.weather_quick_settings_title)
            setTextColor(palette.textPrimary)
            textSize = 17f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, (14 * dp).toInt())
        }
        card.addView(titleView)

        // Unit selector row
        val currentUnit = NexusWeatherRepository.getTemperatureUnit(this)
        val unitRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(0, 0, 0, (14 * dp).toInt())
        }

        fun createUnitButton(label: String, unitKey: String): Button {
            val isSelected = currentUnit == unitKey
            return Button(this).apply {
                text = label
                isAllCaps = false
                textSize = 13f
                setTextColor(if (isSelected) palette.surfaceLight else palette.textPrimary)
                val bg = GradientDrawable().apply {
                    setColor(if (isSelected) palette.textPrimary else palette.debossedBg)
                    cornerRadius = 14f * dp
                }
                background = bg
                layoutParams = LinearLayout.LayoutParams(0, (42 * dp).toInt(), 1f).apply {
                    marginEnd = (6 * dp).toInt()
                }
                setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    NexusWeatherRepository.setTemperatureUnit(this@NexusWeatherQuickSettingsActivity, unitKey)
                    notifyWidgetUpdate()
                    finish()
                }
            }
        }

        val btnC = createUnitButton(getString(R.string.weather_unit_celsius), "celsius")
        val btnF = createUnitButton(getString(R.string.weather_unit_fahrenheit), "fahrenheit")
        unitRow.addView(btnC)
        unitRow.addView(btnF)
        card.addView(unitRow)

        // Refresh Forecast button
        val refreshBtn = Button(this).apply {
            text = getString(R.string.weather_refresh_button)
            isAllCaps = false
            textSize = 14f
            setTextColor(palette.textPrimary)
            val bg = GradientDrawable().apply {
                setColor(palette.debossedBg)
                cornerRadius = 14f * dp
                setStroke((1f * dp).toInt(), palette.shadow)
            }
            background = bg
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (44 * dp).toInt()).apply {
                bottomMargin = (8 * dp).toInt()
            }
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                NexusWeatherRepository.setTemperatureUnit(this@NexusWeatherQuickSettingsActivity, currentUnit)
                notifyWidgetUpdate()
                finish()
            }
        }

        card.addView(refreshBtn)

        // Location Permissions button
        val locBtn = Button(this).apply {
            text = getString(R.string.weather_open_location_settings)
            isAllCaps = false
            textSize = 13f
            setTextColor(palette.textSecondary)
            val bg = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = 14f * dp
            }
            background = bg
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (38 * dp).toInt())
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                startActivity(intent)
                finish()
            }
        }
        card.addView(locBtn)

        setContentView(root)
    }

    private fun notifyWidgetUpdate() {
        val updateIntent = Intent(this, NexusWeatherWidgetProvider::class.java).apply {
            action = "com.nexus.launcher.ACTION_NEXUS_WIDGET_CONFIG_CHANGED"
            putExtra("appWidgetId", appWidgetId)
        }
        sendBroadcast(updateIntent)
        NexusWeatherPermission.refreshAll(this)
    }
}
