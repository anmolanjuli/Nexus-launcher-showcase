package com.nexus.launcher.domain.search

import android.content.pm.ApplicationInfo
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sorts an app into a category id from [AppCategoryCatalog].
 *
 * Three things are asked, in order of how much they can be trusted:
 * 1. the handful of packages whose names say nothing useful ([CategoryKeywords.EXACT]), and the
 *    OS components;
 * 2. what the app itself declares — `ApplicationInfo.category` and the older "is a game" flag,
 *    which the Play Store fills in and which is why games no longer scatter across Productivity;
 * 3. the words in its package name, then in its own name ([CategoryKeywords]).
 *
 * Anything still unmatched is a Utility. The result can be a category the user has not added —
 * [com.nexus.launcher.ui.DrawerCategories] walks it up to one that is on screen.
 */
@Singleton
class CategoryEngine @Inject constructor() {

    fun categorize(packageName: String): Int = categorize(packageName, label = "", info = null)

    fun categorize(packageName: String, label: String, info: ApplicationInfo?): Int {
        val pkg = packageName.lowercase()
        CategoryKeywords.EXACT[pkg]?.let { return it }
        if (CategoryKeywords.SYSTEM_PREFIXES.any { pkg.startsWith(it) }) return AppCategoryCatalog.SYSTEM
        if (isGame(info)) return AppCategoryCatalog.GAMES
        CategoryKeywords.match(pkg)?.let { return it }
        declared(info)?.let { return it }
        CategoryKeywords.match(label.lowercase())?.let { return it }
        return AppCategoryCatalog.UTILITIES
    }

    private fun isGame(info: ApplicationInfo?): Boolean {
        info ?: return false
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            info.category == ApplicationInfo.CATEGORY_GAME
        ) return true
        @Suppress("DEPRECATION")
        return (info.flags and ApplicationInfo.FLAG_IS_GAME) != 0
    }

    /** The category the app declares for itself, mapped onto ours. */
    private fun declared(info: ApplicationInfo?): Int? {
        if (info == null || android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return null
        return when (info.category) {
            ApplicationInfo.CATEGORY_AUDIO -> AppCategoryCatalog.MUSIC
            ApplicationInfo.CATEGORY_VIDEO -> AppCategoryCatalog.VIDEO
            ApplicationInfo.CATEGORY_IMAGE -> AppCategoryCatalog.PHOTOGRAPHY
            ApplicationInfo.CATEGORY_SOCIAL -> AppCategoryCatalog.SOCIAL
            ApplicationInfo.CATEGORY_NEWS -> AppCategoryCatalog.NEWS
            ApplicationInfo.CATEGORY_MAPS -> AppCategoryCatalog.TRAVEL
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppCategoryCatalog.PRODUCTIVITY
            else -> null
        }
    }
}
