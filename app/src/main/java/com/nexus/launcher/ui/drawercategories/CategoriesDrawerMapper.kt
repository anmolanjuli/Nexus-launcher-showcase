package com.nexus.launcher.ui.drawercategories

import android.content.Context
import androidx.core.content.ContextCompat
import com.nexus.launcher.R
import com.nexus.launcher.domain.model.AppModel
import com.nexus.launcher.ui.DrawerCategories
import com.nexus.launcher.ui.DrawerSearchPillBuilder
import com.nexus.launcher.ui.model.DisplayItem

object CategoriesDrawerMapper {

    fun build(
        context: Context,
        apps: List<AppModel>,
        recents: List<AppModel>,
        folders: List<DisplayItem>,
        drawerCategories: DrawerCategories,
        canvas: com.nexus.launcher.ui.canvas.LauncherCanvasView? = null,
    ): CategoriesDrawerModel {
        val palette = CategoryPalette(context)
        val themeAccent = palette.mistBlue
        // Folders wear the icon they wear everywhere else; the flat glyph is the fallback for a
        // folder the canvas does not know yet.
        val folderIcon = ContextCompat.getDrawable(context, R.drawable.ic_folder_solid)
        val folderSize = CategoryFolderIcon.sizeFor(canvas)
        val collator = java.text.Collator.getInstance(
            com.nexus.launcher.locale.LocaleObserver.getEffectiveLocale(context)
        ).apply { strength = java.text.Collator.SECONDARY }
        val untagged = mutableListOf<CategoryApp>()
        val tagged = folders.groupBy { item ->
            val key = item.categoryName
            if (key.isNullOrBlank()) null else drawerCategories.idOf(key).takeIf { it > 0 }
        }
        tagged[null]?.mapTo(untagged) { toFolder(it, themeAccent, folderIcon, canvas, folderSize) }
        untagged.sortWith(compareBy(collator) { it.label })
        val categories = drawerCategories.keys.value
            .filter { it != DrawerCategories.ALL }
            .mapNotNull { key ->
                val id = drawerCategories.idOf(key)
                val members = apps.filter { drawerCategories.visibleIdFor(it.categoryId) == id }
                val folderApps = tagged[id].orEmpty()
                    .map { toFolder(it, themeAccent, folderIcon, canvas, folderSize) }
                    .sortedWith(compareBy(collator) { it.label })
                if (members.isEmpty() && folderApps.isEmpty()) return@mapNotNull null
                // Folders first, then the apps A-Z — the order the grid and list drawer use.
                val previewApps = folderApps + members
                    .map { toPreview(it, themeAccent, key) }
                    .sortedWith(compareBy(collator) { it.label })
                val recentInCat = recents.mapNotNull { recent ->
                    previewApps.firstOrNull { it.packageName == recent.packageName }
                }
                CategoryGroup(
                    name = DrawerSearchPillBuilder.categoryLabelText(context, key),
                    subtitle = context.resources.getQuantityString(
                        R.plurals.drawer_category_app_count,
                        previewApps.size,
                        previewApps.size,
                    ),
                    accent = themeAccent,
                    apps = previewApps,
                    quickActions = CategoriesShortcutLoader.load(context, members, recents, palette),
                    recents = recentInCat.ifEmpty { previewApps.filter { it.folderId == null }.take(4) },
                    id = id,
                    key = key,
                )
            }
        return CategoriesDrawerModel(categories, untagged)
    }

    fun frequent(categories: List<CategoryGroup>, recents: List<AppModel>): List<CategoryApp> {
        val byPkg = categories.flatMap { it.apps }.filter { it.folderId == null }.associateBy { it.packageName }
        val fromRecents = recents.mapNotNull { byPkg[it.packageName] }
        if (fromRecents.isNotEmpty()) return fromRecents.take(8)
        return categories.flatMap { it.apps }.filter { it.folderId == null }.take(8)
    }

    private fun toPreview(app: AppModel, color: Int, categoryName: String?) = CategoryApp(
        label = app.label,
        color = color,
        packageName = app.packageName,
        className = app.className,
        icon = app.icon,
        categoryName = categoryName,
    )

    private fun toFolder(
        item: DisplayItem,
        color: Int,
        icon: android.graphics.drawable.Drawable?,
        canvas: com.nexus.launcher.ui.canvas.LauncherCanvasView?,
        folderSize: Int,
    ): CategoryApp {
        val id = item.intent?.getLongExtra("folderId", -1L)?.takeIf { it > 0L }
        return CategoryApp(
            label = item.label,
            color = color,
            icon = id?.let { CategoryFolderIcon.of(canvas, it, folderSize) } ?: icon?.mutate(),
            folderId = id,
            categoryName = item.categoryName,
        )
    }
}
