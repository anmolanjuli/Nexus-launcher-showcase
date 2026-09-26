package com.nexus.launcher.ui.folder

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import com.nexus.launcher.data.FolderConfig
import com.nexus.launcher.data.HomeScreenItem

/** Live mini closed-folder preview for the edit sheet — renders selected shape only. */
class FolderEditIconPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var folderItem: HomeScreenItem? = null
    private var config: FolderConfig = FolderConfig()
    private var contents: List<HomeScreenItem> = emptyList()
    private var iconCache: Map<String, Drawable> = emptyMap()

    private val renderer = FolderIconRenderer(context)

    fun update(
        item: HomeScreenItem,
        config: FolderConfig,
        contents: List<HomeScreenItem>,
        iconCache: Map<String, Drawable>
    ) {
        folderItem = item
        this.config = config
        this.contents = contents
        this.iconCache = iconCache
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val item = folderItem ?: return
        val density = resources.displayMetrics.density
        val cx = width / 2f
        val cy = height / 2f
        // Fill most of the available box — the previous 38dp cap made the icon look tiny
        // ("1x1 looking size") inside a much larger preview card.
        val radius = (minOf(width, height) / 2f * 0.90f).coerceAtMost(56f * density)

        renderer.drawFolder(
            canvas = canvas,
            cx = cx,
            cy = cy,
            folderItem = item.copy(folderConfigJson = FolderConfigCodec.toJson(config)),
            contents = contents,
            iconCache = iconCache,
            density = density,
            folderRadiusOverride = radius,
            previewHostView = this
        )
    }
}
