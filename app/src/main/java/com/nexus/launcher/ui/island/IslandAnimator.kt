package com.nexus.launcher.ui.island

import android.animation.ValueAnimator
import androidx.dynamicanimation.animation.FloatPropertyCompat
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce

class IslandAnimator(private val view: IslandView) {

    private var widthAnim: SpringAnimation? = null
    private var heightAnim: SpringAnimation? = null
    private var fade: ValueAnimator? = null

    fun morphTo(widthPx: Float, heightPx: Float, speed: Float, bounce: Boolean) {
        val stiffness = (SpringForce.STIFFNESS_MEDIUM * speed).coerceIn(400f, 1800f)
        val damping = if (bounce) {
            SpringForce.DAMPING_RATIO_LOW_BOUNCY
        } else {
            SpringForce.DAMPING_RATIO_MEDIUM_BOUNCY
        }
        widthAnim?.cancel()
        heightAnim?.cancel()
        widthAnim = spring(WIDTH, widthPx, stiffness, damping)
        heightAnim = spring(HEIGHT, heightPx, stiffness, damping)
        widthAnim?.start()
        heightAnim?.start()
    }

    fun crossfade() {
        fade?.cancel()
        view.contentAlpha = 0f
        fade = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = (160 / view.animSpeed.coerceAtLeast(0.5f)).toLong()
            addUpdateListener {
                view.contentAlpha = it.animatedValue as Float
                view.invalidate()
            }
            start()
        }
    }

    fun pulseDanger() {
        view.dangerPulse = 1f
        ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 900
            addUpdateListener {
                view.dangerPulse = it.animatedValue as Float
                view.invalidate()
            }
            start()
        }
    }

    fun cancel() {
        widthAnim?.cancel()
        heightAnim?.cancel()
        fade?.cancel()
    }

    private fun spring(
        property: FloatPropertyCompat<IslandView>,
        finalValue: Float,
        stiffness: Float,
        damping: Float,
    ): SpringAnimation {
        return SpringAnimation(view, property, finalValue).apply {
            spring = SpringForce(finalValue).apply {
                this.stiffness = stiffness
                dampingRatio = damping
            }
        }
    }

    companion object {
        val WIDTH = object : FloatPropertyCompat<IslandView>("islandW") {
            override fun getValue(obj: IslandView) = obj.animWidth
            override fun setValue(obj: IslandView, value: Float) {
                obj.animWidth = value
                obj.invalidate()
            }
        }
        val HEIGHT = object : FloatPropertyCompat<IslandView>("islandH") {
            override fun getValue(obj: IslandView) = obj.animHeight
            override fun setValue(obj: IslandView, value: Float) {
                obj.animHeight = value
                obj.invalidate()
            }
        }
    }
}
