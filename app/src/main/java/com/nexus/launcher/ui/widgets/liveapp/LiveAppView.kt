package com.nexus.launcher.ui.widgets.liveapp

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.provider.Settings
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.Toast
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.nexus.launcher.R
import com.nexus.launcher.data.HomeScreenItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Container view for Live App Widget hosted in [WidgetOverlayLayout].
 */
class LiveAppView(context: Context) : FrameLayout(context) {

    private val renderer = LiveAppRenderer(context)
    private var boundItem: HomeScreenItem? = null
    private var config = LiveAppConfig()
    private var appsList: List<LiveAppEntry> = emptyList()
    private var hasUsageAccess = true

    private var onContainerLongPress: (() -> Unit)? = null
    var onLongPressDetected: (() -> Unit)?
        get() = onContainerLongPress
        set(value) { onContainerLongPress = value }

    var onDragStarted: (() -> Unit)? = null
    var onDragMoved: ((Float, Float) -> Unit)? = null
    var onDropDetected: ((Float, Float) -> Unit)? = null
    var onBodyTappedInMoveMode: (() -> Unit)? = null

    private var isMoveModeActive = false
    private var isResizeModeActive = false

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var isDragging = false
    private var longPressTriggered = false
    // Sideways and upward swipes belong to the home screen (page swipe, drawer), not the box.
    private val swipeHandoff = com.nexus.launcher.ui.widgets.WidgetPageSwipeHandoff()
    private val longPressRunnable = Runnable {
        if (!isDragging) {
            longPressTriggered = true
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            onContainerLongPress?.invoke()
        }
    }

    private var collectJob: Job? = null

    init {
        setWillNotDraw(false)
        isClickable = true
        isFocusable = true
    }

    fun currentItem(): HomeScreenItem? = boundItem

    fun applyLiveConfig(newConfig: LiveAppConfig) {
        config = newConfig
        invalidate()
    }

    fun setMoveModeActive(active: Boolean) {
        isMoveModeActive = active
    }

    fun setResizeModeActive(active: Boolean) {
        isResizeModeActive = active
    }

    fun bind(item: HomeScreenItem) {
        boundItem = item
        config = LiveAppConfig.parse(item.folderConfigJson)
        startObserving()
        invalidate()
    }

    private fun startObserving() {
        collectJob?.cancel()
        val lifecycleOwner = context as? LifecycleOwner ?: return
        collectJob = lifecycleOwner.lifecycleScope.launch {
            launch {
                LiveAppRepository.liveAppsFlow.collectLatest { apps ->
                    appsList = apps
                    invalidate()
                }
            }
            launch {
                LiveAppRepository.isUsageAccessGranted.collectLatest { granted ->
                    hasUsageAccess = granted
                    invalidate()
                }
            }
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        LiveAppRepository.registerWidget(context)
        startObserving()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        LiveAppRepository.unregisterWidget()
        collectJob?.cancel()
        collectJob = null
        removeCallbacks(longPressRunnable)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (boundItem == null) return
        renderer.draw(
            canvas = canvas,
            width = width.toFloat(),
            height = height.toFloat(),
            config = config,
            apps = appsList,
            hasUsageAccess = hasUsageAccess,
            hostView = this
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (isResizeModeActive) return false
        if (isMoveModeActive) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    isDragging = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = Math.abs(event.rawX - downX)
                    val dy = Math.abs(event.rawY - downY)
                    if (!isDragging && (dx > touchSlop || dy > touchSlop)) {
                        isDragging = true
                        parent?.requestDisallowInterceptTouchEvent(true)
                        onDragStarted?.invoke()
                    }
                    if (isDragging) {
                        val pScaleX = (parent as? android.view.View)?.scaleX?.takeIf { it > 0.01f } ?: 1f
                        val pScaleY = (parent as? android.view.View)?.scaleY?.takeIf { it > 0.01f } ?: 1f
                        translationX += (event.rawX - lastRawX) / pScaleX
                        translationY += (event.rawY - lastRawY) / pScaleY
                        lastRawX = event.rawX
                        lastRawY = event.rawY
                        onDragMoved?.invoke(translationX, translationY)
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        val tx = translationX
                        val ty = translationY
                        translationX = 0f
                        translationY = 0f
                        isDragging = false
                        onDropDetected?.invoke(tx, ty)
                    } else {
                        onBodyTappedInMoveMode?.invoke()
                    }
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    translationX = 0f
                    translationY = 0f
                    isDragging = false
                    return true
                }
            }
        }
        if (swipeHandoff.isActive) {
            swipeHandoff.forward(this, event)
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                swipeHandoff.onDown(event)
                downX = event.x
                downY = event.y
                lastRawX = event.rawX
                lastRawY = event.rawY
                isDragging = false
                longPressTriggered = false
                postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = Math.abs(event.x - downX)
                val dy = Math.abs(event.y - downY)
                if (dx > touchSlop || dy > touchSlop) {
                    if (!longPressTriggered) {
                        removeCallbacks(longPressRunnable)
                        if (swipeHandoff.tryStart(this, event, touchSlop, allowSwipeUp = true)) {
                            parent?.requestDisallowInterceptTouchEvent(true)
                            return true
                        }
                    } else {
                        if (!isDragging) {
                            isDragging = true
                            lastRawX = event.rawX
                            lastRawY = event.rawY
                            parent?.requestDisallowInterceptTouchEvent(true)
                            onDragStarted?.invoke()
                        } else {
                            val pScaleX = (parent as? android.view.View)?.scaleX?.takeIf { it > 0.01f } ?: 1f
                            val pScaleY = (parent as? android.view.View)?.scaleY?.takeIf { it > 0.01f } ?: 1f
                            translationX += (event.rawX - lastRawX) / pScaleX
                            translationY += (event.rawY - lastRawY) / pScaleY
                            lastRawX = event.rawX
                            lastRawY = event.rawY
                            onDragMoved?.invoke(translationX, translationY)
                        }
                    }
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                removeCallbacks(longPressRunnable)
                if (isDragging) {
                    val tx = translationX
                    val ty = translationY
                    translationX = 0f
                    translationY = 0f
                    isDragging = false
                    longPressTriggered = false
                    onDropDetected?.invoke(tx, ty)
                    return true
                }
                if (!longPressTriggered) {
                    handleTap(event.x, event.y)
                }
                longPressTriggered = false
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPressRunnable)
                translationX = 0f
                translationY = 0f
                isDragging = false
                longPressTriggered = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleTap(touchX: Float, touchY: Float) {
        if (appsList.isEmpty() && !hasUsageAccess) {
            try {
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                Toast.makeText(context, context.getString(R.string.live_apps_permission_hint), Toast.LENGTH_SHORT).show()
            }
            return
        }

        val slot = renderer.getSlotAt(touchX, touchY, width.toFloat(), height.toFloat(), config) ?: return
        val maxSlots = minOf(config.gridCols * config.gridRows, LiveAppConfig.MAX_ICONS)
        if (slot < maxSlots && slot < appsList.size) {
            val app = appsList[slot]
            try {
                val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                if (intent != null) {
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                } else {
                    Toast.makeText(context, context.getString(R.string.app_not_found), Toast.LENGTH_SHORT).show()
                }
            } catch (_: Exception) {
                Toast.makeText(context, context.getString(R.string.app_launch_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }
}
