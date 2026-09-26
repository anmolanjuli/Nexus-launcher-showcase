package com.nexus.launcher.ui.widgets

import android.content.Context
import androidx.appcompat.view.ContextThemeWrapper

/**
 * App widgets inflate via [android.widget.RemoteViews], which may only invoke methods on
 * framework widget classes. AppCompat/Material themes route inflation to AppCompat subclasses
 * (e.g. AppCompatImageView), causing "Couldn't add widget" for Google Calendar, Maps, etc.
 */
object WidgetHostContext {

    fun themed(context: Context): Context = ContextThemeWrapper(
        context.applicationContext,
        android.R.style.Theme_DeviceDefault_DayNight
    )
}
