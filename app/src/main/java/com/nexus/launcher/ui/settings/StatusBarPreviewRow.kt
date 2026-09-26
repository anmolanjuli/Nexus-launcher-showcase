package com.nexus.launcher.ui.settings

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.nexus.launcher.data.prefs.NexusSettingsData
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.ui.immersive.ImmersiveStatusBarView
import com.nexus.launcher.ui.immersive.ImmersiveStatus
import com.nexus.launcher.ui.immersive.ImmersiveStatusStyle

/**
 * The status row itself, shown at the top of its settings so a change can be seen rather than
 * imagined. It is the same view the home screen draws ([ImmersiveStatusBarView]) with the same
 * live clock, battery and signal — not a picture of one — so anything the row can do, the
 * preview does.
 *
 * Tapping it shows the row over the wallpaper with the rest of Settings dimmed away.
 */
class StatusBarPreviewRow(private val context: Context) {

    private val density = context.resources.displayMetrics.density
    private val activity = SettingsActivityOf.find(context)
    private val row = activity?.let { host ->
        ImmersiveStatusBarView(host) { ThemeObserver.currentTokens(context).textPrimary }
    }

    val view: View = FrameLayout(context).apply {
        // Wraps the row so a taller bar is actually taller here, with a floor so a short one
        // still reads as a bar.
        minimumHeight = (PREVIEW_HEIGHT_DP * density).toInt()
        layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = (10 * density).toInt() }
        row?.let {
            addView(
                it,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER_VERTICAL,
                ),
            )
        }
        setOnClickListener { StatusBarFullPreview.show(context) }
    }

    fun bind(s: NexusSettingsData) {
        val row = row ?: return
        val tokens = ThemeObserver.currentTokens(context)
        view.background = GradientDrawable().apply {
            cornerRadius = 14f * density
            setColor(tokens.surface)
            setStroke((1 * density).toInt().coerceAtLeast(1), tokens.divider)
        }
        row.setItems(
            ImmersiveStatus.Items(
                clock = s.statusShowClock,
                notifications = s.statusShowNotifications,
                wifi = s.statusShowWifi,
                signal = s.statusShowSignal,
                battery = s.statusShowBattery,
            )
        )
        row.setItemStyle(ImmersiveStatusStyle.of(s))
        row.setBar(s.statusBarHeightDp, s.statusBarPaddingDp, s.statusBarBackground)
        setShown(s.statusBarEnabled)
    }

    /** The master switch takes the preview with it, before the draft comes back round. */
    fun setShown(shown: Boolean) {
        view.visibility = if (shown) View.VISIBLE else View.GONE
    }

    private companion object {
        const val PREVIEW_HEIGHT_DP = 40f
    }
}
