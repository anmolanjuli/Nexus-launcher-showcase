package com.nexus.launcher.ui.folder

import android.content.Context
import android.content.res.ColorStateList
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.nexus.launcher.R
import com.nexus.launcher.data.FolderConfig
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.di.DaoEntryPoint
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import dagger.hilt.android.EntryPointAccessors

/** Minimal header chrome: title left, inline actions right. */
object FolderWindowChromeBinder {

    fun bind(
        context: Context,
        folderCard: LinearLayout,
        overlayRoot: FrameLayout,
        folderItem: HomeScreenItem,
        windowConfig: FolderConfig,
        onConfigChanged: (FolderConfig) -> Unit,
        onRefreshApps: () -> Unit,
        onItemUpdated: (HomeScreenItem) -> Unit = {}
    ) {
        val tokens = try {
            ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }

        val titleEdit = folderCard.findViewById<EditText>(R.id.folder_title_text)
        FolderWindowTitleBinder.bind(titleEdit, folderItem, onItemUpdated)

        val editButton = folderCard.findViewById<ImageButton>(R.id.folder_edit_button)
        val sortButton = folderCard.findViewById<ImageButton>(R.id.folder_sort_button)
        val addButton = folderCard.findViewById<ImageButton>(R.id.folder_add_button)

        editButton.imageTintList = ColorStateList.valueOf(tokens.textPrimary)
        addButton.imageTintList = ColorStateList.valueOf(tokens.textPrimary)
        sortButton.imageTintList = ColorStateList.valueOf(tokens.textPrimary)
        sortButton.clearColorFilter()

        val activity = context as? FragmentActivity
        val dao = EntryPointAccessors.fromApplication(
            context.applicationContext, DaoEntryPoint::class.java
        ).homeScreenDao()

        editButton.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            activity ?: return@setOnClickListener
            FolderWindowEditBinder.openEditSheet(
                context, folderItem, dao, activity.lifecycleScope
            )
        }

        sortButton.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            FolderWindowSortBinder.showSortDropdown(
                sortButton, windowConfig, overlayRoot, onConfigChanged, folderItem.resolveFolderContentsId(), dao, context
            )
        }

        addButton.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            FolderWindowSortBinder.openAppPicker(context, folderItem, onRefreshApps)
        }
    }
}
