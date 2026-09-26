package com.nexus.launcher.ui.canvas

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.provider.Settings
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.model.GestureAction
import com.nexus.launcher.ui.updateHomeShowLabels

object IconSwipeGestureDetector {
    var swipeCandidateItem: HomeScreenItem? = null
    var isIconSwipeCandidate: Boolean = false
    var hasUpAction: Boolean = false
    var hasDownAction: Boolean = false
    private var isFlashlightOn = false

    private fun getGestureKey(item: HomeScreenItem): String {
        return if (item.itemType == 1) "folder_${item.id}" else item.packageName
    }

    // Called on ACTION_DOWN
    fun checkEligibility(
        item: HomeScreenItem?,
        viewModel: com.nexus.launcher.ui.MainViewModel?
    ) {
        swipeCandidateItem = null
        isIconSwipeCandidate = false
        hasUpAction = false
        hasDownAction = false
        if (item == null || viewModel == null) return
        
        val key = getGestureKey(item)
        val upActionStr = viewModel.swipeUpActions.value[key]
        val downActionStr = viewModel.swipeDownActions.value[key]
        val hasUp = upActionStr != null && upActionStr != "NONE"
        val hasDown = downActionStr != null && downActionStr != "NONE"
        
        if (hasUp || hasDown) {
            swipeCandidateItem = item
            hasUpAction = hasUp
            hasDownAction = hasDown
        }
    }

    // Called on ACTION_MOVE before drag-commit. Returns true if branch taken.
    fun checkBranch(
        totalDx: Float,
        totalDy: Float,
        touchSlop: Int,
        cancelLongPress: () -> Unit
    ): Boolean {
        if (swipeCandidateItem != null && !isIconSwipeCandidate) {
            val distance = kotlin.math.hypot(totalDx.toDouble(), totalDy.toDouble())
            if (distance > touchSlop) {
                val isTrendingUp = totalDy < 0
                val isTrendingDown = totalDy > 0
                val isEligibleDirection = (isTrendingUp && hasUpAction) || (isTrendingDown && hasDownAction)
                
                if (isEligibleDirection) {
                    // Loosen verticality check from 2x to 1.2x to accommodate the natural horizontal
                    // arc (wobble) of a downward thumb swipe.
                    if (Math.abs(totalDy) > Math.abs(totalDx) * 1.2f) {
                        isIconSwipeCandidate = true
                        cancelLongPress()
                        return true
                    }
                    // Grace window: instead of capping by total distance (which fast swipes jump past),
                    // cap by horizontal displacement. If they haven't moved significantly horizontally,
                    // suppress the drag commit to give the fast arc time to straighten out.
                    if (Math.abs(totalDx) < touchSlop * 1.5f) {
                        return true
                    }
                }
            }
        }
        return isIconSwipeCandidate
    }

    // Called on ACTION_UP. Returns true if a per-icon action was dispatched, false if the
    // gesture didn't meet the threshold and the caller should fall through to normal snap logic.
    fun handleUp(
        context: android.content.Context,
        totalDy: Float,
        velocityY: Float,
        viewModel: com.nexus.launcher.ui.MainViewModel?
    ): Boolean {
        if (!isIconSwipeCandidate || swipeCandidateItem == null || viewModel == null) return false
        
        val absDy = Math.abs(totalDy)
        val absVy = Math.abs(velocityY)
        
        // Since isIconSwipeCandidate already verified deliberate vertical intent and locked out other actions,
        // we can be much more lenient here to prevent the gesture from "sleeping" on short or slow swipes
        // (especially for downward swipes on icons near the bottom edge of the screen).
        if (absDy >= 80f || (absDy >= 40f && absVy >= 300f)) {
            val key = getGestureKey(swipeCandidateItem!!)
            val direction = if (totalDy < 0) "Swipe Up" else "Swipe Down"
            val actionStr = if (totalDy < 0) {
                viewModel.swipeUpActions.value[key]
            } else {
                viewModel.swipeDownActions.value[key]
            }
            
            var targetStr: String? = null
            val action = try {
                if (actionStr != null) {
                    val parts = actionStr.split("::")
                    if (parts.size > 1) {
                        targetStr = parts.drop(1).joinToString("::")
                    }
                    GestureAction.valueOf(parts[0])
                } else {
                    GestureAction.NONE
                }
            } catch (e: Exception) { GestureAction.NONE }
            
            if (action != GestureAction.NONE && action != GestureAction.LOCK_SCREEN) {
                dispatchAction(context, action, targetStr, swipeCandidateItem!!, viewModel)
            }
            return true
        }
        // Threshold not met — signal the caller to fall through to normal drawer snap logic.
        return false
    }
    
    fun reset() {
        swipeCandidateItem = null
        isIconSwipeCandidate = false
        hasUpAction = false
        hasDownAction = false
    }

    private fun performHaptic(context: Context) {
        var ctx = context
        while (ctx is android.content.ContextWrapper) {
            if (ctx is android.app.Activity) {
                ctx.window.decorView.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                return
            }
            ctx = ctx.baseContext
        }
    }

    fun dispatchAction(context: Context, action: GestureAction, targetStr: String?, item: HomeScreenItem, viewModel: com.nexus.launcher.ui.MainViewModel) {
        performHaptic(context)
        when (action) {
            GestureAction.OPEN_APP_INFO -> {
                if (item.itemType == 1) return // Safe no-op for folders
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${item.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
            GestureAction.TOGGLE_FLASHLIGHT -> {
                try {
                    val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
                    var targetCameraId: String? = null
                    for (cameraId in cameraManager.cameraIdList) {
                        val characteristics = cameraManager.getCameraCharacteristics(cameraId)
                        if (characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true) {
                            targetCameraId = cameraId
                            break
                        }
                    }
                    if (targetCameraId != null) {
                        isFlashlightOn = !isFlashlightOn
                        cameraManager.setTorchMode(targetCameraId, isFlashlightOn)
                    }
                } catch (e: Exception) {
                    // Gracefully ignore CameraAccessException or missing hardware
                }
            }
            GestureAction.OPEN_APP_DRAWER -> {
                viewModel.handleSwipeUp()
            }
            GestureAction.OPEN_SPECIFIC_APP -> {
                if (targetStr != null) {
                    val intent = context.packageManager.getLaunchIntentForPackage(targetStr)
                    if (intent != null) {
                        context.startActivity(intent)
                    }
                }
            }
            GestureAction.OPEN_SPECIFIC_PAGE -> {
                if (targetStr != null) {
                    val targetPage = targetStr.toIntOrNull()
                    if (targetPage != null) {
                        (context as? com.nexus.launcher.ui.MainActivity)?.canvasView?.animateScrollToPage(targetPage)
                    }
                }
            }
            GestureAction.OPEN_SPECIFIC_FOLDER -> {
                if (targetStr != null) {
                    val folderId = targetStr.toLongOrNull()
                    if (folderId != null) {
                        val intent = Intent("nexus.folder.OPEN").apply {
                            putExtra("folderId", folderId)
                        }
                        (context as? com.nexus.launcher.ui.MainActivity)?.canvasView?.onIntentSelected?.invoke(intent)
                    }
                }
            }
            GestureAction.OPEN_SPECIFIC_SHORTCUT -> {
                if (targetStr != null) {
                    val parts = targetStr.split("::")
                    if (parts.size >= 2) {
                        val packageName = parts[0]
                        val shortcutId = parts.drop(1).joinToString("::") // Rejoin just in case ID contains ::
                        val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps
                        try {
                            launcherApps.startShortcut(packageName, shortcutId, null, null, android.os.Process.myUserHandle())
                        } catch (e: Exception) {
                            // Gracefully no-op if shortcut no longer exists
                        }
                    }
                }
            }
            GestureAction.EXPAND_NOTIFICATIONS -> {
                // With the launcher keeping notifications itself, this opens its own sheet —
                // which is the point of that setting: no reaching for the shade.
                if (com.nexus.launcher.service.NotificationHistory.enabled) {
                    com.nexus.launcher.ui.notifications.NotificationSheet.show(context)
                    return
                }
                val service = com.nexus.launcher.service.NexusAccessibilityService.instance
                if (service != null) {
                    service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                } else {
                    showAccessibilityDialog(context)
                }
            }
            GestureAction.OPEN_QUICK_SETTINGS -> {
                val service = com.nexus.launcher.service.NexusAccessibilityService.instance
                if (service != null) {
                    service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
                } else {
                    showAccessibilityDialog(context)
                }
            }
            GestureAction.SHOW_RECENTS -> {
                val service = com.nexus.launcher.service.NexusAccessibilityService.instance
                if (service != null) {
                    service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS)
                } else {
                    showAccessibilityDialog(context)
                }
            }
            GestureAction.TAKE_SCREENSHOT -> {
                val service = com.nexus.launcher.service.NexusAccessibilityService.instance
                if (service != null) {
                    if (android.os.Build.VERSION.SDK_INT >= 28) {
                        service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT)
                    } else {
                        android.widget.Toast.makeText(context, context.getString(com.nexus.launcher.R.string.toast_screenshot_requires_android_9), android.widget.Toast.LENGTH_SHORT).show()
                    }
                } else {
                    showAccessibilityDialog(context)
                }
            }
            GestureAction.SHOW_POWER_MENU -> {
                val service = com.nexus.launcher.service.NexusAccessibilityService.instance
                if (service != null) {
                    service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_POWER_DIALOG)
                } else {
                    showAccessibilityDialog(context)
                }
            }
            GestureAction.SPLIT_SCREEN -> {
                val service = com.nexus.launcher.service.NexusAccessibilityService.instance
                if (service != null) {
                    service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_TOGGLE_SPLIT_SCREEN)
                } else {
                    showAccessibilityDialog(context)
                }
            }
            GestureAction.OPEN_SEARCH -> {
                (context as? com.nexus.launcher.ui.MainActivity)?.let { activity ->
                    com.nexus.launcher.search.ui.NexusSearchOverlay.show(activity, com.nexus.launcher.search.SearchContext.HOME_SCREEN)
                }
            }
            GestureAction.NEXT_PAGE -> {
                (context as? com.nexus.launcher.ui.MainActivity)?.canvasView?.let { view ->
                    if (view.currentPage + 1 < view.totalPages) {
                        view.animateScrollToPage(view.currentPage + 1)
                    }
                }
            }
            GestureAction.PREV_PAGE -> {
                (context as? com.nexus.launcher.ui.MainActivity)?.canvasView?.let { view ->
                    if (view.currentPage > 0) {
                        view.animateScrollToPage(view.currentPage - 1)
                    }
                }
            }
            GestureAction.TOGGLE_LABELS -> {
                val current = viewModel.nexusSettings.value.homeShowLabels
                viewModel.updateHomeShowLabels(!current)
            }
            GestureAction.OPEN_HOME_EDIT -> {
                (context as? com.nexus.launcher.ui.MainActivity)?.homeEditController?.showHomeEditMode(0f, 0f)
            }
            else -> {}
        }
    }

    fun setupDoubleTapDetector(
        view: LauncherCanvasView,
        dragTouchHandler: com.nexus.launcher.ui.canvas.DragTouchHandler,
        homeItem: HomeScreenItem,
        viewModel: com.nexus.launcher.ui.MainViewModel
    ): androidx.core.view.GestureDetectorCompat? {
        if (homeItem.itemType == 1) return null
        val key = homeItem.packageName
        val doubleTapActionStr = viewModel.doubleTapActions.value[key]
        if (doubleTapActionStr != null && doubleTapActionStr != "NONE") {
            return androidx.core.view.GestureDetectorCompat(view.context, object : android.view.GestureDetector.SimpleOnGestureListener() {
                override fun onSingleTapConfirmed(e: android.view.MotionEvent): Boolean {
                    LauncherTapHelper.handleTap(view, e, dragTouchHandler)
                    return true
                }
                override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                    var targetStr: String? = null
                    val parts = doubleTapActionStr.split("::")
                    if (parts.size > 1) {
                        targetStr = parts.drop(1).joinToString("::")
                    }
                    val action = try { com.nexus.launcher.ui.model.GestureAction.valueOf(parts[0]) } catch (ex: Exception) { com.nexus.launcher.ui.model.GestureAction.NONE }
                    if (action != com.nexus.launcher.ui.model.GestureAction.NONE && action != com.nexus.launcher.ui.model.GestureAction.LOCK_SCREEN) {
                        dispatchAction(view.context, action, targetStr, homeItem, viewModel)
                    }
                    return true
                }
            })
        }
        return null
    }

    internal fun showAccessibilityDialog(context: Context) {
        com.nexus.launcher.ui.folder.FolderAuroraDialogs.showConfirmation(
            context = context,
            title = context.getString(com.nexus.launcher.R.string.dialog_accessibility_required_title),
            body = context.getString(com.nexus.launcher.R.string.accessibility_service_description),
            cancelText = context.getString(com.nexus.launcher.R.string.action_cancel),
            confirmText = context.getString(com.nexus.launcher.R.string.action_open_settings),
            onConfirm = {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            }
        )
    }
}
