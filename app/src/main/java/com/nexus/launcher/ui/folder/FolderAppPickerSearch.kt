package com.nexus.launcher.ui.folder

import com.nexus.launcher.data.HomeScreenItem

object FolderAppPickerSearch {

    fun filter(
        apps: List<HomeScreenItem>,
        query: String,
        excludePackages: Set<String>
    ): List<HomeScreenItem> {
        val q = query.trim().lowercase()
        return apps.filter { app ->
            app.packageName !in excludePackages &&
                (q.isEmpty() || app.packageName.lowercase().contains(q) ||
                    app.folderTitle.lowercase().contains(q))
        }
    }

    fun filterWithLabels(
        apps: List<HomeScreenItem>,
        query: String,
        excludePackages: Set<String>,
        labelFor: (HomeScreenItem) -> String
    ): List<HomeScreenItem> {
        val q = query.trim().lowercase()
        return apps.filter { app ->
            app.packageName !in excludePackages &&
                (q.isEmpty() ||
                    app.packageName.lowercase().contains(q) ||
                    labelFor(app).lowercase().contains(q))
        }
    }
}
