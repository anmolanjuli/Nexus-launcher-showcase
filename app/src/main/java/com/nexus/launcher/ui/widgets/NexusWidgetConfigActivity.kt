package com.nexus.launcher.ui.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

class NexusWidgetConfigActivity : Activity() {
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private lateinit var config: NexusWidgetConfig.InstanceConfig

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = intent.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        config = NexusWidgetConfig.read(this, appWidgetId)

        val dp = resources.displayMetrics.density
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0E0C18"))
            setPadding((16 * dp).toInt(), (32 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt())
        }

        // The Soft UI / Glass / Frosted mode picker that used to live here was removed — that
        // per-item choice was redundant with the global UI Style toggle (Nexus Settings >
        // Appearance): NexusWidgetConfig.isGlassSurface()/isNeumorphicSurface() (which every
        // renderer routes through) already only follow the global toggle. This screen now only
        // sets initial opacity before the widget is placed; the widget's own long-press settings
        // sheet still exposes the full Expressive Gradient toggle + swatch picker afterward.
        val opacityTitle = TextView(this).apply {
            text = this@NexusWidgetConfigActivity.getString(com.nexus.launcher.R.string.dock_settings_opacity)
            setTextColor(Color.WHITE)
            textSize = 14f
        }
        root.addView(opacityTitle)

        val slider = SeekBar(this).apply {
            max = 100
            progress = (config.backgroundOpacity * 100).toInt()
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    config = config.copy(backgroundOpacity = progress / 100f)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
            setPadding(0, (16 * dp).toInt(), 0, (32 * dp).toInt())
        }
        root.addView(slider)

        val spacer = android.view.View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        root.addView(spacer)

        val doneBtn = Button(this).apply {
            text = this@NexusWidgetConfigActivity.getString(com.nexus.launcher.R.string.action_done)
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(Color.parseColor("#33FFFFFF"))
                cornerRadius = 8 * dp
            }
            setOnClickListener {
                saveAndFinish()
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        root.addView(doneBtn)

        setContentView(root)
    }

    private fun saveAndFinish() {
        NexusWidgetConfig.write(this, config)

        val appWidgetManager = AppWidgetManager.getInstance(this)
        val info = appWidgetManager.getAppWidgetInfo(appWidgetId)
        if (info != null) {
            val updateIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                component = info.provider
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
            }
            sendBroadcast(updateIntent)
        }

        val resultValue = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        setResult(RESULT_OK, resultValue)
        finish()
    }
}
