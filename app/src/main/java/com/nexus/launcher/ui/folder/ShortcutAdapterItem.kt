package com.nexus.launcher.ui.folder

import android.graphics.drawable.Drawable

sealed class ShortcutAdapterItem {
    data class Header(
        val packageName: String,
        val appLabel: String,
        val icon: Drawable?,
        var isExpanded: Boolean
    ) : ShortcutAdapterItem()
}
