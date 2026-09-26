package com.nexus.launcher.ui.widgets.progress

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.settings.views.NexusSegmentedRow
import com.nexus.launcher.ui.widgets.mosaic.LivingMosaicHaptics
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Edit dialog for configuring an individual progress slot in the Progress Bar widget.
 */
object ProgressSlotEditDialog {

    fun show(
        context: Context,
        tokens: NexusColorTokens,
        dp: Float,
        track: ProgressTrack,
        onSave: (ProgressTrack) -> Unit
    ) {
        com.nexus.launcher.ui.folder.FolderBlurCoordinator.setWorkspaceBlur(context, true)
        var currentPreset = track.presetType ?: ProgressTrack.PRESET_CUSTOM
        var currentLabel = track.label
        var currentStart = track.startDateMillis
        var currentTarget = track.targetDateMillis
        var currentMode = track.displayMode

        if (currentStart == 0L) currentStart = System.currentTimeMillis()
        if (currentTarget == 0L) currentTarget = currentStart + 86400000L * 30L

        val scroll = ScrollView(context)
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt())
        }
        scroll.addView(container)

        // 1. Preset Selector
        val presetRow = NexusSegmentedRow(context).apply {
            configure(
                context.getString(com.nexus.launcher.R.string.progress_preset_label),
                listOf(
                    ProgressTrack.PRESET_YEAR to context.getString(R.string.progress_preset_year),
                    ProgressTrack.PRESET_MONTH to context.getString(R.string.progress_preset_month),
                    ProgressTrack.PRESET_WEEK to context.getString(R.string.progress_preset_week),
                    ProgressTrack.PRESET_CUSTOM to context.getString(R.string.progress_preset_custom)
                ),
                currentPreset
            )
        }
        container.addView(presetRow)

        // 2. Label Input
        val labelHeader = TextView(context).apply {
            text = context.getString(R.string.progress_track_label)
            NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
            setPadding(0, (10 * dp).toInt(), 0, (4 * dp).toInt())
        }
        val labelEdit = EditText(context).apply {
            setText(currentLabel)
            hint = context.getString(com.nexus.launcher.R.string.progress_track_name_hint)
            setSingleLine(true)
            setTextColor(tokens.textPrimary)
            setHintTextColor(tokens.textSecondary)
            background = GradientDrawable().apply {
                cornerRadius = 10f * dp
                setColor(tokens.surface)
                setStroke((1f * dp).toInt().coerceAtLeast(1), tokens.divider)
            }
            val pad = (12 * dp).toInt()
            setPadding(pad, pad, pad, pad)
        }
        container.addView(labelHeader)
        container.addView(labelEdit)

        // 3. Custom Date Pickers
        val datePickerLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = if (currentPreset == ProgressTrack.PRESET_CUSTOM) View.VISIBLE else View.GONE
            setPadding(0, (8 * dp).toInt(), 0, 0)
        }

        val df = SimpleDateFormat(android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "yyyyMMMd"), Locale.getDefault())
        val startBtn = TextView(context).apply {
            text = context.getString(R.string.common_label_value, context.getString(R.string.progress_start_date), df.format(Date(currentStart)))
            NexusTypeScale.caption.bindTo(this, tokens.textPrimary)
            background = GradientDrawable().apply {
                cornerRadius = 8f * dp
                setColor(tokens.surfaceRaised)
            }
            setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
            setOnClickListener {
                val cal = Calendar.getInstance().apply { timeInMillis = currentStart }
                DatePickerDialog(context, { _, y, m, d ->
                    val sel = Calendar.getInstance().apply { set(y, m, d, 0, 0, 0) }
                    currentStart = sel.timeInMillis
                    text = context.getString(R.string.common_label_value, context.getString(R.string.progress_start_date), df.format(Date(currentStart)))
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }
        }

        val endBtn = TextView(context).apply {
            text = context.getString(R.string.common_label_value, context.getString(R.string.progress_end_date), df.format(Date(currentTarget)))
            NexusTypeScale.caption.bindTo(this, tokens.textPrimary)
            background = GradientDrawable().apply {
                cornerRadius = 8f * dp
                setColor(tokens.surfaceRaised)
            }
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (6 * dp).toInt()
            }
            setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
            setOnClickListener {
                val cal = Calendar.getInstance().apply { timeInMillis = currentTarget }
                DatePickerDialog(context, { _, y, m, d ->
                    val sel = Calendar.getInstance().apply { set(y, m, d, 23, 59, 59) }
                    currentTarget = sel.timeInMillis
                    text = context.getString(R.string.common_label_value, context.getString(R.string.progress_end_date), df.format(Date(currentTarget)))
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }
        }

        datePickerLayout.addView(startBtn)
        datePickerLayout.addView(endBtn)
        container.addView(datePickerLayout)

        presetRow.onValueChanged = { value ->
            LivingMosaicHaptics.tick(presetRow)
            currentPreset = value
            datePickerLayout.visibility = if (value == ProgressTrack.PRESET_CUSTOM) View.VISIBLE else View.GONE
            if (value != ProgressTrack.PRESET_CUSTOM && labelEdit.text.isNullOrBlank()) {
                val defaultName = when (value) {
                    ProgressTrack.PRESET_YEAR -> context.getString(R.string.progress_preset_year)
                    ProgressTrack.PRESET_MONTH -> context.getString(R.string.progress_preset_month)
                    ProgressTrack.PRESET_WEEK -> context.getString(R.string.progress_preset_week)
                    else -> "Track"
                }
                labelEdit.setText(defaultName)
            }
        }

        // 4. Display Mode Selector
        val modeRow = NexusSegmentedRow(context).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (10 * dp).toInt()
            }
            configure(
                context.getString(com.nexus.launcher.R.string.progress_badge_mode),
                listOf(
                    ProgressTrack.MODE_REMAINING_DAYS to context.getString(R.string.progress_mode_remaining_days),
                    ProgressTrack.MODE_REMAINING_PERCENT to context.getString(R.string.progress_mode_remaining_percent),
                    ProgressTrack.MODE_ELAPSED_DAYS to context.getString(R.string.progress_mode_elapsed_days),
                    ProgressTrack.MODE_ELAPSED_PERCENT to context.getString(R.string.progress_mode_elapsed_percent)
                ),
                currentMode
            )
            onValueChanged = { value ->
                LivingMosaicHaptics.tick(this)
                currentMode = value
            }
        }
        container.addView(modeRow)

        val dialog = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(context.getString(R.string.progress_edit_event))
            .setView(scroll)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val finalLabel = labelEdit.text.toString().trim().ifBlank { context.getString(com.nexus.launcher.R.string.progress_track_default) }
                val pType = if (currentPreset == ProgressTrack.PRESET_CUSTOM) null else currentPreset
                val updated = track.copy(
                    label = finalLabel,
                    presetType = pType,
                    startDateMillis = currentStart,
                    targetDateMillis = currentTarget,
                    displayMode = currentMode
                )
                onSave(updated)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(tokens.textPrimary)
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(tokens.textSecondary)
            dialog.window?.setBackgroundDrawable(GradientDrawable().apply {
                val frostedTokens = com.nexus.launcher.ui.glass.FrostedGlassEngine.resolveFrostedTokens(tokens)
                val fillAlpha = (com.nexus.launcher.ui.glass.FrostedGlassEngine.sheetFillAlpha(1f, 0.70f) * 255f).toInt()
                cornerRadius = 18f * dp
                setColor((frostedTokens.surfaceRaised and 0x00FFFFFF) or (fillAlpha shl 24))
                setStroke((1f * dp).toInt().coerceAtLeast(1), frostedTokens.border)
            })
        }
        dialog.setOnDismissListener {
            com.nexus.launcher.ui.folder.FolderBlurCoordinator.setWorkspaceBlur(context, false)
        }
        dialog.show()
    }
}
