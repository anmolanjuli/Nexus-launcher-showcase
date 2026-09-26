package com.nexus.launcher.ui.settings

import androidx.annotation.StringRes
import com.nexus.launcher.R

enum class SettingsHubGroup(@StringRes val titleRes: Int) {
    LAYOUT(R.string.settings_group_layout),
    APPEARANCE(R.string.settings_group_appearance),
    BEHAVIOUR(R.string.settings_group_behaviour),
    SYSTEM(R.string.settings_group_system),
}
