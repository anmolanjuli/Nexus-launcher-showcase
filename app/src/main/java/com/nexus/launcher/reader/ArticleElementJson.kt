package com.nexus.launcher.reader

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import java.lang.reflect.Type

/**
 * Reads and writes [ArticleElement] with the kind of thing it is written alongside it.
 *
 * [ArticleElement] is a sealed class: a paragraph, a heading, a blockquote or an image. Plain Gson
 * writes whichever fields the object happens to have and, on the way back, has no idea which
 * subclass it was looking at — so it hands back a `LinkedTreeMap` in a list typed
 * `List<ArticleElement>`. Nothing complains until something reads an element, at which point the
 * cast fails and the reader crashes on the article it just cached. That is what this exists to
 * prevent, and why [kind] is written into every element.
 *
 * The kind names are storage format: an article cached by an older build has to keep opening, so
 * they are appended to, never renamed.
 */
object ArticleElementJson : JsonSerializer<ArticleElement>, JsonDeserializer<ArticleElement> {

    private const val KIND = "kind"
    private const val PARAGRAPH = "paragraph"
    private const val HEADING = "heading"
    private const val QUOTE = "quote"
    private const val IMAGE = "image"

    override fun serialize(
        src: ArticleElement,
        typeOfSrc: Type,
        context: JsonSerializationContext,
    ): JsonElement {
        val obj = JsonObject()
        when (src) {
            is ArticleElement.Paragraph -> {
                obj.addProperty(KIND, PARAGRAPH)
                obj.addProperty("text", src.text)
            }
            is ArticleElement.Heading -> {
                obj.addProperty(KIND, HEADING)
                obj.addProperty("text", src.text)
                obj.addProperty("level", src.level)
            }
            is ArticleElement.Blockquote -> {
                obj.addProperty(KIND, QUOTE)
                obj.addProperty("text", src.text)
            }
            is ArticleElement.Image -> {
                obj.addProperty(KIND, IMAGE)
                obj.addProperty("url", src.url)
                src.caption?.let { obj.addProperty("caption", it) }
            }
        }
        return obj
    }

    /**
     * Returns null for anything unreadable — an element written by a build that knew a kind this
     * one does not, or a file from before kinds were written at all. The caller drops the article
     * and extracts it again, which is a moment's wait rather than a crash.
     */
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext,
    ): ArticleElement? {
        val obj = json as? JsonObject ?: return null
        val text = obj.get("text")?.asString
        return when (obj.get(KIND)?.asString) {
            PARAGRAPH -> text?.let { ArticleElement.Paragraph(it) }
            HEADING -> text?.let { ArticleElement.Heading(it, obj.get("level")?.asInt ?: 2) }
            QUOTE -> text?.let { ArticleElement.Blockquote(it) }
            IMAGE -> obj.get("url")?.asString?.let {
                ArticleElement.Image(it, obj.get("caption")?.asString)
            }
            else -> null
        }
    }
}
