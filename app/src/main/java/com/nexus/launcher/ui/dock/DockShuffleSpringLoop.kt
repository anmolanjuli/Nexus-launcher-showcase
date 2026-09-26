package com.nexus.launcher.ui.dock

import android.view.Choreographer
import android.view.View
import java.lang.ref.WeakReference

/** Keeps dock shuffle springs advancing at display refresh until they settle. */
internal object DockShuffleSpringLoop {

    private var dockRef = WeakReference<View>(null)
    private var framePosted = false

    private val frameCallback = Choreographer.FrameCallback {
        framePosted = false
        val keepRunning = DockLayoutRenderer.isSpringLoopActive()
        if (keepRunning) {
            postFrame()
        }
        dockRef.get()?.invalidate()
    }

    fun attach(dock: View) {
        dockRef = WeakReference(dock)
        DockLayoutRenderer.requestInvalidate = {
            if (DockLayoutRenderer.isSpringLoopActive()) {
                wake()
            } else {
                dock.invalidate()
            }
        }
    }

    fun wake() {
        postFrame()
    }

    private fun postFrame() {
        if (framePosted) return
        framePosted = true
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }
}
