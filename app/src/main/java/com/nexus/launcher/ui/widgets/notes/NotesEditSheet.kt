package com.nexus.launcher.ui.widgets.notes

import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.MainActivity
import com.nexus.launcher.ui.settings.views.NexusSliderRow
import com.nexus.launcher.ui.widgets.mosaic.LivingMosaicHaptics

/**
 * Aurora modal bottom sheet editor for Notes widget instances.
 * Features title & body inputs, 2x2 Eisenhower urgency matrix, Status & NexusSliderRow progress controls, Save and Clear actions.
 */
class NotesEditSheet(
    private val activity: MainActivity,
    private val appWidgetId: Int
) {
    private val mainContainer by lazy { activity.findViewById<FrameLayout>(R.id.main_container) }
    private val dp get() = activity.resources.displayMetrics.density
    private val tokens: NexusColorTokens
        get() = try {
            ThemeObserver.currentTokens(activity)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }

    private var dialog: com.google.android.material.bottomsheet.BottomSheetDialog? = null
    private var selectedUrgency = NoteData.URGENCY_LATER
    private var selectedStatus = NoteData.STATUS_OPEN
    private var selectedProgress = 0

    fun show() {
        if (dialog != null) dismiss()
        com.nexus.launcher.ui.folder.FolderBlurCoordinator.setWorkspaceBlur(activity, true)
        val currentNote = NotesDataStore.read(activity, appWidgetId)
        selectedUrgency = currentNote.urgency
        selectedStatus = currentNote.status
        selectedProgress = currentNote.progress

        // Card Container
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = com.nexus.launcher.ui.widgets.NexusEditBottomSheetHelper.buildCardBackground(tokens, dp)
            setPadding((18 * dp).toInt(), 0, (18 * dp).toInt(), (8 * dp).toInt())
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            isClickable = true
            isFocusable = true
        }
        card.addView(com.nexus.launcher.ui.widgets.NexusEditBottomSheetHelper.createDragHandle(activity, tokens, dp))

        // Header Row
        val headerRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val headerTitle = TextView(activity).apply {
            text = activity.getString(R.string.notes_edit_title)
            NexusTypeScale.hubRowTitle.bindTo(this, tokens.textPrimary)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeBtn = ImageView(activity).apply {
            setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
            imageTintList = ColorStateList.valueOf(tokens.textSecondary)
            setPadding((6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt())
            setOnClickListener { dismiss() }
        }
        headerRow.addView(headerTitle)
        headerRow.addView(closeBtn)
        card.addView(headerRow)

        // Scrollable Form Body
        val scrollView = ScrollView(activity).apply {
            isVerticalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val formLayout = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (12 * dp).toInt(), 0, (12 * dp).toInt())
        }

        // 1. Title Input
        val titleInput = EditText(activity).apply {
            hint = activity.getString(R.string.notes_title_hint)
            setHintTextColor(tokens.textSecondary and 0x00FFFFFF or 0x88000000.toInt())
            setTextColor(tokens.textPrimary)
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            filters = arrayOf(InputFilter.LengthFilter(NoteData.MAX_TITLE_CHARS))
            setText(currentNote.title)
            background = inputBackground()
            setPadding((14 * dp).toInt(), (10 * dp).toInt(), (14 * dp).toInt(), (10 * dp).toInt())
        }
        formLayout.addView(titleInput)

        // 2. Body Input
        val bodyInput = EditText(activity).apply {
            hint = activity.getString(R.string.notes_body_hint)
            setHintTextColor(tokens.textSecondary and 0x00FFFFFF or 0x88000000.toInt())
            setTextColor(tokens.textPrimary)
            textSize = 14f
            typeface = Typeface.DEFAULT
            minLines = 3
            maxLines = 6
            filters = arrayOf(InputFilter.LengthFilter(NoteData.MAX_BODY_CHARS))
            setText(currentNote.body)
            background = inputBackground()
            setPadding((14 * dp).toInt(), (12 * dp).toInt(), (14 * dp).toInt(), (12 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (10 * dp).toInt()
            }
        }
        formLayout.addView(bodyInput)

        // 3. Urgency 2x2 Quadrant Selector
        val urgencyLabel = TextView(activity).apply {
            text = activity.getString(com.nexus.launcher.R.string.notes_urgency_label)
            NexusTypeScale.labelSmall.bindTo(this, tokens.textSecondary)
            setPadding(0, (14 * dp).toInt(), 0, (6 * dp).toInt())
        }
        formLayout.addView(urgencyLabel)

        val (matrixView, _) = NotesEditUrgencyMatrix.buildMatrix(
            activity, dp, tokens, selectedUrgency
        ) { newUrgency ->
            selectedUrgency = newUrgency
        }
        formLayout.addView(matrixView)

        // 4. Status & Progress Section
        val statusSectionLabel = TextView(activity).apply {
            text = activity.getString(R.string.notes_status_label)
            NexusTypeScale.labelSmall.bindTo(this, tokens.textSecondary)
            setPadding(0, (14 * dp).toInt(), 0, (6 * dp).toInt())
        }
        formLayout.addView(statusSectionLabel)

        // Status Segmented Row (Open, WIP, Done)
        val statusRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (38 * dp).toInt())
        }

        val statusViews = mutableMapOf<String, Pair<TextView, Int>>()
        lateinit var sliderRow: NexusSliderRow

        val updateStatusUI: () -> Unit = {
            statusViews.forEach { (st, pair) ->
                val (tv, stColor) = pair
                val isSel = st == selectedStatus
                tv.background = GradientDrawable().apply {
                    cornerRadius = 10f * dp
                    if (isSel) {
                        setColor(tokens.surfaceRaised)
                        setStroke((1.5f * dp).toInt(), stColor)
                    } else {
                        setColor(tokens.surfaceRaised and 0x00FFFFFF or 0x66000000.toInt())
                        setStroke((1 * dp).toInt(), tokens.divider)
                    }
                }
                tv.setTextColor(if (isSel) stColor else tokens.textSecondary)
            }
        }

        val createStatusBtn = { statusId: String, label: String, color: Int ->
            TextView(activity).apply {
                text = label
                gravity = Gravity.CENTER
                textSize = 12.5f
                typeface = Typeface.DEFAULT_BOLD
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                    marginStart = (3 * dp).toInt()
                    marginEnd = (3 * dp).toInt()
                }
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    LivingMosaicHaptics.tick(this)
                    selectedStatus = statusId
                    when (statusId) {
                        NoteData.STATUS_DONE -> {
                            selectedProgress = 100
                            sliderRow.setValue(100)
                        }
                        NoteData.STATUS_OPEN -> {
                            selectedProgress = 0
                            sliderRow.setValue(0)
                        }
                        NoteData.STATUS_WIP -> {
                            // Keep current progress without forcing 50%
                        }
                    }
                    updateStatusUI()
                }
            }
        }

        val btnOpen = createStatusBtn(NoteData.STATUS_OPEN, activity.getString(R.string.notes_status_open), NoteData.COLOR_OPEN)
        val btnWip = createStatusBtn(NoteData.STATUS_WIP, activity.getString(R.string.notes_status_wip), NoteData.COLOR_WIP)
        val btnDone = createStatusBtn(NoteData.STATUS_DONE, activity.getString(R.string.notes_status_done), NoteData.COLOR_DONE)

        statusViews[NoteData.STATUS_OPEN] = Pair(btnOpen, NoteData.COLOR_OPEN)
        statusViews[NoteData.STATUS_WIP] = Pair(btnWip, NoteData.COLOR_WIP)
        statusViews[NoteData.STATUS_DONE] = Pair(btnDone, NoteData.COLOR_DONE)

        statusRow.addView(btnOpen)
        statusRow.addView(btnWip)
        statusRow.addView(btnDone)
        formLayout.addView(statusRow)

        // 5. Nexus Slider Row for Progress %
        sliderRow = NexusSliderRow(activity).apply {
            configure(
                label = activity.getString(R.string.notes_progress_label),
                min = 0,
                max = 100,
                value = selectedProgress,
                stepSize = 1f,
                formatValue = { "$it%" }
            )
            onValueChanged = { progressVal ->
                selectedProgress = progressVal
                selectedStatus = when {
                    progressVal == 100 -> NoteData.STATUS_DONE
                    progressVal == 0 -> NoteData.STATUS_OPEN
                    else -> NoteData.STATUS_WIP
                }
                updateStatusUI()
            }
            setPadding(0, (10 * dp).toInt(), 0, 0)
        }
        formLayout.addView(sliderRow)

        updateStatusUI()

        scrollView.addView(formLayout)
        card.addView(scrollView)

        // Action Buttons Row
        val buttonRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, (14 * dp).toInt(), 0, 0)
        }

        val clearBtn = TextView(activity).apply {
            text = activity.getString(com.nexus.launcher.R.string.common_clear)
            gravity = Gravity.CENTER
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(tokens.danger)
            background = GradientDrawable().apply {
                cornerRadius = 14f * dp
                setColor(tokens.surfaceRaised)
                setStroke((1 * dp).toInt(), tokens.divider)
            }
            setPadding((16 * dp).toInt(), (10 * dp).toInt(), (16 * dp).toInt(), (10 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(0, (44 * dp).toInt(), 1f).apply { marginEnd = (6 * dp).toInt() }
            setOnClickListener {
                LivingMosaicHaptics.tick(this)
                AlertDialog.Builder(activity)
                    .setTitle(R.string.notes_clear_confirm_title)
                    .setMessage(R.string.notes_clear_confirm_msg)
                    .setPositiveButton(com.nexus.launcher.R.string.common_clear) { _, _ ->
                        NotesDataStore.clear(activity, appWidgetId)
                        notifyWidgetUpdate()
                        dismiss()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
        }

        val saveBtn = TextView(activity).apply {
            text = activity.getString(com.nexus.launcher.R.string.action_save)
            gravity = Gravity.CENTER
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = 14f * dp
                setColor(tokens.accent)
            }
            setPadding((16 * dp).toInt(), (10 * dp).toInt(), (16 * dp).toInt(), (10 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(0, (44 * dp).toInt(), 1f).apply { marginStart = (6 * dp).toInt() }
            setOnClickListener {
                LivingMosaicHaptics.tick(this)
                val updatedNote = NoteData(
                    title = titleInput.text.toString().trim(),
                    body = bodyInput.text.toString().trim(),
                    urgency = selectedUrgency,
                    status = selectedStatus,
                    progress = selectedProgress,
                    updatedAt = System.currentTimeMillis()
                )
                NotesDataStore.write(activity, appWidgetId, updatedNote)
                notifyWidgetUpdate()
                dismiss()
            }
        }

        buttonRow.addView(clearBtn)
        buttonRow.addView(saveBtn)
        card.addView(buttonRow)

        val dlg = com.nexus.launcher.ui.widgets.NexusEditBottomSheetHelper.create(activity, card) { dismiss() }
        dialog = dlg
        dlg.show()
    }

    private fun inputBackground() = GradientDrawable().apply {
        cornerRadius = 12f * dp
        setColor(tokens.surfaceRaised)
        setStroke((1 * dp).toInt(), tokens.divider)
    }

    private fun notifyWidgetUpdate() {
        val intent = Intent("com.nexus.launcher.ACTION_NEXUS_WIDGET_CONFIG_CHANGED").apply {
            putExtra("appWidgetId", appWidgetId)
            setPackage(activity.packageName)
        }
        activity.sendBroadcast(intent)
    }

    fun dismiss() {
        val d = dialog
        dialog = null
        d?.dismiss()
        com.nexus.launcher.ui.folder.FolderBlurCoordinator.setWorkspaceBlur(activity, false)
    }

    companion object {
        private const val TAG = "notes_edit_sheet"
    }
}
