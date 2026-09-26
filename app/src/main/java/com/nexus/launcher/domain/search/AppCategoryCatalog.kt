package com.nexus.launcher.domain.search

/**
 * Every category an app can be sorted into, and what happens when one is not on screen.
 *
 * Ids 1..7 are the categories the drawer starts with; 8 and up are the suggested ones a user can
 * add ([SUGGESTED]). [CategoryEngine] always sorts an app into the most specific id it can, even
 * when that category has not been added — [fallbackOf] then walks it up to one that is there
 * (Photography → Media, Shopping → Utilities, and so on). So adding a suggested category
 * gathers its apps out of the broader ones straight away, and removing a category hands its apps
 * back to the nearest fitting one instead of dropping them into a heap.
 *
 * Ids are permanent: they are what app assignments, folder tags and backups store. Only append.
 */
object AppCategoryCatalog {

    const val SOCIAL = 1
    const val MEDIA = 2
    const val GAMES = 3
    const val PRODUCTIVITY = 4
    const val FINANCE = 5
    const val UTILITIES = 6
    const val SYSTEM = 7

    const val SHOPPING = 8
    const val TRAVEL = 9
    const val HEALTH = 10
    const val NEWS = 11
    const val EDUCATION = 12
    const val PHOTOGRAPHY = 13
    const val MUSIC = 14
    const val VIDEO = 15
    const val FOOD = 16
    const val WEATHER = 17
    const val PERSONALIZATION = 18

    /** The first id that is not on by default. */
    const val FIRST_SUGGESTED = SHOPPING

    /** Suggested categories, in the order the "add a category" list offers them. */
    val SUGGESTED = listOf(
        SHOPPING, TRAVEL, HEALTH, NEWS, EDUCATION, PHOTOGRAPHY, MUSIC, VIDEO, FOOD, WEATHER,
        PERSONALIZATION,
    )

    /** Where an app goes while its own category is not on screen. */
    private val FALLBACK = mapOf(
        SHOPPING to UTILITIES,
        TRAVEL to UTILITIES,
        HEALTH to UTILITIES,
        NEWS to MEDIA,
        EDUCATION to PRODUCTIVITY,
        PHOTOGRAPHY to MEDIA,
        MUSIC to MEDIA,
        VIDEO to MEDIA,
        FOOD to UTILITIES,
        WEATHER to UTILITIES,
        PERSONALIZATION to UTILITIES,
    )

    /** The next category to try for [id], or null once there is nowhere broader to go. */
    fun fallbackOf(id: Int): Int? = FALLBACK[id]
}
