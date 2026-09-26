package com.nexus.launcher.ui.island

internal class IslandSession {
    var shape: IslandShape = IslandShape.DORMANT
    var primary: IslandPayload? = null
    var secondary: IslandPayload? = null
    var pinned: Boolean = false
    var expanded: Boolean = false
    var dismissed: MutableSet<String> = mutableSetOf()
    var lastActivityMs: Long = 0L
    /** Nothing has happened for a while: the resting capsule steps back, but stays reachable. */
    var idleDimmed: Boolean = false

    private var activeList: List<IslandPayload> = emptyList()
    private var activeIndex: Int = 0

    val hasMultipleFeatures: Boolean
        get() = activeList.size > 1

    /** Everything live right now, in priority order — one page each in the open card. */
    val active: List<IslandPayload>
        get() = activeList

    /** Which of them the controls act on. */
    val selectedIndex: Int
        get() = activeIndex

    /** Follow the page the card is showing, so play, pause and the app's buttons hit that one. */
    fun select(index: Int) {
        if (index !in activeList.indices || index == activeIndex) return
        activeIndex = index
        recomputePrimarySecondary()
        lastActivityMs = System.currentTimeMillis()
    }

    fun applyActive(active: List<IslandPayload>) {
        val live = active.filter { it.identity !in dismissed }
        activeList = live
        if (activeIndex >= live.size) {
            activeIndex = 0
        }
        recomputePrimarySecondary()
        if (primary == null) {
            expanded = false
            pinned = false
            shape = IslandShape.DORMANT
        } else if (expanded) {
            shape = IslandShape.EXPANDED
        } else if (secondary != null) {
            shape = IslandShape.MULTI
        } else {
            shape = IslandShape.COMPACT
        }
        if (primary != null) {
            lastActivityMs = System.currentTimeMillis()
            idleDimmed = false
        }
    }

    private fun recomputePrimarySecondary() {
        if (activeList.isEmpty()) {
            primary = null
            secondary = null
            return
        }
        val safeIdx = activeIndex.coerceIn(0, activeList.size - 1)
        primary = activeList[safeIdx]
        secondary = if (activeList.size > 1) {
            activeList[(safeIdx + 1) % activeList.size]
        } else null
    }

    fun cycleNext(): Boolean {
        if (activeList.size <= 1) return false
        activeIndex = (activeIndex + 1) % activeList.size
        recomputePrimarySecondary()
        lastActivityMs = System.currentTimeMillis()
        return true
    }

    fun cyclePrev(): Boolean {
        if (activeList.size <= 1) return false
        activeIndex = (activeIndex - 1 + activeList.size) % activeList.size
        recomputePrimarySecondary()
        lastActivityMs = System.currentTimeMillis()
        return true
    }

    fun tap(): IslandTapResult {
        lastActivityMs = System.currentTimeMillis()
        idleDimmed = false
        if (primary == null) {
            if (expanded) {
                expanded = false
                shape = IslandShape.DORMANT
                return IslandTapResult.RETRACT
            } else {
                expanded = true
                shape = IslandShape.EXPANDED
                return IslandTapResult.EXPAND
            }
        }
        if (expanded) {
            expanded = false
            shape = if (secondary != null) IslandShape.MULTI else IslandShape.COMPACT
            return IslandTapResult.RETRACT
        }
        expanded = true
        shape = IslandShape.EXPANDED
        return IslandTapResult.EXPAND
    }

    fun collapse() {
        expanded = false
        shape = if (primary != null) {
            if (secondary != null) IslandShape.MULTI else IslandShape.COMPACT
        } else {
            IslandShape.DORMANT
        }
        lastActivityMs = System.currentTimeMillis()
    }

    fun dismissCurrent() {
        primary?.let { dismissed.add(it.identity) }
        expanded = false
        pinned = false
        primary = null
        if (secondary != null) {
            primary = secondary
            secondary = null
            shape = IslandShape.COMPACT
        } else {
            shape = IslandShape.DORMANT
        }
    }

    fun togglePin() {
        pinned = !pinned
        lastActivityMs = System.currentTimeMillis()
    }

    fun swap() {
        cycleNext()
    }
}

internal enum class IslandTapResult { NONE, EXPAND, RETRACT, LAUNCH }
