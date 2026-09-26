package com.nexus.launcher.ui.folder

import android.graphics.RectF
import com.nexus.launcher.data.FolderConfig
import kotlin.math.min

/** Plate-aware preview placement — keeps icon grids inside visible folder chrome. */
object FolderIconPreviewLayout {

    data class Metrics(
        val cx: Float,
        val cy: Float,
        /** Half-side of the largest square fitting the shape — the budget for grid layouts. */
        val contentRadius: Float,
        val clipBounds: RectF,
        val clipRadius: Float,
        val clipShapeType: Int,
        /**
         * Radius of the largest *circle* fitting the shape — the budget for radially arranged
         * layouts (Fan, Hero + Orbit).
         *
         * Those were previously sized against [contentRadius], which is the inscribed **square**.
         * On a circular plate that square is only 0.707 of the plate radius, so a radial layout
         * budgeted against it threw away 29% of the radius it could actually have used, and the
         * preview sat small in the middle of an empty plate. A circle of radius R fits a circular
         * plate of radius R exactly, so radial layouts get the full amount.
         */
        val radialRadius: Float = contentRadius
    )

    fun resolve(
        cx: Float,
        cy: Float,
        outerRadius: Float,
        outerRect: RectF,
        shapeStyle: Int,
        config: FolderConfig,
        density: Float
    ): Metrics {
        val shape = FolderShapeStyle.normalize(shapeStyle)
        when (shape) {
            FolderShapeStyle.FILE_FOLDER -> {
                val previewR = FolderIconShapeDraw.fileFolderPreviewRadius(outerRect, outerRadius)
                return Metrics(cx, cy, previewR, outerRect, outerRadius, shape)
            }
            FolderShapeStyle.SOFT_CAPSULE -> {
                val plateRect = FolderIconPlateDraw.cardBounds(outerRect, density, config)
                val well = FolderIconShapeDraw.softCapsuleWellBounds(plateRect)
                val previewR = FolderIconShapeDraw.softCapsulePreviewRadius(plateRect, outerRadius)
                val clipR = FolderIconPlateDraw.cardRadius(outerRadius, density, config)
                return Metrics(
                    well.centerX(),
                    well.centerY(),
                    previewR,
                    plateRect,
                    clipR,
                    FolderShapeStyle.SQUIRCLE
                )
            }
        }

        val plateRect = FolderIconPlateDraw.cardBounds(outerRect, density, config)
        val plateR = FolderIconPlateDraw.cardRadius(outerRadius, density, config)
        val clipShape = FolderIconPlateDraw.mapShapeStyle(shape)
        val clearance = inscribedInset(shape, plateRect.width(), plateR)
        val content = RectF(
            plateRect.left + clearance,
            plateRect.top + clearance,
            plateRect.right - clearance,
            plateRect.bottom - clearance
        )
        val contentR = min(content.width(), content.height()) / 2f
        return Metrics(
            content.centerX(),
            content.centerY(),
            contentR,
            plateRect,
            plateR,
            clipShape,
            radialRadius = inscribedCircle(shape, plateRect.width())
        )
    }

    /**
     * Inset from the plate bounds to the largest axis-aligned square that fits *inside* the
     * selected shape's actual path, so the preview grid fills the shape without its corners
     * being sliced off by the clip.
     *
     * These were previously hand-tuned percentages that ran 2.4x to 10x too small — a circle
     * reserved 0.055 of the plate width where the geometry needs 0.146, and a hexagon reserved
     * 0.03 against a required 0.167. The grid was drawn oversized and the shape clip simply cut
     * the overflow away, which is what made preview icons look badly fitted in every shape except
     * Square.
     *
     * For the rounded-rect family (which covers everything here except the hexagon) a centred
     * square's corner is the binding constraint, and it clears a corner of radius R exactly when
     * it is inset by `R * (1 - 1/sqrt(2))`. Square falls out as R = 0, circle as R = half.
     * Corner radii below mirror [FolderIconShapeDraw.getShapePath] and must track it.
     */
    private fun inscribedInset(shape: Int, plateWidth: Float, plateRadius: Float): Float {
        val half = plateWidth / 2f
        if (half <= 0f) return 0f
        if (shape == HEXAGON) return half * HEXAGON_INSET_RATIO
        val cornerRadius = when (shape) {
            2 -> 0f                              // Square — no corner to clear
            6 -> 0f                              // None — no plate is drawn, nothing clips
            1, FolderShapeStyle.SOFT_CAPSULE -> plateRadius * 0.4f
            5 -> plateRadius * 0.7f              // Pebble — largest of its four radii binds
            3 -> plateRadius                     // Three fully-rounded corners
            0, FolderShapeStyle.BALL, FolderShapeStyle.PILL -> half
            else -> half
        }
        return (cornerRadius * CORNER_INSET_FACTOR).coerceIn(0f, half * 0.5f)
    }

    /**
     * Radius of the largest centred circle fitting the shape. Every rounded-rect variant is
     * bounded by its flat edges rather than its corners, so they all land on half the plate
     * width; the circle shapes are their own inscribed circle; the hexagon is bounded by its
     * inradius, `circumradius * cos(30)` with the same 1.05 circumradius its path uses.
     */
    private fun inscribedCircle(shape: Int, plateWidth: Float): Float {
        val half = plateWidth / 2f
        if (half <= 0f) return 0f
        return if (shape == HEXAGON) half * HEXAGON_INRADIUS_RATIO else half
    }

    private const val HEXAGON = 4

    /** `1.05 * cos(30)` — hexagon inradius over plate half-width. */
    private const val HEXAGON_INRADIUS_RATIO = 0.90933f

    /** `1 - 1/sqrt(2)`: how far a centred square must sit in from a corner of radius R. */
    private const val CORNER_INSET_FACTOR = 0.29289322f

    /**
     * Pointy-top hexagon, circumradius `1.05 * half` (see [FolderIconShapeDraw.getShapePath]).
     * The upper slanted edge binds before the vertical sides do, giving an inscribed half-side
     * of `Rh * sqrt(3) / (sqrt(3) + 1)` and hence this inset.
     */
    private const val HEXAGON_INSET_RATIO = 0.33428f
}
