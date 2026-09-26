package com.nexus.launcher.ui.drawercategories

import android.view.View

/**
 * Shows the app or folder menu for a category tile once a long press turns out not to be a drag.
 *
 * A relay rather than another callback threaded through every view: the tile callbacks are
 * passed down through the card, grid and list views, and this is needed at the one place that
 * knows a drag did not start ([CategoriesDrawerIconTouch]).
 */
object CategoriesDrawerMenu {

    private var listener: ((CategoryApp, View) -> Unit)? = null

    fun setListener(value: ((CategoryApp, View) -> Unit)?) {
        listener = value
    }

    fun show(app: CategoryApp, tile: View) {
        listener?.invoke(app, tile)
    }
}
