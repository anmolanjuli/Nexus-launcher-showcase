package com.nexus.launcher.ui.backup

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.room.withTransaction
import com.nexus.launcher.data.HomeScreenDao
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.di.DaoEntryPoint
import dagger.hilt.android.EntryPointAccessors
import java.io.File

internal object NovaDrawerImporter {
    private const val TAG = "NovaDrawerImporter"

    suspend fun importDrawerFolders(
        context: android.content.Context,
        dbFile: File,
        dao: HomeScreenDao,
        homeRows: List<NovaImportPersist.NovaRow>
    ) {
        if (!dbFile.exists()) return

        val roomDb = EntryPointAccessors.fromApplication(
            context.applicationContext,
            DaoEntryPoint::class.java
        ).launcherDatabase()
        val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        val homeFolderKeys = homeRows.mapNotNull { it.folderKey }.toSet()
        val parsedApps = mutableMapOf<Int, MutableSet<String>>()

        try {
            val cursor = db.rawQuery("SELECT _id, title FROM drawer_groups WHERE groupType = 'FOLDER_APP_GROUP'", null)
            val drawerGroups = mutableListOf<Pair<Int, String>>()
            while (cursor.moveToNext()) {
                val idIdx = cursor.getColumnIndex("_id")
                val titleIdx = cursor.getColumnIndex("title")
                if (idIdx >= 0 && titleIdx >= 0) {
                    drawerGroups.add(Pair(cursor.getInt(idIdx), cursor.getString(titleIdx)))
                }
            }
            cursor.close()

            roomDb.withTransaction {
                for ((groupId, title) in drawerGroups) {
                    val folderItem = HomeScreenItem(
                        packageName = "",
                        page = -2,
                        column = 0,
                        row = 0,
                        itemType = 1,
                        folderTitle = title,
                        folderConfigJson = com.nexus.launcher.ui.folder.FolderConfigCodec.toJson(com.nexus.launcher.data.FolderConfig())
                    )
                    val newFolderId = dao.insertItemAndGetId(folderItem)
                    val novaContainerKey = -200 - groupId
                    parsedApps[novaContainerKey] = mutableSetOf()

                    val appCursor = db.rawQuery("SELECT component FROM appgroups WHERE groupId = ?", arrayOf(groupId.toString()))
                    var childRank = 0
                    while (appCursor.moveToNext()) {
                        val compIdx = appCursor.getColumnIndex("component")
                        if (compIdx >= 0) {
                            val comp = appCursor.getString(compIdx)
                            if (!comp.isNullOrBlank()) {
                                val packageName = comp.substringBefore("/")
                                if (packageName.isNotBlank()) {
                                    parsedApps[novaContainerKey]?.add(packageName)
                                    val childItem = HomeScreenItem(
                                        packageName = packageName,
                                        page = -1,
                                        column = 0,
                                        row = childRank++,
                                        xFraction = 0f,
                                        yFraction = 0f,
                                        itemType = 0,
                                        containerId = newFolderId
                                    )
                                    dao.insertItem(childItem)
                                }
                            }
                        }
                    }
                    appCursor.close()

                    if (novaContainerKey !in homeFolderKeys) {
                        val fallbackItems = homeRows.filter { it.container == novaContainerKey && it.mappedItemType == 0 }
                        for (item in fallbackItems) {
                            val pkg = item.packageName
                            if (pkg.isNotBlank() && !parsedApps[novaContainerKey]!!.contains(pkg)) {
                                parsedApps[novaContainerKey]?.add(pkg)
                                val childItem = HomeScreenItem(
                                    packageName = pkg,
                                    page = -1,
                                    column = 0,
                                    row = childRank++,
                                    xFraction = 0f,
                                    yFraction = 0f,
                                    itemType = 0,
                                    containerId = newFolderId
                                )
                                dao.insertItem(childItem)
                            }
                        }
                    }
                }
            }
            Log.d(TAG, "Drawer folders import completed successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Error importing drawer folders", e)
        } finally {
            db.close()
        }
    }
}
