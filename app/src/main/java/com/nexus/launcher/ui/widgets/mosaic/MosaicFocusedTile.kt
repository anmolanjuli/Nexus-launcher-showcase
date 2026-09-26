package com.nexus.launcher.ui.widgets.mosaic

import android.view.View
import android.widget.FrameLayout

/** A real Mosaic tile temporarily reparented into the expanded Focus Window. */
data class MosaicFocusedTile internal constructor(
    val index: Int,
    internal val cell: FrameLayout,
    internal val widget: View
)
