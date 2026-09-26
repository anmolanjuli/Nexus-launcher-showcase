package com.nexus.launcher.ui.widgets.glance

import android.content.Context
import com.nexus.launcher.R

/**
 * Maps Open-Meteo WMO weather codes (0..99) to localized condition strings.
 */
object NexusWeatherConditionResolver {

    fun resolveCondition(context: Context, code: Int): String {
        return when (code) {
            0 -> context.getString(R.string.weather_condition_clear)
            1 -> context.getString(R.string.weather_condition_mainly_clear)
            2 -> context.getString(R.string.weather_condition_partly_cloudy)
            3 -> context.getString(R.string.weather_condition_overcast)
            45, 48 -> context.getString(R.string.weather_condition_fog)
            51, 53, 55, 56, 57 -> context.getString(R.string.weather_condition_drizzle)
            61, 63, 65, 66, 67, 80, 81, 82 -> context.getString(R.string.weather_condition_rain)
            71, 73, 75, 77, 85, 86 -> context.getString(R.string.weather_condition_snow)
            95, 96, 99 -> context.getString(R.string.weather_condition_thunderstorm)
            else -> context.getString(R.string.weather_condition_clear)
        }
    }
}
