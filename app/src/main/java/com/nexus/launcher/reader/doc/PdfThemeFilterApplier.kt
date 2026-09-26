package com.nexus.launcher.reader.doc

import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.widget.ImageView
import com.nexus.launcher.feed.NexusFeedEInkStyler

/**
 * Applies PDF theme adaptation filters to page image views.
 * Pre-allocates filter instances to avoid garbage collection pressure during scrolling and page-turns.
 */
object PdfThemeFilterApplier {

    /** What white paper becomes under [paperTintFilter] — so the margins match the page exactly. */
    private val PAPER_SURFACE = Color.parseColor("#F9F1E6")
    private val PAPER_INK = Color.parseColor("#6B6152")
    private val INVERT_SURFACE = Color.parseColor("#0D0D0D")
    private val INVERT_INK = Color.parseColor("#8A8A8A")


    /**
     * Warm paper: the page's whites go to #F9F1E6, its blacks stay black.
     *
     * The tint is carried by the colour, never by alpha. MULTIPLY takes the source's alpha into
     * the result — `Sa * Da` — so tinting through a 65%-opaque sepia also dropped the whole page
     * to 65% opacity, and the text with it: solid black ink became grey ink over the background,
     * which is exactly the loss of sharpness against Original. An opaque tint mixed 65% of the way
     * from white to sepia gives the same warmth with the ink left alone.
     */
    private val paperTintFilter = PorterDuffColorFilter(
        PAPER_SURFACE,
        PorterDuff.Mode.MULTIPLY
    )

    // Full RGB inversion: white becomes black, black becomes white
    private val invertColorsFilter = ColorMatrixColorFilter(
        ColorMatrix(
            floatArrayOf(
                -1f,  0f,  0f,  0f, 255f,
                 0f, -1f,  0f,  0f, 255f,
                 0f,  0f, -1f,  0f, 255f,
                 0f,  0f,  0f,  1f,   0f
            )
        )
    )

    /**
     * The colour the page's own paper appears to be under [theme] — what the margins around it
     * should be, so the reader reads as one sheet rather than a page floating in a dark frame.
     *
     * Original keeps the theme's background: a PDF's paper is white, and a white surround on a
     * dark screen glares. The other two tint the page itself, so the surround follows the tint.
     */
    fun surfaceColor(theme: PdfThemeModeStore.ThemeMode, fallback: Int): Int = when (theme) {
        PdfThemeModeStore.ThemeMode.PAPER_TINT -> PAPER_SURFACE
        PdfThemeModeStore.ThemeMode.INVERT_COLORS -> INVERT_SURFACE
        else -> fallback
    }

    /** Ink that reads on [surfaceColor] — for the margin's own text, like the immersive status. */
    fun inkColor(theme: PdfThemeModeStore.ThemeMode, fallback: Int): Int = when (theme) {
        PdfThemeModeStore.ThemeMode.PAPER_TINT -> PAPER_INK
        PdfThemeModeStore.ThemeMode.INVERT_COLORS -> INVERT_INK
        else -> fallback
    }

    fun apply(
        imageView: ImageView,
        theme: PdfThemeModeStore.ThemeMode,
        isEInk: Boolean
    ) {
        when (theme) {
            PdfThemeModeStore.ThemeMode.ORIGINAL -> {
                NexusFeedEInkStyler.applyImageGrayscale(imageView, isEInk)
            }
            PdfThemeModeStore.ThemeMode.PAPER_TINT -> {
                imageView.colorFilter = paperTintFilter
            }
            PdfThemeModeStore.ThemeMode.INVERT_COLORS -> {
                imageView.colorFilter = invertColorsFilter
            }
        }
    }
}
