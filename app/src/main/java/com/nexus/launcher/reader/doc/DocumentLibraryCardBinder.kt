package com.nexus.launcher.reader.doc

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.text.format.DateUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.feed.NexusFeedEInkStyler
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.typography.NexusTypeScale

/**
 * Builds individual document list cards with cover thumbnails and circular progress rings.
 */
object DocumentLibraryCardBinder {

    fun buildDocumentCard(
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
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = NexusFeedEInkStyler.createCardBackground(isEInk, isDark, tokens, dp, 14f)
            setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (8 * dp).toInt()
            }

            // Cover thumbnail with 3dp circular progress ring
            val progressPercent = DocumentProgress.percentOf(doc)

            val coverView = CoverProgressThumbnailView(context).apply {
                layoutParams = LinearLayout.LayoutParams((54 * dp).toInt(), (74 * dp).toInt()).apply {
                    marginEnd = (12 * dp).toInt()
                }
                ringStrokeWidthDp = 3f
                title = doc.effectiveTitle
                fileType = doc.fileType
                this.progressPercent = progressPercent
                trackColor = tokens.surfaceRaised
                progressColor = if (isEInk) tokens.textPrimary else Color.WHITE
                this.isEInk = isEInk
                coverBitmap = coverManager.getMemoryCached(doc.uri)
            }
            addView(coverView)

            if (coverView.coverBitmap == null && (doc.fileType == "pdf" || doc.fileType == "epub")) {
                onLoadCover(doc) { bmp -> coverView.coverBitmap = bmp }
            }

            // Info column
            val infoColumn = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)

                // Title: 13sp medium
                val title = TextView(context).apply {
                    text = doc.effectiveTitle
                    textSize = 13f
                    typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                    setTextColor(tokens.textPrimary)
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                }
                addView(title)

                // Collection badge if assigned
                val assignedCol = collections.find { it.id == doc.collectionId }
                if (assignedCol != null) {
                    val colView = TextView(context).apply {
                        text = "● " + assignedCol.name
                        textSize = 11f
                        val colColor = if (!assignedCol.colorTag.isNullOrBlank()) {
                            try { Color.parseColor(assignedCol.colorTag) } catch (_: Exception) { tokens.accent }
                        } else {
                            tokens.accent
                        }
                        setTextColor(colColor)
                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            topMargin = (2 * dp).toInt()
                        }
                    }
                    addView(colView)
                }

                // Progress + timestamp
                val metaText = TextView(context).apply {
                    text = DocumentProgress.metaLine(context, doc)
                    textSize = 11f
                    setTextColor(tokens.textSecondary)
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = (4 * dp).toInt()
                    }
                }
                addView(metaText)
            }
            addView(infoColumn)

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

}
