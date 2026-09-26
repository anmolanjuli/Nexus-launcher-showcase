package com.nexus.launcher.ui

import android.content.Context
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.domain.model.AppModel
import com.nexus.launcher.ui.model.DisplayItem

import java.text.Collator
import java.util.Locale

/**
 * Builds the drawer's browse/search list. Extracted from [MainViewModel] to keep that file under
 * the file-size limit.
 *
 * Drawer folders previously had no category of their own, so they showed under every category
 * tab unconditionally (added in unfiltered after [base]'s own category filtering already ran).
 * [folderCategories] (packageName-less, keyed by folder id) lets a folder be tagged like an app
 * and filtered the same way in browse mode — search still surfaces every folder regardless of
 * category, matching how search already ignores the category filter for apps ([all] vs [base]).
 */
object DrawerItemsBuilder {
    /** Hidden apps removed, renames applied, narrowed to [categoryId] when given, then sorted. */
    fun prepare(
        apps: List<AppModel>, hidden: Set<String>, renamed: Map<String, String>,
        sort: com.nexus.launcher.ui.model.SortType, categoryId: Int?,
        locale: Locale = Locale.getDefault(),
        resolveCategory: (Int) -> Int = { it },
    ): List<AppModel> {
        val visible = apps.filter { it.packageName !in hidden }
            .map { app -> renamed[app.packageName]?.let { app.copy(label = it) } ?: app }
            .filter { categoryId == null || resolveCategory(it.categoryId) == categoryId }
        val collator = Collator.getInstance(locale).apply { strength = Collator.SECONDARY }
        return when (sort) {
            com.nexus.launcher.ui.model.SortType.ALPHABETICAL -> visible.sortedWith(compareBy(collator) { it.label })
            com.nexus.launcher.ui.model.SortType.ALPHABETICAL_DESC -> visible.sortedWith(compareByDescending(collator) { it.label })
            com.nexus.launcher.ui.model.SortType.INSTALL_DATE -> visible.sortedByDescending { it.installTime }
            com.nexus.launcher.ui.model.SortType.LAST_USED -> visible
        }
    }

    /** A first-run dock: the phone, messaging and camera apps it can find, topped up to five. */
    fun suggestedDock(context: Context, apps: List<AppModel>, categories: DrawerCategories): List<DisplayItem> {
        val keywords = listOf("dialer", "contacts", "mms", "messaging", "camera", "gallery", "photos")
        val dock = mutableListOf<AppModel>()
        for (kw in keywords) {
            val app = apps.firstOrNull { it.packageName.contains(kw, true) || it.label.contains(kw, true) }
            if (app != null && app !in dock && dock.size < 5) dock.add(app)
        }
        for (app in apps) {
            if (dock.size >= 5) break
            if (app !in dock) dock.add(app)
        }
        return dock.map { app ->
            val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
            DisplayItem(app.label, app.icon, intent, categories.keyForApp(app.categoryId))
        }
    }

    fun build(
        context: Context,
        categories: DrawerCategories,
        base: List<AppModel>,
        all: List<AppModel>,
        query: String,
        hidden: Set<String>,
        drawerFolderItems: List<HomeScreenItem>,
        folderCategories: Map<Long, Int>,
        selectedCategory: String,
        foldered: Set<String>
    ): List<DisplayItem> {
        val base = base.filter { it.packageName !in foldered }
        val all = all.filter { it.packageName !in foldered }

        fun folderDisplayItem(folder: HomeScreenItem): DisplayItem {
            val catId = folderCategories[folder.id.toLong()]
            val folderIntent = android.content.Intent("nexus.folder.OPEN").apply {
                putExtra("folderId", folder.id.toLong())
                putExtra("folderTitle", folder.folderTitle)
            }
            return DisplayItem(folder.folderTitle, null, folderIntent, catId?.let { categories.keyFor(it) })
        }

        val locale = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
        val collator = Collator.getInstance(locale).apply { strength = Collator.SECONDARY }

        if (query.isBlank()) {
            val visibleFolders = drawerFolderItems.filter { folder ->
                selectedCategory == "All" ||
                    folderCategories[folder.id.toLong()] == categories.idOf(selectedCategory)
            }.map(::folderDisplayItem)
            val baseMapped = base.map { app ->
                val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                DisplayItem(app.label, app.icon, intent, categories.keyForApp(app.categoryId))
            }
            return visibleFolders.sortedWith(compareBy(collator) { it.label }) + baseMapped
        }

        val q = query.trim().lowercase()
        val folderResults = drawerFolderItems.map(::folderDisplayItem)
            .filter { it.label.lowercase().contains(q) }
        val regularResults = all.filter { app ->
            app.label.lowercase().contains(q) || app.packageName.lowercase().contains(q)
        }.map { app ->
            val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
            DisplayItem(app.label, app.icon, intent, categories.keyForApp(app.categoryId))
        }
        val hiddenMatches = hidden
            .mapNotNull { pkg -> all.find { it.packageName == pkg } }
            .filter { app -> app.label.lowercase().contains(q) || app.packageName.lowercase().contains(q) }
            .map { app ->
                val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                DisplayItem(app.label, app.icon, intent, "Hidden")
            }
        return (folderResults + regularResults + hiddenMatches).sortedWith(
            compareByDescending<DisplayItem> { it.intent?.action == "nexus.folder.OPEN" }
                .thenBy { !it.label.lowercase().startsWith(q) }
                .thenBy(collator) { it.label }
        )
    }
}
