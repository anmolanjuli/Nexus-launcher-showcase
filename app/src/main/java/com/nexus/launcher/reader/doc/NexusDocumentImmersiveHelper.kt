package com.nexus.launcher.reader.doc

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.nexus.launcher.reader.NexusReaderThemeHelper

/**
 * Coordinates full-black immersive reading mode for the Document Reader.
 * Hides system bars and toolbars, and turns the surrounding chrome black ("all black except the file").
 */
class NexusDocumentImmersiveHelper(
    private val activity: Activity,
    private val rootLayout: FrameLayout,
    private val statusBarShelf: View,
    private val palette: NexusReaderThemeHelper.ReaderPalette,
    private val toolbarBuilder: NexusDocumentToolbarBuilder,
    private val getFileBackground: () -> Int = { palette.bg }
) {
    var isImmersive: Boolean = false
        private set

    fun toggleImmersive() {
        setImmersive(!isImmersive)
    }

    fun updateBackground(bg: Int = getFileBackground()) {
        rootLayout.setBackgroundColor(bg)
        statusBarShelf.setBackgroundColor(bg)
        activity.window.decorView.setBackgroundColor(bg)
    }

    fun setImmersive(enabled: Boolean) {
        if (isImmersive == enabled) return
        isImmersive = enabled

        val insetsController = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        val fileBg = getFileBackground()

        if (enabled) {
            toolbarBuilder.hideToolbars()
            insetsController?.let { controller ->
                controller.hide(WindowInsetsCompat.Type.systemBars())
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
            updateBackground(fileBg)
        } else {
            toolbarBuilder.showToolbars()
            insetsController?.let { controller ->
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
            updateBackground(fileBg)
        }
    }
}
