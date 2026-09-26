package com.nexus.launcher.ui.canvas

/**
 * TEMP diagnostic only — tag DrawerPhysics. Remove after Nova swipe investigation.
 * Progress = 1 - drawerTranslationY / viewHeight (same formula as LauncherDrawEngine).
 */
internal object DrawerPhysicsLog {

    /**
     * Off by default. [onDragMove] runs on every ACTION_MOVE of a drawer drag, and each call
     * built a ~200-char string and pushed it through the logging socket — per-frame work on the
     * exact gesture this diagnostic was added to investigate.
     *
     * This was the only diagnostic that had noticed the problem. It now shares the one switch the
     * others were given, so re-arming is a single edit rather than five.
     */
    private const val ENABLED = com.nexus.launcher.util.NexusDiag.ENABLED

    fun onDragMove(view: LauncherCanvasView, dy: Float, fingerY: Float) {
        if (!ENABLED) return
        val h = view.viewHeight.toFloat()
        val ty = view.drawerTranslationY
        val progress = if (h > 0f) (1f - ty / h).coerceIn(0f, 1f) else -1f
        android.util.Log.d(
            "DrawerPhysics",
            "MOVE dy=$dy fingerY=$fingerY drawerTY=$ty viewH=$h " +
                "denomTravel=$h progress=$progress " +
                "view.height=${view.height} scrollY=${view.scrollY} " +
                "dockReserve=${view.dockBottomReserve}"
        )
    }

    fun onFlingUp(
        view: LauncherCanvasView,
        velocityY: Float,
        velocityThreshold: Float,
        wasPureTap: Boolean
    ) {
        if (!ENABLED) return
        val h = view.viewHeight.toFloat()
        val ty = view.drawerTranslationY
        val progress = if (h > 0f) (1f - ty / h).coerceIn(0f, 1f) else -1f
        val mid = h / 2f
        android.util.Log.d(
            "DrawerPhysics",
            "FLING velocityY=$velocityY threshold=$velocityThreshold " +
                "wasPureTap=$wasPureTap drawerTY=$ty viewH=$h midSnap=$mid " +
                "progress=$progress pastMid=${ty <= mid}"
        )
    }

    fun onSnap(view: LauncherCanvasView, targetY: Float, nextState: String, durationMs: Long) {
        if (!ENABLED) return
        val h = view.viewHeight.toFloat()
        val ty = view.drawerTranslationY
        val remaining = if (h > 0f) Math.abs(ty - targetY) / h else -1f
        android.util.Log.d(
            "DrawerPhysics",
            "SNAP fromTY=$ty targetY=$targetY viewH=$h " +
                "remainingFraction=$remaining durationMs=$durationMs next=$nextState"
        )
    }
}
