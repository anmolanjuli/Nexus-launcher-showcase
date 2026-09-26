package com.nexus.launcher.ui.folder

import android.content.Context
import android.graphics.Color
import android.widget.LinearLayout
import com.nexus.launcher.R
import com.nexus.launcher.data.HomeScreenDao
import com.nexus.launcher.data.HomeScreenItem
import kotlinx.coroutines.CoroutineScope

/**
 * Builds the flat (non-radial) folder context menu's title + rows into [menuCard]. Extracted from
 * [FolderContextMenuLauncher] to keep that file under the file-size limit.
 */
internal object FolderFlatMenuRows {
    fun build(
        menuCard: LinearLayout,
        context: Context,
        folderItem: HomeScreenItem,
        dao: HomeScreenDao,
        coroutineScope: CoroutineScope,
        accentColor: Int,
        onSelect: (() -> Unit)?,
        dismiss: () -> Unit
    ) {
        val density = context.resources.displayMetrics.density
        val isEInk = false
        val tokens = com.nexus.launcher.ui.NexusContextMenuDesignHelper.resolveTokens(context)

        val titleView = android.widget.TextView(context).apply {
            text = FolderContextMenuLauncher.folderDisplayName(context, folderItem)
            typeface = if (isEInk) android.graphics.Typeface.MONOSPACE else null
            com.nexus.launcher.typography.NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
            setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (12 * density).toInt())
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        menuCard.addView(titleView)
        menuCard.addView(com.nexus.launcher.ui.NexusDesignSystem.buildContextMenuDivider(context))

        menuCard.addView(com.nexus.launcher.ui.NexusDesignSystem.buildContextMenuRow(context, context.getString(R.string.action_add_apps), R.drawable.ic_add, isDestructive = false) {
            dismiss()
            FolderContextMenuActions.openAppPicker(context, folderItem, coroutineScope)
        })
        menuCard.addView(com.nexus.launcher.ui.NexusDesignSystem.buildContextMenuDivider(context))

        menuCard.addView(com.nexus.launcher.ui.NexusDesignSystem.buildContextMenuRow(context, context.getString(R.string.action_edit), R.drawable.ic_edit, isDestructive = false) {
            dismiss()
            FolderContextMenuActions.openEditSheet(context, folderItem, dao, coroutineScope)
        })
        menuCard.addView(com.nexus.launcher.ui.NexusDesignSystem.buildContextMenuDivider(context))

        if (folderItem.page < 0) {
            // Only drawer folders (not home-screen folders) filter by category chip.
            menuCard.addView(com.nexus.launcher.ui.NexusDesignSystem.buildContextMenuRow(context, context.getString(R.string.action_set_category), R.drawable.ic_category, isDestructive = false) {
                dismiss()
                val activity = context as? com.nexus.launcher.ui.MainActivity ?: return@buildContextMenuRow
                val viewModel = activity.viewModel
                val current = viewModel.getFolderCategory(folderItem.id.toLong()) ?: 0
                com.nexus.launcher.ui.settings.CategoryPickerSheet.show(
                    activity.supportFragmentManager, viewModel.categories, current,
                    title = context.getString(R.string.menu_change_category),
                    context = activity
                ) { categoryId ->
                    viewModel.setFolderCategory(folderItem.id.toLong(), categoryId)
                }
            })
            menuCard.addView(com.nexus.launcher.ui.NexusDesignSystem.buildContextMenuDivider(context))
        }

        menuCard.addView(com.nexus.launcher.ui.NexusDesignSystem.buildContextMenuRow(context, context.getString(R.string.action_select), R.drawable.ic_select, isDestructive = false) {
            dismiss()
            onSelect?.invoke()
        })
        menuCard.addView(com.nexus.launcher.ui.NexusDesignSystem.buildContextMenuDivider(context))

        menuCard.addView(com.nexus.launcher.ui.NexusDesignSystem.buildContextMenuRow(context, context.getString(R.string.folder_context_resize), R.drawable.ic_resize, isDestructive = false) {
            dismiss()
            (context as? com.nexus.launcher.ui.MainActivity)?.homeEditController?.showFolderResizeMode(folderItem)
        })
        menuCard.addView(com.nexus.launcher.ui.NexusDesignSystem.buildContextMenuDivider(context))

        if (folderItem.page >= 0) {
            menuCard.addView(com.nexus.launcher.ui.NexusDesignSystem.buildContextMenuRow(context, context.getString(R.string.folder_context_flip), R.drawable.ic_flip, isDestructive = false) {
                dismiss()
                (context as? com.nexus.launcher.ui.MainActivity)?.homeEditController?.showFolderFlipMode(folderItem)
            })
            menuCard.addView(com.nexus.launcher.ui.NexusDesignSystem.buildContextMenuDivider(context))
        }

        menuCard.addView(com.nexus.launcher.ui.NexusDesignSystem.buildContextMenuRow(context, context.getString(R.string.action_remove), R.drawable.ic_remove, isDestructive = true) {
            dismiss()
            FolderContextMenuActions.confirmRemove(context, folderItem, dao, coroutineScope)
        })
    }
}
