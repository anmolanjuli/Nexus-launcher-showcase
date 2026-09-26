package com.nexus.launcher.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which layout shape the home screen is currently showing, as an [ItemPosition] shape string.
 *
 * Portrait phone is the base shape: its positions live in `home_screen_items` itself. Every
 * other shape reads and writes its own rows in `item_positions` through
 * [ShapeAwareHomeScreenDao]. Only the home activity sets a non-base shape, and only while it
 * is started — anything that writes while it is in the background (backup restore from
 * Settings, for instance) therefore writes base positions.
 */
object LayoutShapeState {

    private val _active = MutableStateFlow(ItemPosition.SHAPE_PHONE_PORTRAIT)
    val active: StateFlow<String> = _active.asStateFlow()

    val isBase: Boolean get() = _active.value == ItemPosition.SHAPE_PHONE_PORTRAIT

    /** Base (portrait) grid size, kept current from settings; used to give items created in
     *  another shape a real base cell. */
    @Volatile var baseColumns: Int = com.nexus.launcher.data.prefs.NexusDefaults.HOME_COLUMNS
    @Volatile var baseRows: Int = com.nexus.launcher.data.prefs.NexusDefaults.HOME_ROWS

    /** Grid size currently laid out on screen (the landscape grid in landscape). */
    @Volatile var liveColumns: Int = baseColumns
    @Volatile var liveRows: Int = baseRows

    fun setActive(shape: String) {
        _active.value = shape
    }

    /**
     * Derives live positions for the items of a non-base shape that the user has not placed by
     * hand: (shape, base items, manual positions for that shape) -> derived positions. Set by
     * the UI layer (it needs the shape's grid geometry); null until then, when non-base shapes
     * show items at their base positions.
     */
    @Volatile var deriver: ((String, List<HomeScreenItem>, List<ItemPosition>) -> List<ItemPosition>)? = null

    private val _deriveEpoch = MutableStateFlow(0)

    /** Bumped when derived positions must be recomputed (the shape's grid geometry changed). */
    val deriveEpoch: StateFlow<Int> = _deriveEpoch.asStateFlow()

    fun invalidateDerived() {
        _deriveEpoch.value = _deriveEpoch.value + 1
    }
}
