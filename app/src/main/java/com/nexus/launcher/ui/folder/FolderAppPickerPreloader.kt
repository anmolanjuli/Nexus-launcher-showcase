package com.nexus.launcher.ui.folder

import android.content.Context
import android.content.Intent
import androidx.fragment.app.FragmentManager
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.di.DaoEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object FolderAppPickerPreloader {

    fun preloadAndShow(
        context: Context,
        fragmentManager: FragmentManager,
        folderId: Long,
        coroutineScope: CoroutineScope,
        onAppsSaved: (() -> Unit)? = null
    ) {
        com.nexus.launcher.ui.picker.FolderAppPickerOverlay.show(
            context = context,
            folderId = folderId,
            onAppsSaved = onAppsSaved
        )
    }
}
