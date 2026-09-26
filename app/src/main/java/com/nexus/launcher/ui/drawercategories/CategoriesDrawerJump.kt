package com.nexus.launcher.ui.drawercategories

/**
 * Picking a category from the drawer's dropdown filters the canvas drawer — which the Categories
 * modes do not draw. There it means "take me to that category" instead, and this carries the
 * request from the dropdown to whichever Categories view is on screen.
 */
object CategoriesDrawerJump {

    private var listener: ((Int) -> Unit)? = null

    fun setListener(value: ((Int) -> Unit)?) {
        listener = value
    }

    fun to(categoryId: Int) {
        if (categoryId <= 0) return
        listener?.invoke(categoryId)
    }
}
