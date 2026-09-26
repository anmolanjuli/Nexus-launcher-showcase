package com.nexus.launcher.ui

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import com.nexus.launcher.R

internal fun MainActivity.promptNotificationPermission() {
    if (isNotificationListenerEnabled()) return
    val shown = getSharedPreferences("nexus_prefs", Context.MODE_PRIVATE)
        .getBoolean("notification_permission_asked", false)
    if (shown) return
    getSharedPreferences("nexus_prefs", Context.MODE_PRIVATE)
        .edit().putBoolean("notification_permission_asked", true).apply()
    val mainContainer = findViewById<FrameLayout>(R.id.main_container)
    val density = resources.displayMetrics.density
    val accentColor = canvasView.accentColor
    val scrim = View(this).apply {
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        setBackgroundColor(Color.parseColor("#80000000"))
        alpha = 0f
    }
    mainContainer.addView(scrim)
    scrim.animate().alpha(1f).setDuration(200).start()
    var sheet: NotificationPermissionSheet? = null
    fun dismiss() {
        sheet?.animate()
            ?.translationY(sheet!!.height.toFloat())
            ?.alpha(0f)
            ?.setDuration(250)
            ?.withEndAction { mainContainer.removeView(sheet) }
            ?.start()
        scrim.animate().alpha(0f).setDuration(200)
            .withEndAction { mainContainer.removeView(scrim) }
            .start()
    }
    sheet = NotificationPermissionSheet(
        this, density, accentColor,
        onEnable = {
            dismiss()
            startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
        },
        onDismiss = { dismiss() }
    )
    sheet.layoutParams = FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.WRAP_CONTENT
    ).apply {
        gravity = Gravity.BOTTOM
    }
    sheet.translationY = 800f * density
    sheet.alpha = 0f
    mainContainer.addView(sheet)
    sheet.animate()
        .translationY(0f)
        .alpha(1f)
        .setDuration(300)
        .setInterpolator(DecelerateInterpolator())
        .start()
    scrim.setOnClickListener { dismiss() }
}

private fun MainActivity.isNotificationListenerEnabled(): Boolean {
    val flat = Settings.Secure.getString(
        contentResolver,
        "enabled_notification_listeners"
    )
    return flat?.contains(packageName) == true
}
