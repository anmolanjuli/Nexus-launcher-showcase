package com.nexus.launcher.ui.folder

import android.content.Context
import androidx.activity.ComponentDialog
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.LifecycleOwner

object FolderEditBackPressHandler {

    fun register(
        dialog: ComponentDialog?,
        lifecycleOwner: LifecycleOwner,
        context: Context,
        hasChanges: () -> Boolean,
        onDismiss: () -> Unit
    ) {
        dialog?.onBackPressedDispatcher?.addCallback(
            lifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (hasChanges()) {
                        FolderAuroraDialogs.showDiscard(context) { onDismiss() }
                    } else {
                        onDismiss()
                    }
                }
            }
        )
    }
}
