package com.nexus.launcher.ui.folder

import android.content.Context
import android.content.Intent
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import com.nexus.launcher.data.HomeScreenItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

object FolderIconTouchHandler {

    fun attach(
        view: View,
        context: Context,
        item: HomeScreenItem,
        density: Float,
        gridLayout: GridLayout,
        cardContainer: LinearLayout?,
        sorted: MutableList<HomeScreenItem>,
        metrics: FolderWindowGridMetrics.Layout,
        dao: com.nexus.launcher.data.HomeScreenDao,
        coroutineScope: CoroutineScope
    ) {
        var downX = 0f
        var downY = 0f
        var downTime = 0L
        var reorderMode = false
        var menuShown = false
        var fingerMoved = false
        var originalDragIndex = -1
        var currentDragIndex = -1
        
        var escapedToCanvas = false
        
        view.setOnTouchListener { _, event ->
            if (escapedToCanvas) {
                val act = context as? com.nexus.launcher.ui.MainActivity
                if (act != null) {
                    val location = IntArray(2)
                    act.canvasView.getLocationOnScreen(location)
                    val localX = event.rawX - location[0]
                    val localY = event.rawY - location[1]
                    when (event.action) {
                        MotionEvent.ACTION_MOVE -> {
                            act.canvasView.dragHandler.onTouchMove(localX, localY, 0f, 0f, 0)
                            act.canvasView.invalidate()
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            act.canvasView.dragHandler.onTouchUp(localX, localY)
                            act.canvasView.invalidate()
                            FolderWindowManager.dismissFolder()
                        }
                    }
                }
                return@setOnTouchListener true
            }
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    downTime = System.currentTimeMillis()
                    reorderMode = false
                    menuShown = false
                    fingerMoved = false
                    originalDragIndex = -1
                    currentDragIndex = -1
                    escapedToCanvas = false
                    
                    view.postDelayed({
                        if (!fingerMoved && !menuShown && !reorderMode && !escapedToCanvas) {
                            menuShown = true
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                            view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                            FolderWindowAppMenuLauncher.show(
                                context = context,
                                anchorView = view,
                                item = item,
                                onRemove = {
                                    view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                                    coroutineScope.launch(Dispatchers.IO) {
                                        dao.updateItem(item.copy(containerId = -1, page = -1, column = -1, row = -1))
                                        withContext(Dispatchers.Main) {
                                            FolderWindowManager.refreshOpenWindowIfShowing(context)
                                        }
                                    }
                                }
                            )
                        }
                    }, 450L)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (escapedToCanvas) {
                        val act = context as? com.nexus.launcher.ui.MainActivity
                        act?.canvasView?.dragHandler?.onTouchMove(event.rawX, event.rawY, event.rawX - 100f, event.rawY, 0)
                        return@setOnTouchListener true
                    }
                    
                    val dx = abs(event.rawX - downX)
                    val dy = abs(event.rawY - downY)
                    val threshold = 8 * density
                    
                    if (dx > threshold || dy > threshold) {
                        fingerMoved = true
                    }
                    
                    val elapsed = System.currentTimeMillis() - downTime
                    
                    if (fingerMoved && elapsed > 450) {
                        if (menuShown) {
                            var actContext = context
                            while (actContext is android.content.ContextWrapper) {
                                if (actContext is com.nexus.launcher.ui.MainActivity) {
                                    actContext.contextMenuManager.dismiss()
                                    break
                                }
                                actContext = actContext.baseContext
                            }
                            menuShown = false
                        }
                        if (!reorderMode) {
                            view.parent?.requestDisallowInterceptTouchEvent(true)
                            originalDragIndex = gridLayout.indexOfChild(view)
                            currentDragIndex = originalDragIndex
                        }
                        reorderMode = true
                        view.scaleX = 1.15f
                        view.scaleY = 1.15f
                        view.alpha = 0.8f
                        view.translationZ = 10f
                        
                        view.translationX = event.rawX - downX
                        view.translationY = event.rawY - downY
                        
                        val gridLoc = IntArray(2)
                        gridLayout.getLocationOnScreen(gridLoc)
                        val localX = event.rawX - gridLoc[0]
                        val localY = event.rawY - gridLoc[1]
                        
                        val padding = 50 * density
                        val escapeBounds = if (cardContainer != null) {
                            val cardLoc = IntArray(2)
                            cardContainer.getLocationOnScreen(cardLoc)
                            val cLeft = cardLoc[0] - padding
                            val cTop = cardLoc[1] - padding
                            val cRight = cardLoc[0] + cardContainer.width + padding
                            val cBottom = cardLoc[1] + cardContainer.height + padding
                            event.rawX < cLeft || event.rawX > cRight || event.rawY < cTop || event.rawY > cBottom
                        } else {
                            val cLeft = gridLoc[0] - padding
                            val cTop = gridLoc[1] - padding
                            val cRight = gridLoc[0] + gridLayout.width + padding
                            val cBottom = gridLoc[1] + gridLayout.height + padding
                            event.rawX < cLeft || event.rawX > cRight || event.rawY < cTop || event.rawY > cBottom
                        }

                        if (escapeBounds) {
                            escapedToCanvas = true
                            FolderDragState.isDraggingFromFolder = true
                            reorderMode = false
                            view.scaleX = 1f
                            view.scaleY = 1f
                            view.alpha = 1f
                            view.translationX = 0f
                            view.translationY = 0f
                            view.translationZ = 0f
                            for (i in 0 until gridLayout.childCount) {
                                val child = gridLayout.getChildAt(i)
                                child.translationX = 0f
                                child.translationY = 0f
                            }
                            
                            val pm = context.packageManager
                            val intent = pm.getLaunchIntentForPackage(item.packageName) ?: Intent()
                            intent.putExtra("dragged_from_folder_id", item.containerId)
                            intent.putExtra("dragged_folder_item_id", item.id.toLong())
                            val label = try {
                                pm.getApplicationLabel(pm.getApplicationInfo(item.packageName, 0)).toString()
                            } catch (_: Exception) {
                                item.packageName
                            }
                            val iconView = (view as? android.view.ViewGroup)?.getChildAt(0) as? ImageView
                            val displayItem = com.nexus.launcher.ui.model.DisplayItem(
                                label = label,
                                icon = iconView?.drawable,
                                intent = intent,
                                categoryName = null
                            )
                            
                            val act = context as? com.nexus.launcher.ui.MainActivity
                            if (act != null) {
                                act.canvasView.dragHandler.onLongPressDetected(displayItem, android.graphics.Rect(), act.canvasView)
                                act.canvasView.dragHandler.onTouchMove(event.rawX, event.rawY, event.rawX - 100f, event.rawY, 0)
                                
                                // Visually hide the folder but do not dismiss it yet to keep the touch stream alive
                                act.canvasView.openFolderItemId = null
                                act.canvasView.folderOverlayFrozen = false
                                act.canvasView.setBlurState(false)
                                FolderWindowManager.activeCard?.visibility = View.INVISIBLE
                                FolderWindowManager.activeScrim?.visibility = View.INVISIBLE
                            } else {
                                FolderWindowManager.dismissFolder()
                            }
                            return@setOnTouchListener true
                        }
                        
                        var targetIndex = currentDragIndex
                        var minDistance = Float.MAX_VALUE
                        for (i in 0 until gridLayout.childCount) {
                            val child = gridLayout.getChildAt(i)
                            val cx = child.left + child.width / 2f
                            val cy = child.top + child.height / 2f
                            val distSq = (localX - cx) * (localX - cx) + (localY - cy) * (localY - cy)
                            if (distSq < minDistance) {
                                minDistance = distSq
                                targetIndex = i
                            }
                        }

                        if (targetIndex != currentDragIndex && currentDragIndex >= 0) {
                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                            currentDragIndex = targetIndex
                            
                            for (i in 0 until gridLayout.childCount) {
                                val child = gridLayout.getChildAt(i)
                                if (child == view) continue
                                
                                var visIndex = i
                                if (i in (originalDragIndex + 1)..currentDragIndex) {
                                    visIndex = i - 1
                                } else if (i in currentDragIndex until originalDragIndex) {
                                    visIndex = i + 1
                                }
                                
                                val targetChild = gridLayout.getChildAt(visIndex)
                                val origX = child.left
                                val origY = child.top
                                val visX = targetChild.left
                                val visY = targetChild.top
                                
                                child.animate()
                                    .translationX((visX - origX).toFloat())
                                    .translationY((visY - origY).toFloat())
                                    .setDuration(220)
                                    .setInterpolator(android.view.animation.OvershootInterpolator(1.0f))
                                    .start()
                            }
                        }
                    }
                    reorderMode
                }
                MotionEvent.ACTION_UP -> {
                    com.nexus.launcher.ui.folder.FolderDragState.isDraggingFromFolder = false
                    if (escapedToCanvas) {
                        val act = context as? com.nexus.launcher.ui.MainActivity
                        act?.canvasView?.dragHandler?.onTouchUp(event.rawX, event.rawY)
                        FolderWindowManager.dismissFolder()
                        return@setOnTouchListener true
                    }
                    if (reorderMode) {
                        view.scaleX = 1f
                        view.scaleY = 1f
                        view.alpha = 1f
                        view.translationX = 0f
                        view.translationY = 0f
                        view.translationZ = 0f
                        reorderMode = false
                        
                        for (i in 0 until gridLayout.childCount) {
                            val child = gridLayout.getChildAt(i)
                            child.translationX = 0f
                            child.translationY = 0f
                        }
                        
                        if (currentDragIndex != originalDragIndex && originalDragIndex >= 0 && currentDragIndex >= 0) {
                            val draggedItem = sorted.removeAt(originalDragIndex)
                            sorted.add(currentDragIndex, draggedItem)
                            
                            coroutineScope.launch(Dispatchers.IO) {
                                val minIdx = minOf(originalDragIndex, currentDragIndex)
                                val maxIdx = maxOf(originalDragIndex, currentDragIndex)
                                val cols = metrics.widthCols
                                
                                for (i in minIdx..maxIdx) {
                                    val it = sorted[i]
                                    dao.updateItem(it.copy(column = i % cols, row = i / cols))
                                }
                                
                                val folderDb = dao.getItemById(item.containerId.toInt())
                                if (folderDb != null) {
                                    val currentConfig = FolderConfigCodec.parse(folderDb.folderConfigJson)
                                    if (currentConfig.sortMode != 0) {
                                        FolderActionEngine.saveFolderConfig(
                                            folderDb.id.toLong(),
                                            folderDb.folderTitle,
                                            currentConfig.copy(sortMode = 0),
                                            dao
                                        )
                                    }
                                }
                                
                                withContext(Dispatchers.Main) {
                                    FolderWindowManager.refreshOpenWindowIfShowing(context)
                                }
                            }
                        }

                    } else if (!menuShown && !fingerMoved && System.currentTimeMillis() - downTime < 450) {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        val intent = context.packageManager.getLaunchIntentForPackage(item.packageName)
                        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        intent?.let { context.startActivity(it) }
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    com.nexus.launcher.ui.folder.FolderDragState.isDraggingFromFolder = false
                    view.scaleX = 1f
                    view.scaleY = 1f
                    view.alpha = 1f
                    view.translationX = 0f
                    view.translationY = 0f
                    reorderMode = false
                    true
                }
                else -> false
            }
        }
    }
}
