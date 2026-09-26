package com.nexus.launcher.reader.doc

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.format.Formatter
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.WindowCompat
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.glass.FloatingSurfaces
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shared dialog card builder and secondary dialog factory for Document Library context menus.
 */
object NexusDocDialogFactory {

    fun resolveTokens(context: Context): NexusColorTokens {
        return if (com.nexus.launcher.feed.NexusFeedEInkCoordinator.isEInkMode(context)) {
            com.nexus.launcher.feed.NexusFeedEInkCoordinator.getTokens(context)
        } else {
            try {
                ThemeObserver.currentTokens(context)
            } catch (_: Exception) {
                NexusColorTokens.Dark
            }
        }
    }

    fun createDialogCard(
        context: Context,
        title: String,
        tokens: NexusColorTokens,
        dp: Float
    ): Triple<Dialog, FrameLayout, LinearLayout> {
        val isEInk = com.nexus.launcher.feed.NexusFeedEInkCoordinator.isEInkMode(context)
        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar)
        dialog.window?.let { win ->
            WindowCompat.setDecorFitsSystemWindows(win, false)
            win.statusBarColor = Color.TRANSPARENT
            win.navigationBarColor = Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                win.isNavigationBarContrastEnforced = false
                win.isStatusBarContrastEnforced = false
            }
            win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            win.setFormat(PixelFormat.TRANSLUCENT)
            win.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            win.setDimAmount(0.65f)
            if (!isEInk) {
                FloatingSurfaces.applyBlurBehind(win)
            }
            win.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        }

        val root = FrameLayout(context).apply {
            setOnClickListener { dialog.dismiss() }
        }

        val cardWidth = (330 * dp).toInt().coerceAtMost(
            context.resources.displayMetrics.widthPixels - (32 * dp).toInt()
        )

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = if (isEInk) {
                GradientDrawable().apply {
                    setColor(tokens.surface)
                    cornerRadius = 0f
                    setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
                }
            } else {
                FloatingSurfaces.sheetCard(tokens, 20 * dp, dp)
            }
            setPadding((20 * dp).toInt(), (18 * dp).toInt(), (20 * dp).toInt(), (18 * dp).toInt())
            layoutParams = FrameLayout.LayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
            setOnClickListener { /* consume click inside card */ }
        }

        val titleView = TextView(context).apply {
            text = title
            typeface = if (isEInk) Typeface.MONOSPACE else Typeface.DEFAULT_BOLD
            NexusTypeScale.title.bindTo(this, tokens.textPrimary)
            setPadding(0, 0, 0, (10 * dp).toInt())
        }
        card.addView(titleView)

        root.addView(card)
        dialog.setContentView(root)
        return Triple(dialog, root, card)
    }

    fun buildActionButton(
        context: Context,
        label: String,
        tokens: NexusColorTokens,
        dp: Float,
        isPrimary: Boolean = false,
        isDestructive: Boolean = false,
        onClick: () -> Unit
    ): View {
        val isEInk = com.nexus.launcher.feed.NexusFeedEInkCoordinator.isEInkMode(context)
        return TextView(context).apply {
            text = label
            textSize = 13f
            typeface = if (isEInk) Typeface.MONOSPACE else Typeface.DEFAULT_BOLD
            val textColor = when {
                isDestructive -> tokens.danger
                isPrimary -> tokens.surface
                else -> tokens.textSecondary
            }
            setTextColor(textColor)
            setPadding((14 * dp).toInt(), (8 * dp).toInt(), (14 * dp).toInt(), (8 * dp).toInt())
            background = GradientDrawable().apply {
                val bg = when {
                    isPrimary -> tokens.textPrimary
                    else -> Color.TRANSPARENT
                }
                setColor(bg)
                cornerRadius = if (isEInk) 0f else 14 * dp
                if (isEInk && isPrimary) {
                    setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
                }
            }
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
        }
    }

    fun buildMenuItemRow(
        context: Context,
        label: String,
        tokens: NexusColorTokens,
        dp: Float,
        isDestructive: Boolean = false,
        onClick: () -> Unit
    ): View {
        val isEInk = com.nexus.launcher.feed.NexusFeedEInkCoordinator.isEInkMode(context)
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt())
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = if (isEInk) 0f else 12 * dp
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            val tv = TextView(context).apply {
                text = label
                typeface = if (isEInk) Typeface.MONOSPACE else null
                NexusTypeScale.bodyStrong.bindTo(this, if (isDestructive) tokens.danger else tokens.textPrimary)
            }
            addView(tv)
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            }
        }
    }

    fun showFileInfoDialog(
        context: Context,
        document: DocumentRecord,
        collectionName: String
    ) {
        val dp = context.resources.displayMetrics.density
        val isEInk = com.nexus.launcher.feed.NexusFeedEInkCoordinator.isEInkMode(context)
        val tokens = resolveTokens(context)
        val (dialog, _, card) = createDialogCard(context, context.getString(R.string.nexus_doc_file_info), tokens, dp)

        val locale = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
        val datePattern = android.text.format.DateFormat.getBestDateTimePattern(locale, "yMMMd")
        val dateFmt = SimpleDateFormat(datePattern, locale)
        val timeFmt = android.text.format.DateFormat.getTimeFormat(context)
        fun formatDateTime(ts: Long): String {
            val d = Date(ts)
            return "${dateFmt.format(d)} • ${timeFmt.format(d)}"
        }
        val addedDate = formatDateTime(if (document.addedTimestamp > 0) document.addedTimestamp else document.lastReadTimestamp)
        val lastReadDate = formatDateTime(document.lastReadTimestamp)
        val sizeFormatted = Formatter.formatFileSize(context, document.fileSize)

        val entries = listOf(
            context.getString(R.string.nexus_doc_info_name) to document.effectiveTitle,
            context.getString(R.string.nexus_doc_all_collections) to collectionName,
            context.getString(R.string.nexus_doc_info_size) to sizeFormatted,
            context.getString(R.string.nexus_doc_info_added) to addedDate,
            context.getString(R.string.nexus_doc_info_last_read) to lastReadDate
        )

        entries.forEach { (label, value) ->
            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, (3 * dp).toInt(), 0, (3 * dp).toInt())
                val lView = TextView(context).apply {
                    text = label
                    typeface = if (isEInk) Typeface.MONOSPACE else null
                    NexusTypeScale.caption.bindTo(this, tokens.textSecondary)
                    layoutParams = LinearLayout.LayoutParams((90 * dp).toInt(), LinearLayout.LayoutParams.WRAP_CONTENT)
                }
                val vView = TextView(context).apply {
                    text = value
                    typeface = if (isEInk) Typeface.MONOSPACE else null
                    NexusTypeScale.body.bindTo(this, tokens.textPrimary)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                }
                addView(lView)
                addView(vView)
            }
            card.addView(row)
        }

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            setPadding(0, (14 * dp).toInt(), 0, 0)
            addView(buildActionButton(context, context.getString(R.string.nexus_doc_overflow_close), tokens, dp, isPrimary = true) {
                dialog.dismiss()
            })
        }
        card.addView(btnRow)
        dialog.show()
    }

    fun showRemoveConfirmDialog(
        context: Context,
        document: DocumentRecord,
        onConfirm: () -> Unit
    ) {
        val dp = context.resources.displayMetrics.density
        val isEInk = com.nexus.launcher.feed.NexusFeedEInkCoordinator.isEInkMode(context)
        val tokens = resolveTokens(context)
        val (dialog, _, card) = createDialogCard(context, context.getString(R.string.nexus_doc_remove_title), tokens, dp)

        val desc = TextView(context).apply {
            text = context.getString(R.string.nexus_doc_remove_desc, document.effectiveTitle)
            typeface = if (isEInk) Typeface.MONOSPACE else null
            NexusTypeScale.body.bindTo(this, tokens.textSecondary)
            setPadding(0, 0, 0, (18 * dp).toInt())
        }
        card.addView(desc)

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            addView(buildActionButton(context, context.getString(R.string.nexus_doc_cancel), tokens, dp) {
                dialog.dismiss()
            })
            addView(buildActionButton(context, context.getString(R.string.nexus_doc_remove_action), tokens, dp, isDestructive = true) {
                dialog.dismiss()
                onConfirm()
            })
        }
        card.addView(btnRow)
        dialog.show()
    }
}
