package com.nexus.launcher.ui.widgets.shortcutbox

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

/** Definition model for Nexus system/utility shortcuts. */
data class NexusShortcutItem(
    val id: String,
    @StringRes val titleRes: Int,
    @DrawableRes val iconRes: Int
)
