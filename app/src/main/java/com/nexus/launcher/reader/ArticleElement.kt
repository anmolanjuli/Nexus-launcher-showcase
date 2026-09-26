package com.nexus.launcher.reader

/**
 * Structural element within an extracted article body.
 */
sealed class ArticleElement {
    data class Paragraph(val text: String) : ArticleElement()
    data class Heading(val text: String, val level: Int) : ArticleElement()
    data class Blockquote(val text: String) : ArticleElement()
    data class Image(val url: String, val caption: String? = null) : ArticleElement()
}
