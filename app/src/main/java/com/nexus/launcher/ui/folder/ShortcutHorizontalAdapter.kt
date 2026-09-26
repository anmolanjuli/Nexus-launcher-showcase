package com.nexus.launcher.ui.folder

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale

class ShortcutHorizontalAdapter(
    private val shortcuts: List<ShortcutInfo>,
    private val onShortcutSelected: (ShortcutInfo) -> Unit,
    private val onDismiss: () -> Unit
) : RecyclerView.Adapter<ShortcutHorizontalAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val dp = parent.context.resources.displayMetrics.density
        val tokens = try {
            ThemeObserver.currentTokens(parent.context)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }

        val container = LinearLayout(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams((80 * dp).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt())
        }

        val iconBg = FrameLayout(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams((56 * dp).toInt(), (56 * dp).toInt())
            background = GradientDrawable().apply {
                setColor(tokens.surfaceRaised)
                cornerRadius = 16 * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
            }
        }
        val icon = ImageView(parent.context).apply {
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                setMargins((8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
            }
        }
        iconBg.addView(icon)
        container.addView(iconBg)

        val label = TextView(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (4 * dp).toInt()
            }
            NexusTypeScale.caption.bindTo(this, tokens.textPrimary)
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        container.addView(label)

        return ViewHolder(container, icon, label)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val shortcut = shortcuts[position]
        val context = holder.itemView.context
        val isNexus = shortcut.`package` == context.packageName
        val density = context.resources.displayMetrics.densityDpi

        val tokens = try {
            ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }

        val rawIcon = if (isNexus) {
            com.nexus.launcher.ui.widgets.shortcutbox.NexusBuiltinShortcuts.getDrawable(context, shortcut.id)
        } else {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
            try { launcherApps.getShortcutIconDrawable(shortcut, density) } catch (e: Exception) { null }
        }

        if (isNexus && rawIcon != null) {
            val tinted = rawIcon.mutate()
            tinted.setTint(tokens.textPrimary)
            holder.icon.setImageDrawable(tinted)
            holder.icon.imageTintList = ColorStateList.valueOf(tokens.textPrimary)
        } else {
            holder.icon.imageTintList = null
            holder.icon.setImageDrawable(rawIcon)
        }

        holder.label.text = shortcut.shortLabel?.toString() ?: holder.itemView.context.getString(com.nexus.launcher.R.string.home_edit_shortcut)

        holder.itemView.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            onShortcutSelected(shortcut)
            onDismiss()
        }
    }

    override fun getItemCount() = shortcuts.size

    class ViewHolder(view: View, val icon: ImageView, val label: TextView) : RecyclerView.ViewHolder(view)
}
