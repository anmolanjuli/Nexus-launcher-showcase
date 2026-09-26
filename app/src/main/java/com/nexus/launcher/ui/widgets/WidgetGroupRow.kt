package com.nexus.launcher.ui.widgets

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale

class WidgetGroupRow(
    context: Context,
    private val group: WidgetAppGroup,
    private val onWidgetSelected: (WidgetAppGroup, WidgetProviderEntry) -> Unit,
    private val onWidgetLongPressed: ((View, WidgetAppGroup, WidgetProviderEntry) -> Unit)? = null,
    startExpanded: Boolean = false,
    showHeader: Boolean = true,
    private val spanOf: ((WidgetProviderEntry) -> Pair<Int, Int>)? = null
) : LinearLayout(context) {

    private val dp = resources.displayMetrics.density
    private var expanded = startExpanded
    private val widgetsContainer: View
    private val tokens: NexusColorTokens = try {
        ThemeObserver.currentTokens(context)
    } catch (_: Exception) {
        NexusColorTokens.Dark
    }

    init {
        orientation = VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            setMargins((16 * dp).toInt(), (4 * dp).toInt(), (16 * dp).toInt(), (4 * dp).toInt())
        }

        background = com.nexus.launcher.ui.glass.NeumorphicSurfaces.raisedOr(this, tokens, 14 * dp) {
            GradientDrawable().apply {
                setColor(tokens.surfaceRaised)
                cornerRadius = 14 * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
            }
        }

        widgetsContainer = if (!showHeader) buildGridContainer() else buildScrollContainer(startExpanded)

        if (showHeader) {
            val chevron = ImageView(context).apply {
                setImageResource(if (expanded) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down)
                imageTintList = android.content.res.ColorStateList.valueOf(tokens.textSecondary)
                layoutParams = LayoutParams((20 * dp).toInt(), (20 * dp).toInt())
            }
            val countBadge = TextView(context).apply {
                text = "${group.widgets.size}"
                layoutParams = LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = (8 * dp).toInt() }
                background = GradientDrawable().apply {
                    setColor(tokens.surface)
                    cornerRadius = 8 * dp
                    setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
                }
                setPadding((8 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt())
                NexusTypeScale.labelSmall.bindTo(this, tokens.textSecondary)
                gravity = Gravity.CENTER
            }

            val header = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt())
                background = getRippleDrawable()
                isClickable = true
                isFocusable = true

                addView(ImageView(context).apply {
                    setImageDrawable(group.appIcon)
                    layoutParams = LayoutParams((32 * dp).toInt(), (32 * dp).toInt())
                })
                addView(TextView(context).apply {
                    text = group.appLabel
                    NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                        setMargins((12 * dp).toInt(), 0, (8 * dp).toInt(), 0)
                    }
                })
                addView(countBadge)
                addView(chevron)
                setOnClickListener {
                    expanded = !expanded
                    widgetsContainer.visibility = if (expanded) View.VISIBLE else View.GONE
                    chevron.setImageResource(if (expanded) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down)
                }
            }
            addView(header)
        } else {
            expanded = true
            widgetsContainer.visibility = View.VISIBLE
        }
        addView(widgetsContainer)
    }

    private fun isFullWidthWidget(entry: WidgetProviderEntry): Boolean =
        entry.info?.provider?.className?.contains("NexusSearchWidgetProvider") == true ||
        entry.nexusKind == "search"

    private fun buildGridContainer(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = VERTICAL
            visibility = View.VISIBLE
            setPadding((8 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

            val (wideWidgets, gridWidgets) = group.widgets.partition { isFullWidthWidget(it) }

            for (widget in wideWidgets) {
                addView(createWidgetCard(widget, isGrid = false, isWide = true))
            }

            for (rowWidgets in gridWidgets.chunked(3)) {
                val row = LinearLayout(context).apply {
                    orientation = HORIZONTAL
                    layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                        bottomMargin = (8 * dp).toInt()
                    }
                }
                for (widget in rowWidgets) {
                    row.addView(createWidgetCard(widget, isGrid = true))
                }
                repeat(3 - rowWidgets.size) {
                    row.addView(View(context).apply {
                        layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
                    })
                }
                addView(row)
            }
        }
    }

    private fun buildScrollContainer(startExpanded: Boolean): HorizontalScrollView {
        return HorizontalScrollView(context).apply {
            visibility = if (startExpanded) View.VISIBLE else View.GONE
            isHorizontalScrollBarEnabled = false
            setPadding((12 * dp).toInt(), 0, (12 * dp).toInt(), (12 * dp).toInt())
            clipToPadding = false
            val cardsRow = LinearLayout(context).apply {
                orientation = HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            for (widget in group.widgets) {
                cardsRow.addView(createWidgetCard(widget, isGrid = false))
            }
            addView(cardsRow)
        }
    }

    private fun getRippleDrawable(): android.graphics.drawable.Drawable? {
        val outValue = TypedValue()
        context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
        return context.getDrawable(outValue.resourceId)
    }

    private fun createWidgetCard(
        entry: WidgetProviderEntry,
        isGrid: Boolean = false,
        isWide: Boolean = false
    ): View {
        return LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isClickable = true
            isFocusable = true
            setPadding((8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
            layoutParams = when {
                isWide -> LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = (8 * dp).toInt()
                }
                isGrid -> LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins((4 * dp).toInt(), 0, (4 * dp).toInt(), 0)
                }
                else -> LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    setMargins(0, 0, (12 * dp).toInt(), 0)
                }
            }
            // Neumorphism: each preview sits in a well pressed into the group's raised card. Sunken,
            // not raised — tiles can sit in a horizontal scroller, which would crop a shadow.
            background = com.nexus.launcher.ui.glass.NeumorphicSurfaces.sunkenOr(this, tokens, 12 * dp) {
                GradientDrawable().apply {
                    setColor(tokens.surface)
                    cornerRadius = 12 * dp
                    setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
                }
            }

            val isNexusWidget = group.packageName == context.packageName || entry.nexusKind != null
            val previewView = ImageView(context).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                when {
                    isWide -> {
                        // Full-width pill preview (search bar) — wide bitmap so radius = h/2 looks like a pill
                        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, (60 * dp).toInt())
                        val livePreview = if (isNexusWidget) {
                            NexusWidgetPreviewCache.getPreview(context, entry, 300 * dp, 60 * dp)
                        } else null
                        if (livePreview != null) setImageBitmap(livePreview)
                        else if (entry.previewBitmap != null) setImageBitmap(entry.previewBitmap)
                        else setImageDrawable(group.appIcon)
                    }
                    isGrid -> {
                        val isPerf = isNexusWidget && (entry.info?.provider?.className?.contains("NexusPerformanceWidgetProvider") == true || entry.nexusKind == "performance")
                        val cardHeight = if (isPerf) (110 * dp).toInt() else (90 * dp).toInt()
                        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, cardHeight)
                        val targetW = if (isPerf) (180 * dp) else (100 * dp)
                        val targetH = if (isPerf) (110 * dp) else (90 * dp)
                        val livePreview = if (isNexusWidget) {
                            NexusWidgetPreviewCache.getPreview(context, entry, targetW, targetH)
                        } else null
                        if (livePreview != null) setImageBitmap(livePreview)
                        else if (entry.previewBitmap != null) setImageBitmap(entry.previewBitmap)
                        else setImageDrawable(group.appIcon)
                    }
                    else -> {
                        val maxH = 110 * dp
                        val maxW = 180 * dp
                        var targetW = (entry.minWidthDp * dp).coerceAtLeast(60 * dp)
                        var targetH = (entry.minHeightDp * dp).coerceAtLeast(60 * dp)
                        if (entry.info?.provider?.className == "com.nexus.launcher.search.ui.NexusSearchWidgetProvider") {
                            targetW *= 1.4f
                            targetH *= 1.4f
                        }
                        val scale = minOf(maxW / targetW, maxH / targetH, 1f)
                        layoutParams = LayoutParams((targetW * scale).toInt(), (targetH * scale).toInt())
                        val livePreview = if (isNexusWidget) {
                            NexusWidgetPreviewCache.getPreview(context, entry, targetW * scale, targetH * scale)
                        } else null
                        if (livePreview != null) setImageBitmap(livePreview)
                        else if (entry.previewBitmap != null) setImageBitmap(entry.previewBitmap)
                        else setImageDrawable(group.appIcon)
                    }
                }
            }
            addView(previewView)

            addView(TextView(context).apply {
                text = entry.label
                NexusTypeScale.caption.bindTo(this, tokens.textPrimary)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    topMargin = (6 * dp).toInt()
                }
                gravity = if (isGrid && !isWide) Gravity.CENTER_HORIZONTAL else Gravity.START
            })

            addView(TextView(context).apply {
                text = spanOf?.invoke(entry)?.let { (sx, sy) -> "${sx}×${sy}" }
                    ?: if (entry.isLivingMosaic) {
                        val (sx, sy) = NexusWidgetKinds.mosaicSpan(entry.nexusKind)
                        "${sx}×${sy}"
                    } else {
                        "${entry.minWidthDp}×${entry.minHeightDp} dp"
                    }
                NexusTypeScale.labelSmall.bindTo(this, tokens.textSecondary)
                layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                    topMargin = (2 * dp).toInt()
                }
                gravity = if (isGrid && !isWide) Gravity.CENTER_HORIZONTAL else Gravity.START
            })

            setOnClickListener { onWidgetSelected(group, entry) }
            setOnLongClickListener { v ->
                onWidgetLongPressed?.invoke(v, group, entry)
                true
            }
        }
    }
}
