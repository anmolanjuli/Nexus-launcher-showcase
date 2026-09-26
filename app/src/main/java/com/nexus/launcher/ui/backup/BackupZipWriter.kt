package com.nexus.launcher.ui.backup

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Zips a staging directory (manifest.json + thumbs/ + assets/) into [zipFile]. Call on IO. */
object BackupZipWriter {
    /**
     * Entries the backups list needs, written first. A zip read over a SAF stream is scanned
     * sequentially, so ordering these ahead of the (far larger) assets lets the catalog stop
     * reading as soon as it has the manifest and previews.
     */
    private fun readOrder(relativePath: String): Int = when {
        relativePath == "manifest.json" -> 0
        relativePath.startsWith("thumbs/") -> 1
        else -> 2
    }

    fun zipDirectory(stagingRoot: File, zipFile: File) {
        if (zipFile.exists()) zipFile.delete()
        ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
            stagingRoot.walkTopDown().filter { it.isFile }.sortedBy {
                readOrder(it.relativeTo(stagingRoot).path.replace('\\', '/'))
            }.forEach { file ->
                val relative = file.relativeTo(stagingRoot).path.replace('\\', '/')
                zos.putNextEntry(ZipEntry(relative))
                BufferedInputStream(FileInputStream(file)).use { input ->
                    input.copyTo(zos)
                }
                zos.closeEntry()
            }
        }
    }
}
