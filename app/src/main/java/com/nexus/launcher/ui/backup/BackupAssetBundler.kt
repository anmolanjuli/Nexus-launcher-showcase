package com.nexus.launcher.ui.backup

import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.data.prefs.NexusSettingsData
import com.nexus.launcher.data.prefs.PreferenceManager
import com.nexus.launcher.ui.folder.FolderConfigCodec
import java.io.File

/**
 * Copies referenced images into a staging [assets] tree and returns relative paths
 * for the manifest. Call on Dispatchers.IO.
 */
object BackupAssetBundler {

    data class Result(
        val assets: BackupManifestBuilder.AssetRefs,
        /** folder item id → relative cover path written into folder.coverImageFile */
        val coverPathByFolderId: Map<Int, String>,
        /** custom font id → relative asset path, for the typography manifest block */
        val fontPathById: Map<String, String> = emptyMap()
    )

    /**
     * Copies user-imported font files into the staging tree. Without this a restore would list
     * fonts whose files only ever existed on the source device.
     */
    fun bundleFonts(
        stagingRoot: File,
        fonts: List<com.nexus.launcher.typography.CustomFontEntry>
    ): Map<String, String> {
        if (fonts.isEmpty()) return emptyMap()
        val dir = File(File(stagingRoot, "assets"), "fonts").also { it.mkdirs() }
        val out = mutableMapOf<String, String>()
        for (entry in fonts) {
            val src = File(entry.filePath)
            if (!src.isFile) continue
            val safeId = entry.id.replace(Regex("[^A-Za-z0-9._-]"), "_")
            val destName = "$safeId${extensionOf(src)}"
            runCatching {
                src.copyTo(File(dir, destName), overwrite = true)
                out[entry.id] = "assets/fonts/$destName"
            }
        }
        return out
    }

    fun bundle(
        stagingRoot: File,
        items: List<HomeScreenItem>,
        settings: NexusSettingsData,
        preferenceManager: PreferenceManager
    ): Result {
        val assetsDir = File(stagingRoot, "assets").also { it.mkdirs() }
        val folderCoversDir = File(assetsDir, "folder_covers").also { it.mkdirs() }
        val appIconsDir = File(assetsDir, "app_icons").also { it.mkdirs() }
        val wallpaperDir = File(assetsDir, "wallpaper").also { it.mkdirs() }

        val folderCoverEntries = mutableListOf<Map<String, String>>()
        val coverById = mutableMapOf<Int, String>()
        for (item in items) {
            if (item.itemType != 1) continue
            val config = FolderConfigCodec.parse(item.folderConfigJson)
            val srcPath = config.customIconPackage ?: continue
            val src = File(srcPath)
            if (!src.isFile) continue
            val safeTitle = item.folderTitle.ifBlank { "folder_${item.id}" }
                .replace(Regex("[^A-Za-z0-9._-]"), "_")
            val destName = "${safeTitle}_${item.id}${extensionOf(src)}"
            val dest = File(folderCoversDir, destName)
            src.copyTo(dest, overwrite = true)
            val rel = "assets/folder_covers/$destName"
            coverById[item.id] = rel
            folderCoverEntries.add(
                mapOf("referencedIn" to "folder:${item.folderTitle.ifBlank { item.id.toString() }}", "file" to rel)
            )
        }

        val iconEntries = mutableListOf<Map<String, String>>()
        for ((pkg, iconData) in preferenceManager.getCustomIcons()) {
            val src = File(iconData)
            if (!src.isFile) continue
            val destName = "$pkg${extensionOf(src)}"
            val dest = File(appIconsDir, destName)
            src.copyTo(dest, overwrite = true)
            val rel = "assets/app_icons/$destName"
            iconEntries.add(mapOf("packageName" to pkg, "file" to rel))
        }

        val wallpaperEntries = mutableListOf<Map<String, String>>()
        val galleryPath = settings.wallpaperGalleryPath
        if (settings.wallpaperType == "gallery" && galleryPath.isNotBlank()) {
            val src = File(galleryPath)
            if (src.isFile) {
                val destName = "current${extensionOf(src)}"
                val dest = File(wallpaperDir, destName)
                src.copyTo(dest, overwrite = true)
                wallpaperEntries.add(mapOf("file" to "assets/wallpaper/$destName"))
            }
        }

        return Result(
            assets = BackupManifestBuilder.AssetRefs(
                folderCovers = folderCoverEntries,
                customAppIcons = iconEntries,
                wallpaperImages = wallpaperEntries
            ),
            coverPathByFolderId = coverById
        )
    }

    private fun extensionOf(file: File): String {
        val name = file.name
        val dot = name.lastIndexOf('.')
        return if (dot >= 0) name.substring(dot) else ".bin"
    }
}
