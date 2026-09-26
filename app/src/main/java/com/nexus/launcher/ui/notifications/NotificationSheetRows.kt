package com.nexus.launcher.ui.notifications

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.typography.NexusTypeScale

/** The rows the notification sheet is built from: an app header, and one notification. */
internal object NotificationSheetRows {

    fun appHeader(
        context: Context,
        tokens: NexusColorTokens,
        group: NotificationGroup,
        dp: Float,
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (14 * dp).toInt(), 0, (6 * dp).toInt())
        }
        val icon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams((20 * dp).toInt(), (20 * dp).toInt()).apply {
                marginEnd = (10 * dp).toInt()
            }
            setImageDrawable(appIcon(context, group.packageName))
        }
        val label = TextView(context).apply {
            text = group.appLabel
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        NexusTypeScale.labelSmall.bindTo(label, tokens.textSecondary)
        row.addView(icon)
        row.addView(label)
        return row
    }

    fun entryRow(
        context: Context,
        tokens: NexusColorTokens,
        entry: NotificationEntry,
        dp: Float,
        onOpen: (NotificationEntry) -> Unit,
        onDismiss: (NotificationEntry) -> Unit,
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * dp).toInt(), (10 * dp).toInt(), (8 * dp).toInt(), (10 * dp).toInt())
            isClickable = true
            // So the page can take this exact row away the moment its ✕ is tapped.
            tag = entry.key
            setOnClickListener { onOpen(entry) }
            // Cleared notifications read as past: same row, quieter.
            alpha = if (entry.active) 1f else 0.6f
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val title = TextView(context).apply {
            text = entry.title.ifBlank { entry.text }
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        NexusTypeScale.body.bindTo(title, tokens.textPrimary)
        column.addView(title)
        val bodyText = if (entry.title.isBlank()) "" else entry.text
        if (bodyText.isNotBlank()) {
            val body = TextView(context).apply {
                text = bodyText
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            NexusTypeScale.caption.bindTo(body, tokens.textSecondary)
            column.addView(body)
        }
        val time = TextView(context).apply {
            text = relativeTime(context, entry.postedAt)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { marginStart = (8 * dp).toInt() }
        }
        NexusTypeScale.labelSmall.bindTo(time, tokens.textSecondary)
        row.addView(column)
        row.addView(time)
        // Every row can be removed: a live one is cleared from the shade too, a kept one is
        // simply forgotten.
        val clear = ImageView(context).apply {
            setImageResource(R.drawable.ic_close)
            imageTintList = android.content.res.ColorStateList.valueOf(tokens.textSecondary)
            // 18dp of icon inside a 40dp target: the small one was easy to miss, and a miss
            // hit the row underneath it.
            layoutParams = LinearLayout.LayoutParams((40 * dp).toInt(), (40 * dp).toInt()).apply {
                marginStart = (4 * dp).toInt()
            }
            val inset = (11 * dp).toInt()
            setPadding(inset, inset, inset, inset)
            contentDescription = context.getString(R.string.notification_sheet_dismiss)
            setOnClickListener { onDismiss(entry) }
        }
        row.addView(clear)
        return row
    }

    private fun appIcon(context: Context, packageName: String): android.graphics.drawable.Drawable? =
        runCatching { context.packageManager.getApplicationIcon(packageName) }.getOrNull()

    private fun relativeTime(context: Context, at: Long): CharSequence {
        if (at <= 0L) return ""
        return android.text.format.DateUtils.getRelativeTimeSpanString(
            at, System.currentTimeMillis(), android.text.format.DateUtils.MINUTE_IN_MILLIS,
            android.text.format.DateUtils.FORMAT_ABBREV_RELATIVE,
        )
    }
}
