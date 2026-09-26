package com.nexus.launcher.ui.folder

import android.content.Context
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.View
import com.nexus.launcher.data.FolderConfig
import com.nexus.launcher.data.HomeScreenItem

/** Live mini open-folder preview for the edit sheet. */
class FolderEditPreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var folderItem: HomeScreenItem? = null
    private var config: FolderConfig = FolderConfig()
    private var contents: List<HomeScreenItem> = emptyList()
    private var iconCache: Map<String, Drawable> = emptyMap()
    private var title: String = "Folder"
    private val glass = com.nexus.launcher.ui.glass.GlassPreviewComposer(context)

    fun update(
        item: HomeScreenItem,
        config: FolderConfig,
        contents: List<HomeScreenItem>,
        iconCache: Map<String, Drawable>,
        title: String = FolderContextMenuLauncher.folderDisplayName(context, item)
    ) {
        folderItem = item
        this.config = config
        this.contents = contents
        this.iconCache = iconCache
        this.title = title
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val item = folderItem ?: return
        FolderEditMiniWindowDraw.draw(
            context = context,
            canvas = canvas,
            viewWidth = width,
            viewHeight = height,
            title = title,
            folderItem = item,
            config = config,
            contents = contents,
            iconCache = iconCache,
            glass = glass
        )
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        glass.release()
    }
}
