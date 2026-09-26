package com.nexus.launcher.ui.canvas

import android.graphics.RectF
import com.nexus.launcher.ui.dock.DockSpringPhysics

/**
 * Spring-tracked landing highlight for a home-screen drag.
 *
 * The highlight used to be recomputed from the finger every frame and drawn at the exact target
 * cell, so it teleported the instant the finger crossed a cell boundary. A grid that only ever
 * cuts between discrete states reads as dead no matter how it is styled; letting the same shape
 * glide between cells is what makes it feel like it is tracking you.
 *
 * Centre and half-extents are sprung independently so a move and a span change (1x1 icon to a
 * 2x2 folder, or the merge-morph onto a target icon) resolve as one continuous motion rather
 * than a jump plus a resize. Uses [DockSpringPhysics] — the frame-stepped integrator this project
 * already uses for continuously-retargeted motion — rather than [SettlePhysics], which is for
 * fixed-duration settles with a known endpoint.
 */
internal class DragGridHighlight {

    private val centerX = DockSpringPhysics.Channel(0f)
    private val centerY = DockSpringPhysics.Channel(0f)
    private val halfWidth = DockSpringPhysics.Channel(0f)
    private val halfHeight = DockSpringPhysics.Channel(0f)

    private var armed = false

    /** Current sprung geometry. Valid only after a [step] that returned normally. */
    val rect = RectF()

    /** Call whenever no drag is in flight so the next one snaps instead of flying in. */
    fun reset() {
        armed = false
    }

    /**
     * Retargets the highlight at [target]. Returns true while still in motion, so the caller
     * knows to schedule another frame even if the finger has stopped moving.
     */
    fun step(target: RectF): Boolean {
        val targetCx = target.centerX()
        val targetCy = target.centerY()
        val targetHw = target.width() / 2f
        val targetHh = target.height() / 2f

        if (!armed) {
            armed = true
            DockSpringPhysics.snap(centerX, targetCx)
            DockSpringPhysics.snap(centerY, targetCy)
            DockSpringPhysics.snap(halfWidth, targetHw)
            DockSpringPhysics.snap(halfHeight, targetHh)
            rect.set(target)
            return false
        }

        var moving = false
        if (DockSpringPhysics.stepCustom(centerX, targetCx, STIFFNESS, DAMPING)) moving = true
        if (DockSpringPhysics.stepCustom(centerY, targetCy, STIFFNESS, DAMPING)) moving = true
        if (DockSpringPhysics.stepCustom(halfWidth, targetHw, STIFFNESS, DAMPING)) moving = true
        if (DockSpringPhysics.stepCustom(halfHeight, targetHh, STIFFNESS, DAMPING)) moving = true

        rect.set(
            centerX.position - halfWidth.position,
            centerY.position - halfHeight.position,
            centerX.position + halfWidth.position,
            centerY.position + halfHeight.position
        )
        return moving
    }

    private companion object {
        /** Tuning knobs for the glide. Higher stiffness chases the cell harder. */
        const val STIFFNESS = 0.34f
        const val DAMPING = 0.66f
    }
}
