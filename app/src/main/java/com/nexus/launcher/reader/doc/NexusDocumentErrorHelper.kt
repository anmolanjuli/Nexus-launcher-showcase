package com.nexus.launcher.reader.doc

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.reader.NexusReaderThemeHelper
import com.nexus.launcher.typography.NexusTypeScale

/**
 * Renders user-friendly document load error messages into the reader content container.
 */
object NexusDocumentErrorHelper {

    fun showError(
        context: Context,
        container: FrameLayout,
        message: String,
        palette: NexusReaderThemeHelper.ReaderPalette,
        dp: Float
    ) {
        val errorView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((32 * dp).toInt(), 0, (32 * dp).toInt(), 0)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            addView(TextView(context).apply {
                text = message
                NexusTypeScale.bodyStrong.bindTo(this, palette.textPrimary)
                gravity = Gravity.CENTER
            })
        }
        container.addView(errorView)
    }
}
