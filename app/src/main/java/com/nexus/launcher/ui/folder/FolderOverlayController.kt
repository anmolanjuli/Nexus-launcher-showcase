package com.nexus.launcher.ui.folder

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import java.lang.ref.WeakReference

/** Attaches folder overlay to activity mainContainer and handles back dismissal. */
internal object FolderOverlayController {

    private var currentOverlay: View? = null
    private var backCallback: OnBackPressedCallback? = null
    private var ownerActivity: WeakReference<Activity>? = null

    fun isShowing(): Boolean = currentOverlay?.visibility == View.VISIBLE

    fun getOverlay(): View? = currentOverlay

    fun getOrInflateOverlay(activity: Activity, onDismiss: () -> Unit): View {
        val host = activity as? ComponentActivity ?: return currentOverlay ?: throw IllegalStateException("Activity is not ComponentActivity")
        
        if (currentOverlay == null || ownerActivity?.get() !== activity) {
            // Clean up any stale overlay if the activity changed
            if (currentOverlay != null) {
                currentOverlay?.let { overlay ->
                    (overlay.parent as? ViewGroup)?.removeView(overlay)
                }
            }

            val inflater = android.view.LayoutInflater.from(activity)
            val decorView = activity.window.decorView as ViewGroup
            val overlayRoot = inflater.inflate(com.nexus.launcher.R.layout.view_folder_window, decorView, false)
            
            val mainContainer = activity.findViewById<android.widget.FrameLayout>(com.nexus.launcher.R.id.main_container)
            mainContainer.addView(
                overlayRoot,
                android.widget.FrameLayout.LayoutParams(
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
            overlayRoot.visibility = View.GONE
            currentOverlay = overlayRoot
            ownerActivity = WeakReference(activity)
        }

        currentOverlay?.visibility = View.VISIBLE
        com.nexus.launcher.ui.island.IslandController.notifyChrome()
        
        backCallback?.remove()
        val callback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                onDismiss()
            }
        }
        backCallback = callback
        host.onBackPressedDispatcher.addCallback(host, callback)
        
        return currentOverlay!!
    }

    fun hide() {
        currentOverlay?.visibility = View.GONE
        backCallback?.remove()
        backCallback = null
        com.nexus.launcher.ui.island.IslandController.notifyChrome()
    }

    fun invalidateCache(callerActivity: Activity) {
        val currentOwner = ownerActivity?.get()
        if (currentOwner != null && currentOwner !== callerActivity) {
            android.util.Log.d("FolderReopen", "invalidateCache ignored: called by old activity")
            return
        }

        currentOverlay?.let { overlay ->
            (overlay.parent as? ViewGroup)?.removeView(overlay)
        }
        currentOverlay = null
        backCallback?.remove()
        backCallback = null
        ownerActivity = null
    }
}
