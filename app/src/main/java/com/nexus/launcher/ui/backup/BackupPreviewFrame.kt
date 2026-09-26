package com.nexus.launcher.ui.backup

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ImageView
import android.widget.LinearLayout
import com.nexus.launcher.theme.NexusColorTokens

/**
 * A backup's home-screen preview, sized to the device's real screen aspect.
 *
 * Deliberately not `ManagePagesPhoneFrame`: that locks to `ManagePagesThumbSpec`, which is 20%
 * shorter than the real screen so Manage Pages' cards sit nicely in a grid. Manage Pages renders
 * its thumbnails at that same squat aspect, so they match — but a backup preview is a genuine
 * screenshot, and forcing it into that frame cropped the top and bottom off the home screen.
 */
object BackupPreviewFrame {

    fun create(
        context: Context,
        density: Float,
        tokens: NexusColorTokens,
        widthPx: Int,
        bitmap: Bitmap?
    ): View {
        val dm = context.resources.displayMetrics
        val aspect = dm.heightPixels.toFloat().coerceAtLeast(1f) /
            dm.widthPixels.toFloat().coerceAtLeast(1f)
        val heightPx = (widthPx * aspect).toInt().coerceAtLeast(1)

        return ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(widthPx, heightPx)
            // The bitmap already carries the screen's aspect, so FIT_CENTER neither crops nor
            // letterboxes — it lands edge to edge.
            scaleType = ImageView.ScaleType.FIT_CENTER
            background = GradientDrawable().apply {
                setColor(tokens.surfaceRaised)
                cornerRadius = 14 * density
                setStroke((1 * density).toInt().coerceAtLeast(1), tokens.divider)
            }
            clipToOutline = true
            outlineProvider = ViewOutlineProvider.BACKGROUND
            if (bitmap != null) setImageBitmap(bitmap)
        }
    }
}
