package com.nexus.launcher.ui.settings

import android.content.Context
import android.view.View
import com.nexus.launcher.R
import com.nexus.launcher.data.prefs.NexusSettingsData
import com.nexus.launcher.ui.settings.views.NexusSegmentedRow
import com.nexus.launcher.ui.settings.views.NexusToggleRow
import com.nexus.launcher.ui.settings.views.SettingsSectionGroupView

/**
 * Settings → Notifications → the launcher's own notification sheet: whether to keep notifications
 * after they are cleared, and for how long.
 *
 * Off by default — it stores notification text in the launcher's database, so it is the user's
 * to turn on — and turning it off again wipes what was kept.
 */
class NotificationHistorySection(private val context: Context) {

    private val toggle = NexusToggleRow(context)
    private val retention = NexusSegmentedRow(context)

    /**
     * The rows, unparented — the caller adds this to its own section. It used to be pre-wrapped
     * in a `section` here as well, which gave it a parent before the Notifications page could
     * add it, and opening the page crashed ("child already has a parent").
     */
    val container = SettingsSectionGroupView(context).apply {
        addChildRow(toggle)
        addChildRow(retention)
    }

    private val options = listOf(
        "1" to R.string.notification_retention_1h,
        "6" to R.string.notification_retention_6h,
        "24" to R.string.notification_retention_1d,
        "72" to R.string.notification_retention_3d,
        "168" to R.string.notification_retention_7d,
    )

    fun bind(s: NexusSettingsData) {
        toggle.configure(
            context.getString(R.string.notification_history_title),
            s.notificationHistory,
            subtitle = context.getString(R.string.notification_history_subtitle),
        )
        retention.configure(
            context.getString(R.string.notification_retention_title),
            options.map { (value, res) -> value to context.getString(res) },
            nearestOption(s.notificationRetentionHours),
            inline = true,
        )
        retention.visibility = if (s.notificationHistory) View.VISIBLE else View.GONE
    }

    fun setListeners(
        onPatch: ((NexusSettingsData) -> NexusSettingsData) -> Unit,
        isIgnoreCallbacks: () -> Boolean,
    ) {
        toggle.onCheckedChanged = { checked ->
            if (!isIgnoreCallbacks()) {
                retention.visibility = if (checked) View.VISIBLE else View.GONE
                onPatch { s -> s.copy(notificationHistory = checked) }
            }
        }
        retention.onValueChanged = { value ->
            val hours = value.toIntOrNull()
            if (hours != null && !isIgnoreCallbacks()) {
                onPatch { s -> s.copy(notificationRetentionHours = hours) }
            }
        }
    }

    /** A stored value from elsewhere (a backup) need not be one of the offered steps. */
    private fun nearestOption(hours: Int): String =
        options.minByOrNull { kotlin.math.abs((it.first.toIntOrNull() ?: 0) - hours) }?.first
            ?: options.first().first
}
