package com.nexus.launcher.ui.widgets

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.settings.views.NexusSegmentedRow
import com.nexus.launcher.ui.settings.views.SettingsSectionGroupView
import com.nexus.launcher.ui.widgets.mosaic.LivingMosaicHaptics

/**
 * Clock quote and Calendar specific settings section builders for NexusWidgetSettingsSheet.
 */
object NexusWidgetSettingsClockCalendarViews {

    fun buildClockMessageGroup(
        context: Context,
        dp: Float,
        tokens: NexusColorTokens,
        getConfig: () -> NexusWidgetConfig.InstanceConfig,
        updateConfig: (NexusWidgetConfig.InstanceConfig) -> Unit,
        onChanged: () -> Unit
    ): Pair<SettingsSectionGroupView, () -> Unit> {
        val group = SettingsSectionGroupView(context)

        val presetRow = NexusSegmentedRow(context).apply {
            val presets = listOf(
                "0" to context.getString(com.nexus.launcher.R.string.icon_subtitle_default),
                "1" to context.getString(com.nexus.launcher.R.string.widget_quote_joy),
                "2" to context.getString(com.nexus.launcher.R.string.widget_quote_focus),
                "3" to context.getString(com.nexus.launcher.R.string.widget_quote_kind)
            )
            val currentPreset = when (getConfig().customText) {
                context.getString(R.string.clock_greeting_quote) -> "0"
                context.getString(R.string.clock_greeting_quote_2) -> "1"
                context.getString(R.string.clock_greeting_quote_3) -> "2"
                context.getString(R.string.clock_greeting_quote_4) -> "3"
                null -> "0"
                else -> "custom"
            }
            configure(context.getString(com.nexus.launcher.R.string.widget_preset_quotes), presets, if (currentPreset == "custom") "0" else currentPreset)
            onValueChanged = { value ->
                LivingMosaicHaptics.tick(this)
                val text = when (value) {
                    "0" -> context.getString(R.string.clock_greeting_quote)
                    "1" -> context.getString(R.string.clock_greeting_quote_2)
                    "2" -> context.getString(R.string.clock_greeting_quote_3)
                    "3" -> context.getString(R.string.clock_greeting_quote_4)
                    else -> context.getString(R.string.clock_greeting_quote)
                }
                updateConfig(getConfig().copy(customText = text))
                onChanged()
            }
        }
        group.addChildRow(presetRow)

        val customTextContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (10 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
            isClickable = true
            isFocusable = true
        }

        val headerText = TextView(context).apply {
            text = context.getString(R.string.clock_message_title)
            NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
        }

        val valueText = TextView(context).apply {
            text = getConfig().customText.takeUnless { it.isNullOrBlank() }
                ?: context.getString(R.string.clock_greeting_quote)
            NexusTypeScale.caption.bindTo(this, tokens.textSecondary)
            setPadding(0, (4 * dp).toInt(), 0, 0)
        }

        customTextContainer.addView(headerText)
        customTextContainer.addView(valueText)

        customTextContainer.setOnClickListener {
            LivingMosaicHaptics.tick(customTextContainer)
            val input = android.widget.EditText(context).apply {
                setText(getConfig().customText.takeUnless { it.isNullOrBlank() } ?: "")
                hint = context.getString(R.string.clock_custom_message_hint)
                filters = arrayOf(android.text.InputFilter.LengthFilter(40))
                setSingleLine(true)
                setTextColor(tokens.textPrimary)
                setHintTextColor(tokens.textSecondary)
                background = GradientDrawable().apply {
                    cornerRadius = 10f * dp
                    setColor(tokens.surface)
                    setStroke((1f * dp).toInt().coerceAtLeast(1), tokens.divider)
                }
                val pad = (14 * dp).toInt()
                setPadding(pad, pad, pad, pad)
            }

            val inputContainer = FrameLayout(context).apply {
                val pad = (20 * dp).toInt()
                setPadding(pad, (8 * dp).toInt(), pad, (8 * dp).toInt())
                addView(input, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
            }

            val dialog = android.app.AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(context.getString(R.string.clock_custom_message_dialog_title))
                .setView(inputContainer)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    LivingMosaicHaptics.tick(customTextContainer)
                    val entered = input.text.toString().trim()
                    val resultText = if (entered.isBlank()) null else entered
                    updateConfig(getConfig().copy(customText = resultText))
                    valueText.text = resultText ?: context.getString(R.string.clock_greeting_quote)
                    onChanged()
                }
                .setNegativeButton(android.R.string.cancel) { _, _ ->
                    LivingMosaicHaptics.tick(customTextContainer)
                }
                .create()

            dialog.setOnShowListener {
                dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE)?.setTextColor(tokens.textPrimary)
                dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE)?.setTextColor(tokens.textSecondary)
                dialog.window?.setBackgroundDrawable(GradientDrawable().apply {
                    cornerRadius = 18f * dp
                    setColor(tokens.surfaceRaised)
                    setStroke((1f * dp).toInt().coerceAtLeast(1), tokens.divider)
                })
            }
            dialog.show()
        }

        group.addChildRow(customTextContainer)

        val resetViews = {
            val current = getConfig()
            valueText.text = current.customText ?: context.getString(R.string.clock_greeting_quote)
            presetRow.configure(context.getString(com.nexus.launcher.R.string.widget_preset_quotes), listOf("0" to context.getString(com.nexus.launcher.R.string.icon_subtitle_default), "1" to context.getString(com.nexus.launcher.R.string.widget_quote_joy), "2" to context.getString(com.nexus.launcher.R.string.widget_quote_focus), "3" to context.getString(com.nexus.launcher.R.string.widget_quote_kind)), "0")
        }

        return Pair(group, resetViews)
    }

    fun buildCalendarGroup(
        context: Context,
        getConfig: () -> NexusWidgetConfig.InstanceConfig,
        updateConfig: (NexusWidgetConfig.InstanceConfig) -> Unit,
        onChanged: () -> Unit
    ): SettingsSectionGroupView {
        val group = SettingsSectionGroupView(context)

        val viewModeRow = NexusSegmentedRow(context).apply {
            configure(
                context.getString(com.nexus.launcher.R.string.widget_calendar_view_mode),
                listOf("DAILY" to context.getString(com.nexus.launcher.R.string.widget_calendar_daily), "MONTHLY" to context.getString(com.nexus.launcher.R.string.widget_calendar_monthly)),
                getConfig().calendarViewMode
            )
            onValueChanged = { value ->
                LivingMosaicHaptics.tick(this)
                updateConfig(getConfig().copy(calendarViewMode = value))
                onChanged()
            }
        }
        group.addChildRow(viewModeRow)

        val countRow = NexusSegmentedRow(context).apply {
            configure(
                context.getString(com.nexus.launcher.R.string.widget_calendar_show_count),
                listOf("false" to context.getString(com.nexus.launcher.R.string.widget_calendar_date), "true" to context.getString(com.nexus.launcher.R.string.widget_calendar_count)),
                getConfig().showCountWhenSmall.toString()
            )
            onValueChanged = { value ->
                LivingMosaicHaptics.tick(this)
                updateConfig(getConfig().copy(showCountWhenSmall = value == "true"))
                onChanged()
            }
        }
        group.addChildRow(countRow)
        return group
    }
}
