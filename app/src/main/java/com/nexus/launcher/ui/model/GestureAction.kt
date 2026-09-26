package com.nexus.launcher.ui.model

import android.content.Context
import androidx.annotation.StringRes
import com.nexus.launcher.R

enum class GestureAction(
    @StringRes val displayNameRes: Int,
    val displayName: String,
    val appliesToApp: Boolean = true,
    val appliesToFolder: Boolean = true
) {
    NONE(R.string.gesture_action_none, "None"),
    OPEN_APP_INFO(R.string.gesture_action_open_app_info, "Open App Info", appliesToFolder = false),
    LOCK_SCREEN(R.string.gesture_action_lock_screen, "Lock Screen"),
    TOGGLE_FLASHLIGHT(R.string.gesture_action_toggle_flashlight, "Toggle Flashlight"),
    OPEN_APP_DRAWER(R.string.gesture_action_open_app_drawer, "Open App Drawer"),
    OPEN_SPECIFIC_APP(R.string.gesture_action_open_specific_app, "Open Specific App"),
    OPEN_SPECIFIC_PAGE(R.string.gesture_action_open_specific_page, "Open Specific Page"),
    OPEN_SPECIFIC_FOLDER(R.string.gesture_action_open_specific_folder, "Open Specific Folder"),
    OPEN_SPECIFIC_SHORTCUT(R.string.gesture_action_open_specific_shortcut, "Open Specific Shortcut"),
    EXPAND_NOTIFICATIONS(R.string.gesture_action_expand_notifications, "Expand Notifications"),
    OPEN_QUICK_SETTINGS(R.string.gesture_action_open_quick_settings, "Open Quick Settings"),
    SHOW_RECENTS(R.string.gesture_action_show_recents, "Show Recents"),
    TAKE_SCREENSHOT(R.string.gesture_action_take_screenshot, "Take Screenshot"),
    SHOW_POWER_MENU(R.string.gesture_action_show_power_menu, "Show Power Menu"),
    OPEN_SEARCH(R.string.gesture_action_open_search, "Open Search"),
    NEXT_PAGE(R.string.gesture_action_next_page, "Next Page"),
    PREV_PAGE(R.string.gesture_action_prev_page, "Previous Page"),
    TOGGLE_LABELS(R.string.gesture_action_toggle_labels, "Toggle Labels"),
    OPEN_HOME_EDIT(R.string.gesture_action_open_home_edit, "Home Edit Mode"),
    SPLIT_SCREEN(R.string.gesture_action_split_screen, "Split Screen");

    fun getDisplayName(context: Context): String = context.getString(displayNameRes)
}
