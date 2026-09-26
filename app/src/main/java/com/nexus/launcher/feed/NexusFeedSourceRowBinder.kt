package com.nexus.launcher.feed

import android.content.ClipData
import android.content.ClipDescription
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.DragEvent
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.data.FeedSource
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.settings.views.NexusElasticSwitch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object NexusFeedSourceRowBinder {

    fun buildSourceRow(
        context: Context,
        source: FeedSource,
        dp: Float,
        scope: CoroutineScope,
        sourceManager: NexusFeedSourceManager,
        sourcesContainer: LinearLayout,
        tokens: NexusColorTokens = if (NexusFeedEInkCoordinator.isEInkMode(context)) NexusFeedEInkCoordinator.getTokens(context) else try { ThemeObserver.currentTokens(context) } catch (_: Exception) { NexusColorTokens.Dark },
        isEInk: Boolean = NexusFeedEInkCoordinator.isEInkMode(context),
        onDragStateChanged: (Boolean) -> Unit,
        onReorderCommitted: (List<Int>) -> Unit
    ): View {
        val row = LinearLayout(context).apply {
            tag = source.id
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
        }

        val avatar = TextView(context).apply {
            text = NexusFeedImageLoader.getMonogram(source.title)
            typeface = if (isEInk) android.graphics.Typeface.MONOSPACE else null
            NexusTypeScale.labelSmall.bindTo(this, tokens.textPrimary)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(tokens.surfaceRaised)
                if (isEInk) {
                    cornerRadius = 0f
                    setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
                } else {
                    shape = GradientDrawable.OVAL
                }
            }
            layoutParams = LinearLayout.LayoutParams((45 * dp).toInt(), (45 * dp).toInt()).apply {
                marginEnd = (12 * dp).toInt()
            }
            setupDragHandle(this, row, source, sourcesContainer, onDragStateChanged, onReorderCommitted)
        }
        row.addView(avatar)

        val middle = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(context).apply {
                text = source.title
                typeface = if (isEInk) android.graphics.Typeface.MONOSPACE else null
                NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
                isSingleLine = true
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            })
            addView(TextView(context).apply {
                text = source.category
                typeface = if (isEInk) android.graphics.Typeface.MONOSPACE else null
                NexusTypeScale.caption.bindTo(this, tokens.textSecondary)
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = (2 * dp).toInt()
                }
            })
        }
        row.addView(middle)

        // Enable/disable toggle — the app's own switch, matching every Settings toggle.
        val toggle = NexusElasticSwitch(context).apply {
            paperMode = NexusFeedEInkCoordinator.isEInkMode(context)
            applyTokens(tokens)
            setChecked(source.isEnabled, animate = false)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginEnd = (8 * dp).toInt()
            }
            onCheckedChange = { isChecked ->
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                scope.launch { sourceManager.toggleSource(source, isChecked) }
            }
        }
        row.addView(toggle)

        val deleteBtn = ImageView(context).apply {
            setImageResource(R.drawable.ic_delete)
            imageTintList = ColorStateList.valueOf(tokens.textSecondary)
            val btnSize = (36 * dp).toInt()
            val pad = (5 * dp).toInt()
            layoutParams = LinearLayout.LayoutParams(btnSize, btnSize)
            setPadding(pad, pad, pad, pad)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                scope.launch { sourceManager.removeSource(source) }
            }
        }
        row.addView(deleteBtn)
        return row
    }

    private fun setupDragHandle(
        handleView: View,
        rowView: View,
        source: FeedSource,
        sourcesContainer: LinearLayout,
        onDragStateChanged: (Boolean) -> Unit,
        onReorderCommitted: (List<Int>) -> Unit
    ) {
        handleView.setOnTouchListener { v, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                val item = ClipData.Item(source.id.toString())
                val data = ClipData("FEED_SOURCE_REORDER", arrayOf(ClipDescription.MIMETYPE_TEXT_PLAIN), item)
                val touchX = event.x + handleView.left
                val touchY = event.y + handleView.top
                val shadow = object : View.DragShadowBuilder(rowView) {
                    override fun onProvideShadowMetrics(outShadowSize: android.graphics.Point, outShadowTouchPoint: android.graphics.Point) {
                        val view = view ?: return
                        outShadowSize.set(view.width, view.height)
                        outShadowTouchPoint.set(touchX.toInt().coerceIn(0, view.width), touchY.toInt().coerceIn(0, view.height))
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    rowView.startDragAndDrop(data, shadow, rowView, 0)
                } else {
                    @Suppress("DEPRECATION")
                    rowView.startDrag(data, shadow, rowView, 0)
                }
                true
            } else {
                false
            }
        }

        rowView.setOnDragListener { targetView, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> {
                    onDragStateChanged(true)
                    if (event.localState == rowView) rowView.alpha = 0.6f
                    true
                }
                DragEvent.ACTION_DRAG_ENTERED -> {
                    val draggedView = event.localState as? View ?: return@setOnDragListener true
                    if (draggedView != targetView) {
                        val fromIdx = sourcesContainer.indexOfChild(draggedView)
                        val toIdx = sourcesContainer.indexOfChild(targetView)
                        if (fromIdx != -1 && toIdx != -1 && fromIdx != toIdx) {
                            sourcesContainer.removeView(draggedView)
                            sourcesContainer.addView(draggedView, toIdx)
                            targetView.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                    }
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> {
                    rowView.alpha = 1.0f
                    onDragStateChanged(false)
                    val newIds = (0 until sourcesContainer.childCount).mapNotNull {
                        sourcesContainer.getChildAt(it).tag as? Int
                    }
                    if (newIds.isNotEmpty()) {
                        onReorderCommitted(newIds)
                    }
                    true
                }
                else -> true
            }
        }
    }
}
