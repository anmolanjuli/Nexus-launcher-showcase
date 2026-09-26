package com.nexus.launcher.ui.settings

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale

/**
 * Carries a screenshot of Settings across the activity restart a language change forces.
 *
 * `AppCompatDelegate.setApplicationLocales` restarts the activity so every string and layout
 * direction re-resolves. The restart cannot be avoided. A flat colour cover still flashes against
 * a frosted/wallpaper page, so the outgoing screen is captured and held as both the window
 * backdrop and a decor overlay — same pixels, no luminance jump.
 *
 * Flicker across the destroy/create gap is killed three ways:
 * 1. [prepareIncoming] applies a theme with `windowDisablePreview`, so the system keeps the old
 *    frame on screen instead of painting `windowBackground` as a starting preview.
 * 2. [installEarly] sets that same bitmap as the new window's background *before* [setContentView],
 *    then lays the overlay on the decor view so the first drawn frame is already covered.
 * 3. Pending-transition hosts call [suppressTransitionAnim] so the platform does not animate the
 *    swap.
 *
 * The bitmap lives here rather than in saved state because it must not be parcelled. The process
 * survives an activity restart, so a plain reference is enough, and [STALE_AFTER_MS] drops it if
 * the expected restart never happens.
 */
object SettingsLocaleTransition {

    private const val STALE_AFTER_MS = 4000L

    /** Held long enough for the message to be readable, measured across the whole restart. */
    private const val MIN_VISIBLE_MS = 620L
    private const val OVERLAY_TAG = "settings_locale_snapshot"

    private var snapshot: Bitmap? = null
    private var capturedAt = 0L
    private var windowHoldsSnapshot = false

    fun hasPending(): Boolean {
        val bitmap = snapshot ?: return false
        return !bitmap.isRecycled &&
            SystemClock.elapsedRealtime() - capturedAt <= STALE_AFTER_MS
    }

    /**
     * Must run before `super.onCreate`. Switches to a no-preview theme so the relaunch does not
     * flash the static window background between the old and new instances.
     */
    fun prepareIncoming(activity: Activity) {
        if (!hasPending()) return
        activity.setTheme(R.style.Theme_NexusLauncher_Settings_LocaleSwitch)
    }

    /**
     * Must run immediately after `super.onCreate`, before [Activity.setContentView].
     *
     * Painting the snapshot as the window background covers any frame that happens before the
     * decor overlay is attached; the overlay then keeps real content from showing until reveal.
     */
    fun installEarly(activity: Activity) {
        if (!hasPending()) return
        val bitmap = snapshot ?: return
        suppressTransitionAnim(activity)
        activity.window.setBackgroundDrawable(BitmapDrawable(activity.resources, bitmap))
        windowHoldsSnapshot = true
        coverIfPending(activity)
    }

    fun suppressTransitionAnim(activity: Activity) {
        if (!hasPending()) return
        activity.overridePendingTransition(0, 0)
    }

    /**
     * Grabs the window's current pixels, then runs [onCaptured].
     *
     * Uses [PixelCopy] rather than drawing the view tree to a software canvas: the settings pages
     * are full of hardware `RenderEffect` blur, which a software draw renders unblurred — the
     * snapshot would then differ from the screen it is meant to be replacing. [onCaptured] runs
     * either way, so a failed capture degrades to the plain restart rather than blocking the
     * language change.
     */
    fun captureThen(activity: Activity, onCaptured: () -> Unit) {
        val window = activity.window
        val decor = window.peekDecorView()
        if (decor == null || decor.width <= 0 || decor.height <= 0) {
            onCaptured()
            return
        }
        val bitmap = try {
            Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        } catch (_: Throwable) {
            onCaptured()
            return
        }
        var finished = false
        fun finishOnce(captured: Bitmap?) {
            if (finished) return
            finished = true
            snapshot?.recycle()
            snapshot = captured
            capturedAt = SystemClock.elapsedRealtime()
            windowHoldsSnapshot = false
            // Raise it here too: the restart is not instant, and without this the message would
            // only appear on the far side of it.
            if (captured != null) {
                activity.window.setBackgroundDrawable(
                    BitmapDrawable(activity.resources, captured),
                )
                windowHoldsSnapshot = true
                coverIfPending(activity)
                suppressTransitionAnim(activity)
            }
            onCaptured()
        }
        try {
            PixelCopy.request(
                window,
                bitmap,
                { result ->
                    if (result == PixelCopy.SUCCESS) {
                        finishOnce(bitmap)
                    } else {
                        bitmap.recycle()
                        finishOnce(null)
                    }
                },
                Handler(Looper.getMainLooper()),
            )
        } catch (_: Throwable) {
            bitmap.recycle()
            finishOnce(null)
        }
    }

    /**
     * Lays the captured screen over [activity], if one was captured recently.
     *
     * Attaches to the decor view (not `android.R.id.content`) so it can be installed before
     * [Activity.setContentView] and stay above whatever content is inflated afterwards.
     */
    fun coverIfPending(activity: Activity) {
        val bitmap = snapshot ?: return
        if (!hasPending()) {
            release(activity)
            return
        }
        val decor = activity.window.peekDecorView() as? ViewGroup ?: return
        if (decor.findViewWithTag<View>(OVERLAY_TAG) != null) {
            decor.findViewWithTag<View>(OVERLAY_TAG)?.bringToFront()
            return
        }
        decor.addView(buildOverlay(activity, bitmap))
    }

    /**
     * The captured page, with the progress label floating on it.
     *
     * The backdrop is the screenshot rather than a flat colour precisely because a flat panel is a
     * luminance change against a page that may be showing a blurred wallpaper. The label sits in a
     * small rounded plate so it stays legible over whatever the page happened to look like.
     */
    private fun buildOverlay(activity: Activity, bitmap: Bitmap): View {
        val dp = activity.resources.displayMetrics.density
        val tokens = runCatching { ThemeObserver.currentTokens(activity) }
            .getOrDefault(NexusColorTokens.Dark)

        val plate = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(tokens.surfaceRaised)
                cornerRadius = 999f
                setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
            }
            setPadding((18 * dp).toInt(), (12 * dp).toInt(), (22 * dp).toInt(), (12 * dp).toInt())
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { gravity = Gravity.CENTER }
            addView(
                ProgressBar(activity).apply {
                    isIndeterminate = true
                    indeterminateTintList =
                        android.content.res.ColorStateList.valueOf(tokens.accent)
                    layoutParams = LinearLayout.LayoutParams((20 * dp).toInt(), (20 * dp).toInt())
                        .apply { marginEnd = (12 * dp).toInt() }
                },
            )
            addView(
                TextView(activity).apply {
                    text = activity.getString(R.string.settings_applying_language)
                    NexusTypeScale.body.bindTo(this, tokens.textPrimary)
                },
            )
        }

        return FrameLayout(activity).apply {
            tag = OVERLAY_TAG
            isClickable = true
            isFocusable = true
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            )
            addView(
                ImageView(activity).apply {
                    setImageBitmap(bitmap)
                    scaleType = ImageView.ScaleType.FIT_XY
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                },
            )
            addView(plate)
        }
    }

    /**
     * Drops the snapshot once the restored page has laid out.
     *
     * No cross-fade: dissolving a full-screen still into a re-laid-out (often RTL-flipped) page
     * reads as a flash. The hold already communicated the language change; removal is instant.
     */
    fun revealWhenReady(activity: Activity) {
        val decor = activity.window.peekDecorView() as? ViewGroup ?: return
        val overlay = decor.findViewWithTag<View>(OVERLAY_TAG) ?: return
        val remaining = (MIN_VISIBLE_MS - (SystemClock.elapsedRealtime() - capturedAt))
            .coerceIn(0L, MIN_VISIBLE_MS)
        // First post: wait for this traversal. Second: hold for readability, then uncover.
        overlay.post {
            overlay.postDelayed({
                (overlay.parent as? ViewGroup)?.removeView(overlay)
                release(activity)
            }, remaining)
        }
    }

    private fun release(activity: Activity?) {
        if (windowHoldsSnapshot && activity != null) {
            val bg = runCatching { ThemeObserver.currentTokens(activity).bg }
                .getOrDefault(NexusColorTokens.Dark.bg)
            activity.window.setBackgroundDrawable(
                android.graphics.drawable.ColorDrawable(bg),
            )
            windowHoldsSnapshot = false
        } else {
            windowHoldsSnapshot = false
        }
        snapshot?.recycle()
        snapshot = null
        capturedAt = 0L
    }
}
