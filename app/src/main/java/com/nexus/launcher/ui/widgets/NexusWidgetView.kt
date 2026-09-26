package com.nexus.launcher.ui.widgets

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.ViewConfiguration
import kotlin.math.abs

class NexusWidgetView(context: Context, private val appWidgetManager: AppWidgetManager) : AppWidgetHostView(context) {

    var onLongPressDetected: (() -> Unit)? = null
    var onDragStarted: (() -> Unit)? = null
    var onDragMoved: ((deltaX: Float, deltaY: Float) -> Unit)? = null
    var onDropDetected: ((deltaX: Float, deltaY: Float) -> Unit)? = null
    var onWidgetBodyTappedInMoveMode: (() -> Unit)? = null

    /**
     * When true (e.g. Living Mosaic children), skip host long-press/drag so the
     * parent mosaic owns edit gestures and page swipes.
     */
    var suppressHostGestures = false

    private var isResizeModeActive = false
    private var isMoveModeActive = false
    private val pageSwipeHandoff = WidgetPageSwipeHandoff()

    fun setResizeModeActive(active: Boolean) {
        isResizeModeActive = active
    }

    fun setMoveModeActive(active: Boolean) {
        isMoveModeActive = active
    }

    private var isLongPressActive = false
    private var isDragging = false
    private var longPressStartX = 0f
    private var longPressStartY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private val touchSlop by lazy { ViewConfiguration.get(context).scaledTouchSlop }

    private val longPressHandler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        isLongPressActive = true
        
        // Synthesize ACTION_CANCEL to abort any child clicks
        sendCancelToChildren()

        performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
        onLongPressDetected?.invoke()
    }

    private fun sendCancelToChildren() {
        val cancel = MotionEvent.obtain(
            android.os.SystemClock.uptimeMillis(),
            android.os.SystemClock.uptimeMillis(),
            MotionEvent.ACTION_CANCEL,
            0f, 0f, 0
        )
        super.dispatchTouchEvent(cancel)
        cancel.recycle()
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (suppressHostGestures) {
            return super.dispatchTouchEvent(ev)
        }
        if (isMoveModeActive) {
            when (ev.actionMasked) {
                MotionEvent.ACTION_UP -> {
                    onWidgetBodyTappedInMoveMode?.invoke()
                }
            }
            return true
        }
        if (pageSwipeHandoff.isActive) {
            pageSwipeHandoff.forward(this, ev)
            return true
        }

        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isLongPressActive = false
                isDragging = false
                longPressStartX = ev.rawX
                longPressStartY = ev.rawY
                lastRawX = ev.rawX
                lastRawY = ev.rawY
                pageSwipeHandoff.onDown(ev)
                
                super.dispatchTouchEvent(ev)
                
                longPressHandler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - longPressStartX
                val dy = ev.rawY - longPressStartY
                
                if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                    longPressHandler.removeCallbacks(longPressRunnable)
                    
                    if (isLongPressActive && !isMoveModeActive) {
                        if (!isDragging) {
                            isDragging = true
                            lastRawX = ev.rawX
                            lastRawY = ev.rawY
                            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                            onDragStarted?.invoke()
                        } else {
                            val pScaleX = (parent as? android.view.View)?.scaleX?.takeIf { it > 0.01f } ?: 1f
                            val pScaleY = (parent as? android.view.View)?.scaleY?.takeIf { it > 0.01f } ?: 1f
                            translationX += (ev.rawX - lastRawX) / pScaleX
                            translationY += (ev.rawY - lastRawY) / pScaleY
                            lastRawX = ev.rawX
                            lastRawY = ev.rawY
                            onDragMoved?.invoke(translationX, translationY)
                        }
                        return true
                    }
                    if (!isLongPressActive && !isDragging &&
                        pageSwipeHandoff.tryStart(this, ev, touchSlop, allowSwipeUp = true)
                    ) {
                        sendCancelToChildren()
                        parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    }
                }
                
                if (isDragging) return true
                return super.dispatchTouchEvent(ev)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                longPressHandler.removeCallbacks(longPressRunnable)
                
                if (isDragging) {
                    val dx = translationX
                    val dy = translationY
                    translationX = 0f
                    translationY = 0f
                    isDragging = false
                    isLongPressActive = false
                    if (android.os.Build.VERSION.SDK_INT >= 27) {
                        performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY_RELEASE)
                    }
                    onDropDetected?.invoke(dx, dy)
                    return true
                }
                
                if (isLongPressActive) {
                    isLongPressActive = false
                    return true
                }
                
                return super.dispatchTouchEvent(ev)
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun updateAppWidgetSize(newOptions: android.os.Bundle?, minWidth: Int, minHeight: Int, maxWidth: Int, maxHeight: Int) {
        // We removed the isResizeModeActive suppression so widgets can natively reflow and anchor correctly during drag.
        super.updateAppWidgetSize(newOptions, minWidth, minHeight, maxWidth, maxHeight)
    }

    override fun updateAppWidgetSize(newOptions: android.os.Bundle, sizes: MutableList<android.util.SizeF>) {
        super.updateAppWidgetSize(newOptions, sizes)
    }

    // -------------------------------------------------------------------------
    // Deferred-update support
    //
    // When the launcher returns from another app, AppWidgetHost.startListening()
    // causes the AppWidgetService to immediately replay the last RemoteViews for
    // every widget on the binder thread.  updateAppWidget() is then called on the
    // main thread while the window-return animation is still running, producing a
    // visible re-inflate flash.
    //
    // WidgetHostLifecycle.onStart() calls beginDefer() on all existing views
    // BEFORE startListening().  The system replay is buffered here.  After the
    // animation completes (~380 ms), commitDeferred() applies the buffered views
    // in one shot and notifies collection adapters to reconnect.
    //
    // Null RemoteViews are passed through immediately: they carry no visual
    // content and blocking them would leave the host view in a cleared state.
    // -------------------------------------------------------------------------

    private var deferringUpdates = false
    private var pendingRemoteViews: android.widget.RemoteViews? = null

    /** Call before startListening() to prevent the system replay from flashing. */
    fun beginDefer() {
        deferringUpdates = true
        pendingRemoteViews = null
    }

    /**
     * Call after the animation settles to apply the buffered RemoteViews (if any)
     * and notify collection-widget adapters (e.g. Google Calendar ListView) to
     * reconnect their RemoteViewsService data source.
     */
    fun commitDeferred() {
        deferringUpdates = false
        val views = pendingRemoteViews
        pendingRemoteViews = null
        if (views != null) {
            super.updateAppWidget(views)
        }
        // Notify collection adapters regardless of whether a new RemoteViews was
        // received.  stopListening() severed the RemoteViewsAdapter connection;
        // this re-establishes it so ListView-based widgets (Calendar, etc.) show
        // live data instead of their empty initial layout ("Nothing planned").
        NexusWidgetViewNotify.refresh(this)
    }

    override fun updateAppWidget(remoteViews: android.widget.RemoteViews?) {
        if (deferringUpdates) {
            if (remoteViews != null) {
                // Store only the most recent update; earlier ones are superseded.
                pendingRemoteViews = remoteViews
            }
            return
        }
        super.updateAppWidget(remoteViews)
    }


    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        NexusWidgetViewNotify.onAttachedToWindow(this)
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        NexusWidgetViewNotify.onWindowVisibilityChanged(this, visibility)
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        NexusWidgetViewNotify.onVisibilityAggregated(this, isVisible)
    }
}


