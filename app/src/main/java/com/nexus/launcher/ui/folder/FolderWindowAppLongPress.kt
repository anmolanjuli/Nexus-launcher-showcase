package com.nexus.launcher.ui.folder

import android.content.Context
import android.view.View
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.di.DaoEntryPoint
import com.nexus.launcher.ui.MainActivity
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object FolderWindowAppLongPress {

    fun handle(
        context: Context,
        item: HomeScreenItem,
        anchor: View,
        onRemoved: () -> Unit
    ) {
        var activity: MainActivity? = null
        var currentContext = context
        while (currentContext is android.content.ContextWrapper) {
            if (currentContext is MainActivity) {
                activity = currentContext
                break
            }
            currentContext = currentContext.baseContext
        }
        if (activity == null) return
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        val dao = EntryPointAccessors.fromApplication(
            context.applicationContext, DaoEntryPoint::class.java
        ).homeScreenDao()
        FolderWindowAppMenuLauncher.show(
            context = context,
            anchorView = anchor,
            item = item,
            onRemove = {
                scope.launch {
                    FolderRemoveEngine.removeFromFolder(item, dao)
                    kotlinx.coroutines.withContext(Dispatchers.Main) { onRemoved() }
                }
            }
        )
    }
}
