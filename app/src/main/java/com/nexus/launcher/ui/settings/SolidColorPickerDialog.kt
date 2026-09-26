package com.nexus.launcher.ui.settings

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

object SolidColorPickerDialog {

    fun show(
        context: Context,
        initialHex: String,
        accentColorHex: String,
        onPicked: (String) -> Unit
    ) {
        val dp = context.resources.displayMetrics.density
        
        val dialogView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (24 * dp).toInt()
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0E0C18"))
                cornerRadius = 16 * dp
            }
        }

        val preview = android.view.View(context).apply {
            layoutParams = LinearLayout.LayoutParams((120 * dp).toInt(), (56 * dp).toInt()).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = (24 * dp).toInt()
            }
            background = GradientDrawable().apply {
                cornerRadius = 12 * dp
                setStroke((1 * dp).toInt(), Color.parseColor("#33FFFFFF"))
            }
        }
        dialogView.addView(preview)

        val hexValueText = TextView(context).apply {
            textSize = 13f
            setTextColor(Color.parseColor(accentColorHex))
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (16 * dp).toInt()
                bottomMargin = (24 * dp).toInt()
            }
        }

        var hsv = FloatArray(3)
        try {
            Color.colorToHSV(Color.parseColor(initialHex), hsv)
        } catch (e: Exception) {
            hsv = floatArrayOf(0f, 1f, 1f)
        }

        fun updatePreview(hue: Float) {
            hsv[0] = hue
            hsv[1] = 1f
            hsv[2] = 1f
            val c = Color.HSVToColor(hsv)
            val hex = String.format("#%06X", 0xFFFFFF and c)
            (preview.background as GradientDrawable).setColor(c)
            hexValueText.text = hex
        }

        dialogView.addView(hexValueText)

        val wheelContainer = LinearLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER
            val pad = (16 * dp).toInt()
            setPadding(pad, 0, pad, pad)
        }
        
        val hueWheel = com.nexus.launcher.ui.settings.NexusHueWheelView(
            context = context,
            density = dp,
            initialHue = hsv[0],
            onHueChanged = { hue ->
                updatePreview(hue)
            }
        )
        wheelContainer.addView(hueWheel)
        dialogView.addView(wheelContainer)

        val buttonsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        var dialog: AlertDialog? = null

        val cancelBtn = TextView(context).apply {
            text = context.getString(com.nexus.launcher.R.string.action_cancel)
            setTextColor(Color.parseColor("#99FFFFFF")) // 60% alpha white
            textSize = 14f
            setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
            setOnClickListener { dialog?.dismiss() }
        }
        
        val applyBtn = TextView(context).apply {
            text = context.getString(com.nexus.launcher.R.string.action_apply)
            setTextColor(Color.parseColor(accentColorHex))
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
            setOnClickListener {
                val c = Color.HSVToColor(hsv)
                val hex = String.format("#%06X", 0xFFFFFF and c)
                onPicked(hex)
                dialog?.dismiss()
            }
        }

        buttonsRow.addView(cancelBtn)
        buttonsRow.addView(applyBtn)
        dialogView.addView(buttonsRow)

        dialog = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setView(dialogView)
            .create()
            
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        dialog.show()
        
        updatePreview(hsv[0])
    }
}
