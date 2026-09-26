package com.nexus.launcher.reader.doc

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import kotlin.math.roundToInt

/**
 * Pinned horizontal row displaying recently read documents with 110dp 2:3 covers,
 * subtle drop shadow, 2-line wrapped titles, 2dp progress bars, snap-to-page, and edge gradient fades.
 */
class DocumentLibraryContinueReadingView(
    context: Context,
    private var isEInk: Boolean,
    private val onDocumentClick: (DocumentRecord) -> Unit,
    private val onDocumentLongClick: (DocumentRecord) -> Unit
) : LinearLayout(context) {

    private val dp = resources.displayMetrics.density
    private val itemsContainer = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding((16 * dp).toInt(), (4 * dp).toInt(), (16 * dp).toInt(), (4 * dp).toInt())
    }

    private val headerView: TextView
    private val scroll: HorizontalScrollView
    private val leftFadeView: View
    private val rightFadeView: View

    init {
        orientation = VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = (14 * dp).toInt()
        }

        // Section header: 14sp uppercase, letter-spacing 0.05em, textSecondary
        headerView = TextView(context).apply {
            text = context.getString(R.string.nexus_doc_continue_reading)
            textSize = 14f
            isAllCaps = true
            letterSpacing = 0.05f
            typeface = if (isEInk) Typeface.MONOSPACE else Typeface.DEFAULT_BOLD
            setPadding((16 * dp).toInt(), 0, (16 * dp).toInt(), (8 * dp).toInt())
        }
        addView(headerView)

        val scrollWrapper = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            clipToPadding = false
            addView(itemsContainer)

            // Snap-to-item behavior on release
            setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                    v.postDelayed({
                        val itemWidthPx = (74 * dp).toInt() + (16 * dp).toInt()
                        if (itemWidthPx > 0) {
                            val snapTarget = (scrollX.toFloat() / itemWidthPx).roundToInt() * itemWidthPx
                            smoothScrollTo(snapTarget.coerceAtLeast(0), 0)
                        }
                    }, 50)
                }
                false
            }
        }
        scrollWrapper.addView(scroll)

        // Left gradient fade (fades into bg)
        leftFadeView = View(context).apply {
            layoutParams = FrameLayout.LayoutParams((24 * dp).toInt(), LayoutParams.MATCH_PARENT, Gravity.START)
            isClickable = false
            isFocusable = false
        }
        scrollWrapper.addView(leftFadeView)

        // Right gradient fade (fades into bg)
        rightFadeView = View(context).apply {
            layoutParams = FrameLayout.LayoutParams((24 * dp).toInt(), LayoutParams.MATCH_PARENT, Gravity.END)
            isClickable = false
            isFocusable = false
        }
        scrollWrapper.addView(rightFadeView)

        addView(scrollWrapper)
    }

    fun bind(
        tokens: NexusColorTokens,
        documents: List<DocumentRecord>,
        coverManager: DocumentCoverManager,
        onLoadCover: (DocumentRecord, (Bitmap?) -> Unit) -> Unit
    ) {
        isEInk = com.nexus.launcher.feed.NexusFeedEInkCoordinator.isEInkMode(context)
        if (documents.isEmpty()) {
            visibility = GONE
            return
        }
        visibility = VISIBLE

        headerView.typeface = if (isEInk) Typeface.MONOSPACE else Typeface.DEFAULT_BOLD
        headerView.setTextColor(tokens.textSecondary)

        // Edge gradient fades using tokens.bg
        leftFadeView.background = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(tokens.bg, Color.TRANSPARENT)
        )
        rightFadeView.background = GradientDrawable(
            GradientDrawable.Orientation.RIGHT_LEFT,
            intArrayOf(tokens.bg, Color.TRANSPARENT)
        )

        itemsContainer.removeAllViews()
        for (doc in documents) {
            val item = buildItem(tokens, doc, coverManager, onLoadCover)
            itemsContainer.addView(item)
        }
    }

    private fun buildItem(
        tokens: NexusColorTokens,
        doc: DocumentRecord,
        coverManager: DocumentCoverManager,
        onLoadCover: (DocumentRecord, (Bitmap?) -> Unit) -> Unit
    ): View {
        val coverW = (74 * dp).toInt()
        val coverH = (110 * dp).toInt()

        return LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LayoutParams(coverW, LayoutParams.WRAP_CONTENT).apply {
                marginEnd = (16 * dp).toInt()
            }

            // Progress percent calculation
            val progressPercent = DocumentProgress.percentOf(doc)

            // 110dp tall 2:3 cover with subtle drop shadow
            val coverView = CoverProgressThumbnailView(context).apply {
                layoutParams = LayoutParams(coverW, coverH)
                ringStrokeWidthDp = 0f
                bottomProgressBarHeightDp = 0f
                title = doc.effectiveTitle
                fileType = doc.fileType
                this.progressPercent = progressPercent
                trackColor = tokens.surfaceRaised
                progressColor = tokens.accent
                isEInk = this@DocumentLibraryContinueReadingView.isEInk
                coverBitmap = coverManager.getMemoryCached(doc.uri)

                if (!this@DocumentLibraryContinueReadingView.isEInk) {
                    elevation = 4 * dp
                    outlineProvider = ViewOutlineProvider.BACKGROUND
                }
            }
            addView(coverView)

            if (coverView.coverBitmap == null && (doc.fileType == "pdf" || doc.fileType == "epub")) {
                onLoadCover(doc) { bmp ->
                    coverView.coverBitmap = bmp
                }
            }

            // Progress bar underneath: 2dp thick, accent fill, surfaceRaised track
            val progressBar = LinearLayout(context).apply {
                orientation = HORIZONTAL
                layoutParams = LayoutParams(coverW, (2 * dp).toInt().coerceAtLeast(2)).apply {
                    topMargin = (6 * dp).toInt()
                }
                background = GradientDrawable().apply {
                    setColor(tokens.surfaceRaised)
                    cornerRadius = 1 * dp
                }

                if (progressPercent > 0) {
                    val fillView = View(context).apply {
                        val fillW = ((coverW * (progressPercent.coerceIn(0, 100) / 100f))).toInt().coerceAtLeast((2 * dp).toInt())
                        layoutParams = LayoutParams(fillW, LayoutParams.MATCH_PARENT)
                        background = GradientDrawable().apply {
                            setColor(tokens.accent)
                            cornerRadius = 1 * dp
                        }
                    }
                    addView(fillView)
                }
            }
            addView(progressBar)

            // Book Title: 2 lines max, wrap, not truncate
            val titleView = TextView(context).apply {
                text = doc.effectiveTitle
                textSize = 12f
                setTextColor(tokens.textPrimary)
                maxLines = 2
                ellipsize = null
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LayoutParams(coverW, LayoutParams.WRAP_CONTENT).apply {
                    topMargin = (5 * dp).toInt()
                }
            }
            addView(titleView)

            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onDocumentClick(doc)
            }

            setOnLongClickListener {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onDocumentLongClick(doc)
                true
            }
        }
    }
}
