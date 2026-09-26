package com.nexus.launcher.ui.settings

import android.content.Context
import com.nexus.launcher.R
import com.nexus.launcher.data.prefs.NexusSettingsData
import com.nexus.launcher.ui.settings.views.NexusToggleRow

/**
 * Settings → Home → Show Dock. Off hides the dock and gives its space to the home grid
 * (DockPresence); the dock's apps stay saved. Kept out of HomeScreenSettingsFragment, which is
 * at its size limit; it binds to the same settings draft.
 */
class HomeShowDockRow(private val context: Context) {

    val row = NexusToggleRow(context)

    fun bind(s: NexusSettingsData) {
        row.configure(
            context.getString(R.string.home_settings_show_dock),
            s.homeShowDock,
            subtitle = context.getString(R.string.home_settings_show_dock_subtitle)
        )
    }

    fun setListeners(
        onPatch: ((NexusSettingsData) -> NexusSettingsData) -> Unit,
        isIgnoreCallbacks: () -> Boolean
    ) {
        row.onCheckedChanged = { checked ->
            if (!isIgnoreCallbacks()) {
                // Putting the dock back is always allowed; hiding it is Premium.
                if (checked || com.nexus.launcher.premium.PremiumGate.allow(
                        context, com.nexus.launcher.premium.PremiumFeature.IMMERSIVE_HOME,
                    )
                ) {
                    onPatch { s -> s.copy(homeShowDock = checked) }
                } else {
                    row.setChecked(true)
                }
            }
        }
    }
}
