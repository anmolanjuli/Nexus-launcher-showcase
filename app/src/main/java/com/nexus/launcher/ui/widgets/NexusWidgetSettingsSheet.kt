package com.nexus.launcher.ui.widgets

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.MainActivity
import com.nexus.launcher.ui.folder.FolderAuroraDialogs
import com.nexus.launcher.ui.settings.views.NexusSegmentedRow
import com.nexus.launcher.ui.settings.views.NexusSettingsButtons
import com.nexus.launcher.ui.settings.views.NexusSliderRow
import com.nexus.launcher.ui.settings.views.NexusToggleRow
import com.nexus.launcher.ui.settings.views.SettingsSectionGroupView
import com.nexus.launcher.ui.widgets.mosaic.LivingMosaicHaptics
import com.nexus.launcher.ui.widgets.mosaic.MosaicBackgroundPickerBinder

class NexusWidgetSettingsSheet(
    private val activity: MainActivity,
    private val appWidgetId: Int,
    private val widgetRect: Rect,
    private val onConfigChanged: () -> Unit
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
    private var originalConfig: NexusWidgetConfig.InstanceConfig? = null
    private var originalLaCrosseConfig: com.nexus.launcher.ui.widgets.clock.LaCrosseClockConfig? = null
    private var originalRetroMusicConfig: com.nexus.launcher.ui.widgets.music.RetroMusicConfig? = null
    private var previewView: ImageView? = null

    fun show() {
        dismiss()
        com.nexus.launcher.ui.folder.FolderBlurCoordinator.setWorkspaceBlur(activity, true)
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = NexusWidgetSettingsSheetLayout.cardBackground(tokens, dp)
            setPadding((16 * dp).toInt(), 0, (16 * dp).toInt(), (8 * dp).toInt())
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setOnClickListener { }
        }
        card.addView(NexusEditBottomSheetHelper.createDragHandle(activity, tokens, dp))
        card.addView(NexusWidgetSettingsSheetLayout.buildHeaderRow(activity, tokens, dp) { requestDismiss() })
        val appWidgetManager = AppWidgetManager.getInstance(activity)
        val info = appWidgetManager.getAppWidgetInfo(appWidgetId)

        var config = NexusWidgetConfig.read(activity, appWidgetId)
        originalConfig = config
        var footer: NexusSettingsButtons.Footer? = null
        var refreshApplyEnabled: () -> Unit = {}

        val clockMsgContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        val lacrosseContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }
        val retroMusicContainer = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
        }

        val carouselHelper = NexusWidgetStyleCarouselHelper(
            context = activity,
            tokens = tokens,
            dp = dp,
            providerClassName = info?.provider?.className,
            getConfig = { config },
            onStyleChanged = { newStyle ->
                config = config.copy(clockStyle = newStyle)
                NexusWidgetConfig.write(activity, config)
                clockMsgContainer.visibility = if (newStyle == 0 || newStyle == 3) View.VISIBLE else View.GONE
                lacrosseContainer.visibility = if (newStyle == com.nexus.launcher.ui.widgets.clock.NexusClockRenderer.STYLE_LACROSSE_LCD) View.VISIBLE else View.GONE
                retroMusicContainer.visibility = if (newStyle > 0) View.VISIBLE else View.GONE
                notifyUpdate()
                refreshApplyEnabled()
            },
            onClockMessageVisibilityChanged = { visible ->
                clockMsgContainer.visibility = if (visible) View.VISIBLE else View.GONE
            }
        )

        originalLaCrosseConfig = if (carouselHelper.isClockWidget) {
            com.nexus.launcher.ui.widgets.clock.LaCrosseClockConfig.read(activity, appWidgetId)
        } else null
        originalRetroMusicConfig = if (carouselHelper.isMusicWidget) {
            com.nexus.launcher.ui.widgets.music.RetroMusicConfig.read(activity, appWidgetId)
        } else null

        val hasChanges: () -> Boolean = {
            config != originalConfig || NexusWidgetSettingsOptionsBuilder.isSubConfigDirty(
                activity, appWidgetId, carouselHelper.isClockWidget, carouselHelper.isMusicWidget,
                originalLaCrosseConfig, originalRetroMusicConfig
            )
        }
        refreshApplyEnabled = {
            footer?.applyButton?.isEnabled = hasChanges()
        }

        // Previews show the wallpaper under this widget; see WidgetGlassPreviewDrawable.
        WidgetGlassPreviewDrawable.anchor = NexusWidgetHostChrome.findHost(
            activity.widgetHostLifecycle.widgetOverlayLayout, appWidgetId
        )?.let { java.lang.ref.WeakReference(it) }
        previewView = carouselHelper.attachTo(card)

        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, (16 * dp).toInt())
            clipToPadding = false
        }
        val scroll = CappedScrollView(activity).apply {
            capPx = NexusWidgetSettingsSheetLayout.calculateScrollCapPx(activity, dp)
            isVerticalScrollBarEnabled = true
            scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
            addView(content)
        }
        card.addView(scroll)
        // The card's side padding sits outside the scroll, so raised cards' shadows were cut at
        // the scroll's edge. Lends some of it to the scroll content instead.
        com.nexus.launcher.ui.glass.NeumorphicSurfaces.makeShadowRoom(scroll)

        // 1. Appearance Card
        content.addView(NexusWidgetSettingsSheetLayout.sectionHeader(activity, activity.getString(com.nexus.launcher.R.string.folder_edit_section_appearance), tokens, dp))
        val (appearanceGroup, resetAppearance) = NexusWidgetSettingsSheetViews.buildAppearanceGroup(
            context = activity,
            dp = dp,
            tokens = tokens,
            getConfig = { config },
            updateConfig = { updated ->
                config = updated
                NexusWidgetConfig.write(activity, config)
            },
            onChanged = {
                notifyUpdate()
                carouselHelper.updateLiveConfig(config)
                refreshApplyEnabled()
            }
        )
        content.addView(appearanceGroup)

        // 2. Geometry Card
        content.addView(NexusWidgetSettingsSheetLayout.sectionHeader(activity, activity.getString(com.nexus.launcher.R.string.widget_section_geometry), tokens, dp))
        val (geometryGroup, resetGeometry) = NexusWidgetSettingsSheetViews.buildGeometryGroup(
            context = activity,
            tokens = tokens,
            getConfig = { config },
            updateConfig = { updated ->
                config = updated
                NexusWidgetConfig.write(activity, config)
            },
            onChanged = {
                notifyUpdate()
                carouselHelper.updateLiveConfig(config)
                refreshApplyEnabled()
            },
            allowCircle = NexusWidgetShapeGeometry.supportsCircle(info?.provider?.className)
        )
        content.addView(geometryGroup)

        // 3. Clock Message Card (if applicable)
        var resetClockMessage: (() -> Unit)? = null
        var resetLaCrosse: (() -> Unit)? = null
        if (carouselHelper.isClockWidget) {
            val (clockMsgGroup, resetMsg) = NexusWidgetSettingsSheetViews.buildClockMessageGroup(
                context = activity,
                dp = dp,
                tokens = tokens,
                getConfig = { config },
                updateConfig = { updated ->
                    config = updated
                    NexusWidgetConfig.write(activity, config)
                },
                onChanged = {
                    notifyUpdate()
                    carouselHelper.updateLiveConfig(config)
                    refreshApplyEnabled()
                }
            )
            resetClockMessage = resetMsg
            clockMsgContainer.addView(NexusWidgetSettingsSheetLayout.sectionHeader(activity, activity.getString(com.nexus.launcher.R.string.clock_message_title), tokens, dp))
            clockMsgContainer.addView(clockMsgGroup)
            clockMsgContainer.visibility = if (config.clockStyle == 0 || config.clockStyle == 3) View.VISIBLE else View.GONE
            content.addView(clockMsgContainer)

            val (lcGroup, resetLc) = com.nexus.launcher.ui.widgets.clock.NexusWidgetSettingsLaCrosseViews.buildLaCrosseGroup(
                context = activity,
                appWidgetId = appWidgetId,
                onChanged = {
                    notifyUpdate()
                    carouselHelper.updateLiveConfig(config)
                    refreshApplyEnabled()
                }
            )
            resetLaCrosse = resetLc
            lacrosseContainer.addView(NexusWidgetSettingsSheetLayout.sectionHeader(activity, activity.getString(R.string.widget_lacrosse_section_title), tokens, dp))
            lacrosseContainer.addView(lcGroup)
            lacrosseContainer.visibility = if (config.clockStyle == com.nexus.launcher.ui.widgets.clock.NexusClockRenderer.STYLE_LACROSSE_LCD) View.VISIBLE else View.GONE
            content.addView(lacrosseContainer)
        }

        // 4. Widget Specific Options (Calendar, Agenda, Notes, Retro Music)
        val resetRetroMusic = NexusWidgetSettingsOptionsBuilder.attachWidgetOptions(
            context = activity,
            content = content,
            dp = dp,
            tokens = tokens,
            isCalendar = carouselHelper.isCalendarWidget,
            isAgenda = carouselHelper.isAgendaWidget,
            isNotes = carouselHelper.isNotesWidget,
            isMusic = carouselHelper.isMusicWidget,
            appWidgetId = appWidgetId,
            retroMusicContainer = retroMusicContainer,
            getConfig = { config },
            updateConfig = { updated ->
                config = updated
                NexusWidgetConfig.write(activity, config)
            },
            onChanged = {
                notifyUpdate()
                carouselHelper.updateLiveConfig(config)
                refreshApplyEnabled()
            }
        )

        footer = NexusSettingsButtons.buildFooter(
            context = activity,
            dp = dp,
            onReset = {
                config = NexusWidgetConfig.InstanceConfig(
                    appWidgetId = appWidgetId,
                    backgroundMode = NexusWidgetConfig.BG_GLASS,
                    backgroundOpacity = 0.65f,
                    accentColor = config.accentColor,
                    frostedGradientIndex = 0,
                    shapeStyle = config.shapeStyle.let { if (carouselHelper.isCalendarWidget || carouselHelper.isClockWidget) it else 1 },
                    calendarViewMode = "MONTHLY",
                    showCountWhenSmall = false,
                    cornerRadius = 20,
                    isExpressive = false,
                    clockStyle = 0,
                    customText = null,
                    isBorderless = false,
                    fontFamily = "default"
                )
                NexusWidgetConfig.write(activity, config)
                notifyUpdate()
                resetAppearance()
                resetGeometry()
                resetClockMessage?.invoke()
                resetLaCrosse?.invoke()
                resetRetroMusic?.invoke()
                carouselHelper.resetToStyle(0, config)
                clockMsgContainer.visibility = View.VISIBLE
                lacrosseContainer.visibility = View.GONE
                retroMusicContainer.visibility = View.GONE
                refreshApplyEnabled()
            },
            onApply = {
                originalConfig = config
                originalLaCrosseConfig = if (carouselHelper.isClockWidget) com.nexus.launcher.ui.widgets.clock.LaCrosseClockConfig.read(activity, appWidgetId) else null
                originalRetroMusicConfig = if (carouselHelper.isMusicWidget) com.nexus.launcher.ui.widgets.music.RetroMusicConfig.read(activity, appWidgetId) else null
                dismiss()
            }
        )

        refreshApplyEnabled()
        footer?.let { card.addView(it.container) }

        val hostOnShow = NexusWidgetHostChrome.findHost(
            activity.widgetHostLifecycle.widgetOverlayLayout, appWidgetId
        )
        if (hostOnShow != null) NexusWidgetHostChrome.apply(hostOnShow, appWidgetId)
        previewView?.post { refreshPreview() }

        val dlg = NexusEditBottomSheetHelper.create(activity, card) { requestDismiss() }
        dialog = dlg
        dlg.show()
    }

    private val broadcastRunnable = Runnable {
        val intent = Intent("com.nexus.launcher.ACTION_NEXUS_WIDGET_CONFIG_CHANGED")
        intent.putExtra("appWidgetId", appWidgetId)
        intent.setPackage(activity.packageName)
        activity.sendBroadcast(intent)
        val host = NexusWidgetHostChrome.findHost(
            activity.widgetHostLifecycle.widgetOverlayLayout, appWidgetId
        )
        if (host != null) NexusWidgetHostChrome.apply(host, appWidgetId)
        // The broadcast above only rebakes this widget's own RemoteViews bitmap — whether its
        // Glass blur backdrop SIBLING view exists at all is decided during a bind pass
        // (AppWidgetOverlayBinder.syncGlassBackdrop), which a plain settings-change broadcast
        // never triggers on its own. Without this, switching this widget's own background mode
        // to/from Glass correctly rebaked the widget's content but left its blur backdrop stuck
        // showing (or missing) whatever it was before — "works in the edit sheet [live preview]
        // but not on the actual widget."
        activity.widgetHostLifecycle.widgetOverlayLayout.rebindCachedWidgets()
    }

    private fun notifyUpdate() {
        onConfigChanged()
        refreshPreview()
        mainContainer.removeCallbacks(broadcastRunnable)
        mainContainer.postDelayed(broadcastRunnable, 100)
    }

    private fun refreshPreview() {
        val preview = previewView ?: return
        val host = NexusWidgetHostChrome.findHost(
            activity.widgetHostLifecycle.widgetOverlayLayout, appWidgetId
        )
        val currentCfg = NexusWidgetConfig.read(activity, appWidgetId)
        NexusWidgetSettingsSheetLayout.renderLivePreview(activity, appWidgetId, currentCfg, host, preview, dp)
    }

    fun requestDismiss() {
        val currentConfig = NexusWidgetConfig.read(activity, appWidgetId)
        val isSubDirty = NexusWidgetSettingsOptionsBuilder.isSubConfigDirty(
            activity, appWidgetId, originalLaCrosseConfig != null, originalRetroMusicConfig != null,
            originalLaCrosseConfig, originalRetroMusicConfig
        )
        val isDirty = (originalConfig != null && currentConfig != originalConfig) || isSubDirty
        if (isDirty) {
            FolderAuroraDialogs.showDiscard(activity) {
                originalConfig?.let { NexusWidgetConfig.write(activity, it) }
                NexusWidgetSettingsOptionsBuilder.revertSubConfigs(
                    activity, appWidgetId, originalLaCrosseConfig, originalRetroMusicConfig
                )
                notifyUpdate()
                dismiss()
            }
        } else {
            dismiss()
        }
    }

    fun dismiss() {
        com.nexus.launcher.ui.folder.FolderBlurCoordinator.setWorkspaceBlur(activity, false)
        activity.canvasView.animate().translationY(0f).setDuration(300).start()
        activity.widgetHostLifecycle.widgetOverlayLayout.animate().translationY(0f).setDuration(300).start()
        previewView = null
        originalConfig = null
        originalLaCrosseConfig = null
        originalRetroMusicConfig = null
        WidgetGlassPreviewDrawable.anchor = null
        dialog?.dismiss()
        dialog = null
    }

    private companion object { const val TAG = "nexus_widget_settings_sheet" }
}
