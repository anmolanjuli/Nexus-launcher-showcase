package com.nexus.launcher.ui.settings

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentActivity
import com.nexus.launcher.ui.settings.views.NexusNavRow
import com.nexus.launcher.ui.settings.views.NexusSegmentedRow
import com.nexus.launcher.ui.settings.views.NexusSliderRow
import com.nexus.launcher.ui.settings.views.NexusToggleRow

/**
 * The last step of a settings search hit: once its page (or the Dock sheet) is on screen, find
 * the row by the label it shows, open a collapsed accordion around it, scroll it into view and
 * pulse it so the eye lands on it.
 *
 * Pages build their rows when they are first shown, so the lookup retries briefly until the
 * row exists. A row the page is deliberately hiding (a setting that depends on another one
 * being on) is left alone — the page is still open, which is where the user needs to be.
 */
object SettingsSearchReveal {

    private const val ATTEMPTS = 20
    private const val RETRY_MS = 50L
    private const val SETTLE_MS = 80L
    private const val PULSE_MS = 1400L
    private const val PULSE_ALPHA = 70

    private val handler = Handler(Looper.getMainLooper())

    /** Reveals [label] on settings page [position], or in the Dock sheet shown under [dockTag]. */
    fun revealOnPage(activity: FragmentActivity, position: Int, label: String, @ColorInt accent: Int, dockTag: String) =
        reveal(label, accent) {
            val fragments = activity.supportFragmentManager
            if (position == SettingsHubCatalog.PAGER_DOCK_DIALOG) {
                (fragments.findFragmentByTag(dockTag) as? DialogFragment)?.dialog?.window?.decorView
            } else {
                // ViewPager2's FragmentStateAdapter tags each page "f<position>".
                fragments.findFragmentByTag("f$position")?.view
            }
        }

    fun reveal(label: String, @ColorInt accent: Int, root: () -> View?) {
        val target = label.trim()
        if (target.isEmpty()) return
        var attempts = 0
        fun attempt() {
            val page = root()?.takeIf { it.isAttachedToWindow && it.height > 0 }
            val text = page?.let { findLabel(it, target) }
            if (page == null || text == null) {
                if (++attempts < ATTEMPTS) handler.postDelayed({ attempt() }, RETRY_MS)
                return
            }
            if (!openCollapsedAncestors(text, page)) return
            // Opening an accordion only takes effect on the next layout pass.
            handler.postDelayed({
                val row = rowFor(text)
                scrollIntoView(row)
                pulse(row, accent)
            }, SETTLE_MS)
        }
        handler.post { attempt() }
    }

    private fun findLabel(view: View, target: String): TextView? {
        if (view is TextView && view !is EditText &&
            view.text?.toString()?.trim()?.equals(target, ignoreCase = true) == true
        ) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) findLabel(view.getChildAt(i), target)?.let { return it }
        }
        return null
    }

    /** Accordions here are a header row followed by the container it shows and hides. */
    private fun openCollapsedAncestors(view: View, root: View): Boolean {
        var node = view
        while (node !== root) {
            val parent = node.parent as? ViewGroup ?: break
            if (node.visibility != View.VISIBLE) {
                val index = parent.indexOfChild(node)
                val header = (index - 1 downTo 0).map { parent.getChildAt(it) }
                    .firstOrNull { it.visibility == View.VISIBLE }
                if (header is NexusNavRow) header.performClick()
                if (node.visibility != View.VISIBLE) return false
            }
            node = parent
        }
        return true
    }

    private fun rowFor(label: TextView): View {
        var node: View = label
        repeat(5) {
            if (node is NexusToggleRow || node is NexusSliderRow || node is NexusSegmentedRow || node is NexusNavRow) {
                return node
            }
            node = node.parent as? View ?: return label.parent as? View ?: label
        }
        return label.parent as? View ?: label
    }

    private fun scrollIntoView(row: View) {
        var parent = row.parent
        while (parent != null && parent !is ScrollView && parent !is NestedScrollView) parent = parent.parent
        val scroll = parent as? ViewGroup ?: return
        val content = scroll.getChildAt(0) ?: return
        val rect = Rect()
        row.getDrawingRect(rect)
        (content as? ViewGroup)?.offsetDescendantRectToMyCoords(row, rect) ?: return
        val y = (rect.top - scroll.height / 3).coerceAtLeast(0)
        when (scroll) {
            is NestedScrollView -> scroll.smoothScrollTo(0, y)
            is ScrollView -> scroll.smoothScrollTo(0, y)
        }
    }

    private fun pulse(row: View, @ColorInt accent: Int) {
        if (row.width == 0 || row.height == 0) return
        val glow = GradientDrawable().apply {
            cornerRadius = 14f * row.resources.displayMetrics.density
            setColor(accent)
            alpha = 0
            setBounds(0, 0, row.width, row.height)
        }
        row.overlay.add(glow)
        ValueAnimator.ofInt(0, PULSE_ALPHA, 0, PULSE_ALPHA, 0).apply {
            duration = PULSE_MS
            addUpdateListener {
                glow.alpha = it.animatedValue as Int
                row.invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    row.overlay.remove(glow)
                }
            })
            start()
        }
    }
}
