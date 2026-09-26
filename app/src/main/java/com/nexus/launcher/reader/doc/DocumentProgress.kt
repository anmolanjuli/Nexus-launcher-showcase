package com.nexus.launcher.reader.doc

import android.content.Context
import android.text.format.DateUtils
import com.nexus.launcher.R

/**
 * How far through a document the reader is, and how much reading is left.
 *
 * One place, because the answer was being worked out in four: the list card, the grid card, the
 * Continue Reading row and the cover's progress ring each had their own copy of the arithmetic, and
 * they had already drifted — a text document read to the middle showed "4820%" on one card and a
 * flat 50% on another.
 *
 * [DocumentRecord.lastReadPosition] means something different per file type, which is where the
 * drift came from: a page index in a PDF, a chapter and scroll offset packed into one number in an
 * EPUB, and a scroll offset in pixels in a text file. [totalUnits] is the matching whole.
 */
object DocumentProgress {

    /** EPUB packs its position as `chapter * CHAPTER_STRIDE + scrollY`. */
    private const val CHAPTER_STRIDE = 10000

    /** Reading long enough, and far enough in, for a pace to mean anything. */
    private const val MIN_MILLIS_FOR_ESTIMATE = 90_000L
    private const val MIN_PERCENT_FOR_ESTIMATE = 3

    /** At and above this, the book is done. */
    private const val FINISHED_PERCENT = 99

    fun percentOf(doc: DocumentRecord): Int {
        if (doc.totalPages <= 0) return 0
        return when (doc.fileType) {
            "epub" -> {
                val chapter = doc.lastReadPosition / CHAPTER_STRIDE
                val within = (doc.lastReadPosition % CHAPTER_STRIDE) / CHAPTER_STRIDE.toFloat()
                (((chapter + within) / doc.totalPages) * 100).toInt().coerceIn(0, 100)
            }
            "pdf" -> (((doc.lastReadPosition + 1).toFloat() / doc.totalPages) * 100).toInt().coerceIn(0, 100)
            // Text: position and total are both scroll extents, so the ratio is the answer.
            else -> ((doc.lastReadPosition.toFloat() / doc.totalPages) * 100).toInt().coerceIn(0, 100)
        }
    }

    fun isFinished(doc: DocumentRecord): Boolean = percentOf(doc) >= FINISHED_PERCENT

    /** "Page 34 of 210", "Chapter 3 of 24", "62%" — or the file type when nothing has been read. */
    fun positionLabel(context: Context, doc: DocumentRecord): String {
        if (doc.totalPages <= 0) return doc.fileType.uppercase()
        return when (doc.fileType) {
            "pdf" -> context.getString(
                R.string.nexus_doc_page_status,
                (doc.lastReadPosition + 1).coerceAtMost(doc.totalPages),
                doc.totalPages,
            )
            "epub" -> context.getString(
                R.string.nexus_epub_chapter_status,
                ((doc.lastReadPosition / CHAPTER_STRIDE) + 1).coerceAtMost(doc.totalPages),
                doc.totalPages,
            )
            else -> com.nexus.launcher.locale.LocaleDigitUtils.formatPercent(percentOf(doc), context)
        }
    }

    /** The short form for a grid tile: "p. 34 / 210", "ch. 3 / 24", "62%". */
    fun shortPositionLabel(doc: DocumentRecord, context: Context? = null): String {
        if (doc.totalPages <= 0) return doc.fileType.uppercase()
        return when (doc.fileType) {
            "pdf" -> "p. ${(doc.lastReadPosition + 1).coerceAtMost(doc.totalPages)} / ${doc.totalPages}"
            "epub" -> "ch. ${((doc.lastReadPosition / CHAPTER_STRIDE) + 1).coerceAtMost(doc.totalPages)} / ${doc.totalPages}"
            else -> if (context != null) {
                com.nexus.launcher.locale.LocaleDigitUtils.formatPercent(percentOf(doc), context)
            } else {
                com.nexus.launcher.locale.LocaleDigitUtils.formatPercent(percentOf(doc), java.util.Locale.getDefault())
            }
        }
    }

    /**
     * How much reading is left, in this reader's own pace — or null when there is not yet enough
     * of a session to say. The pace is the document's own: total time spent in it against how far
     * it has come, so a dense textbook and a novel each get their own answer, and a reader who
     * skims gets a shorter estimate than one who does not.
     */
    fun remainingLabel(context: Context, doc: DocumentRecord): String? {
        if (isFinished(doc)) return context.getString(R.string.nexus_doc_finished)
        val percent = percentOf(doc)
        if (doc.readingMillis < MIN_MILLIS_FOR_ESTIMATE || percent < MIN_PERCENT_FOR_ESTIMATE) return null
        val millisPerPercent = doc.readingMillis / percent
        val remaining = millisPerPercent * (100 - percent)
        val minutes = (remaining / DateUtils.MINUTE_IN_MILLIS).toInt()
        return when {
            minutes < 1 -> context.getString(R.string.nexus_doc_time_left_min, 1)
            minutes < 60 -> context.getString(R.string.nexus_doc_time_left_min, minutes)
            minutes % 60 == 0 -> context.getString(R.string.nexus_doc_time_left_hr, minutes / 60)
            else -> context.getString(R.string.nexus_doc_time_left_hr_min, minutes / 60, minutes % 60)
        }
    }

    /** The card's line under the title: where you are, when you were last there, how much is left. */
    fun metaLine(context: Context, doc: DocumentRecord): String {
        val lastRead = DateUtils.getRelativeTimeSpanString(
            doc.lastReadTimestamp,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE,
        ).toString()
        val parts = mutableListOf<String>()
        if (doc.totalPages > 0) parts.add(positionLabel(context, doc))
        parts.add(lastRead)
        remainingLabel(context, doc)?.let { parts.add(it) }
        return parts.joinToString(" • ")
    }
}
