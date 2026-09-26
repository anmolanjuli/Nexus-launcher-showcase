package com.nexus.launcher.ui.folder

object FolderAppPickerSaveLabel {

    fun format(selectedCount: Int): String {
        if (selectedCount <= 0) return "Save to Folder"
        val noun = if (selectedCount == 1) "app" else "apps"
        return "Save $selectedCount $noun to Folder"
    }
}
