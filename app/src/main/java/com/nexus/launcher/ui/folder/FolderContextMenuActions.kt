package com.nexus.launcher.ui.folder

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.LinearLayout
import androidx.fragment.app.FragmentActivity
import com.nexus.launcher.R
import com.nexus.launcher.data.HomeScreenDao
import com.nexus.launcher.data.HomeScreenItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object FolderContextMenuActions {

    fun wireMenuActions(
        context: Context,
        folderItem: HomeScreenItem,
        dao: HomeScreenDao,
        coroutineScope: CoroutineScope,
        menuWrapper: View,
        onSelect: (() -> Unit)? = null,
        dismiss: () -> Unit
    ) {
        menuWrapper.findViewById<LinearLayout>(R.id.folder_menu_add).setOnClickListener {
            dismiss()
            openAppPicker(context, folderItem, coroutineScope)
        }
        menuWrapper.findViewById<LinearLayout>(R.id.folder_menu_edit).setOnClickListener {
            dismiss()
            openEditSheet(context, folderItem, dao, coroutineScope)
        }
        menuWrapper.findViewById<LinearLayout>(R.id.folder_menu_select)?.setOnClickListener {
            dismiss()
            onSelect?.invoke()
        }
        menuWrapper.findViewById<LinearLayout>(R.id.folder_menu_resize)?.setOnClickListener {
            dismiss()
            (context as? com.nexus.launcher.ui.MainActivity)?.homeEditController?.showFolderResizeMode(folderItem)
        }
        val flipMenu = menuWrapper.findViewById<LinearLayout>(R.id.folder_menu_flip)
        if (folderItem.page >= 0) {
            flipMenu?.visibility = View.VISIBLE
            flipMenu?.setOnClickListener {
                dismiss()
                (context as? com.nexus.launcher.ui.MainActivity)?.homeEditController?.showFolderFlipMode(folderItem)
            }
        } else {
            flipMenu?.visibility = View.GONE
        }
        menuWrapper.findViewById<LinearLayout>(R.id.folder_menu_remove).setOnClickListener {
            dismiss()
            confirmRemove(context, folderItem, dao, coroutineScope)
        }
    }

    fun openAppPicker(context: Context, folderItem: HomeScreenItem, coroutineScope: CoroutineScope) {
        val activity = context as? FragmentActivity ?: return
        FolderAppPickerBottomSheet.preloadAndShow(
            context,
            activity.supportFragmentManager,
            folderItem.resolveFolderContentsId(),
            coroutineScope,
            onAppsSaved = {
                FolderWindowManager.refreshOpenWindowIfShowing(context)
            }
        )
    }

    fun openEditSheet(
        context: Context,
        folderItem: HomeScreenItem,
        dao: HomeScreenDao,
        coroutineScope: CoroutineScope
    ) {
        val activity = context as? FragmentActivity ?: return
        FolderEditBottomSheet(folderItem, dao, coroutineScope)
            .show(activity.supportFragmentManager, "FolderEditBottomSheet")
    }

    fun confirmRemove(
        context: Context,
        folderItem: HomeScreenItem,
        dao: HomeScreenDao,
        coroutineScope: CoroutineScope
    ) {
        coroutineScope.launch(Dispatchers.IO) {
            val contentsId = folderItem.resolveFolderContentsId()
            val appCount = dao.getItemsInFolderSync(contentsId).size
            withContext(Dispatchers.Main) {
                FolderAuroraDialogs.showRemove(context, appCount) {
                    coroutineScope.launch(Dispatchers.IO) {
                        if (folderItem.id.toLong() == contentsId) {
                            FolderActionEngine.removeFolder(contentsId, dao)
                        } else {
                            dao.removeItemById(folderItem.id)
                        }
                    }
                }
            }
        }
    }
}
