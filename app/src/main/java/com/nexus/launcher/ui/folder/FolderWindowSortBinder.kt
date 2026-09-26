package com.nexus.launcher.ui.folder

import android.view.View
import android.widget.ImageButton
import androidx.fragment.app.FragmentActivity
import com.nexus.launcher.R
import com.nexus.launcher.data.FolderConfig
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.settings.views.AuroraDropdownOverlay
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/** Sort control in the open folder window header. */
object FolderWindowSortBinder {

    private fun sortLabels(context: android.content.Context) = listOf(
        context.getString(com.nexus.launcher.R.string.folder_sort_custom), context.getString(com.nexus.launcher.R.string.drawer_sort_az), context.getString(com.nexus.launcher.R.string.drawer_sort_za)
    )

    fun bind(
        sortBtn: ImageButton,
        folderItem: HomeScreenItem,
        config: FolderConfig,
        overlayHost: android.view.ViewGroup,
        onSortChanged: (FolderConfig) -> Unit,
        dao: com.nexus.launcher.data.HomeScreenDao,
        context: android.content.Context
    ) {
        sortBtn.setOnClickListener {
            showSortDropdown(sortBtn, config, overlayHost, onSortChanged, folderItem.resolveFolderContentsId(), dao, context)
        }
    }

    fun showSortDropdown(
        anchor: View,
        config: FolderConfig,
        overlayHost: android.view.ViewGroup,
        onSortChanged: (FolderConfig) -> Unit,
        folderId: Long,
        dao: com.nexus.launcher.data.HomeScreenDao,
        context: android.content.Context
    ) {
        val currentConfig = FolderWindowManager.activeConfig ?: config
        val dp = context.resources.displayMetrics.density
        val accent = android.graphics.Color.parseColor(FolderAuroraTheme.ACCENT)
        
        val overlay = android.widget.FrameLayout(context).apply {
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
            elevation = 48f * dp
            setOnClickListener { (parent as? android.view.ViewGroup)?.removeView(this) }
        }

        val card = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(android.graphics.Color.parseColor("#F0121820"))
                cornerRadius = 14f * dp
                setStroke((1f * dp).toInt().coerceAtLeast(1), android.graphics.Color.argb(0x66, 255, 255, 255))
            }
            setOnClickListener { /* consume */ }
        }

        val buttons = mutableListOf<android.widget.TextView>()
        
        sortLabels(context).forEachIndexed { index, label ->
            if (index > 0) {
                card.addView(View(context).apply {
                    layoutParams = android.widget.LinearLayout.LayoutParams(
                        android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                        (1f * dp).toInt().coerceAtLeast(1)
                    )
                    setBackgroundColor(android.graphics.Color.parseColor("#1AFFFFFF"))
                })
            }
            val row = android.widget.TextView(context).apply {
                text = label
                textSize = 15f
                gravity = android.view.Gravity.CENTER_VERTICAL
                val padH = (14 * dp).toInt()
                val padV = (11 * dp).toInt()
                setPadding(padH, padV, padH, padV)
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                )
                
                if (index == currentConfig.sortMode) {
                    setTextColor(accent)
                } else {
                    setTextColor(android.graphics.Color.WHITE)
                }
                
                setOnClickListener { btn ->
                    btn.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    
                    // Clear highlight from ALL buttons first
                    for (b in buttons) {
                        b.setTextColor(android.graphics.Color.WHITE)
                    }
                    // Apply highlight ONLY to the selected button
                    (btn as android.widget.TextView).setTextColor(accent)
                    
                    val newConfig = currentConfig.copy(sortMode = index)
                    onSortChanged(newConfig)
                    
                    (context as? androidx.lifecycle.LifecycleOwner)?.lifecycleScope?.launch(kotlinx.coroutines.Dispatchers.IO) {
                        FolderActionEngine.applySort(folderId, index, dao, context)
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            FolderWindowManager.refreshOpenWindowIfShowing(context)
                        }
                    }
                    (overlay.parent as? android.view.ViewGroup)?.removeView(overlay)
                }
            }
            buttons.add(row)
            card.addView(row)
        }

        val cardWidth = (120 * dp).toInt()
        for (i in 0 until card.childCount) {
            (card.getChildAt(i).layoutParams as? android.widget.LinearLayout.LayoutParams)?.width = cardWidth
        }

        overlay.addView(card, android.widget.FrameLayout.LayoutParams(
            cardWidth,
            android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.TOP or android.view.Gravity.START
        ))

        overlayHost.addView(overlay)
        overlay.bringToFront()

        val anchorLoc = IntArray(2)
        anchor.getLocationOnScreen(anchorLoc)
        val hostLoc = IntArray(2)
        overlayHost.getLocationOnScreen(hostLoc)
        
        val left = anchorLoc[0] - hostLoc[0] + anchor.width - cardWidth
        val top = anchorLoc[1] - hostLoc[1] + anchor.height + (4 * dp).toInt()

        (card.layoutParams as android.widget.FrameLayout.LayoutParams).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.LEFT
            leftMargin = left.coerceAtLeast((16 * dp).toInt())
            topMargin = top.coerceAtLeast((16 * dp).toInt())
        }
        card.requestLayout()
    }

    fun openAppPicker(
        context: android.content.Context,
        folderItem: HomeScreenItem,
        onAppsSaved: (() -> Unit)? = null
    ) {
        val activity = context as? FragmentActivity ?: return
        FolderAppPickerBottomSheet.preloadAndShow(
            context,
            activity.supportFragmentManager,
            folderItem.resolveFolderContentsId(),
            activity.lifecycleScope,
            onAppsSaved
        )
    }
}
