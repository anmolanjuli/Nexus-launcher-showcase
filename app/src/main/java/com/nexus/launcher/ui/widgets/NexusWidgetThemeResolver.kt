package com.nexus.launcher.ui.widgets

import android.content.Context
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver

/**
 * Resolves color tokens for a widget/folder. Always follows the app's global theme now —
 * the per-item "Widget Theme"/"Folder Theme" override (Follow / pinned Light / pinned Dark) that
 * used to let one item ignore the global theme was removed as redundant, the same call made for
 * the per-item Glass/Soft-UI toggle: one global setting should govern this, not a per-item
 * override nobody but the item's own creator could see. [themeMode] is kept as a parameter (and
 * still read/written by [NexusWidgetConfig]) purely so none of this resolver's ~29 call sites need
 * to change — it's simply ignored now. An item with an explicit Light/Dark override persisted
 * from before this change silently reverts to following the global theme; no migration needed.
 */
object NexusWidgetThemeResolver {

    fun resolve(context: Context, themeMode: String?): NexusColorTokens {
        return try {
            ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }
    }
}
