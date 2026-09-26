package com.nexus.launcher.ui.island

/** A page of the opened island. */
enum class IslandPage {
    /** Whatever is live right now — the call, the track, the download — with its controls. */
    ACTIVITY,

    /** A glance: weather, battery, torch, notifications. */
    GLANCE,
}

/**
 * What the opened island can show, in order: a page for each thing that is live right now,
 * then the glance.
 *
 * One page per live thing, rather than one page for whichever is on top: with a stopwatch, a
 * route and a track all running, the open card used to show the first of them and the only way
 * to reach the others was to close it, swipe the capsule, and open it again. Now they are all
 * pages of the same card, and a swipe inside it moves between them.
 */
object IslandPages {

    fun of(live: List<IslandPayload>): List<IslandPage> {
        val pages = MutableList(live.size) { IslandPage.ACTIVITY }
        pages.add(IslandPage.GLANCE)
        return pages
    }
}
