package com.nexus.launcher.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.nexus.launcher.data.HomeScreenDao
import com.nexus.launcher.data.HomeScreenItem

object LegacyDockMigration {

    suspend fun rescueLegacyDockItems(dao: HomeScreenDao, context: Context) {
        val all = dao.getAllItemsDebug()
        // Dock already configured (e.g. Nova/native restore) — never rewrite home rows into dock.
        if (all.any { it.page == HomeScreenViewModel.DOCK_CONTAINER }) return

        val pm = context.packageManager
        val galleryPackage = resolveGalleryPackage(pm)
        val legacyIntents = listOfNotNull(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_APP_MESSAGING) },
            Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE),
            galleryPackage?.let { pkg ->
                pm.getLaunchIntentForPackage(pkg) ?: Intent(Intent.ACTION_MAIN).apply { setPackage(pkg) }
            }
        )
        val legacyPackages = legacyIntents.mapNotNull { intent ->
            pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName ?: intent.`package`
        }.distinct().let { resolved ->
            if (galleryPackage != null && galleryPackage !in resolved) resolved + galleryPackage else resolved
        }
        val orphans = all.filter { item ->
            item.page != HomeScreenViewModel.DOCK_CONTAINER &&
                item.packageName in legacyPackages
        }.distinctBy { it.packageName }
            .sortedBy { legacyPackages.indexOf(it.packageName) }
        orphans.forEachIndexed { index, item ->
            val slot = index.coerceAtMost(4)
            // id=0: must insert a NEW dock row. Keeping the home id with OnConflict REPLACE
            // would overwrite the home row and steal the icon off the workspace.
            dao.insertItem(
                item.copy(
                    id = 0,
                    page = HomeScreenViewModel.DOCK_CONTAINER,
                    column = slot,
                    row = 0,
                    xFraction = (slot * 0.2f) + 0.1f,
                    yFraction = 0.5f
                )
            )
        }
    }

    suspend fun resetDockToDefaultApps(dao: HomeScreenDao, context: Context) {
        val pm = context.packageManager
        val defaultIntents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_APP_CONTACTS) },
            Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_APP_MESSAGING) },
            Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
        )
        val all = dao.getAllItemsDebug()
        all.filter { it.page == HomeScreenViewModel.DOCK_CONTAINER }.forEach {
            dao.removeItemById(it.id)
        }
        val slotSpan = 1f / 4f
        val newItems = mutableListOf<HomeScreenItem>()
        defaultIntents.forEachIndexed { index, intent ->
            val packageName = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName ?: return@forEachIndexed
            val item = HomeScreenItem(
                packageName = packageName,
                page = HomeScreenViewModel.DOCK_CONTAINER,
                column = index,
                row = 0,
                xFraction = (index * slotSpan) + (slotSpan / 2f),
                yFraction = 0.5f
            )
            val assignedId = dao.insertItemAndGetId(item)
            newItems.add(item.copy(id = assignedId.toInt()))
        }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            com.nexus.launcher.ui.dock.DockLayoutRenderer.commitItems(newItems)
        }
    }

    suspend fun populateDefaultDockIfEmpty(dao: HomeScreenDao, context: Context) {
        if (dao.getAllItemsDebug().isNotEmpty()) return
        val pm = context.packageManager
        val defaultIntents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_APP_CONTACTS) },
            Intent(Intent.ACTION_MAIN).apply { addCategory(Intent.CATEGORY_APP_MESSAGING) },
            Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
        )
        val maxIcons = HomeScreenViewModel.maxDockIcons.coerceAtLeast(1)
        val slotSpan = 1f / maxIcons

        defaultIntents.forEachIndexed { index, intent ->
            val packageName = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
                ?.activityInfo?.packageName ?: return@forEachIndexed
            dao.insertItem(
                HomeScreenItem(
                    packageName = packageName,
                    page = HomeScreenViewModel.DOCK_CONTAINER,
                    column = index,
                    row = 0,
                    xFraction = (index * slotSpan) + (slotSpan / 2f),
                    yFraction = 0.5f
                )
            )
        }
    }

    private fun resolveGalleryPackage(pm: PackageManager): String? {
        val galleryIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_GALLERY)
        var galleryPackage = pm.resolveActivity(galleryIntent, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName
        if (galleryPackage == null || galleryPackage == "android") {
            galleryPackage = listOf(
                "com.google.android.apps.photos",
                "com.sec.android.gallery3d"
            ).find { pkg ->
                try {
                    pm.getPackageInfo(pkg, 0)
                    true
                } catch (_: Exception) {
                    false
                }
            }
        }
        return galleryPackage
    }
}
