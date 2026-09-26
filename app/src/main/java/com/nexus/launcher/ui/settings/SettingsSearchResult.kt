package com.nexus.launcher.ui.settings

data class SettingsSearchResult(
    val pages: List<SettingsHubPage>,
    val controls: List<SettingsSearchControl>,
) {
    val isEmpty: Boolean get() = pages.isEmpty() && controls.isEmpty()
}
