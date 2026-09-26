package com.nexus.launcher.ui.backup

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.typography.NexusTypeScale

/**
 * Backup tiles for the two-column grid: a preview of the home screen the backup restores, its
 * name and date, and a Restore action in the footer. Tapping a tile opens [BackupDetailDialog],
 * which shows the same preview at a size worth looking at.
 */
object BackupCardFactory {

    fun gridParams(density: Float, columnWidthPx: Int) = GridLayout.LayoutParams().apply {
        width = 0
        height = ViewGroup.LayoutParams.WRAP_CONTENT
        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
        setMargins(
            (6 * density).toInt(), (6 * density).toInt(),
            (6 * density).toInt(), (14 * density).toInt()
        )
    }

    fun createTile(
        context: Context,
        density: Float,
        tokens: NexusColorTokens,
        entry: BackupCatalog.Entry,
        columnWidthPx: Int,
        onOpen: () -> Unit,
        onRestore: () -> Unit,
        onLongPress: () -> Unit
    ): View {
        val dp = density
        val tile = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = gridParams(dp, columnWidthPx)
            clipChildren = false
        }

        // A true screen-aspect preview, not Manage Pages' deliberately-squat phone frame — the
        // capture is a real screenshot, and that frame's 0.8x aspect cropped the top and bottom
        // off it (status-bar row of widgets gone, dock gone).
        val frameW = (columnWidthPx - (12 * dp)).toInt().coerceAtLeast((60 * dp).toInt())
        val coverFile = entry.thumbFiles.getOrNull(entry.defaultPage) ?: entry.thumbFiles.firstOrNull()
        val bmp = coverFile?.let { runCatching { BitmapFactory.decodeFile(it.absolutePath) }.getOrNull() }
        val frame = BackupPreviewFrame.create(context, dp, tokens, frameW, bmp)
        (frame.layoutParams as LinearLayout.LayoutParams).gravity = Gravity.CENTER_HORIZONTAL
        frame.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onOpen()
        }
        frame.setOnLongClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onLongPress()
            true
        }
        tile.addView(frame)

        tile.addView(TextView(context).apply {
            text = entry.label
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
            NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (10 * dp).toInt() }
        })
        tile.addView(TextView(context).apply {
            text = shortSubtitle(context, entry)
            isSingleLine = true
            NexusTypeScale.caption.bindTo(this, tokens.textSecondary)
        })
        tile.addView(restoreButton(context, dp, tokens, onRestore))
        return tile
    }

    /** "3 pages · Mar 27, 2026" — the full detail lives in the enlarged view. */
    fun shortSubtitle(context: Context, entry: BackupCatalog.Entry): String {
        val pages = context.resources.getQuantityString(
            R.plurals.backup_page_count, entry.pageCount, entry.pageCount
        )
        return "$pages · ${formatDate(entry.lastModified)}"
    }

    /** "3 pages · 47 items · 2.1 MB · 27 Mar 2026, 20:14" */
    fun fullSubtitle(context: Context, entry: BackupCatalog.Entry): String {
        val pages = context.resources.getQuantityString(
            R.plurals.backup_page_count, entry.pageCount, entry.pageCount
        )
        val items = context.resources.getQuantityString(
            R.plurals.backup_item_count, entry.itemCount, entry.itemCount
        )
        val size = android.text.format.Formatter.formatShortFileSize(context, entry.sizeBytes)
        return "$pages · $items · $size · ${formatDate(entry.lastModified, withTime = true)}"
    }

    private fun formatDate(millis: Long, withTime: Boolean = false): String {
        // The language's own order for day, month and year, not the English one.
        val locale = java.util.Locale.getDefault()
        val pattern = android.text.format.DateFormat.getBestDateTimePattern(
            locale, if (withTime) "dMMMyyyyHHmm" else "dMMMyyyy"
        )
        return java.text.SimpleDateFormat(pattern, locale).format(java.util.Date(millis))
    }

    fun restoreButton(
        context: Context, density: Float, tokens: NexusColorTokens, onRestore: () -> Unit
    ): View = TextView(context).apply {
        text = context.getString(R.string.backup_restore_action)
        gravity = Gravity.CENTER
        NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
        background = GradientDrawable().apply {
            setColor(tokens.surfaceRaised)
            cornerRadius = 14 * density
        }
        setPadding(0, (11 * density).toInt(), 0, (11 * density).toInt())
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (10 * density).toInt() }
        setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            onRestore()
        }
    }
}
