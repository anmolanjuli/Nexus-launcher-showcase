package com.nexus.launcher.ui.folder

import android.content.Context
import androidx.fragment.app.FragmentManager
import com.nexus.launcher.ui.settings.IconEditGestureSection

object FolderEditGesturesHelper {

    fun attach(
        bodyViews: FolderEditBodyViews,
        context: Context,
        fragmentManager: FragmentManager,
        dp: Float,
        pendingSwipeUp: String,
        pendingSwipeDown: String,
        onSwipeUpChanged: (String) -> Unit,
        onSwipeDownChanged: (String) -> Unit
    ) {
        bodyViews.gesturesContainer.removeAllViews()
        bodyViews.gesturesContainer.addView(
            IconEditGestureSection.build(
                context = context,
                fragmentManager = fragmentManager,
                dp = dp,
                itemType = 1,
                initialSwipeUp = pendingSwipeUp,
                initialSwipeDown = pendingSwipeDown,
                initialDoubleTap = "",
                onSwipeUpChanged = onSwipeUpChanged,
                onSwipeDownChanged = onSwipeDownChanged,
                onDoubleTapChanged = { },
                showDoubleTap = false,
                showGesturesLabel = false
            )
        )
    }
}
