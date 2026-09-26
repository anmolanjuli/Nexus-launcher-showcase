package com.nexus.launcher.ui.widgets.weather

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class WeatherData(
    val currentTemp: Int,
    val apparentTemp: Int,
    val weatherCode: Int,
    val highTemp: Int,
    val lowTemp: Int,
    val unit: String,
    val timestamp: Long,
    val daily: List<DailyForecast>,
    val hourly: List<HourlyForecast>,
    val locationName: String?,
    val humidity: Int = 0,
    val windSpeed: Int = 0,
    val sunrise: String? = null,
    val sunset: String? = null,
    val precipitationProbability: Int = 0
)

data class HourlyForecast(
    val time: String,
    val temp: Int,
    val weatherCode: Int
)

data class DailyForecast(
    val highTemp: Int,
    val lowTemp: Int,
    val weatherCode: Int,
    val dayName: String
)

object NexusWeatherRepository {
    private const val PREFS_NAME = "nexus_weather_cache"
    private const val CACHE_DURATION_MS = 30 * 60 * 1000L // 30 minutes

    suspend fun getWeather(context: Context): WeatherData? = withContext(Dispatchers.IO) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedJson = prefs.getString("weather_data", null)
        val cachedTime = prefs.getLong("weather_timestamp", 0)

        if (cachedJson != null && System.currentTimeMillis() - cachedTime < CACHE_DURATION_MS) {
            try {
                val json = JSONObject(cachedJson)
                var cachedLoc = prefs.getString("weather_location", null)
                if (NexusWeatherLocation.isPlaceholder(context, cachedLoc)) {
                    cachedLoc = placeNameFromJson(context, json)
                    if (cachedLoc.isNullOrBlank()) {
                        prefs.edit().remove("weather_location").apply()
                    } else {
                        prefs.edit().putString("weather_location", cachedLoc).apply()
                    }
                }
                return@withContext parseWeatherJson(context, json, cachedTime, cachedLoc)
            } catch (e: Exception) {
                Log.e("WeatherRepo", "Failed to parse cached weather", e)
            }
        }

        val location = NexusWeatherLocation.resolve(context) ?: return@withContext null

        val isFahrenheit = getTemperatureUnit(context) == "fahrenheit"
        val unit = if (isFahrenheit) "fahrenheit" else "celsius"
        val urlString = "https://api.open-meteo.com/v1/forecast?latitude=${location.latitude}&longitude=${location.longitude}&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m,relative_humidity_2m&hourly=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min,weather_code,sunrise,sunset,precipitation_probability_max&forecast_days=7&temperature_unit=$unit&timezone=auto"

        val locationName = NexusWeatherLocation.placeName(context, location)

        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                prefs.edit().apply {
                    putString("weather_data", response)
                    putLong("weather_timestamp", System.currentTimeMillis())
                    if (locationName.isNullOrBlank()) {
                        remove("weather_location")
                    } else {
                        putString("weather_location", locationName)
                    }
                }.apply()
                return@withContext parseWeatherJson(context, JSONObject(response), System.currentTimeMillis(), locationName)
            } else {
                Log.e("WeatherRepo", "API returned code ${connection.responseCode}")
            }
        } catch (e: Exception) {
            Log.e("WeatherRepo", "Failed to fetch weather", e)
            if (cachedJson != null) {
                try {
                    val cachedLoc = prefs.getString("weather_location", null)
                        ?.takeUnless { NexusWeatherLocation.isPlaceholder(context, it) }
                    return@withContext parseWeatherJson(context, JSONObject(cachedJson), cachedTime, cachedLoc)
                } catch (ex: Exception) {}
            }
        }
        return@withContext null
    }

    private fun parseWeatherJson(context: Context, json: JSONObject, timestamp: Long, locationName: String? = null): WeatherData {
        val currentLocale = androidx.core.os.ConfigurationCompat.getLocales(context.resources.configuration)[0] ?: Locale.getDefault()
        val is24Hour = android.text.format.DateFormat.is24HourFormat(context)

        val current = json.getJSONObject("current")
        // Required: a response without these fails the fetch, so the widget keeps its last real
        // reading. Defaulting them invented weather — a missing temperature showed as 20° and a
        // missing code as clear sky. The extras below can default; these cannot.
        val currentTemp = current.getDouble("temperature_2m").roundToInt()
        val apparentTemp = current.optDouble("apparent_temperature", currentTemp.toDouble()).roundToInt()
        val weatherCode = current.getInt("weather_code")
        val windSpeed = current.optDouble("wind_speed_10m", 0.0).roundToInt()
        val humidity = current.optInt("relative_humidity_2m", 0)

        val daily = json.getJSONObject("daily")
        val maxTemps = daily.getJSONArray("temperature_2m_max")
        val minTemps = daily.getJSONArray("temperature_2m_min")
        val dailyCodes = daily.getJSONArray("weather_code")
        val times = daily.getJSONArray("time")

        var sunriseStr: String? = null
        var sunsetStr: String? = null
        var precipProb = 0

        if (daily.has("sunrise") && daily.getJSONArray("sunrise").length() > 0) {
            val sTime = daily.getJSONArray("sunrise").getString(0)
            val sDate = try { java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).parse(sTime) } catch (_: Exception) { null }
            if (sDate != null) {
                val sFormat = java.text.SimpleDateFormat(if (is24Hour) "H:mm" else "h:mm a", currentLocale)
                sunriseStr = com.nexus.launcher.locale.LocaleDigitUtils.localizeDigits(sFormat.format(sDate), currentLocale)
            }
        }
        if (daily.has("sunset") && daily.getJSONArray("sunset").length() > 0) {
            val sTime = daily.getJSONArray("sunset").getString(0)
            val sDate = try { java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US).parse(sTime) } catch (_: Exception) { null }
            if (sDate != null) {
                val sFormat = java.text.SimpleDateFormat(if (is24Hour) "H:mm" else "h:mm a", currentLocale)
                sunsetStr = com.nexus.launcher.locale.LocaleDigitUtils.localizeDigits(sFormat.format(sDate), currentLocale)
            }
        }
        if (daily.has("precipitation_probability_max") && daily.getJSONArray("precipitation_probability_max").length() > 0) {
            precipProb = daily.getJSONArray("precipitation_probability_max").optInt(0, 0)
        }

        val dayFormat = java.text.SimpleDateFormat("EEE", currentLocale)
        val todayLabel = context.getString(com.nexus.launcher.R.string.weather_today)

        val forecasts = mutableListOf<DailyForecast>()
        for (i in 0 until minOf(7, times.length())) {
            val dateStr = times.getString(i)
            val format = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val date = format.parse(dateStr)
            val dayName = if (date != null) dayFormat.format(date) else ""
            forecasts.add(
                DailyForecast(
                    highTemp = maxTemps.getDouble(i).roundToInt(),
                    lowTemp = minTemps.getDouble(i).roundToInt(),
                    weatherCode = dailyCodes.getInt(i),
                    dayName = if (i == 0) todayLabel else dayName
                )
            )
        }

        val high = if (forecasts.isNotEmpty()) forecasts[0].highTemp else currentTemp
        val low = if (forecasts.isNotEmpty()) forecasts[0].lowTemp else currentTemp
        val unitStr = if (getTemperatureUnit(context) == "fahrenheit") "°F" else "°C"

        val hourlyList = mutableListOf<HourlyForecast>()
        if (json.has("hourly")) {
            val hourly = json.getJSONObject("hourly")
            val hTimes = hourly.getJSONArray("time")
            val hTemps = hourly.getJSONArray("temperature_2m")
            val hCodes = hourly.getJSONArray("weather_code")
            
            // Find current hour index
            val currentIso = current.getString("time")
            var startIndex = 0
            for (i in 0 until hTimes.length()) {
                if (hTimes.getString(i) >= currentIso) {
                    startIndex = i + 1 // next 3 hours
                    break
                }
            }
            val hourFormat = java.text.SimpleDateFormat(if (is24Hour) "H:mm" else "h a", currentLocale)
            val isoFormat = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US)
            for (i in startIndex until minOf(startIndex + 3, hTimes.length())) {
                val timeStr = hTimes.getString(i) // "2024-01-01T14:00"
                val date = try { isoFormat.parse(timeStr) } catch (_: Exception) { null }
                val formattedTime = if (date != null) {
                    com.nexus.launcher.locale.LocaleDigitUtils.localizeDigits(hourFormat.format(date), currentLocale)
                } else timeStr
                hourlyList.add(HourlyForecast(
                    time = formattedTime,
                    temp = hTemps.getDouble(i).roundToInt(),
                    weatherCode = hCodes.getInt(i)
                ))
            }
        }

        return WeatherData(
            currentTemp = currentTemp,
            apparentTemp = apparentTemp,
            weatherCode = weatherCode,
            highTemp = high,
            lowTemp = low,
            unit = unitStr,
            timestamp = timestamp,
            daily = forecasts,
            hourly = hourlyList,
            locationName = locationName,
            humidity = humidity,
            windSpeed = windSpeed,
            sunrise = sunriseStr,
            sunset = sunsetStr,
            precipitationProbability = precipProb
        )
    }

    private fun placeNameFromJson(context: Context, json: JSONObject): String? {
        if (!json.has("latitude") || !json.has("longitude")) return null
        val lat = json.optDouble("latitude", Double.NaN)
        val lon = json.optDouble("longitude", Double.NaN)
        if (lat.isNaN() || lon.isNaN()) return null
        return NexusWeatherLocation.placeName(context, lat, lon)
    }

    fun clearCachedPlaceName(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().remove("weather_location").apply()
    }

    fun getTemperatureUnit(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val override = prefs.getString("weather_unit_override", "auto") ?: "auto"
        if (override == "celsius" || override == "fahrenheit") return override
        return if (isFahrenheit()) "fahrenheit" else "celsius"
    }

    fun setTemperatureUnit(context: Context, unit: String) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("weather_unit_override", unit)
            .remove("weather_timestamp")
            .remove("weather_data")
            .apply()
    }

    fun hasLocationPermission(context: Context): Boolean {
        return androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED ||
        androidx.core.content.ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    fun getCachedTemperature(context: Context): Pair<Int, String>? {
        if (!hasLocationPermission(context)) return null
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val cachedJson = prefs.getString("weather_data", null) ?: return null
        return try {
            val json = JSONObject(cachedJson)
            val current = json.getJSONObject("current")
            val temp = current.getDouble("temperature_2m").roundToInt()
            val unitStr = if (getTemperatureUnit(context) == "fahrenheit") "°F" else "°C"
            Pair(temp, unitStr)
        } catch (_: Exception) {
            null
        }
    }

    private fun isFahrenheit(): Boolean {
        val country = Locale.getDefault().country
        return country == "US" || country == "LR" || country == "MM"
    }
}

