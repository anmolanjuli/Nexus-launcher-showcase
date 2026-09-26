package com.nexus.launcher.ui.folder

import android.content.Context
import android.view.View
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.nexus.launcher.data.HomeScreenDao
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.di.DaoEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope

object FolderWindowEditBinder {

    fun bind(context: Context, button: View, folderItem: HomeScreenItem) {
        button.setOnClickListener {
            val activity = context as? FragmentActivity ?: return@setOnClickListener
            val dao = EntryPointAccessors.fromApplication(
                context.applicationContext, DaoEntryPoint::class.java
            ).homeScreenDao()
            openEditSheet(activity, folderItem, dao, activity.lifecycleScope)
        }
    }

    fun openEditSheet(
        context: Context,
        folderItem: HomeScreenItem,
        dao: HomeScreenDao,
        coroutineScope: CoroutineScope
    ) {
        FolderContextMenuActions.openEditSheet(context, folderItem, dao, coroutineScope)
    }
}
