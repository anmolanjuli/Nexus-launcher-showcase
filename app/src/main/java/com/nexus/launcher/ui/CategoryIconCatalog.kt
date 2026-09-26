package com.nexus.launcher.ui

import androidx.annotation.DrawableRes
import com.nexus.launcher.R

/**
 * Every icon a drawer category can wear, in picker order.
 *
 * Categories store an icon by its name here, never by drawable id: resource ids are renumbered
 * between builds, so a stored id could come back as a different icon after an update or a backup
 * restore. Names are permanent — rename or remove one and categories using it fall back to their
 * default icon.
 *
 * The first nine are the drawer's own drawn icons; the rest are Material Symbols Rounded, filled
 * (`ic_cat_*`, Apache License 2.0), which is the family the rest of the app's icons come from.
 * To add one: drop a 24dp single-colour vector in res/drawable and append a line here.
 */
object CategoryIconCatalog {

    val icons: List<Pair<String, Int>> = listOf(
        "grid" to R.drawable.ic_apps,
        "people" to R.drawable.ic_category_social,
        "play" to R.drawable.ic_category_media,
        "games" to R.drawable.ic_category_games,
        "briefcase" to R.drawable.ic_category_productivity,
        "wallet" to R.drawable.ic_category_finance,
        "wrench" to R.drawable.ic_category_utilities,
        "gear" to R.drawable.ic_settings,
        "tag" to R.drawable.ic_category_custom,
        "palette" to R.drawable.ic_palette,
        "heart" to R.drawable.ic_cat_heart,
        "star" to R.drawable.ic_cat_star,
        "home" to R.drawable.ic_cat_home,
        "chat" to R.drawable.ic_cat_chat,
        "music" to R.drawable.ic_cat_music,
        "movie" to R.drawable.ic_cat_movie,
        "camera" to R.drawable.ic_cat_camera,
        "book" to R.drawable.ic_cat_book,
        "news" to R.drawable.ic_cat_news,
        "school" to R.drawable.ic_cat_school,
        "ideas" to R.drawable.ic_cat_ideas,
        "code" to R.drawable.ic_cat_code,
        "cloud" to R.drawable.ic_cat_cloud,
        "shopping" to R.drawable.ic_cat_shopping,
        "savings" to R.drawable.ic_cat_savings,
        "food" to R.drawable.ic_cat_food,
        "cafe" to R.drawable.ic_cat_cafe,
        "fitness" to R.drawable.ic_cat_fitness,
        "health" to R.drawable.ic_cat_health,
        "travel" to R.drawable.ic_cat_travel,
        "car" to R.drawable.ic_cat_car,
        "map" to R.drawable.ic_cat_map,
        "globe" to R.drawable.ic_cat_globe,
        "pets" to R.drawable.ic_cat_pets,
    )

    private val byName = icons.toMap()

    @DrawableRes
    fun resOf(name: String?): Int? = name?.let { byName[it] }

    /** The icon a category shows until the user picks one. */
    fun defaultNameFor(key: String): String = when (key) {
        "All" -> "grid"
        "Social" -> "people"
        "Media" -> "play"
        "Games" -> "games"
        "Productivity" -> "briefcase"
        "Finance" -> "wallet"
        "Utilities" -> "wrench"
        "System" -> "gear"
        "Shopping" -> "shopping"
        "Travel" -> "travel"
        "Health" -> "fitness"
        "News" -> "news"
        "Education" -> "school"
        "Photography" -> "camera"
        "Music" -> "music"
        "Video" -> "movie"
        "Food" -> "food"
        "Weather" -> "cloud"
        "Personalization" -> "palette"
        else -> "tag"
    }
}
