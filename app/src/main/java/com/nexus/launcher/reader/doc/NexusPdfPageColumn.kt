package com.nexus.launcher.reader.doc

import android.widget.ImageView
import android.widget.LinearLayout

/**
 * The column of page slots a continuous PDF scrolls through: one [ImageView] per page of the
 * document, each already the size that page will be, so the scroll has its full length from the
 * first frame and never grows under the reader's thumb.
 *
 * The slots start empty. [NexusPdfPageView] fills the few near the viewport and empties the rest.
 */
internal object NexusPdfPageColumn {

    fun build(
        container: LinearLayout,
        pageCount: Int,
        width: Int,
        height: Int,
        gap: Int,
        theme: PdfThemeModeStore.ThemeMode,
        isEInk: Boolean,
    ): List<ImageView> {
        val views = ArrayList<ImageView>(pageCount)
        repeat(pageCount) {
            val page = ImageView(container.context).apply {
                layoutParams = LinearLayout.LayoutParams(width, height).apply { bottomMargin = gap }
                scaleType = ImageView.ScaleType.FIT_CENTER
                background = null
                PdfThemeFilterApplier.apply(this, theme, isEInk)
            }
            container.addView(page)
            views.add(page)
        }
        return views
    }
}
