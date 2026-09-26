package com.nexus.launcher.ui.widgets.progress

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.MainActivity
import com.nexus.launcher.ui.settings.views.NexusSegmentedRow
import com.nexus.launcher.ui.settings.views.NexusSettingsButtons
import com.nexus.launcher.ui.settings.views.SettingsSectionGroupView
import com.nexus.launcher.ui.widgets.NexusEditBottomSheetHelper
import com.nexus.launcher.ui.widgets.NexusWidgetConfig
import com.nexus.launcher.ui.widgets.NexusWidgetHostChrome
import com.nexus.launcher.ui.widgets.NexusWidgetSettingsSheetLayout
import com.nexus.launcher.ui.widgets.NexusWidgetSettingsSheetViews
import com.nexus.launcher.ui.widgets.NexusWidgetThemeResolver
import com.nexus.launcher.ui.widgets.mosaic.LivingMosaicHaptics

/**
 * Settings sheet for the Progress Bar widget with dual tabs: Events and Widget UI.
 */
class ProgressSettingsSheet(
    private val activity: MainActivity,
    private val appWidgetId: Int,
    private val initialSlotIndex: Int = -1
) {
    private val dp get() = activity.resources.displayMetrics.density
    private val mainContainer by lazy { activity.findViewById<FrameLayout>(R.id.main_container) }
    private var dialog: com.google.android.material.bottomsheet.BottomSheetDialog? = null

    private var config = NexusWidgetConfig.read(activity, appWidgetId)
    private var originalConfig = config
    private var tracks = ProgressDataStore.loadTracks(activity, appWidgetId).toMutableList()
    private val originalTracks = tracks.toList()

    private var previewView: ImageView? = null
    private val renderer = NexusProgressRenderer()
    private var selectedTab = "events"

    fun show() {
        dismiss()
        com.nexus.launcher.ui.folder.FolderBlurCoordinator.setWorkspaceBlur(activity, true)
        val tokens = NexusWidgetThemeResolver.resolve(activity, config.themeMode)

        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = NexusEditBottomSheetHelper.buildCardBackground(tokens, dp)
            setPadding((16 * dp).toInt(), 0, (16 * dp).toInt(), (8 * dp).toInt())
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnClickListener { }
        }
        card.addView(NexusEditBottomSheetHelper.createDragHandle(activity, tokens, dp))

        // 1. Header
        val headerRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (8 * dp).toInt()
            }
        }
        val title = TextView(activity).apply {
            text = activity.getString(R.string.widget_name_progress)
            NexusTypeScale.title.bindTo(this, tokens.textPrimary)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeBtn = ImageView(activity).apply {
            setImageResource(R.drawable.ic_close)
            imageTintList = ColorStateList.valueOf(tokens.textSecondary)
            val pad = (6 * dp).toInt()
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams((32 * dp).toInt(), (32 * dp).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(tokens.surfaceRaised)
            }
            setOnClickListener {
                LivingMosaicHaptics.tick(it)
                requestDismiss()
            }
        }
        headerRow.addView(title)
        headerRow.addView(closeBtn)
        card.addView(headerRow)

        // 2. Live Preview Box
        val previewContainer = FrameLayout(activity).apply {
            background = GradientDrawable().apply {
                cornerRadius = 16f * dp
                setColor(tokens.surfaceRaised)
                setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
            }
            setPadding((8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (110 * dp).toInt()).apply {
                bottomMargin = (10 * dp).toInt()
            }
        }
        previewView = ImageView(activity).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
        }
        previewContainer.addView(previewView)
        card.addView(previewContainer)

        // 3. Tab Switcher: Events vs Widget UI
        val tabSwitcher = NexusSegmentedRow(activity).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (8 * dp).toInt()
            }
            configure(
                "",
                listOf(
                    "events" to activity.getString(R.string.progress_tab_events),
                    "ui" to activity.getString(R.string.progress_tab_widget_ui)
                ),
                selectedTab
            )
        }
        card.addView(tabSwitcher)

        // 4. Tab Content Containers
        val eventsContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.VISIBLE
        }
        val uiContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }

        // Populate Events Tab
        val eventsGroup = buildEventsGroup(tokens)
        eventsContainer.addView(eventsGroup)

        // Populate Widget UI Tab
        val (appGroup, _) = NexusWidgetSettingsSheetViews.buildAppearanceGroup(
            activity, dp, tokens,
            getConfig = { config },
            updateConfig = { config = it; NexusWidgetConfig.write(activity, config) },
            onChanged = { notifyUpdate() }
        )
        uiContainer.addView(appGroup)

        val (geoGroup, _) = NexusWidgetSettingsSheetViews.buildGeometryGroup(
            activity, tokens,
            getConfig = { config },
            updateConfig = { config = it; NexusWidgetConfig.write(activity, config) },
            onChanged = { notifyUpdate() }
        )
        uiContainer.addView(geoGroup)

        tabSwitcher.onValueChanged = { tab ->
            LivingMosaicHaptics.tick(tabSwitcher)
            selectedTab = tab
            eventsContainer.visibility = if (tab == "events") View.VISIBLE else View.GONE
            uiContainer.visibility = if (tab == "ui") View.VISIBLE else View.GONE
        }

        val scroll = ScrollView(activity).apply {
            isVerticalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            addView(eventsContainer)
            addView(uiContainer)
        }
        scroll.addView(content)
        card.addView(scroll)

        // 5. Footer Actions
        val footer = NexusSettingsButtons.buildFooter(
            context = activity,
            dp = dp,
            onReset = {
                config = originalConfig
                tracks = originalTracks.toMutableList()
                NexusWidgetConfig.write(activity, config)
                ProgressDataStore.saveTracks(activity, appWidgetId, tracks)
                notifyUpdate()
                dismiss()
            },
            onApply = {
                NexusWidgetConfig.write(activity, config)
                ProgressDataStore.saveTracks(activity, appWidgetId, tracks)
                notifyUpdate()
                dismiss()
            }
        )
        card.addView(footer.container)
        val dlg = NexusEditBottomSheetHelper.create(activity, card) { requestDismiss() }
        dialog = dlg
        dlg.show()
        refreshPreview()

        if (initialSlotIndex in 0 until tracks.size) {
            editEvent(initialSlotIndex, tokens)
        }
    }

    private fun buildEventsGroup(tokens: NexusColorTokens): SettingsSectionGroupView {
        val group = SettingsSectionGroupView(activity)

        val countRow = NexusSegmentedRow(activity).apply {
            configure(
                activity.getString(R.string.progress_num_events),
                listOf("1" to "1", "2" to "2", "3" to "3"),
                tracks.size.coerceIn(1, 3).toString()
            )
            onValueChanged = { value ->
                LivingMosaicHaptics.tick(this)
                val targetCount = value.toInt()
                while (tracks.size < targetCount) {
                    val pType = if (tracks.size == 1) ProgressTrack.PRESET_MONTH else ProgressTrack.PRESET_WEEK
                    val label = if (tracks.size == 1) activity.getString(R.string.progress_preset_month) else activity.getString(R.string.progress_preset_week)
                    tracks.add(ProgressTrack(label = label, presetType = pType))
                }
                while (tracks.size > targetCount) {
                    tracks.removeAt(tracks.size - 1)
                }
                ProgressDataStore.saveTracks(activity, appWidgetId, tracks)
                rebuildEventRows(group, tokens)
                notifyUpdate()
            }
        }
        group.addChildRow(countRow)
        populateEventRows(group, tokens)
        return group
    }

    private fun populateEventRows(group: SettingsSectionGroupView, tokens: NexusColorTokens) {
        val now = System.currentTimeMillis()
        for (i in 0 until 3) {
            val track = tracks.getOrNull(i)
            val isEnabled = i < tracks.size
            val slotRow = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((16 * dp).toInt(), (11 * dp).toInt(), (16 * dp).toInt(), (11 * dp).toInt())
                isClickable = isEnabled
                isFocusable = isEnabled
                if (isEnabled) {
                    setOnClickListener {
                        LivingMosaicHaptics.tick(this)
                        editEvent(i, tokens)
                    }
                }
            }

            val slotTitle = TextView(activity).apply {
                text = activity.getString(R.string.progress_event_title, i + 1)
                NexusTypeScale.bodyStrong.bindTo(this, if (isEnabled) tokens.textPrimary else tokens.textSecondary)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }

            val slotDesc = TextView(activity).apply {
                if (track != null) {
                    val calculated = ProgressPresets.calculate(activity, track, now)
                    text = "${track.label}  ·  ${calculated.badgeText}"
                } else {
                    text = activity.getString(R.string.progress_event_empty)
                }
                NexusTypeScale.caption.bindTo(this, if (isEnabled) tokens.textPrimary else tokens.textSecondary)
            }
            slotRow.addView(slotTitle)
            slotRow.addView(slotDesc)
            group.addChildRow(slotRow)
        }
    }

    private fun rebuildEventRows(group: SettingsSectionGroupView, tokens: NexusColorTokens) {
        while (group.childCount > 1) {
            group.removeViewAt(1)
        }
        populateEventRows(group, tokens)
    }

    private fun editEvent(slotIndex: Int, tokens: NexusColorTokens) {
        val track = tracks.getOrNull(slotIndex) ?: return
        ProgressSlotEditDialog.show(activity, tokens, dp, track) { updated ->
            tracks[slotIndex] = updated
            ProgressDataStore.saveTracks(activity, appWidgetId, tracks)
            notifyUpdate()
        }
    }

    private fun notifyUpdate() {
        val intent = Intent("com.nexus.launcher.ACTION_NEXUS_WIDGET_CONFIG_CHANGED").apply {
            putExtra("appWidgetId", appWidgetId)
            setPackage(activity.packageName)
        }
        activity.sendBroadcast(intent)
        refreshPreview()
    }

    private fun refreshPreview() {
        val preview = previewView ?: return
        val w = (220 * dp).toInt().coerceAtLeast(1)
        val h = (100 * dp).toInt().coerceAtLeast(1)
        val bmp = renderer.render(activity, w, h, config, 220, 100, -1f)
        if (bmp != null) preview.setImageBitmap(bmp)
    }

    fun dismiss() {
        com.nexus.launcher.ui.folder.FolderBlurCoordinator.setWorkspaceBlur(activity, false)
        dialog?.dismiss()
        dialog = null
    }

    private fun requestDismiss() {
        dismiss()
    }

    companion object {
        private const val TAG = "ProgressSettingsSheet"
    }
}
