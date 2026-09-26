package com.nexus.launcher.ui.folder

import android.content.Context
import com.nexus.launcher.R

object FolderEditShapeHelper {

    val basicShapeIds = listOf("6", "0", "1", "2", "3", "4", "5")
    val specialShapeIds = listOf(
        FolderShapeStyle.FILE_FOLDER.toString(),
        FolderShapeStyle.SOFT_CAPSULE.toString(),
        FolderShapeStyle.BALL.toString()
    )

    fun allEntries(context: Context): List<FolderShapeCarouselEntry> = buildList {
        add(FolderShapeCarouselEntry.Section(context.getString(R.string.folder_shape_category_basic)))
        basicShapeIds.forEach { id ->
            add(FolderShapeCarouselEntry.Shape(id, labelFor(context, id.toIntOrNull() ?: 1)))
        }
        add(FolderShapeCarouselEntry.Section(context.getString(R.string.folder_shape_category_special)))
        specialShapeIds.forEach { id ->
            add(FolderShapeCarouselEntry.Shape(id, labelFor(context, id.toIntOrNull() ?: FolderShapeStyle.SQUIRCLE)))
        }
    }

    fun bind(
        context: Context,
        preferredShape: Int,
        shapeThumbs: FolderShapeThumbnailRow
    ): Int {
        val shape = FolderShapeStyle.normalize(preferredShape)
        val entries = allEntries(context)
        val selected = shape.toString().takeIf { v -> entries.any { it is FolderShapeCarouselEntry.Shape && it.id == v } }
            ?: FolderShapeStyle.SQUIRCLE.toString()
        shapeThumbs.configure(entries, selected)
        return selected.toIntOrNull() ?: shape
    }

    private fun labelFor(context: Context, shape: Int): String = when (shape) {
        6 -> context.getString(R.string.folder_shape_none)
        0 -> context.getString(R.string.folder_shape_round)
        1 -> context.getString(R.string.folder_shape_squircle)
        2 -> context.getString(R.string.folder_shape_square)
        3 -> context.getString(R.string.folder_shape_teardrop)
        4 -> context.getString(R.string.folder_shape_hexagon)
        5 -> context.getString(R.string.folder_shape_pebble)
        FolderShapeStyle.FILE_FOLDER -> context.getString(R.string.folder_shape_file_folder)
        FolderShapeStyle.SOFT_CAPSULE -> context.getString(R.string.folder_shape_soft_capsule)
        FolderShapeStyle.BALL -> context.getString(R.string.folder_shape_ball)
        else -> context.getString(R.string.folder_shape_squircle)
    }
}
