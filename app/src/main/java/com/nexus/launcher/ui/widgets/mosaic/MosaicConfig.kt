package com.nexus.launcher.ui.widgets.mosaic

import com.nexus.launcher.ui.dock.DockFrostedGradients
import org.json.JSONArray
import org.json.JSONObject

data class MosaicChild(
    val appWidgetId: Int,
    val providerPackage: String = "",
    val providerClassName: String = "",
    val backgroundMode: String = MosaicConfig.BG_INHERIT,
    val frostedGradientIndex: Int = 0,
    val backgroundOpacity: Float = 1f,
    val isExpressive: Boolean = false,
    val spanX: Int = 2,
    val spanY: Int = 2
)

data class MosaicPage(
    val children: List<MosaicChild> = emptyList(),
    val layoutTemplate: String? = null
)

data class MosaicConfig(
    val mode: String = MODE_MOSAIC,
    val focusIndex: Int = 0,
    val pageIndex: Int = 0,
    val surfaceOpacity: Float = DEFAULT_OPACITY,
    /** GLASS (default) or FROSTED (shared DockFrostedGradients). */
    val backgroundMode: String = BG_GLASS,
    val frostedGradientIndex: Int = 0,
    /** Intensity of glass specular reflection / refraction (0.0 to 1.0) — same slider/formula as
     *  widgets' `glassRefraction`. Was previously hardcoded to 0.70f everywhere with no way to
     *  adjust it; now a real per-mosaic setting. */
    val glassRefraction: Float = 0.70f,
    val pages: List<MosaicPage> = listOf(MosaicPage()),
    /**
     * Free-size box fractions (same keys as standalone widgets).
     * ≤0 means fall back to spanX/spanY cell size. Preserved across config rewrites.
     */
    val wFrac: Float = -1f,
    val hFrac: Float = -1f,
    val isExpressive: Boolean = false,
    val shapeStyle: Int = 1
) {
    fun currentPage(): MosaicPage {
        val idx = pageIndex.coerceIn(0, pages.lastIndex.coerceAtLeast(0))
        return pages.getOrElse(idx) { MosaicPage() }
    }

    fun currentChildren(): List<MosaicChild> = currentPage().children

    fun allChildren(): List<MosaicChild> = pages.flatMap { it.children }

    fun updateCurrentPage(transform: (MosaicPage) -> MosaicPage): MosaicConfig {
        val idx = pageIndex.coerceIn(0, pages.lastIndex.coerceAtLeast(0))
        val mutable = pages.toMutableList()
        if (mutable.isEmpty()) mutable += MosaicPage()
        val safeIdx = idx.coerceIn(0, mutable.lastIndex)
        mutable[safeIdx] = transform(mutable[safeIdx])
        val currentKids = mutable[safeIdx].children
        val focus = focusIndex.coerceIn(0, (currentKids.size - 1).coerceAtLeast(0))
        return copy(pages = mutable, focusIndex = focus)
    }

    fun withCurrentChildren(kids: List<MosaicChild>): MosaicConfig {
        val idx = pageIndex.coerceIn(0, pages.lastIndex.coerceAtLeast(0))
        val mutable = pages.toMutableList()
        if (mutable.isEmpty()) mutable += MosaicPage()
        val safeIdx = idx.coerceIn(0, mutable.lastIndex)
        val currentTemplate = mutable[safeIdx].layoutTemplate
        mutable[safeIdx] = MosaicPage(kids, currentTemplate)
        val focus = focusIndex.coerceIn(0, (kids.size - 1).coerceAtLeast(0))
        return copy(pages = mutable, focusIndex = focus)
    }

    fun addPage(): MosaicConfig {
        val next = pages + MosaicPage()
        return copy(pages = next, pageIndex = next.lastIndex)
    }

    fun removePage(index: Int): MosaicConfig {
        if (pages.size <= 1) return this
        if (index !in pages.indices) return this
        val mutable = pages.toMutableList()
        val orphaned = mutable[index].children
        val mergeInto = if (index > 0) index - 1 else 1
        val target = mutable[mergeInto]
        mutable[mergeInto] = MosaicPage(target.children + orphaned, target.layoutTemplate)
        mutable.removeAt(index)
        val newPageIndex = pageIndex.coerceIn(0, mutable.lastIndex)
        return copy(pages = mutable, pageIndex = newPageIndex, focusIndex = 0)
    }

    fun moveChild(fromIndex: Int, toIndex: Int): MosaicConfig {
        val kids = currentChildren().toMutableList()
        if (fromIndex !in kids.indices || toIndex !in kids.indices) return this
        val item = kids.removeAt(fromIndex)
        kids.add(toIndex, item)
        return withCurrentChildren(kids)
    }

    fun reorderChild(fromIndex: Int, toIndex: Int): MosaicConfig = moveChild(fromIndex, toIndex)

    fun toJson(): String {
        val root = JSONObject()
        root.put(KEY_KIND, KIND)
        root.put(KEY_MODE, mode)
        root.put(KEY_FOCUS, focusIndex)
        root.put(KEY_PAGE, pageIndex)
        root.put(KEY_OPACITY, surfaceOpacity.coerceIn(MIN_OPACITY, 1f))
        root.put(KEY_BG_MODE, backgroundMode)
        root.put(KEY_FROSTED, frostedGradientIndex)
        root.put(KEY_REFRACTION, glassRefraction.coerceIn(0f, 1f).toDouble())
        val pagesArr = JSONArray()
        pages.forEach { page ->
            pagesArr.put(JSONObject().apply {
                put(KEY_CHILDREN, childrenArray(page.children))
                page.layoutTemplate?.let { put(KEY_TEMPLATE, it) }
            })
        }
        root.put(KEY_PAGES, pagesArr)
        if (wFrac > 0f) root.put(KEY_W_FRAC, wFrac.toDouble())
        if (hFrac > 0f) root.put(KEY_H_FRAC, hFrac.toDouble())
        if (isExpressive) root.put(KEY_EXPRESSIVE, isExpressive)
        if (shapeStyle != 1) root.put(KEY_SHAPE_STYLE, shapeStyle)
        return root.toString()
    }

    companion object {
        const val KIND = "living_mosaic"
        const val MODE_MOSAIC = "mosaic"
        const val MODE_SINGLE = "single"
        const val BG_GLASS = "GLASS"
        const val BG_FROSTED = "FROSTED"
        const val BG_INHERIT = "INHERIT"
        const val MAX_CHILDREN_PER_PAGE = 12
        const val DEFAULT_OPACITY = 0.82f
        const val MIN_OPACITY = 0f

        private const val KEY_KIND = "kind"
        private const val KEY_MODE = "mode"
        private const val KEY_FOCUS = "focusIndex"
        private const val KEY_PAGE = "pageIndex"
        private const val KEY_OPACITY = "surfaceOpacity"
        private const val KEY_BG_MODE = "backgroundMode"
        private const val KEY_FROSTED = "frostedGradientIndex"
        private const val KEY_REFRACTION = "glassRefraction"
        private const val KEY_PAGES = "pages"
        private const val KEY_TEMPLATE = "layoutTemplate"
        private const val KEY_CHILDREN = "children"
        private const val KEY_ID = "appWidgetId"
        private const val KEY_PKG = "providerPackage"
        private const val KEY_CLS = "providerClassName"
        private const val KEY_CHILD_BG_MODE = "backgroundMode"
        private const val KEY_CHILD_FROSTED = "frostedGradientIndex"
        private const val KEY_CHILD_BG_OPACITY = "backgroundOpacity"
        private const val KEY_W_FRAC = "wFrac"
        private const val KEY_H_FRAC = "hFrac"
        private const val KEY_EXPRESSIVE = "isExpressive"
        private const val KEY_SHAPE_STYLE = "shapeStyle"

        fun parse(json: String?): MosaicConfig {
            if (json.isNullOrBlank()) return MosaicConfig()
            return try {
                val root = JSONObject(json)
                if (root.optString(KEY_KIND) != KIND &&
                    !root.has(KEY_CHILDREN) &&
                    !root.has(KEY_PAGES)
                ) {
                    return MosaicConfig()
                }
                val pages = parsePages(root)
                val bgRaw = root.optString(KEY_BG_MODE, BG_GLASS).uppercase()
                val bgMode = when (bgRaw) {
                    BG_FROSTED -> BG_FROSTED
                    else -> BG_GLASS
                }
                MosaicConfig(
                    mode = root.optString(KEY_MODE, MODE_MOSAIC),
                    focusIndex = root.optInt(KEY_FOCUS, 0).coerceAtLeast(0),
                    pageIndex = root.optInt(KEY_PAGE, 0).coerceIn(0, pages.lastIndex.coerceAtLeast(0)),
                    surfaceOpacity = root.optDouble(KEY_OPACITY, DEFAULT_OPACITY.toDouble())
                        .toFloat().coerceIn(MIN_OPACITY, 1f),
                    backgroundMode = bgMode,
                    frostedGradientIndex = root.optInt(KEY_FROSTED, 0)
                        .coerceIn(0, DockFrostedGradients.PRESET_COUNT - 1),
                    glassRefraction = root.optDouble(KEY_REFRACTION, 0.70).toFloat().coerceIn(0f, 1f),
                    pages = pages,
                    wFrac = root.optDouble(KEY_W_FRAC, -1.0).toFloat(),
                    hFrac = root.optDouble(KEY_H_FRAC, -1.0).toFloat(),
                    isExpressive = root.optBoolean(KEY_EXPRESSIVE, false),
                    shapeStyle = root.optInt(KEY_SHAPE_STYLE, 1)
                )
            } catch (_: Exception) {
                MosaicConfig()
            }
        }

        fun isMosaicJson(json: String?): Boolean {
            if (json.isNullOrBlank()) return false
            return try {
                JSONObject(json).optString(KEY_KIND) == KIND
            } catch (_: Exception) {
                false
            }
        }

        private fun parsePages(root: JSONObject): List<MosaicPage> {
            val pagesArr = root.optJSONArray(KEY_PAGES)
            if (pagesArr != null && pagesArr.length() > 0) {
                val out = mutableListOf<MosaicPage>()
                for (i in 0 until pagesArr.length()) {
                    val o = pagesArr.optJSONObject(i) ?: continue
                    val template = o.optString(KEY_TEMPLATE, "").takeIf { it.isNotBlank() }
                    out += MosaicPage(parseChildren(o.optJSONArray(KEY_CHILDREN)), template)
                }
                return if (out.isEmpty()) listOf(MosaicPage()) else out
            }
            val rootTemplate = root.optString(KEY_TEMPLATE, "").takeIf { it.isNotBlank() }
            return listOf(MosaicPage(parseChildren(root.optJSONArray(KEY_CHILDREN)), rootTemplate))
        }

        private fun parseChildren(arr: JSONArray?): List<MosaicChild> {
            if (arr == null) return emptyList()
            val kids = mutableListOf<MosaicChild>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optInt(KEY_ID, -1)
                kids += MosaicChild(
                    appWidgetId = id,
                    providerPackage = o.optString(KEY_PKG, "").ifBlank { o.optString("providerPackageName", "") },
                    providerClassName = o.optString(KEY_CLS, ""),
                    backgroundMode = o.optString(KEY_CHILD_BG_MODE, BG_INHERIT).uppercase().let {
                        when (it) {
                            BG_GLASS, BG_FROSTED -> it
                            else -> BG_INHERIT
                        }
                    },
                    frostedGradientIndex = o.optInt(KEY_CHILD_FROSTED, 0)
                        .coerceIn(0, DockFrostedGradients.PRESET_COUNT - 1),
                    backgroundOpacity = o.optDouble(KEY_CHILD_BG_OPACITY, 1.0).toFloat().coerceIn(0f, 1f),
                    isExpressive = o.optBoolean(KEY_EXPRESSIVE, false),
                    spanX = o.optInt("spanX", 2).coerceAtLeast(1),
                    spanY = o.optInt("spanY", 2).coerceAtLeast(1)
                )
            }
            return kids
        }

        private fun childrenArray(children: List<MosaicChild>): JSONArray {
            val arr = JSONArray()
            children.forEach { child ->
                arr.put(JSONObject().apply {
                    put(KEY_ID, child.appWidgetId)
                    put(KEY_PKG, child.providerPackage)
                    put(KEY_CLS, child.providerClassName)
                    if (child.backgroundMode != BG_INHERIT) put(KEY_CHILD_BG_MODE, child.backgroundMode)
                    if (child.frostedGradientIndex != 0) put(KEY_CHILD_FROSTED, child.frostedGradientIndex)
                    if (child.backgroundOpacity != 1f) put(KEY_CHILD_BG_OPACITY, child.backgroundOpacity.toDouble())
                    if (child.isExpressive) put(KEY_EXPRESSIVE, child.isExpressive)
                    if (child.spanX != 2) put("spanX", child.spanX)
                    if (child.spanY != 2) put("spanY", child.spanY)
                })
            }
            return arr
        }
    }
}
