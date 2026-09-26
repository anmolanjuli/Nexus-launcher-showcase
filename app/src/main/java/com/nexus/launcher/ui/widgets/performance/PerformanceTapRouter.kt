package com.nexus.launcher.ui.widgets.performance

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * Resolves tap deep-links for Device Performance Widget metrics.
 */
object PerformanceTapRouter {

    fun createCpuPendingIntent(context: Context, appWidgetId: Int): PendingIntent {
        val devIntent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val target = if (devIntent.resolveActivity(context.packageManager) != null) {
            devIntent
        } else {
            Intent(Settings.ACTION_DEVICE_INFO_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, appWidgetId + 100, target, flags)
    }

    fun createRamPendingIntent(context: Context, appWidgetId: Int): PendingIntent {
        val memIntent = Intent("android.settings.MEMORY_SAVER_SETTINGS").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val target = if (memIntent.resolveActivity(context.packageManager) != null) {
            memIntent
        } else {
            val devIntent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            if (devIntent.resolveActivity(context.packageManager) != null) {
                devIntent
            } else {
                Intent(Settings.ACTION_MANAGE_ALL_APPLICATIONS_SETTINGS).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
            }
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, appWidgetId + 200, target, flags)
    }

    fun createBatteryPendingIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(Intent.ACTION_POWER_USAGE_SUMMARY).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val fallback = if (intent.resolveActivity(context.packageManager) != null) {
            intent
        } else {
            Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, appWidgetId + 1000, fallback, flags)
    }

    fun createStoragePendingIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val target = if (intent.resolveActivity(context.packageManager) != null) {
            intent
        } else {
            Intent(Settings.ACTION_STORAGE_VOLUME_ACCESS_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getActivity(context, appWidgetId + 2000, target, flags)
    }
}
