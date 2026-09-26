package com.nexus.launcher.ui.folder

import android.content.pm.ShortcutInfo
import androidx.fragment.app.FragmentManager
import com.nexus.launcher.ui.picker.ShortcutPickerOverlay

/**
 * Compatibility bridge delegating legacy invocations to [ShortcutPickerOverlay].
 */
object ShortcutPickerBottomSheet {

    fun show(
        fragmentManager: FragmentManager,
        onShortcutSelected: (ShortcutInfo) -> Unit
    ) {
        val activity = fragmentManager.fragments.firstOrNull()?.activity
        if (activity != null) {
            ShortcutPickerOverlay.show(activity, onShortcutSelected)
        }
    }
}
