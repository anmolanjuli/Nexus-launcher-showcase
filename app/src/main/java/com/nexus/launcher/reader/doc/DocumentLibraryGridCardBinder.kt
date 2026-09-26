package com.nexus.launcher.reader.doc

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.feed.NexusFeedEInkStyler
import com.nexus.launcher.theme.NexusColorTokens

/**
 * Builds individual standardized 2:3 document grid tiles for the Library grid view.
 * Features 2dp vertical spine line on cover, 3dp bottom progress bar, 13sp medium title,
 * 11sp textSecondary metadata, and collection color tag integration.
 */
object DocumentLibraryGridCardBinder {

    fun buildGridCard(
        context: Context,
        doc: DocumentRecord,
        collections: List<CollectionRecord>,
        tokens: NexusColorTokens,
        dp: Float,
        isEInk: Boolean,
        isDark: Boolean,
        coverManager: DocumentCoverManager,
        onLoadCover: (DocumentRecord, (android.graphics.Bitmap?) -> Unit) -> Unit,
        onClick: () -> Unit,
        onLongClick: () -> Unit
    ): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = NexusFeedEInkStyler.createCardBackground(isEInk, isDark, tokens, dp, 14f)
            setPadding((10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

            val progressPercent = DocumentProgress.percentOf(doc)

            val assignedCol = collections.find { it.id == doc.collectionId }
            val colColor = if (assignedCol != null) resolveCollectionColor(assignedCol.colorTag, tokens.accent) else tokens.accent

            // 2:3 Aspect ratio standardized cover (84dp x 126dp)
            val coverW = (84 * dp).toInt()
            val coverH = (126 * dp).toInt()

            val coverView = CoverProgressThumbnailView(context).apply {
                layoutParams = LinearLayout.LayoutParams(coverW, coverH).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                }
                ringStrokeWidthDp = 0f
                spineWidthDp = 2f
                spineColor = colColor
                bottomProgressBarHeightDp = 3f
                title = doc.effectiveTitle
                fileType = doc.fileType
                this.progressPercent = progressPercent
                trackColor = tokens.surfaceRaised
                progressColor = tokens.accent
                this.isEInk = isEInk
                coverBitmap = coverManager.getMemoryCached(doc.uri)
            }
            addView(coverView)

            if (coverView.coverBitmap == null && (doc.fileType == "pdf" || doc.fileType == "epub")) {
                onLoadCover(doc) { bmp -> coverView.coverBitmap = bmp }
            }

            // Title: 13sp medium
            val titleView = TextView(context).apply {
                text = doc.effectiveTitle
                textSize = 13f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                setTextColor(tokens.textPrimary)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (8 * dp).toInt()
                }
            }
            addView(titleView)

            // Metadata: 11sp textSecondary with collection color dot
            val metaLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = (3 * dp).toInt()
                }

                if (assignedCol != null) {
                    val dot = View(context).apply {
                        val dotSize = (6 * dp).toInt()
                        layoutParams = LinearLayout.LayoutParams(dotSize, dotSize).apply {
                            marginEnd = (4 * dp).toInt()
                        }
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(colColor)
                        }
                    }
                    addView(dot)

                    val nameView = TextView(context).apply {
                        text = assignedCol.name
                        textSize = 11f
                        setTextColor(tokens.textSecondary)
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    }
                    addView(nameView)
                } else {
                    val progressView = TextView(context).apply {
                        text = formatProgress(context, doc)
                        textSize = 11f
                        setTextColor(tokens.textSecondary)
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                    }
                    addView(progressView)
                }
            }
            addView(metaLayout)

            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            }

            setOnLongClickListener {
                performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                onLongClick()
                true
            }
        }
    }

    /**
     * Where the reader is, and how much is left when there is enough reading behind it to say.
     * A text document used to print its scroll offset here as a percentage — "4820%".
     */
    private fun formatProgress(context: Context, doc: DocumentRecord): String {
        val position = DocumentProgress.shortPositionLabel(doc, context)
        val remaining = DocumentProgress.remainingLabel(context, doc) ?: return position
        return "$position • $remaining"
    }

    private fun resolveCollectionColor(colorHex: String?, defaultColor: Int): Int {
        if (colorHex.isNullOrBlank()) return defaultColor
        return try {
            Color.parseColor(colorHex)
        } catch (_: Exception) {
            defaultColor
        }
    }
}
