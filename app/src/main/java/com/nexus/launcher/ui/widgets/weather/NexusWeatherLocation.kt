package com.nexus.launcher.ui.widgets.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import androidx.core.content.ContextCompat
import com.nexus.launcher.R
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.coroutines.resume

internal object NexusWeatherLocation {
    private const val FIX_TIMEOUT_MS = 8_000L

    fun hasPermission(context: Context): Boolean {
        val app = context.applicationContext
        return ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
    }

    suspend fun resolve(context: Context): Location? {
        val app = context.applicationContext
        if (!hasPermission(app)) return null
        lastKnown(app)?.let { return it }
        return withTimeoutOrNull(FIX_TIMEOUT_MS) { requestCurrent(app) }
    }

    fun placeName(labelContext: Context, location: Location): String? {
        return try {
            val geocoder = Geocoder(labelContext.applicationContext, Locale.ENGLISH)
            val address = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                ?.firstOrNull()
            address?.locality ?: address?.subAdminArea ?: address?.adminArea
        } catch (_: Exception) {
            null
        }
    }

    fun placeName(labelContext: Context, latitude: Double, longitude: Double): String? {
        val location = Location("cache").apply {
            this.latitude = latitude
            this.longitude = longitude
        }
        return placeName(labelContext, location)
    }

    /** True when [name] is a localized "Local Area" placeholder, not a real place. */
    fun isPlaceholder(context: Context, name: String?): Boolean {
        if (name.isNullOrBlank()) return true
        val app = context.applicationContext
        fun label(locale: Locale): String {
            val config = android.content.res.Configuration(app.resources.configuration)
            config.setLocales(android.os.LocaleList(locale))
            return app.createConfigurationContext(config).getString(R.string.weather_local_area)
        }
        return name == label(Locale.ENGLISH) || name == label(Locale("ne", "NP"))
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(context: Context): Location? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        var best: Location? = null
        for (provider in manager.getProviders(true)) {
            val location = manager.getLastKnownLocation(provider) ?: continue
            if (best == null || location.time > best.time) best = location
        }
        return best
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestCurrent(context: Context): Location? =
        suspendCancellableCoroutine { cont ->
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            val provider = preferredProvider(manager) ?: run {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val cancel = CancellationSignal()
                cont.invokeOnCancellation { cancel.cancel() }
                manager.getCurrentLocation(
                    provider,
                    cancel,
                    ContextCompat.getMainExecutor(context)
                ) { location ->
                    if (cont.isActive) cont.resume(location)
                }
            } else {
                val listener = LocationListener { location ->
                    if (cont.isActive) cont.resume(location)
                }
                manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                cont.invokeOnCancellation { manager.removeUpdates(listener) }
            }
        }

    private fun preferredProvider(manager: LocationManager): String? {
        return when {
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                LocationManager.NETWORK_PROVIDER
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                LocationManager.GPS_PROVIDER
            else -> manager.getProviders(true).firstOrNull()
        }
    }
}
