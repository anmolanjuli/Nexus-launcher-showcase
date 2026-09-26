package com.nexus.launcher.reader.doc

import android.content.Context
import android.net.Uri
import com.nexus.launcher.reader.ArticleElement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Streams plain text files with 5MB size protection and transforms content into paragraph elements.
 */
class NexusTxtRendererEngine(
    private val context: Context,
    private val uri: Uri
) {
    class TxtFileTooLargeException(message: String) : Exception(message)

    companion object {
        private const val MAX_FILE_BYTES = 5 * 1024 * 1024L // 5MB limit
    }

    suspend fun loadParagraphs(): List<ArticleElement.Paragraph> = withContext(Dispatchers.IO) {
        // Size validation
        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
        val fileSize = pfd?.statSize ?: 0L
        pfd?.close()

        if (fileSize > MAX_FILE_BYTES) {
            throw TxtFileTooLargeException("File exceeds 5MB limit")
        }

        val paragraphs = mutableListOf<ArticleElement.Paragraph>()
        context.contentResolver.openInputStream(uri)?.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream)).use { reader ->
                val currentBlock = StringBuilder()
                var line: String? = reader.readLine()

                while (line != null) {
                    if (line.isBlank()) {
                        if (currentBlock.isNotBlank()) {
                            paragraphs.add(ArticleElement.Paragraph(currentBlock.toString().trim()))
                            currentBlock.clear()
                        }
                    } else {
                        if (currentBlock.isNotEmpty()) currentBlock.append(" ")
                        currentBlock.append(line.trim())
                    }
                    line = reader.readLine()
                }

                if (currentBlock.isNotBlank()) {
                    paragraphs.add(ArticleElement.Paragraph(currentBlock.toString().trim()))
                }
            }
        }

        if (paragraphs.isEmpty()) {
            paragraphs.add(ArticleElement.Paragraph(context.getString(com.nexus.launcher.R.string.reader_empty_file)))
        }

        paragraphs
    }
}
