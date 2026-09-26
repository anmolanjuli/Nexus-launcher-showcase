package com.nexus.launcher.ui.dock

import android.content.Context
import android.content.res.Configuration
import android.graphics.RectF
import com.nexus.launcher.ui.canvas.LayoutProfile

/**
 * Single source of truth for converting between "main axis / cross axis" and (x, y) coordinates.
 *
 * Horizontal (portrait or large-screen landscape):
 *   main = X, cross = Y
 *   mainSize = width, crossSize = height
 *
 * Vertical (phone landscape only: LayoutProfile.isPhoneLandscape):
 *   main = Y, cross = X
 *   mainSize = height, crossSize = width
 */
object DockAxis {

    fun isVertical(configuration: Configuration): Boolean =
        LayoutProfile.isPhoneLandscape(configuration)

    fun isVertical(context: Context): Boolean =
        LayoutProfile.isPhoneLandscape(context)

    fun isVertical(dock: DockLayout): Boolean =
        isVertical(dock.resources.configuration)

    fun main(x: Float, y: Float, isVertical: Boolean): Float =
        if (isVertical) y else x

    fun cross(x: Float, y: Float, isVertical: Boolean): Float =
        if (isVertical) x else y

    fun x(main: Float, cross: Float, isVertical: Boolean): Float =
        if (isVertical) cross else main

    fun y(main: Float, cross: Float, isVertical: Boolean): Float =
        if (isVertical) main else cross

    fun point(main: Float, cross: Float, isVertical: Boolean): Pair<Float, Float> =
        if (isVertical) cross to main else main to cross

    fun mainSize(width: Int, height: Int, isVertical: Boolean): Int =
        if (isVertical) height else width

    fun crossSize(width: Int, height: Int, isVertical: Boolean): Int =
        if (isVertical) width else height

    fun rectF(
        mainStart: Float,
        mainEnd: Float,
        crossStart: Float,
        crossEnd: Float,
        isVertical: Boolean
    ): RectF = if (isVertical) {
        RectF(crossStart, mainStart, crossEnd, mainEnd)
    } else {
        RectF(mainStart, crossStart, mainEnd, crossEnd)
    }

    fun iconBounds(
        mainPos: Float,
        crossPos: Float,
        size: Float,
        isVertical: Boolean
    ): RectF {
        val left = x(mainPos, crossPos, isVertical)
        val top = y(mainPos, crossPos, isVertical)
        return RectF(left, top, left + size, top + size)
    }
}
