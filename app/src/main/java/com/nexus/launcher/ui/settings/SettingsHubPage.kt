package com.nexus.launcher.ui.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes

data class SettingsHubPage(
    val pagerIndex: Int,
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    @DrawableRes val iconRes: Int,
    val group: SettingsHubGroup,
    @StringRes val keywordRes: Int,
)
