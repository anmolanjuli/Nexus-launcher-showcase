package com.nexus.launcher.ui.canvas

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.TextPaint
import com.nexus.launcher.ui.model.DisplayItem

class DrawerRailRenderer(private val density: Float) {

    // Rail: one letter wide + 6dp padding each side
    private val letterTextSizePx = 13f * density
    private val railHPadPx = 6f * density
    // Approximate single-letter width at 11sp ≈ 8dp; total rail = 8 + 6 + 6 = 20dp
    private val railWidthPx = 20f * density
    // Small visual gap between the rail and the screen edge — hit-testing (LauncherRailTouchHelper)
    // already uses a wider, unrelated tap zone, so this is render-position-only.
    private val railEdgeMarginPx = 16f * density

    // Bubble drawn to the side of the rail during scrub (left of rail in LTR, right of rail in RTL)
    private val bubbleTextSizePx = 26f * density
    private val bubbleSizePx = 48f * density
    private val bubbleCornerPx = 10f * density
    private val bubbleGapPx = 8f * density

    private var accentColorInt: Int = Color.parseColor("#7EB8D4") // active-letter / bubble highlight — neutral token color, not the user's Accent Color
    private var idleColorInt: Int = Color.WHITE // idle letters — was hardcoded white, invisible in Light theme
    private var bubbleTextColorInt: Int = Color.BLACK // scrub bubble's own text — was hardcoded white, invisible against a light (textPrimary-colored) bubble in Light theme

    var isRtl: Boolean = false

    fun setAccentColor(color: Int) {
        accentColorInt = color
    }

    fun setIdleColor(color: Int) {
        idleColorInt = color
    }

    fun setBubbleTextColor(color: Int) {
        bubbleTextColorInt = color
    }

    private var activeLetter: Char? = null
    var isVisible: Boolean = true
    var globalAlpha: Int = 255

    var onActiveLetterChanged: ((Char?) -> Unit)? = null

    private val letterPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        style = Paint.Style.FILL
    }

    private val bubbleBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val bubbleTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private var touchY: Float? = null
    
    private var fingerX: Float = -1f
    private var fingerY: Float = -1f
    private var railEdgeX: Float = 0f
    private var springAnim: androidx.dynamicanimation.animation.SpringAnimation? = null

    fun updateFingerPosition(x: Float, y: Float, railEdge: Float) {
        fingerX = x
        fingerY = y
        railEdgeX = railEdge
        springAnim?.cancel()
        springAnim = null
    }

    fun releaseSpring(railEdge: Float, view: android.view.View) {
        springAnim?.cancel()
        springAnim = androidx.dynamicanimation.animation.SpringAnimation(
            this,
            object : androidx.dynamicanimation.animation.FloatPropertyCompat<DrawerRailRenderer>("fingerX") {
                override fun getValue(obj: DrawerRailRenderer) = obj.fingerX
                override fun setValue(obj: DrawerRailRenderer, value: Float) {
                    obj.fingerX = value
                    view.invalidate()
                    if (kotlin.math.abs(value - railEdge) < 1f) {
                        obj.fingerX = -1f
                    }
                }
            }, railEdge
        ).apply {
            spring = androidx.dynamicanimation.animation.SpringForce(railEdge).apply {
                stiffness = androidx.dynamicanimation.animation.SpringForce.STIFFNESS_LOW
                dampingRatio = androidx.dynamicanimation.animation.SpringForce.DAMPING_RATIO_LOW_BOUNCY
            }
        }
        springAnim?.start()
    }

    fun updateTouchY(y: Float?, view: android.view.View? = null) {
        touchY = y
        if (y == null) {
            onActiveLetterChanged?.invoke(null)
            lastHapticLetter = null
            activeLetter = null
            view?.invalidate()
        }
    }

    fun drawRail(
        canvas: Canvas,
        viewWidth: Int,
        @Suppress("UNUSED_PARAMETER") viewHeight: Int,
        gridTop: Int,
        gridBottom: Int,
        rawDrawerApps: List<DisplayItem>,
        context: android.content.Context? = null
    ) {
        val railLeft = if (isRtl) railEdgeMarginPx else (viewWidth - railEdgeMarginPx - railWidthPx)
        val railCenterX = if (isRtl) (railEdgeMarginPx + railWidthPx / 2f) else (viewWidth - railEdgeMarginPx - railWidthPx / 2f)
        val railTop = gridTop.toFloat()
        val railBottom = gridBottom.toFloat()
        val railHeight = railBottom - railTop

        val effectiveLocale = if (context != null) {
            com.nexus.launcher.locale.LocaleObserver.getEffectiveLocale(context)
        } else {
            java.util.Locale.getDefault()
        }
        val alphabet = DrawerAlphabetHelper.getAlphabet(effectiveLocale)
        val letterCount = alphabet.size
        val letterHeight = railHeight / letterCount.toFloat()

        letterPaint.textSize = letterTextSizePx
        bubbleTextPaint.textSize = bubbleTextSizePx
        if (context != null) {
            val tf = com.nexus.launcher.typography.CanvasTypographyHelper.getTypeface(
                context,
                android.graphics.Typeface.BOLD
            )
            letterPaint.typeface = tf
            bubbleTextPaint.typeface = tf
        }

        var activeLetterY = 0f

        for (i in 0 until letterCount) {
            val letter = alphabet[i]
            
            val letterY = railTop + i * letterHeight + letterHeight / 2f
            
            var letterOffsetX = 0f
            if (fingerX != -1f) {
                val isPulledInward = if (isRtl) (fingerX > railEdgeX) else (fingerX < railEdgeX)
                if (isPulledInward) {
                    val pullDistance = if (isRtl) (fingerX - railEdgeX).coerceAtLeast(0f) else (railEdgeX - fingerX).coerceAtLeast(0f)
                    if (pullDistance >= 4f * density) {
                        val distanceFromFinger = kotlin.math.abs(letterY - fingerY)
                        val bulgeRadiusPx = (pullDistance * 3.0f).coerceAtMost(300f * density)
                        val participation = if (bulgeRadiusPx > 0f) {
                            kotlin.math.exp(-(distanceFromFinger * distanceFromFinger) / (2f * bulgeRadiusPx * bulgeRadiusPx / 4f)).toFloat()
                        } else 0f
                        val directionSign = if (isRtl) 1f else -1f
                        letterOffsetX = directionSign * pullDistance * participation * 1.2f
                    }
                }
            }

            val letterX = railCenterX + letterOffsetX

            if (letter == activeLetter) {
                activeLetterY = letterY
                letterPaint.color = accentColorInt
                letterPaint.alpha = globalAlpha
            } else {
                letterPaint.color = idleColorInt
                // Available letters at full opacity (matches app-label opacity); unavailable at 90
                val available = rawDrawerApps.any { DrawerAlphabetHelper.getRailLetter(it.label, effectiveLocale) == letter }
                val baseA = if (available) 255 else 90
                letterPaint.alpha = (baseA * globalAlpha / 255)
            }

            canvas.drawText(letter.toString(), letterX, letterY, letterPaint)
        }

        // Bubble: shown only while actively scrubbing
        val letter = activeLetter
        if (letter != null && touchY != null && activeLetterY != 0f) {
            val bubbleLeft = if (isRtl) (railWidthPx + bubbleGapPx) else (railLeft - bubbleGapPx - bubbleSizePx)
            val bubbleRight = bubbleLeft + bubbleSizePx
            val bubbleTop = activeLetterY - bubbleSizePx / 2f
            val bubbleBottom = activeLetterY + bubbleSizePx / 2f

            // Accent color at 90% opacity (0xE6 = 230 ≈ 90% of 255) multiplied by globalAlpha
            val bubbleAlpha = (230 * globalAlpha / 255)
            bubbleBgPaint.color = (accentColorInt and 0x00FFFFFF) or (bubbleAlpha shl 24)
            bubbleTextPaint.color = bubbleTextColorInt
            canvas.drawRoundRect(
                RectF(bubbleLeft, bubbleTop, bubbleRight, bubbleBottom),
                bubbleCornerPx, bubbleCornerPx,
                bubbleBgPaint
            )

            val textX = bubbleLeft + bubbleSizePx / 2f
            // Vertically center text: baseline = centerY + half cap height
            val textY = activeLetterY + bubbleTextSizePx / 2f - bubbleTextPaint.descent()
            bubbleTextPaint.alpha = globalAlpha
            canvas.drawText(letter.toString(), textX, textY, bubbleTextPaint)
        }
    }

    private var lastHapticLetter: Char? = null

    fun handleRailTouch(
        view: android.view.View,
        y: Float,
        @Suppress("UNUSED_PARAMETER") viewHeight: Int,
        gridTop: Int,
        gridBottom: Int,
        maxScrollY: Float,
        rawDrawerApps: List<DisplayItem>,
        cellHeight: Float,
        columnCount: Int = 5
    ): Float? {
        val railTop = gridTop.toFloat()
        val railBottom = gridBottom.toFloat()
        val railHeight = railBottom - railTop

        if (y < railTop || y > railBottom) return null

        val locale = com.nexus.launcher.locale.LocaleObserver.getEffectiveLocale(view.context)
        val alphabet = DrawerAlphabetHelper.getAlphabet(locale)
        val letterCount = alphabet.size
        val letterHeight = railHeight / letterCount.toFloat()
        val index = ((y - railTop) / letterHeight).toInt().coerceIn(0, alphabet.lastIndex)
        val letter = alphabet[index]

        if (lastHapticLetter != letter) {
            lastHapticLetter = letter
            activeLetter = letter
            onActiveLetterChanged?.invoke(letter)
            view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        }

        val targetAppIndex = rawDrawerApps.indexOfFirst {
            DrawerAlphabetHelper.getRailLetter(it.label, locale) == letter
        }
        if (targetAppIndex != -1) {
            val row = targetAppIndex / columnCount
            return (row * cellHeight).coerceIn(0f, maxScrollY)
        }
        return null
    }
}
