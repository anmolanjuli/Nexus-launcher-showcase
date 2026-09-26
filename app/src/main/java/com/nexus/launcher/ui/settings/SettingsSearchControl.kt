package com.nexus.launcher.ui.settings

import androidx.annotation.StringRes

/**
 * One setting search can find. [titleRes] is the label its row shows on the page — search both
 * lists it under that name and finds the row by it — so it must be the row's own title string.
 * [subtitleRes] and [keywordRes] only widen what matches; 0 means none.
 */
data class SettingsSearchControl(
    @StringRes val titleRes: Int,
    val pagerIndex: Int,
    @StringRes val keywordRes: Int = 0,
    @StringRes val subtitleRes: Int = 0,
)
