package com.nexus.launcher.ui.widgets.mosaic

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewConfiguration
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.nexus.launcher.R
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.MainActivity
import com.nexus.launcher.ui.widgets.AppWidgetController
import com.nexus.launcher.ui.widgets.NexusWidgetConfig

/**
 * Reusable widget list for the Widgets tab in Mosaic Studio.
 * All MosaicConfig mutations are staged via [getDraft]/[setDraft] (shared with the Mosaic tab) —
 * nothing is persisted to Room here. The caller's shared Apply bar commits [getDraft] on tap.
 */
class MosaicWidgetManagerContent(
    private val activity: MainActivity,
    private val appWidgetController: AppWidgetController,
    private val appWidgetHost: AppWidgetHost,
    private val getDraft: () -> MosaicConfig,
    private val setDraft: (MosaicConfig) -> Unit,
    private val onPickerVisibilityChanged: (Boolean) -> Unit,
    private val onConfigChanged: (MosaicConfig) -> Unit
) {
    private val dp get() = activity.resources.displayMetrics.density
    private val manager = AppWidgetManager.getInstance(activity)
    private val touchSlop by lazy { ViewConfiguration.get(activity).scaledTouchSlop }
    private val tokens: NexusColorTokens
        get() = try {
            ThemeObserver.currentTokens(activity)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }

    fun build(item: HomeScreenItem, mosaicView: LivingMosaicView): LinearLayout {
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        content.addView(TextView(activity).apply {
            text = activity.getString(com.nexus.launcher.R.string.mosaic_manager_hint)
            NexusTypeScale.caption.bindTo(this, tokens.textSecondary)
            setPadding(0, 0, 0, (12 * dp).toInt())
        })
        val listHost = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val scroll = ScrollView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (280 * dp).toInt()
            )
            addView(listHost)
        }
        content.addView(scroll)

        fun rebuild() {
            listHost.removeAllViews()
            val children = getDraft().currentChildren()
            val realChildren = children.withIndex().filter { it.value.appWidgetId != -1 }
            realChildren.forEach { indexed ->
                listHost.addView(rowFor(indexed.value, indexed.index, item, mosaicView, scroll) { rebuild() })
            }
            listHost.addView(actionButton("+ " + activity.getString(com.nexus.launcher.R.string.mosaic_add_widget)) {
                onPickerVisibilityChanged(true)
                appWidgetController.launchWidgetPickerForMosaic(
                    mosaic = item,
                    onWidgetAdded = { widgetId, pkg, cls ->
                        val cfg = getDraft()
                        val currentKids = cfg.currentChildren().toMutableList()
                        currentKids.add(MosaicChild(widgetId, pkg, cls))
                        val updated = cfg.withCurrentChildren(currentKids)
                        setDraft(updated)
                        mosaicView.pulseAndRelayout(updated)
                        onConfigChanged(updated)
                        rebuild()
                    },
                    onPickerDismissed = {
                        onPickerVisibilityChanged(false)
                        rebuild()
                    }
                )
            })
        }
        rebuild()
        return content
    }

    private fun actionButton(label: String, onClick: (TextView) -> Unit) = TextView(activity).apply {
        text = label
        NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            setColor(tokens.surfaceRaised)
            cornerRadius = 14 * dp
            setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
        }
        setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = (12 * dp).toInt() }
        setOnClickListener { onClick(this) }
    }

    private fun rowFor(
        child: MosaicChild,
        index: Int,
        item: HomeScreenItem,
        mosaicView: LivingMosaicView,
        scroll: ScrollView,
        onChanged: () -> Unit
    ): LinearLayout {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(tokens.surfaceRaised)
                cornerRadius = 14 * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
            }
            setPadding((8 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (8 * dp).toInt() }
            tag = index
        }
        val handle = TextView(activity).apply {
            text = "⠿"
            textSize = 22f
            setTextColor(tokens.textSecondary)
            setPadding((8 * dp).toInt(), (4 * dp).toInt(), (12 * dp).toInt(), (4 * dp).toInt())
        }
        row.addView(handle)
        row.addView(TextView(activity).apply {
            text = resolveLabel(child)
            NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(heroButton(index == 0) {
            if (index != 0) {
                LivingMosaicHaptics.confirm(it)
                promoteHero(item, mosaicView, index)
                onChanged()
            }
        })
        row.addView(iconButton(R.drawable.ic_settings, activity.getString(com.nexus.launcher.R.string.mosaic_child_title)) {
            LivingMosaicHaptics.click(it)
            val isNexusOwned = child.providerPackage == activity.packageName
            MosaicChildStyleSheet(activity, child, isNexusOwned = isNexusOwned, onPreview = { styledChild ->
                mosaicView.previewChildStyle(index, styledChild)
                previewNexusWidgetStyle(child, styledChild, getDraft())
            }) { styledChild ->
                applyChildStyle(item, mosaicView, index, styledChild)
                applyNexusWidgetStyle(child, styledChild, getDraft())
                onChanged()
            }.show()
        })
        row.addView(iconButton(R.drawable.ic_close, activity.getString(com.nexus.launcher.R.string.mosaic_remove_widget_title)) {
            LivingMosaicHaptics.click(it)
            showRemoveChoice(item, mosaicView, index, it, onChanged)
        })
        MosaicWidgetReorderHelper.wireReorder(
            handle = handle,
            row = row,
            index = index,
            item = item,
            mosaicView = mosaicView,
            scroll = scroll,
            dp = dp,
            touchSlop = touchSlop
        ) { from, to ->
            persistReorder(item, mosaicView, from, to)
            onChanged()
        }
        return row
    }

    private fun iconButton(iconRes: Int, description: String, onClick: (View) -> Unit) = ImageView(activity).apply {
        setImageResource(iconRes)
        contentDescription = description
        imageTintList = android.content.res.ColorStateList.valueOf(tokens.textPrimary)
        setPadding((9 * dp).toInt(), (7 * dp).toInt(), (9 * dp).toInt(), (7 * dp).toInt())
        layoutParams = LinearLayout.LayoutParams((42 * dp).toInt(), (42 * dp).toInt())
        setOnClickListener { onClick(this) }
    }

    private fun heroButton(isHero: Boolean, onClick: (TextView) -> Unit) = TextView(activity).apply {
        text = if (isHero) "★" else "☆"
        contentDescription = activity.getString(if (isHero) R.string.content_desc_current_hero_widget else R.string.content_desc_make_hero_widget)
        textSize = 24f
        gravity = Gravity.CENTER
        minWidth = (48 * dp).toInt()
        setTextColor(if (isHero) tokens.textPrimary else tokens.textSecondary)
        setOnClickListener { onClick(this) }
    }

    private fun promoteHero(item: HomeScreenItem, mosaicView: LivingMosaicView, index: Int) {
        val cfg = getDraft()
        val list = cfg.currentChildren().toMutableList()
        val hero = list.removeAt(index)
        list.add(0, hero)
        val updated = cfg.updateCurrentPage { it.copy(children = list) }
        setDraft(updated)
        mosaicView.pulseAndRelayout(updated)
        onConfigChanged(updated)
    }

    private fun persistReorder(item: HomeScreenItem, mosaicView: LivingMosaicView, from: Int, to: Int) {
        val cfg = getDraft()
        val list = cfg.currentChildren().toMutableList()
        if (from in list.indices && to in list.indices) {
            val moved = list.removeAt(from)
            list.add(to, moved)
            val updated = cfg.updateCurrentPage { it.copy(children = list) }
            setDraft(updated)
            onConfigChanged(updated)
        }
    }

    private fun showRemoveChoice(
        item: HomeScreenItem,
        mosaicView: LivingMosaicView,
        index: Int,
        anchor: View,
        onChanged: () -> Unit
    ) {
        val child = getDraft().currentChildren().getOrNull(index) ?: return
        val hsv = androidx.lifecycle.ViewModelProvider(activity)[com.nexus.launcher.ui.HomeScreenViewModel::class.java]
        val canRestore = hsv.canRestoreMosaicWidget(item, child.spanX, child.spanY)
        MosaicWidgetRemovalDialog(
            activity = activity,
            canRestore = canRestore,
            onRestore = {
                val cfg = getDraft()
                val nextKids = cfg.currentChildren().toMutableList().also { it.removeAt(index) }
                val nextConfig = cfg.updateCurrentPage { it.copy(children = nextKids) }
                setDraft(nextConfig)
                mosaicView.pulseAndRelayout(nextConfig)
                onConfigChanged(nextConfig)
                hsv.restoreMosaicWidgetToHome(item, child, nextConfig)
                onChanged()
            },
            onDelete = {
                if (child.appWidgetId != -1) {
                    try { appWidgetHost.deleteAppWidgetId(child.appWidgetId) } catch (_: Exception) {}
                }
                removeSlot(item, mosaicView, index)
                onChanged()
            }
        ).show()
    }

    private fun removeSlot(item: HomeScreenItem, mosaicView: LivingMosaicView, index: Int) {
        val cfg = getDraft()
        val list = cfg.currentChildren().toMutableList()
        if (index in list.indices) {
            list.removeAt(index)
            val updated = cfg.updateCurrentPage { it.copy(children = list) }
            setDraft(updated)
            mosaicView.pulseAndRelayout(updated)
            onConfigChanged(updated)
        }
    }

    private fun applyChildStyle(item: HomeScreenItem, mosaicView: LivingMosaicView, index: Int, styledChild: MosaicChild) {
        val cfg = getDraft()
        val list = cfg.currentChildren().toMutableList()
        if (index in list.indices) {
            list[index] = styledChild
            val updated = cfg.updateCurrentPage { it.copy(children = list) }
            setDraft(updated)
            mosaicView.pulseAndRelayout(updated)
            onConfigChanged(updated)
        }
    }

    /**
     * BG_INHERIT has no meaning to NexusWidgetConfig/NexusWidgetRenderer (their vocabulary is
     * GLASS/FROSTED/SOLID only) — resolve it to the parent Mosaic's own live appearance before
     * writing, instead of letting it fall through to the renderer's GLASS default.
     *
     * NexusWidgetRenderer only paints `backgroundMode` at all when `isExpressive == true` — when
     * false it unconditionally paints the plain theme surface color (dark), ignoring whatever
     * background was picked. MosaicChildStyleSheet's picker has no separate Expressive toggle of
     * its own — choosing anything other than Inherit here IS the expressive choice, so it must
     * force isExpressive = true or the pick is silently discarded by the renderer.
     */
    private fun resolvedNexusStyle(
        current: NexusWidgetConfig.InstanceConfig,
        styledChild: MosaicChild,
        mosaicConfig: MosaicConfig
    ): NexusWidgetConfig.InstanceConfig {
        val inherit = styledChild.backgroundMode == MosaicConfig.BG_INHERIT
        return current.copy(
            isExpressive = if (inherit) mosaicConfig.isExpressive else true,
            backgroundMode = if (inherit) mosaicConfig.backgroundMode else styledChild.backgroundMode,
            frostedGradientIndex = if (inherit) mosaicConfig.frostedGradientIndex else styledChild.frostedGradientIndex,
            backgroundOpacity = styledChild.backgroundOpacity
        )
    }

    private fun previewNexusWidgetStyle(child: MosaicChild, styledChild: MosaicChild, mosaicConfig: MosaicConfig) {
        val widgetId = child.appWidgetId
        if (widgetId == -1) return
        val original = NexusWidgetConfig.read(activity, widgetId)
        NexusWidgetConfig.write(activity, resolvedNexusStyle(original, styledChild, mosaicConfig))
        notifyWidgetUpdated(widgetId)
    }

    private fun applyNexusWidgetStyle(child: MosaicChild, styledChild: MosaicChild, mosaicConfig: MosaicConfig) {
        val widgetId = child.appWidgetId
        if (widgetId == -1) return
        val current = NexusWidgetConfig.read(activity, widgetId)
        NexusWidgetConfig.write(activity, resolvedNexusStyle(current, styledChild, mosaicConfig))
        notifyWidgetUpdated(widgetId)
    }

    private fun notifyWidgetUpdated(widgetId: Int) {
        val intent = Intent("com.nexus.launcher.ACTION_NEXUS_WIDGET_CONFIG_CHANGED")
        intent.putExtra("appWidgetId", widgetId)
        intent.setPackage(activity.packageName)
        activity.sendBroadcast(intent)
    }

    private fun resolveLabel(child: MosaicChild): String {
        if (child.appWidgetId == -1) return activity.getString(com.nexus.launcher.R.string.mosaic_empty_slot)
        val info = manager.getAppWidgetInfo(child.appWidgetId)
        return info?.loadLabel(activity.packageManager) ?: activity.getString(com.nexus.launcher.R.string.mosaic_widget_number, child.appWidgetId)
    }
}
