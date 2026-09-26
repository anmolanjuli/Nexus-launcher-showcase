package com.nexus.launcher.ui.widgets.shortcutbox

import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import com.nexus.launcher.data.HomeScreenItem
import kotlin.math.abs

/** Touch, long-press, move-mode, and drag dispatch for [ShortcutBoxView]. */
class ShortcutBoxTouchRouter(
    private val host: View,
    private val renderer: ShortcutBoxRenderer,
    private val configProvider: () -> ShortcutBoxConfig,
    private val membersProvider: () -> Map<Int, HomeScreenItem>,
    private val isMoveModeProvider: () -> Boolean,
    private val onEmptySlotTapped: (Int) -> Unit,
    private val onMemberTapped: (HomeScreenItem) -> Unit,
    private val onMemberLongPressed: (HomeScreenItem, Int, Float, Float) -> Unit,
    private val onMemberDragStarted: (HomeScreenItem, Int, Float, Float) -> Unit,
    private val onMemberDragMoved: (Float, Float) -> Unit,
    private val onMemberDragEnded: (Float, Float) -> Unit,
    private val onMemberDragCancelled: () -> Unit,
    private val onContainerLongPressed: () -> Unit,
    private val onDragStarted: () -> (() -> Unit)?,
    private val onDragMoved: () -> ((Float, Float) -> Unit)?,
    private val onDropDetected: () -> ((Float, Float) -> Unit)?,
    private val onBodyTappedInMoveMode: () -> (() -> Unit)?
) {
    private val touchSlop = ViewConfiguration.get(host.context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var downSlot: Int? = null
    private var isLongPressTriggered = false
    private var isDraggingMember = false
    private var isDragging = false
    private var pendingLongPress: Runnable? = null
    // Sideways and upward swipes belong to the home screen (page swipe, drawer), not the box.
    private val swipeHandoff = com.nexus.launcher.ui.widgets.WidgetPageSwipeHandoff()

    fun onTouchEvent(event: MotionEvent): Boolean {
        if (isMoveModeProvider()) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    isDragging = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = abs(event.x - downX)
                    val dy = abs(event.y - downY)
                    if (dx > touchSlop || dy > touchSlop || isDragging) {
                        if (!isDragging) {
                            isDragging = true
                            lastRawX = event.rawX
                            lastRawY = event.rawY
                            host.parent?.requestDisallowInterceptTouchEvent(true)
                            onDragStarted()?.invoke()
                        } else {
                            val pScaleX = (host.parent as? android.view.View)?.scaleX?.takeIf { it > 0.01f } ?: 1f
                            val pScaleY = (host.parent as? android.view.View)?.scaleY?.takeIf { it > 0.01f } ?: 1f
                            host.translationX += (event.rawX - lastRawX) / pScaleX
                            host.translationY += (event.rawY - lastRawY) / pScaleY
                            lastRawX = event.rawX
                            lastRawY = event.rawY
                            onDragMoved()?.invoke(host.translationX, host.translationY)
                        }
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        onDropDetected()?.invoke(host.translationX, host.translationY)
                        isDragging = false
                        host.translationX = 0f
                        host.translationY = 0f
                    } else {
                        onBodyTappedInMoveMode()?.invoke()
                    }
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) {
                        onDropDetected()?.invoke(host.translationX, host.translationY)
                        isDragging = false
                        host.translationX = 0f
                        host.translationY = 0f
                    }
                    return true
                }
            }
            return true
        }
        if (swipeHandoff.isActive) {
            swipeHandoff.forward(host, event)
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeHandoff.onDown(event)
                downX = event.x
                downY = event.y
                lastRawX = event.rawX
                lastRawY = event.rawY
                isLongPressTriggered = false
                isDraggingMember = false
                isDragging = false
                val slot = renderer.getSlotAt(
                    downX, downY, host.width.toFloat(), host.height.toFloat(),
                    configProvider()
                )
                downSlot = slot

                cancelLongPress()
                val lp = Runnable {
                    isLongPressTriggered = true
                    host.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                    val member = downSlot?.let { membersProvider()[it] }
                    if (member != null && downSlot != null) {
                        onMemberLongPressed(member, downSlot!!, lastRawX, lastRawY)
                    } else {
                        onContainerLongPressed()
                    }
                }
                pendingLongPress = lp
                host.postDelayed(lp, ViewConfiguration.getLongPressTimeout().toLong())
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = abs(event.x - downX)
                val dy = abs(event.y - downY)
                if (dx > touchSlop || dy > touchSlop) {
                    if (!isLongPressTriggered) {
                        cancelLongPress()
                        if (swipeHandoff.tryStart(host, event, touchSlop, allowSwipeUp = true)) {
                            host.parent?.requestDisallowInterceptTouchEvent(true)
                            downSlot = null
                            return true
                        }
                    } else {
                        val member = downSlot?.let { membersProvider()[it] }
                        if (member != null && downSlot != null) {
                            if (!isDraggingMember) {
                                isDraggingMember = true
                                host.parent?.requestDisallowInterceptTouchEvent(true)
                                onMemberDragStarted(member, downSlot!!, event.rawX, event.rawY)
                            } else {
                                onMemberDragMoved(event.rawX, event.rawY)
                            }
                        } else {
                            // Long press fired on container — initiate/continue dragging
                            if (!isDragging) {
                                isDragging = true
                                lastRawX = event.rawX
                                lastRawY = event.rawY
                                host.parent?.requestDisallowInterceptTouchEvent(true)
                                onDragStarted()?.invoke()
                            } else {
                                val pScaleX = (host.parent as? android.view.View)?.scaleX?.takeIf { it > 0.01f } ?: 1f
                                val pScaleY = (host.parent as? android.view.View)?.scaleY?.takeIf { it > 0.01f } ?: 1f
                                host.translationX += (event.rawX - lastRawX) / pScaleX
                                host.translationY += (event.rawY - lastRawY) / pScaleY
                                lastRawX = event.rawX
                                lastRawY = event.rawY
                                onDragMoved()?.invoke(host.translationX, host.translationY)
                            }
                        }
                    }
                } else if (isDraggingMember) {
                    onMemberDragMoved(event.rawX, event.rawY)
                }
            }
            MotionEvent.ACTION_UP -> {
                cancelLongPress()
                if (isDraggingMember) {
                    onMemberDragEnded(event.rawX, event.rawY)
                    isDraggingMember = false
                    isLongPressTriggered = false
                    downSlot = null
                    return true
                }
                if (isDragging) {
                    onDropDetected()?.invoke(host.translationX, host.translationY)
                    isDragging = false
                    isLongPressTriggered = false
                    host.translationX = 0f
                    host.translationY = 0f
                    downSlot = null
                    return true
                }
                if (!isLongPressTriggered) {
                    val upSlot = renderer.getSlotAt(
                        event.x, event.y, host.width.toFloat(), host.height.toFloat(),
                        configProvider()
                    )
                    if (upSlot != null && upSlot == downSlot) {
                        host.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        val member = membersProvider()[upSlot]
                        if (member != null) {
                            onMemberTapped(member)
                        } else {
                            onEmptySlotTapped(upSlot)
                        }
                    }
                }
                isLongPressTriggered = false
                downSlot = null
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                cancelLongPress()
                if (isDraggingMember) {
                    onMemberDragCancelled()
                    isDraggingMember = false
                }
                if (isDragging) {
                    onDropDetected()?.invoke(host.translationX, host.translationY)
                    isDragging = false
                    host.translationX = 0f
                    host.translationY = 0f
                }
                isLongPressTriggered = false
                downSlot = null
            }
        }
        return false
    }

    private fun cancelLongPress() {
        pendingLongPress?.let { host.removeCallbacks(it) }
        pendingLongPress = null
    }
}
