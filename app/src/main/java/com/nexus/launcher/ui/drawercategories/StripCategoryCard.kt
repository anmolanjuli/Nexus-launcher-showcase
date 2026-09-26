package com.nexus.launcher.ui.drawercategories

import android.content.Context
import android.content.res.ColorStateList
import android.view.Gravity
import android.view.MotionEvent
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.nexus.launcher.R
import com.nexus.launcher.typography.NexusTypeScale

class StripCategoryCard(context: Context) : LinearLayout(context) {
    private val palette = CategoryPalette(context)
    private val header = LinearLayout(context)
    private val badge = CategoryBadge(context)
    private val title = TextView(context)
    private val seeAll = TextView(context)
    private val expand = ImageView(context)
    private val seeAllRow = LinearLayout(context)
    private val row = LinearLayout(context)
    private val stripScroller: HorizontalScrollView
    private val expandedScroller: HorizontalScrollView
    private val expandedHost = LinearLayout(context)
    private var expanded = false
    private var boundApps: List<CategoryApp> = emptyList()
    private var boundClick: ((CategoryApp) -> Unit)? = null
    private var boundLongPress: ((CategoryApp, android.view.View) -> Unit)? = null

    init {
        orientation = VERTICAL
        val density = resources.displayMetrics.density
        val pad = (14f * density).toInt()
        setPadding(pad, (12f * density).toInt(), pad, (10f * density).toInt())
        background = CategorySurfaces.card(palette, density, 22f)
        header.orientation = HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.isBaselineAligned = false
        badge.layoutParams = LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT,
        ).apply { marginEnd = (10f * density).toInt() }
        title.layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        seeAllRow.orientation = HORIZONTAL
        seeAllRow.gravity = Gravity.CENTER_VERTICAL
        seeAllRow.isBaselineAligned = false
        val chevronSize = (22f * density).toInt()
        expand.layoutParams = LayoutParams(chevronSize, chevronSize)
        expand.scaleType = ImageView.ScaleType.CENTER_INSIDE
        expand.imageTintList = ColorStateList.valueOf(palette.textSecondary)
        seeAllRow.addView(seeAll)
        seeAllRow.addView(expand)
        seeAllRow.setOnClickListener { toggleExpanded() }
        header.addView(badge)
        header.addView(title)
        header.addView(seeAllRow)
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        row.orientation = HORIZONTAL
        row.isBaselineAligned = false
        stripScroller = horizontalScroller(row)
        addView(stripScroller, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = (10f * density).toInt()
        })
        expandedScroller = horizontalScroller(expandedHost)
        addView(expandedScroller, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = (10f * density).toInt()
        })
    }

    fun bind(
        category: CategoryGroup?,
        apps: List<CategoryApp>,
        onAppClick: ((CategoryApp) -> Unit)? = null,
        onAppLongPress: ((CategoryApp, android.view.View) -> Unit)? = null,
        showHeader: Boolean = true,
        showExpand: Boolean = true,
    ) {
        boundApps = apps
        boundClick = onAppClick
        boundLongPress = onAppLongPress
        header.visibility = if (showHeader && category != null) VISIBLE else GONE
        seeAllRow.visibility = if (showExpand && apps.size > 4) VISIBLE else GONE
        if (category != null) {
            badge.bind(
                category.key, palette, selected = true, sizeDp = 28f,
                iconResOverride = category.badgeIcon,
            )
            NexusTypeScale.sectionLabel.bindTo(title, palette.textSecondary)
            title.text = category.name
        }
        NexusTypeScale.caption.bindTo(seeAll, palette.textSecondary)
        expanded = false
        applyExpandedState()
    }

    private fun toggleExpanded() {
        expanded = !expanded
        applyExpandedState()
    }

    private fun applyExpandedState() {
        val down = ContextCompat.getDrawable(context, R.drawable.ic_chevron_down)
        val up = ContextCompat.getDrawable(context, R.drawable.ic_chevron_up)
        expand.setImageDrawable(if (expanded) up else down)
        seeAll.text = context.getString(
            if (expanded) R.string.drawer_category_see_less else R.string.drawer_category_see_all
        )
        seeAllRow.contentDescription = seeAll.text
        stripScroller.visibility = if (expanded) GONE else VISIBLE
        expandedScroller.visibility = if (expanded) VISIBLE else GONE
        if (expanded) {
            val bindGrid = {
                val viewport = expandedScroller.width.takeIf { it > 0 }
                    ?: (width - paddingLeft - paddingRight).coerceAtLeast(1)
                CategoryAppGrid.bindColumnMajor(
                    expandedHost, boundApps, viewport, maxRows = 3, iconDp = 44f, showLabel = true,
                    lightPlate = true, onAppClick = boundClick, onAppLongPress = boundLongPress,
                )
            }
            if (expandedScroller.width == 0) expandedScroller.post { bindGrid() } else bindGrid()
        } else {
            CategoryAppGrid.bindScrollRow(
                row, boundApps, iconDp = 44f, showLabel = true, lightPlate = true,
                onAppClick = boundClick, onAppLongPress = boundLongPress,
            )
        }
    }

    /** A faded edge is the cue that a card has more icons than its width can hold. */
    private fun markScrollable(scroller: HorizontalScrollView) {
        scroller.isHorizontalFadingEdgeEnabled = true
        scroller.setFadingEdgeLength((28f * resources.displayMetrics.density).toInt())
    }

    private fun horizontalScroller(child: android.view.View): HorizontalScrollView {
        return HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
            markScrollable(this)
            clipToPadding = false
            addView(child, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
            var startX = 0f
            var startY = 0f
            setOnTouchListener { view, event ->
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        startX = event.x
                        startY = event.y
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (kotlin.math.abs(event.x - startX) > kotlin.math.abs(event.y - startY)) {
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                        }
                    }
                }
                false
            }
        }
    }
}
