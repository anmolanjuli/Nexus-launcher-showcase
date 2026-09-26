package com.nexus.launcher.ui.settings

import android.content.Context
import android.view.View
import com.nexus.launcher.R
import com.nexus.launcher.data.prefs.NexusSettingsData
import com.nexus.launcher.ui.settings.views.NexusNavRow
import com.nexus.launcher.ui.settings.views.NexusSection
import com.nexus.launcher.ui.settings.views.NexusToggleRow
import com.nexus.launcher.ui.settings.views.SettingsSectionGroupView

/**
 * Settings → Immersive Mode: the status row, from the top down — a live preview of
 * it, then the row itself ([StatusBarBehaviorRows]), then each module as its own switch followed
 * by that module's style ([ImmersiveStatusStyleRows]) and its other settings
 * ([ImmersiveStatusExtraRows]).
 *
 * A module that is switched off keeps its settings on screen, dimmed and untouchable, so what it
 * offers can be read before turning it on. The section as a whole only appears with Immersive
 * Mode on — without it there is no row to configure.
 *
 * Kept out of HomeScreenSettingsFragment, which is at its size limit; it binds the same draft.
 */
class ImmersiveStatusSettingsSection(private val context: Context) {

    private val clock = NexusToggleRow(context)
    private val notifications = NexusToggleRow(context)
    private val wifi = NexusToggleRow(context)
    private val signal = NexusToggleRow(context)
    private val battery = NexusToggleRow(context)

    private val preview = StatusBarPreviewRow(context)
    private val behaviour = StatusBarBehaviorRows(context)
    private val styleRows = ImmersiveStatusStyleRows(context)
    private val extraRows = ImmersiveStatusExtraRows(context)

    private val modules = listOf(
        Module(clock, ImmersiveStatusStyleRows.CLOCK, R.string.status_item_clock, R.drawable.ic_status_clock),
        Module(notifications, ImmersiveStatusStyleRows.NOTIFICATIONS, R.string.status_item_notifications, R.drawable.ic_bell),
        Module(battery, ImmersiveStatusStyleRows.BATTERY, R.string.status_item_battery, R.drawable.ic_status_battery),
        Module(signal, ImmersiveStatusStyleRows.SIGNAL, R.string.status_item_signal, R.drawable.ic_status_signal),
        Module(wifi, ImmersiveStatusStyleRows.WIFI, R.string.status_item_wifi, R.drawable.sc_android_wifi),
    )

    private class Module(
        val toggle: NexusToggleRow,
        val key: String,
        val titleRes: Int,
        val iconRes: Int,
    )

    private var expanded = true
    private val container = SettingsSectionGroupView(context).apply {
        addChildRow(preview.view)
        behaviour.rows.forEach { addChildRow(it) }
        // Switch, then that module's options: a setting and what it offers read as one thing.
        modules.forEach { module ->
            addChildRow(module.toggle)
            styleRows.rowsFor(module.key).forEach { addChildRow(it) }
            extraRows.rowsFor(module.key).forEach { addChildRow(it) }
        }
    }

    val section: NexusSection

    init {
        lateinit var header: NexusNavRow
        header = NexusNavRow(
            context,
            title = context.getString(R.string.home_settings_status_info),
            subtitle = context.getString(R.string.home_settings_status_info_subtitle),
            iconRes = R.drawable.ic_status_bar,
            showChevron = true
        ) {
            expanded = !expanded
            container.visibility = if (expanded) View.VISIBLE else View.GONE
            header.setChevronRotation(if (expanded) 180f else 0f)
        }
        val dp = context.resources.displayMetrics.density
        header.setContentPadding(0, (12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt())
        section = NexusSection(context).apply {
            isTransparentCard = true
            addRow(header)
            addRow(container)
        }
    }

    /** The draft as this section last saw it, so the preview can follow each change. */
    private var current: NexusSettingsData? = null

    fun bind(s: NexusSettingsData) {
        current = s
        modules.forEach { module ->
            module.toggle.configure(context.getString(module.titleRes), shownOf(s, module.key))
            module.toggle.setIcon(module.iconRes)
        }
        // There is no status row without immersive mode, so the whole section steps out of the
        // way rather than sitting there greyed out.
        section.visibility = if (s.immersiveMode) View.VISIBLE else View.GONE
        StatusBarFullPreview.draft = s
        preview.bind(s)
        behaviour.bind(s)
        styleRows.bind(s)
        extraRows.bind(s)
        modules.forEach { dimModule(it.key, shownOf(s, it.key) && s.statusBarEnabled) }
    }

    fun setListeners(
        onPatch: ((NexusSettingsData) -> NexusSettingsData) -> Unit,
        isIgnoreCallbacks: () -> Boolean
    ) {
        // Every row patches through here: the draft goes on as before, and the preview above is
        // rebuilt from the same change straight away.
        val patch: ((NexusSettingsData) -> NexusSettingsData) -> Unit = { transform ->
            onPatch(transform)
            current = current?.let(transform)
            current?.let { updated ->
                StatusBarFullPreview.draft = updated
                preview.bind(updated)
                // Only the rows whose presence depends on another choice; re-binding them all
                // would write values back into the controls that just reported a change.
                extraRows.applyConditions(updated)
            }
        }
        behaviour.onEnabledChanged = { enabled ->
            preview.setShown(enabled)
            modules.forEach { dimModule(it.key, enabled && it.toggle.isChecked) }
        }
        behaviour.setListeners(patch, isIgnoreCallbacks)
        styleRows.setListeners(patch, isIgnoreCallbacks)
        extraRows.setListeners(patch, isIgnoreCallbacks)
        modules.forEach { module ->
            module.toggle.onCheckedChanged = { checked ->
                if (!isIgnoreCallbacks()) {
                    // The draft rebinds later; this module's own rows follow the switch now.
                    dimModule(module.key, checked)
                    patch { s -> change(s, module.key, checked) }
                }
            }
        }
    }

    private fun dimModule(key: String, enabled: Boolean) {
        (styleRows.rowsFor(key) + extraRows.rowsFor(key)).forEach { row ->
            StatusRowDimming.apply(row, enabled)
        }
    }

    private fun shownOf(s: NexusSettingsData, key: String): Boolean = when (key) {
        ImmersiveStatusStyleRows.CLOCK -> s.statusShowClock
        ImmersiveStatusStyleRows.NOTIFICATIONS -> s.statusShowNotifications
        ImmersiveStatusStyleRows.BATTERY -> s.statusShowBattery
        ImmersiveStatusStyleRows.SIGNAL -> s.statusShowSignal
        else -> s.statusShowWifi
    }

    private fun change(s: NexusSettingsData, key: String, checked: Boolean): NexusSettingsData =
        when (key) {
            ImmersiveStatusStyleRows.CLOCK -> s.copy(statusShowClock = checked)
            ImmersiveStatusStyleRows.NOTIFICATIONS -> s.copy(statusShowNotifications = checked)
            ImmersiveStatusStyleRows.BATTERY -> s.copy(statusShowBattery = checked)
            ImmersiveStatusStyleRows.SIGNAL -> s.copy(statusShowSignal = checked)
            else -> s.copy(statusShowWifi = checked)
        }
}
