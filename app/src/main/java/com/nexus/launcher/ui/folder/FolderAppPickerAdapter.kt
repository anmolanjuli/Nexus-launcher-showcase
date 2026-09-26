package com.nexus.launcher.ui.folder

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.nexus.launcher.R
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Card-based list adapter for Folder App Picker matching Shortcut Picker card design. */
class FolderAppPickerAdapter(
    private val selectedIds: MutableSet<Long>,
    private val maxSelectable: Int = -1,
    private val onSelectionChanged: () -> Unit
) : RecyclerView.Adapter<FolderAppPickerAdapter.ViewHolder>() {

    private var items: List<HomeScreenItem> = emptyList()
    private val labelCache = mutableMapOf<String, String>()
    private var iconResolver: com.nexus.launcher.ui.icons.IconResolver? = null

    /**
     * Icons already loaded, by package.
     *
     * An app's icon comes from its APK: a binder call, a resource lookup, and often an adaptive
     * icon to rasterise. Doing that in [onBindViewHolder] means doing it during a fling, for every
     * row that scrolls past, on the thread drawing the fling — which is what made these pickers
     * stutter. A row now draws whatever is already loaded and asks for the rest off-thread.
     */
    private val iconCache = mutableMapOf<String, android.graphics.drawable.Drawable?>()
    private val scope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate
    )

    /** Theme tokens, read once rather than per row: each read is an entry-point lookup. */
    private var cachedTokens: NexusColorTokens? = null

    fun submitList(newItems: List<HomeScreenItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    /** Reads an app's icon: the icon pack's if there is one, the app's own otherwise. */
    private fun loadIcon(
        context: android.content.Context,
        packageName: String,
    ): android.graphics.drawable.Drawable? = try {
        iconResolver?.getIcon(packageName) ?: context.packageManager.getApplicationIcon(packageName)
    } catch (_: Exception) {
        null
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        scope.coroutineContext.cancelChildren()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val context = parent.context
        val dp = context.resources.displayMetrics.density
        val tokens = try {
            ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }

        if (iconResolver == null) {
            iconResolver = try {
                dagger.hilt.android.EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    com.nexus.launcher.ui.icons.IconResolverEntryPoint::class.java
                ).iconResolver()
            } catch (_: Exception) {
                null
            }
        }

        val container = LinearLayout(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                val m = (2 * dp).toInt()
                setMargins(m, (4 * dp).toInt(), m, (4 * dp).toInt())
            }
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val outValue = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true)
            setBackgroundResource(outValue.resourceId)
            val pad = (4 * dp).toInt()
            setPadding(pad, (8 * dp).toInt(), pad, (8 * dp).toInt())
            isClickable = true
            isFocusable = true
        }

        val iconWrapper = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams((48 * dp).toInt(), (48 * dp).toInt()).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = (4 * dp).toInt()
            }
        }

        val icon = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val check = ImageView(context).apply {
            layoutParams = FrameLayout.LayoutParams((18 * dp).toInt(), (18 * dp).toInt()).apply {
                gravity = Gravity.TOP or Gravity.END
            }
            setImageResource(R.drawable.ic_check)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(tokens.textPrimary)
            }
            imageTintList = ColorStateList.valueOf(tokens.surface)
            setPadding((3 * dp).toInt(), (3 * dp).toInt(), (3 * dp).toInt(), (3 * dp).toInt())
        }
        iconWrapper.addView(icon)
        iconWrapper.addView(check)

        val text = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            NexusTypeScale.caption.bindTo(this, tokens.textPrimary)
            gravity = Gravity.CENTER
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        container.addView(iconWrapper)
        container.addView(text)

        return ViewHolder(container, container, icon, text, check)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        val tokens = cachedTokens ?: try {
            ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }.also { cachedTokens = it }

        val pm = context.packageManager
        val label = labelCache.getOrPut(item.packageName) {
            labelFor(pm, item)
        }

        holder.text.text = label
        NexusTypeScale.caption.bindTo(holder.text, tokens.textPrimary)

        val pkg = item.packageName
        holder.boundPackage = pkg
        if (iconCache.containsKey(pkg)) {
            holder.icon.setImageDrawable(iconCache[pkg])
        } else {
            holder.icon.setImageDrawable(null)
            scope.launch {
                val drawable = withContext(kotlinx.coroutines.Dispatchers.IO) { loadIcon(context, pkg) }
                iconCache[pkg] = drawable
                // The holder may have been recycled onto another app while this loaded.
                if (holder.boundPackage == pkg) holder.icon.setImageDrawable(drawable)
            }
        }

        val isSelected = selectedIds.contains(item.id.toLong())
        holder.check.visibility = if (isSelected) View.VISIBLE else View.GONE
        holder.icon.alpha = if (isSelected) 0.82f else 1f

        holder.card.setOnClickListener {
            val itemId = item.id.toLong()
            if (selectedIds.contains(itemId)) {
                selectedIds.remove(itemId)
            } else {
                if (maxSelectable > 0 && selectedIds.size >= maxSelectable) {
                    return@setOnClickListener
                }
                selectedIds.add(itemId)
            }
            notifyItemChanged(position)
            onSelectionChanged()
        }
    }

    override fun getItemCount(): Int = items.size

    companion object {
        private val globalLabelCache = java.util.concurrent.ConcurrentHashMap<String, String>()

        fun labelFor(pm: android.content.pm.PackageManager, item: HomeScreenItem): String {
            return globalLabelCache.getOrPut(item.packageName) {
                try {
                    pm.getApplicationLabel(pm.getApplicationInfo(item.packageName, 0)).toString()
                } catch (_: Exception) {
                    item.packageName
                }
            }
        }
    }

    class ViewHolder(
        val root: View,
        val card: View,
        val icon: ImageView,
        val text: TextView,
        val check: ImageView
    ) : RecyclerView.ViewHolder(root) {
        /** What this row is currently showing — an icon arriving late must not land on another app. */
        var boundPackage: String? = null
    }
}
