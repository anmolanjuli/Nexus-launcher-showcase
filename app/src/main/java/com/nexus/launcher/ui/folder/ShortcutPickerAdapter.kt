package com.nexus.launcher.ui.folder

import android.content.pm.ShortcutInfo
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale

class ShortcutPickerAdapter(
    private val allGroupsProvider: () -> Map<String, List<ShortcutInfo>>,
    private val expandedPackages: MutableSet<String>,
    private val onShortcutSelected: (ShortcutInfo) -> Unit,
    private val onDismiss: () -> Unit,
    private val onItemToggled: () -> Unit
) : RecyclerView.Adapter<ShortcutPickerAdapter.ViewHolder>() {

    private var items: List<ShortcutAdapterItem.Header> = emptyList()

    fun submitList(newItems: List<ShortcutAdapterItem.Header>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val dp = parent.context.resources.displayMetrics.density
        val tokens = try {
            ThemeObserver.currentTokens(parent.context)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }

        val container = LinearLayout(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (4 * dp).toInt(), (16 * dp).toInt(), (4 * dp).toInt())
        }

        val row = LinearLayout(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = com.nexus.launcher.ui.glass.NeumorphicSurfaces.raisedOr(this, tokens, 14 * dp) {
                GradientDrawable().apply {
                    setColor(tokens.surface)
                    cornerRadius = 14 * dp
                    setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
                }
            }
            setPadding((12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt())
        }

        val appIcon = ImageView(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams((40 * dp).toInt(), (40 * dp).toInt())
        }
        row.addView(appIcon)

        val appLabel = TextView(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (16 * dp).toInt()
            }
            NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        row.addView(appLabel)

        val countBadge = TextView(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = (8 * dp).toInt() }
            background = GradientDrawable().apply {
                setColor(tokens.surfaceRaised)
                cornerRadius = 8 * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
            }
            setPadding((8 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt())
            NexusTypeScale.labelSmall.bindTo(this, tokens.textSecondary)
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            textAlignment = View.TEXT_ALIGNMENT_VIEW_END
        }
        row.addView(countBadge)

        val chevron = ImageView(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams((24 * dp).toInt(), (24 * dp).toInt())
            imageTintList = ColorStateList.valueOf(tokens.textSecondary)
        }
        row.addView(chevron)

        container.addView(row)

        val horizontalRecycler = RecyclerView(parent.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (8 * dp).toInt() }
            visibility = View.GONE
        }
        container.addView(horizontalRecycler)

        return ViewHolder(container, appIcon, appLabel, countBadge, chevron, horizontalRecycler, row)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.text.text = item.appLabel
        holder.icon.setImageDrawable(item.icon)

        val shortcuts = allGroupsProvider()[item.packageName] ?: emptyList()
        if (shortcuts.isNotEmpty()) {
            holder.countBadge.visibility = View.VISIBLE
            holder.countBadge.text = shortcuts.size.toString()
        } else {
            holder.countBadge.visibility = View.GONE
        }

        if (item.isExpanded) {
            holder.chevron.setImageResource(R.drawable.ic_chevron_up)
            holder.horizontalRecycler.visibility = View.VISIBLE
            holder.horizontalRecycler.adapter = ShortcutHorizontalAdapter(
                shortcuts, onShortcutSelected, onDismiss
            )
            if (holder.horizontalRecycler.layoutManager == null) {
                holder.horizontalRecycler.layoutManager = LinearLayoutManager(
                    holder.itemView.context, LinearLayoutManager.HORIZONTAL, false
                )
            }
        } else {
            holder.chevron.setImageResource(R.drawable.ic_chevron_down)
            holder.horizontalRecycler.visibility = View.GONE
        }

        holder.row.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            if (item.isExpanded) expandedPackages.remove(item.packageName)
            else expandedPackages.add(item.packageName)
            onItemToggled()
        }
    }

    override fun getItemCount() = items.size

    class ViewHolder(
        view: View,
        val icon: ImageView,
        val text: TextView,
        val countBadge: TextView,
        val chevron: ImageView,
        val horizontalRecycler: RecyclerView,
        val row: View
    ) : RecyclerView.ViewHolder(view)
}
