package com.nexus.launcher.ui.backup

import android.app.Dialog
import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale

/**
 * The enlarged view of one backup, with Restore pinned to the footer so the action is reachable
 * without hunting back to the grid.
 */
class BackupDetailDialog(
    context: Context,
    private val entry: BackupCatalog.Entry,
    private val onRestore: () -> Unit
) : Dialog(context, android.R.style.Theme_Black_NoTitleBar) {

    private val dp get() = context.resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tokens: NexusColorTokens = try {
            ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }

        window?.let { win ->
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(win, false)
            win.statusBarColor = Color.TRANSPARENT
            win.navigationBarColor = Color.TRANSPARENT
            win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            win.setFormat(PixelFormat.TRANSLUCENT)
            win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            win.setDimAmount(0.6f)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                win.isNavigationBarContrastEnforced = false
                win.isStatusBarContrastEnforced = false
            }
            win.setLayout(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT
            )
        }

        val root = FrameLayout(context).apply { setOnClickListener { dismiss() } }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(tokens.surface)
                cornerRadius = 26 * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
            }
            clipToOutline = true
            setPadding((20 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt(), (20 * dp).toInt())
            setOnClickListener { /* consume taps inside the card */ }
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
                setMargins((16 * dp).toInt(), 0, (16 * dp).toInt(), 0)
            }
        }

        card.addView(TextView(context).apply {
            text = entry.label
            NexusTypeScale.title.bindTo(this, tokens.textPrimary)
        })
        card.addView(TextView(context).apply {
            text = BackupCardFactory.fullSubtitle(context, entry)
            NexusTypeScale.caption.bindTo(this, tokens.textSecondary)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (2 * dp).toInt() }
        })
        card.addView(buildPages(tokens))
        card.addView(
            BackupCardFactory.restoreButton(context, dp, tokens) {
                dismiss()
                onRestore()
            }
        )

        root.addView(card)
        setContentView(root)
    }

    /** Every page, side by side, sized off the screen so they read as real home screens. */
    private fun buildPages(tokens: NexusColorTokens): View {
        val frameW = (context.resources.displayMetrics.widthPixels * 0.46f).toInt()

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            clipChildren = false
            clipToPadding = false
            setPadding(0, (16 * dp).toInt(), 0, (6 * dp).toInt())
        }

        if (entry.thumbFiles.isEmpty()) {
            row.addView(TextView(context).apply {
                text = context.getString(R.string.backup_no_previews)
                NexusTypeScale.caption.bindTo(this, tokens.textSecondary)
            })
        } else {
            entry.thumbFiles.forEach { file ->
                val bmp = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
                val frame = BackupPreviewFrame.create(context, dp, tokens, frameW, bmp)
                (frame.layoutParams as LinearLayout.LayoutParams).marginEnd = (12 * dp).toInt()
                row.addView(frame)
            }
        }

        return HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
            addView(row)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
    }
}
