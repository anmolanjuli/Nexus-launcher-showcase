package com.nexus.launcher.ui.folder

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.typography.NexusTypeScale

/**
 * Unified snap carousel for folder shapes — Classic + Styled in one row with inline section labels.
 */
class FolderShapeThumbnailRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    var onValueChanged: ((String) -> Unit)? = null

    private val dp = resources.displayMetrics.density
    private val tileWidth = (76 * dp).toInt()
    private val iconSize = (52 * dp).toInt()
    private val tileGap = (8 * dp).toInt()

    private val scroll = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        clipToPadding = false
        clipChildren = false
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
    }
    private val track = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        clipChildren = false
        clipToPadding = false
        val vPad = (6 * dp).toInt()
        setPadding(0, vPad, 0, vPad)
    }
    private val startSpacer = View(context)
    private val endSpacer = View(context)

    private val shapeTiles = mutableListOf<View>()
    private val tilesById = mutableMapOf<String, LinearLayout>()
    private var selectedValue = ""
    private var isProgrammaticScroll = false
    private var isUserTouching = false
    private var hasUserScrolled = false

    private val snapRunnable = Runnable { snapToNearest() }

    init {
        orientation = VERTICAL
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        val hPad = (16 * dp).toInt()
        val topPad = (10 * dp).toInt()
        setPadding(hPad, topPad, hPad, (4 * dp).toInt())
        clipChildren = false
        clipToPadding = false

        addView(TextView(context).apply {
            text = context.getString(R.string.folder_shape_row_title)
            gravity = Gravity.START
            textAlignment = TEXT_ALIGNMENT_VIEW_START
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (6 * dp).toInt()
            }
            tag = "shape_row_title"
        })

        scroll.addView(track, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ))
        scroll.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    isUserTouching = true
                    hasUserScrolled = true
                    isProgrammaticScroll = false
                    removeCallbacks(snapRunnable)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isUserTouching = false
                    postDelayed(snapRunnable, 100)
                }
            }
            false
        }
        scroll.setOnScrollChangeListener { _, scrollX, _, _, _ ->
            FolderShapeCarouselSnap.applyFocusScaling(shapeTiles, scrollX, scroll.width)
            if (isUserTouching) hasUserScrolled = true
            if (!isUserTouching && !isProgrammaticScroll && hasUserScrolled) {
                removeCallbacks(snapRunnable)
                postDelayed(snapRunnable, 80)
            }
        }
        addView(scroll)
    }

    @Suppress("UNUSED_PARAMETER")
    fun applyAccentColor(color: Int) {
        if (selectedValue.isNotEmpty()) refreshTileArt()
    }

    fun configure(entries: List<FolderShapeCarouselEntry>, initialValue: String) {
        selectedValue = initialValue
        track.removeAllViews()
        shapeTiles.clear()
        tilesById.clear()
        removeCallbacks(snapRunnable)

        track.addView(startSpacer, LinearLayout.LayoutParams(0, 1))

        var firstShape = true
        entries.forEach { entry ->
            when (entry) {
                is FolderShapeCarouselEntry.Section -> {
                    track.addView(buildSectionHeader(entry.title))
                    firstShape = true
                }
                is FolderShapeCarouselEntry.Shape -> {
                    val tile = buildShapeTile(entry.id, entry.title).apply {
                        val lp = layoutParams as LinearLayout.LayoutParams
                        if (!firstShape) lp.marginStart = tileGap
                        firstShape = false
                    }
                    tilesById[entry.id] = tile
                    shapeTiles.add(tile)
                    track.addView(tile)
                }
            }
        }

        track.addView(endSpacer, LinearLayout.LayoutParams(0, 1))
        refreshTileArt()
        isProgrammaticScroll = true
        post {
            updateSpacers()
            scrollToShape(selectedValue, smooth = false)
            FolderShapeCarouselSnap.applyFocusScaling(shapeTiles, scroll.scrollX, scroll.width)
            postDelayed({ isProgrammaticScroll = false }, 120)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0) return
        post {
            updateSpacers()
            if (selectedValue.isNotEmpty()) {
                isProgrammaticScroll = true
                scrollToShape(selectedValue, smooth = false)
                postDelayed({ isProgrammaticScroll = false }, 120)
            }
        }
    }

    private fun updateSpacers() {
        if (scroll.width <= 0 || shapeTiles.isEmpty()) return
        val tile = shapeTiles.first()
        FolderShapeCarouselSnap.updateSideSpacers(
            startSpacer, endSpacer, scroll.width, tile.width, tileGap
        )
    }

    private val tokens: NexusColorTokens
        get() = try {
            com.nexus.launcher.theme.ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }

    private fun buildSectionHeader(title: String): TextView {
        return TextView(context).apply {
            text = title
            NexusTypeScale.caption.bindTo(this, tokens.textSecondary)
            gravity = Gravity.BOTTOM or Gravity.START
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                marginStart = (4 * dp).toInt()
                marginEnd = (2 * dp).toInt()
            }
            setPadding(0, 0, 0, (10 * dp).toInt())
        }
    }

    private fun buildShapeTile(value: String, title: String): LinearLayout {
        return LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(tileWidth, ViewGroup.LayoutParams.WRAP_CONTENT)
            val padH = (6 * dp).toInt()
            val padV = (8 * dp).toInt()
            setPadding(padH, padV, padH, padV)
            isClickable = true
            isFocusable = true
            tag = value
            setOnClickListener { onShapeTapped(value) }
            addView(ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(iconSize, iconSize).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    bottomMargin = (5 * dp).toInt()
                }
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
                tag = "silhouette"
            })
            addView(TextView(context).apply {
                text = title
                NexusTypeScale.caption.bindTo(this)
                gravity = Gravity.CENTER
                maxLines = 1
                tag = "label"
            })
        }
    }

    private fun onShapeTapped(value: String) {
        removeCallbacks(snapRunnable)
        isProgrammaticScroll = true
        hasUserScrolled = false
        if (selectedValue != value) {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            selectedValue = value
            refreshTileArt()
            onValueChanged?.invoke(value)
        }
        scrollToShape(value, smooth = true)
        postDelayed({ isProgrammaticScroll = false }, 320)
    }

    private fun refreshTileArt() {
        val t = tokens
        tilesById.forEach { (value, tile) ->
            val selected = value == selectedValue
            tile.background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 14 * dp
                setColor(Color.TRANSPARENT)
                if (selected) {
                    setStroke((1 * dp).toInt().coerceAtLeast(1), t.divider)
                } else {
                    setStroke(0, Color.TRANSPARENT)
                }
            }
            val image = tile.findViewWithTag<ImageView>("silhouette")
            val label = tile.findViewWithTag<TextView>("label")
            image?.setImageBitmap(
                FolderShapeThumbnailDraw.render(value.toIntOrNull() ?: 0, iconSize, selected, t, dp)
            )
            label?.setTextColor(if (selected) t.textPrimary else t.textSecondary)
        }
        findViewWithTag<TextView>("shape_row_title")?.let { title ->
            NexusTypeScale.body.bindTo(title, t.textPrimary)
        }
    }

    private fun scrollToShape(value: String, smooth: Boolean) {
        val tile = tilesById[value] ?: return
        if (scroll.width <= 0) return
        val targetX = FolderShapeCarouselSnap.scrollXToCenter(tile, scroll.width)
        if (smooth) scroll.smoothScrollTo(targetX, 0) else scroll.scrollTo(targetX, 0)
    }

    private fun snapToNearest() {
        if (scroll.width <= 0 || isProgrammaticScroll || shapeTiles.isEmpty()) return
        val nearest = FolderShapeCarouselSnap.nearestShapeTile(shapeTiles, scroll.scrollX, scroll.width) ?: return
        val id = nearest.tag as? String ?: return
        isProgrammaticScroll = true
        if (selectedValue != id) {
            performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            selectedValue = id
            refreshTileArt()
            onValueChanged?.invoke(id)
        }
        hasUserScrolled = false
        scrollToShape(id, smooth = true)
        postDelayed({ isProgrammaticScroll = false }, 320)
    }
}
