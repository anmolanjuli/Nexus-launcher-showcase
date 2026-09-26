package com.nexus.launcher.ui.widgets.notes

import android.graphics.Paint
import android.text.TextUtils

/**
 * Text wrapping and multi-line layout calculation for canvas note rendering.
 */
object NotesTextLayout {

    fun layoutText(
        text: String,
        paint: Paint,
        maxWidth: Float,
        maxLines: Int
    ): List<String> {
        if (text.isEmpty() || maxLines <= 0 || maxWidth <= 0f) return emptyList()

        val lines = mutableListOf<String>()
        val paragraphs = text.split("\n")

        for (paragraph in paragraphs) {
            if (lines.size >= maxLines) break

            if (paragraph.isEmpty()) {
                lines.add("")
                continue
            }

            var start = 0
            while (start < paragraph.length && lines.size < maxLines) {
                val isLastLine = lines.size == maxLines - 1

                if (isLastLine) {
                    val remainingText = paragraph.substring(start)
                    val ellipsized = TextUtils.ellipsize(
                        remainingText,
                        android.text.TextPaint(paint),
                        maxWidth,
                        TextUtils.TruncateAt.END
                    ).toString()
                    lines.add(ellipsized)
                    break
                }

                val count = paint.breakText(paragraph, start, paragraph.length, true, maxWidth, null)
                if (count <= 0) break

                var end = start + count
                if (end < paragraph.length) {
                    val lastSpace = paragraph.lastIndexOf(' ', end)
                    if (lastSpace > start) {
                        end = lastSpace
                    }
                }

                val line = paragraph.substring(start, end).trimEnd()
                lines.add(line)

                start = end
                while (start < paragraph.length && paragraph[start] == ' ') {
                    start++
                }
            }
        }

        return lines
    }
}
