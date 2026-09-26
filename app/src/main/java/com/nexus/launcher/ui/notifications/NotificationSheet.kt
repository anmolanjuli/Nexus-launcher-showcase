package com.nexus.launcher.ui.notifications

import android.content.Context
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import android.app.Dialog
import com.nexus.launcher.R
import com.nexus.launcher.service.NexusNotificationService
import com.nexus.launcher.service.NotificationHistory
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * The launcher's own notification sheet: what is in the shade right now, and — with notification
 * history on — what has been cleared within the retention the user chose, grouped by app.
 *
 * Tapping a notification that is still posted opens exactly what the real one opens; a cleared
 * one can only open its app, because its action died with it. The ✕ on a row clears a live one
 * for real and forgets a kept one; the page stays open either way.
 *
 * A plain full-screen dialog rather than a bottom sheet: it fills the screen edge to edge, the
 * ✕ in its header and the Back gesture close it, and a tap near the edge cannot dismiss it —
 * which it could while the rows' own ✕ sat beside a sheet's margin.
 */
object NotificationSheet {

    /** Set while the sheet is listing live notifications only, so ✕ can redraw the list. */
    private var liveRefresh: (() -> Unit)? = null

    /**
     * Keys removed while the page is open. Cancelling a notification can bring its group's
     * summary back, or leave the kept copy a moment longer, which read as needing a second tap.
     */
    private val removed = mutableSetOf<String>()

    fun show(context: Context) {
        if (!hasNotificationAccess(context)) {
            context.startActivity(
                Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        removed.clear()
        val tokens = ThemeObserver.currentTokens(context)
        val dp = context.resources.displayMetrics.density
        val dialog = Dialog(context, android.R.style.Theme_DeviceDefault_NoActionBar)
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(tokens.bg)
            val side = (16 * dp).toInt()
            setPadding(side, (12 * dp).toInt(), side, (16 * dp).toInt())
        }
        val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        root.addView(header(context, dp, tokens, dialog))
        root.addView(
            ScrollView(context).apply {
                isFillViewport = true
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(
                    list,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ),
                )
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
        )
        dialog.setContentView(
            root,
            android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        dialog.window?.let { window ->
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(tokens.bg))
            window.setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            )
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
            // Opened from the immersive home screen, the page keeps the bars away too.
            if (com.nexus.launcher.ui.ImmersiveModeController.isActive) {
                com.nexus.launcher.ui.ImmersiveModeController.hideOn(window)
            }
        }
        // Status bar, camera and gesture bar: the page's own content stays clear of all three.
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                androidx.core.view.WindowInsetsCompat.Type.systemBars() or
                    androidx.core.view.WindowInsetsCompat.Type.displayCutout()
            )
            val side = (16 * dp).toInt()
            view.setPadding(
                side + bars.left, (12 * dp).toInt() + bars.top,
                side + bars.right, (16 * dp).toInt() + bars.bottom,
            )
            insets
        }
        bind(context, list, dp, tokens, dialog)
        dialog.show()
    }

    private fun header(
        context: Context,
        dp: Float,
        tokens: com.nexus.launcher.theme.NexusColorTokens,
        dialog: Dialog,
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(context).apply {
            text = context.getString(R.string.notification_sheet_title)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        NexusTypeScale.bodyStrong.bindTo(title, tokens.textPrimary)
        val clear = TextView(context).apply {
            text = context.getString(R.string.notification_sheet_clear)
            setPadding((8 * dp).toInt(), (6 * dp).toInt(), (8 * dp).toInt(), (6 * dp).toInt())
            setOnClickListener {
                NexusNotificationService.dismissAll()
                NotificationHistory.clear(context)
                dialog.dismiss()
            }
        }
        NexusTypeScale.labelSmall.bindTo(clear, tokens.accent)
        val close = android.widget.ImageView(context).apply {
            setImageResource(R.drawable.ic_close)
            imageTintList = android.content.res.ColorStateList.valueOf(tokens.textSecondary)
            // A 22dp glyph in a 44dp target — the bare icon was hard to hit.
            layoutParams = LinearLayout.LayoutParams((44 * dp).toInt(), (44 * dp).toInt()).apply {
                marginStart = (8 * dp).toInt()
            }
            val inset = (11 * dp).toInt()
            setPadding(inset, inset, inset, inset)
            contentDescription = context.getString(R.string.action_close)
            setOnClickListener { dialog.dismiss() }
        }
        row.addView(title)
        row.addView(clear)
        row.addView(close)
        return row
    }

    private fun bind(
        context: Context,
        list: LinearLayout,
        dp: Float,
        tokens: com.nexus.launcher.theme.NexusColorTokens,
        dialog: Dialog,
    ) {
        val owner = context as? LifecycleOwner
        val history = if (NotificationHistory.enabled) NotificationHistory.observe(context) else null
        if (history == null || owner == null) {
            // Nothing is being kept, so there is no flow to follow: redraw after each removal.
            fun refresh() { fill(context, list, dp, tokens, NotificationFeed.live(), dialog) }
            liveRefresh = ::refresh
            refresh()
            return
        }
        liveRefresh = null
        owner.lifecycleScope.launch {
            history.collectLatest { records ->
                fill(context, list, dp, tokens, NotificationFeed.merged(records), dialog)
            }
        }
    }

    private fun fill(
        context: Context,
        list: LinearLayout,
        dp: Float,
        tokens: com.nexus.launcher.theme.NexusColorTokens,
        entries: List<NotificationEntry>,
        dialog: Dialog,
    ) {
        list.removeAllViews()
        val groups = NotificationFeed.group(context, entries.filter { it.key !in removed })
        if (groups.isEmpty()) {
            val empty = TextView(context).apply {
                text = context.getString(R.string.notification_sheet_empty)
                gravity = Gravity.CENTER
                setPadding(0, (40 * dp).toInt(), 0, (40 * dp).toInt())
            }
            NexusTypeScale.caption.bindTo(empty, tokens.textSecondary)
            list.addView(empty)
            return
        }
        groups.forEach { group ->
            list.addView(NotificationSheetRows.appHeader(context, tokens, group, dp))
            group.entries.forEach { entry ->
                list.addView(
                    NotificationSheetRows.entryRow(
                        context, tokens, entry, dp,
                        onOpen = { open(context, it, dialog) },
                        onDismiss = { target ->
                            removed.add(target.key)
                            if (target.active) NexusNotificationService.dismiss(target.key)
                            NotificationHistory.forget(context, target.key)
                            // Take the row away now; the flow catches up a moment later.
                            list.post {
                                liveRefresh?.invoke() ?: run { list.findViewWithTag<View>(target.key)?.let(list::removeView) }
                            }
                        },
                    )
                )
            }
        }
    }

    /** A live notification opens its own target; a cleared one can only open its app. */
    private fun open(context: Context, entry: NotificationEntry, dialog: Dialog) {
        val posted = NexusNotificationService.posted(entry.key)
        val contentIntent = posted?.notification?.contentIntent
        if (contentIntent != null) {
            runCatching { contentIntent.send() }
                .onSuccess { NexusNotificationService.dismiss(entry.key) }
            dialog.dismiss()
            return
        }
        // Nothing to open (a kept notification from an app with no launcher entry): stay put
        // rather than closing the page, which read as being thrown back to the home screen.
        val launch = context.packageManager.getLaunchIntentForPackage(entry.packageName) ?: return
        runCatching { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onSuccess { dialog.dismiss() }
    }

    private fun hasNotificationAccess(context: Context): Boolean {
        val enabled = android.provider.Settings.Secure.getString(
            context.contentResolver, "enabled_notification_listeners",
        ).orEmpty()
        return enabled.contains(context.packageName)
    }
}
