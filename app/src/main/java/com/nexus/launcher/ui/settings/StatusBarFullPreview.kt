package com.nexus.launcher.ui.settings

import android.app.Dialog
import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.nexus.launcher.R
import com.nexus.launcher.data.prefs.NexusSettingsData
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.immersive.ImmersiveStatus
import com.nexus.launcher.ui.immersive.ImmersiveStatusBarView
import com.nexus.launcher.ui.immersive.ImmersiveStatusStyle

/**
 * The status row on its own over the wallpaper, as it will look on the home screen: tapping the
 * preview in Settings opens this, and any tap closes it again.
 */
object StatusBarFullPreview {

    /** The settings being edited, so the preview shows the draft rather than what is saved. */
    @Volatile var draft: NexusSettingsData? = null

    fun show(context: Context) {
        val activity = SettingsActivityOf.find(context) ?: return
        val settings = draft ?: return
        val tokens = ThemeObserver.currentTokens(context)
        val density = context.resources.displayMetrics.density
        val dialog = Dialog(context, android.R.style.Theme_DeviceDefault_NoActionBar)
        val root = FrameLayout(context).apply {
            setBackgroundColor(androidx.core.graphics.ColorUtils.setAlphaComponent(tokens.bg, 0xD8))
            setOnClickListener { dialog.dismiss() }
        }
        val row = ImmersiveStatusBarView(activity) { tokens.textPrimary }.apply {
            setItems(
                ImmersiveStatus.Items(
                    clock = settings.statusShowClock,
                    notifications = settings.statusShowNotifications,
                    wifi = settings.statusShowWifi,
                    signal = settings.statusShowSignal,
                    battery = settings.statusShowBattery,
                )
            )
            setItemStyle(ImmersiveStatusStyle.of(settings))
            setBar(settings.statusBarHeightDp, settings.statusBarPaddingDp, settings.statusBarBackground)
        }
        val atBottom = settings.statusBarPosition == ImmersiveStatusStyle.BAR_BOTTOM
        root.addView(
            row,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                if (atBottom) Gravity.BOTTOM else Gravity.TOP,
            ),
        )
        val hint = TextView(context).apply {
            text = context.getString(R.string.status_preview_hint)
            gravity = Gravity.CENTER
        }
        NexusTypeScale.caption.bindTo(hint, tokens.textSecondary)
        root.addView(
            hint,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
        dialog.setContentView(
            root,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        dialog.window?.let { window ->
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(0))
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
        // The real row sits in the camera's band; here it keeps clear of the bars the same way.
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            view.setPadding(0, if (atBottom) 0 else bars.top, 0, if (atBottom) bars.bottom else 0)
            insets
        }
        dialog.show()
    }

    /** Called when Settings goes away, so a stale draft cannot be shown later. */
    fun clear() {
        draft = null
    }
}
