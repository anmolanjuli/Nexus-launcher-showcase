package com.nexus.launcher.ui.drawercategories

/**
 * Icon size for the Categories modes, taken from the drawer's own setting so a tile here is the
 * size a tile is in the grid and list modes.
 *
 * The grid sizes an icon as its cell's width times the icon-size setting; the category cards ask
 * for sizes in dp instead, so this keeps the ratio between them ([scale]) while pinning the
 * largest — a card's main grid — to what the drawer would draw.
 */
object CategoryIconSize {

    /** The dp a card's main icon grid should use; [GRID_REFERENCE_DP] until the drawer says. */
    @Volatile private var gridIconDp: Float = GRID_REFERENCE_DP

    fun set(multiplier: Float, columns: Int, widthPx: Int, density: Float) {
        if (widthPx <= 0 || columns <= 0 || density <= 0f) return
        val cellWidthDp = widthPx / density / columns
        gridIconDp = (cellWidthDp * multiplier).coerceIn(28f, 96f)
    }

    /** [iconDp] as the card asked for it, scaled to the drawer's icon size. */
    fun scale(iconDp: Float): Float = iconDp * (gridIconDp / GRID_REFERENCE_DP)

    /** The size the cards were drawn at before the setting reached them. */
    private const val GRID_REFERENCE_DP = 52f
}
