package com.nexus.launcher.reader.doc

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.FileInputStream
import java.io.InputStreamReader

/**
 * Extracts Table of Contents from EPUB 3 nav.xhtml or EPUB 2 toc.ncx files.
 * Maps navigation targets to ordered spine chapter indices.
 */
object NexusEpubTocExtractor {

    data class TocItem(
        val title: String,
        val href: String,
        val chapterIndex: Int
    )

    fun extractToc(
        opfDir: File,
        manifestHrefs: Map<String, String>,
        manifestProperties: Map<String, String>,
        spineHrefs: List<String>,
        opfTocId: String?,
        fallbackTitle: (Int) -> String
    ): List<TocItem> {
        // 1. Try EPUB 3 nav document
        val navItemId = manifestProperties.entries.firstOrNull { it.value.contains("nav", ignoreCase = true) }?.key
            ?: manifestHrefs.entries.firstOrNull { it.value.endsWith("nav.xhtml", ignoreCase = true) || it.value.endsWith("toc.xhtml", ignoreCase = true) }?.key

        if (navItemId != null) {
            val navHref = manifestHrefs[navItemId]
            if (navHref != null) {
                val navFile = File(opfDir, navHref)
                if (navFile.exists()) {
                    val ep3Items = parseEpub3Nav(navFile, opfDir, spineHrefs)
                    if (ep3Items.isNotEmpty()) return ep3Items
                }
            }
        }

        // 2. Try EPUB 2 NCX document
        val ncxHref = opfTocId?.let { manifestHrefs[it] }
            ?: manifestHrefs.values.firstOrNull { it.endsWith(".ncx", ignoreCase = true) }

        if (ncxHref != null) {
            val ncxFile = File(opfDir, ncxHref)
            if (ncxFile.exists()) {
                val ncxItems = parseEpub2Ncx(ncxFile, opfDir, spineHrefs)
                if (ncxItems.isNotEmpty()) return ncxItems
            }
        }

        // 3. Fallback: generate from spine
        return spineHrefs.mapIndexed { index, href ->
            TocItem(
                title = fallbackTitle(index),
                href = href,
                chapterIndex = index
            )
        }
    }

    private fun parseEpub2Ncx(ncxFile: File, opfDir: File, spineHrefs: List<String>): List<TocItem> {
        val items = mutableListOf<TocItem>()
        val ncxDir = ncxFile.parentFile ?: opfDir

        try {
            val parser = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                setInput(InputStreamReader(FileInputStream(ncxFile), Charsets.UTF_8))
            }

            var eventType = parser.eventType
            var currentTitle: String? = null
            var currentSrc: String? = null
            var inNavLabel = false
            var inText = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val name = parser.name?.lowercase() ?: ""
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (name) {
                            "navlabel" -> inNavLabel = true
                            "text" -> if (inNavLabel) inText = true
                            "content" -> currentSrc = parser.getAttributeValue(null, "src")
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inText) {
                            val txt = parser.text?.trim()
                            if (!txt.isNullOrBlank()) {
                                currentTitle = txt
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (name) {
                            "text" -> inText = false
                            "navlabel" -> inNavLabel = false
                            "navpoint" -> {
                                if (!currentTitle.isNullOrBlank() && !currentSrc.isNullOrBlank()) {
                                    val resolvedTarget = resolveFileHref(ncxDir, opfDir, currentSrc)
                                    val matchIndex = findMatchingSpineIndex(resolvedTarget, spineHrefs)
                                    if (matchIndex >= 0) {
                                        items.add(TocItem(currentTitle, currentSrc, matchIndex))
                                    }
                                }
                                currentTitle = null
                                currentSrc = null
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {}

        return items
    }

    private fun parseEpub3Nav(navFile: File, opfDir: File, spineHrefs: List<String>): List<TocItem> {
        val items = mutableListOf<TocItem>()
        val navDir = navFile.parentFile ?: opfDir

        try {
            val parser = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                setInput(InputStreamReader(FileInputStream(navFile), Charsets.UTF_8))
            }

            var eventType = parser.eventType
            var currentHref: String? = null
            var currentTitle: StringBuilder? = null
            var inAnchor = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val name = parser.name?.lowercase() ?: ""
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (name == "a") {
                            currentHref = parser.getAttributeValue(null, "href")
                            currentTitle = StringBuilder()
                            inAnchor = true
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (inAnchor && currentTitle != null) {
                            currentTitle.append(parser.text ?: "")
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (name == "a") {
                            val title = currentTitle?.toString()?.trim()
                            val href = currentHref?.trim()
                            if (!title.isNullOrBlank() && !href.isNullOrBlank()) {
                                val resolved = resolveFileHref(navDir, opfDir, href)
                                val matchIndex = findMatchingSpineIndex(resolved, spineHrefs)
                                if (matchIndex >= 0) {
                                    items.add(TocItem(title, href, matchIndex))
                                }
                            }
                            inAnchor = false
                            currentHref = null
                            currentTitle = null
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {}

        return items
    }

    private fun resolveFileHref(baseDir: File, opfDir: File, targetHref: String): String {
        val cleanTarget = targetHref.substringBefore("#").substringBefore("?")
        val targetFile = File(baseDir, cleanTarget)
        val relative = try {
            targetFile.canonicalPath.removePrefix(opfDir.canonicalPath).removePrefix(File.separator)
                .replace("\\", "/")
        } catch (_: Exception) {
            cleanTarget
        }
        return relative
    }

    private fun findMatchingSpineIndex(targetRelative: String, spineHrefs: List<String>): Int {
        val clean = targetRelative.lowercase()
        val exact = spineHrefs.indexOfFirst { it.lowercase() == clean }
        if (exact >= 0) return exact

        // Partial match on filename
        val targetFileName = File(clean).name
        return spineHrefs.indexOfFirst { File(it.lowercase()).name == targetFileName }
    }
}
