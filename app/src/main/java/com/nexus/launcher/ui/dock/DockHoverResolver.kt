package com.nexus.launcher.ui.dock

import com.nexus.launcher.data.HomeScreenItem
import kotlin.math.abs

/** Finger-to-slot resolution using displaced spring positions (no midpoint dead zone). */
internal object DockHoverResolver {

    const val SLOT_CHANGE_IMPULSE_PX = 140f

    fun slotForFingerX(
        fingerX: Float,
        layout: DockRenderHelper.PaintLayout,
        animX: Map<Int, DockSpringPhysics.Channel>,
        isInternalDrag: Boolean,
        draggedOriginalColumn: Int?,
        allowAppendGap: Boolean
    ): Int {
        val slotWidth = layout.slotWidth
        val maxIcons = layout.maxDockIcons
        val originalCount = layout.originalCount
        if (originalCount == 0) return 0

        if (isInternalDrag) {
            return internalInsertIndex(fingerX, layout.axisSize, originalCount, slotWidth, maxIcons)
        }

        val sorted = layout.sortedVisible
        if (sorted.isEmpty()) return 0

        val positions = sorted.map { item ->
            animX[item.id]?.position ?: layout.startOffset + item.column * slotWidth
        }
        val lastSlotIndex = lastInsertIndex(originalCount, maxIcons, allowAppendGap)
        val centers = gapCenters(positions, slotWidth)
        val candidateCount = (lastSlotIndex + 1).coerceAtMost(centers.size).coerceAtLeast(1)
        return nearestSlotIndex(fingerX, centers, candidateCount, maxIcons)
    }

    private fun internalInsertIndex(
        fingerX: Float,
        axisSize: Int,
        originalCount: Int,
        slotWidth: Float,
        maxIcons: Int
    ): Int {
        val slotCount = originalCount.coerceAtLeast(1)
        val startOffset = DockSlotLayout.centerStartOffset(axisSize, slotCount, slotWidth)
        val lastIndex = (originalCount - 1).coerceAtLeast(0).coerceAtMost(maxIcons - 1)
        val centers = (0..lastIndex).map { slot ->
            startOffset + slot * slotWidth + slotWidth * 0.5f
        }
        return nearestSlotIndex(fingerX, centers, centers.size, maxIcons)
    }

    private fun nearestSlotIndex(
        fingerX: Float,
        centers: List<Float>,
        candidateCount: Int,
        maxIcons: Int
    ): Int {
        var bestSlot = 0
        var bestDistance = Float.MAX_VALUE
        for (slot in 0 until candidateCount) {
            val distance = abs(centers[slot] - fingerX)
            if (distance < bestDistance) {
                bestDistance = distance
                bestSlot = slot
            }
        }
        return bestSlot.coerceIn(0, maxIcons - 1)
    }

    private fun lastInsertIndex(
        originalCount: Int,
        maxIcons: Int,
        allowAppendGap: Boolean
    ): Int {
        return if (allowAppendGap && originalCount < maxIcons) {
            originalCount
        } else {
            (originalCount - 1).coerceAtLeast(0).coerceAtMost(maxIcons - 1)
        }
    }

    private fun gapCenters(positions: List<Float>, slotWidth: Float): List<Float> {
        if (positions.isEmpty()) return listOf(0f)
        val half = slotWidth * 0.5f
        val centers = ArrayList<Float>(positions.size + 1)
        centers.add(positions.first() - half)
        for (index in 0 until positions.lastIndex) {
            centers.add((positions[index] + slotWidth + positions[index + 1]) * 0.5f)
        }
        centers.add(positions.last() + half)
        return centers
    }
}
