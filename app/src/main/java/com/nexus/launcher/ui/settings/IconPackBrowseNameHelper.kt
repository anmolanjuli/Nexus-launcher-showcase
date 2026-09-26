package com.nexus.launcher.ui.settings

import java.util.Locale

object IconPackBrowseNameHelper {

    fun cleanName(raw: String): String {
        return raw.replace("_", " ").split(" ").joinToString(" ") {
            it.replaceFirstChar { c -> if (c.isLowerCase()) c.titlecase(Locale.getDefault()) else c.toString() }
        }
    }
}
