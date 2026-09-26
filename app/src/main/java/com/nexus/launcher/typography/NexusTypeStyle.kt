package com.nexus.launcher.typography

import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nexus.launcher.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Represents a typography style specification across Canvas and View layers. */
data class NexusTypeStyle(
    val sizeSp: Float,
    val weight: Int = TypefaceWeightMapper.NORMAL,
    val letterSpacingEm: Float = 0f,
    val lineHeightMultiplier: Float = 1.2f
) {
    fun applyTo(
        textView: TextView,
        color: Int? = null,
        fontProvider: FontProvider? = null
    ) {
        textView.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        val provider = fontProvider ?: resolveFontProvider(textView)
        textView.typeface = provider.getTypeface(weight)
        textView.letterSpacing = letterSpacingEm
        if (color != null) {
            textView.setTextColor(color)
        }
    }

    /** Binds typography to a TextView with an explicit LifecycleOwner. Safe to call repeatedly. */
    fun bindTo(
        lifecycleOwner: LifecycleOwner,
        textView: TextView,
        color: Int? = null
    ) {
        applyTo(textView, color)

        // Cancel and remove any auto-attach listener if previously attached
        (textView.getTag(R.id.tag_typography_binding) as? TypographyAttachListener)?.let { oldListener ->
            oldListener.cleanup()
            textView.removeOnAttachStateChangeListener(oldListener)
            textView.setTag(R.id.tag_typography_binding, null)
        }

        // Cancel previous job if this TextView was already bound to an explicit lifecycle
        (textView.getTag(R.id.tag_typography_job) as? Job)?.cancel()

        try {
            val controller = FontFamilyController.resolve(textView.context)
            val job = lifecycleOwner.lifecycleScope.launch {
                lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                    controller.fontProviderFlow.collect { provider ->
                        textView.typeface = provider.getTypeface(weight)
                    }
                }
            }
            textView.setTag(R.id.tag_typography_job, job)
        } catch (_: Exception) {
            // Fallback if Hilt entry point unavailable in preview/mock contexts
        }
    }

    /**
     * Universal auto-lifecycle bindTo for custom views, factory methods, and standalone widgets.
     * Attaches to the view tree's LifecycleOwner automatically when attached to window.
     * Safe and idempotent to call repeatedly on recycled or re-configured TextViews.
     */
    fun bindTo(
        textView: TextView,
        color: Int? = null
    ) {
        applyTo(textView, color)

        // Cancel and remove existing binding if this TextView was already bound
        (textView.getTag(R.id.tag_typography_binding) as? TypographyAttachListener)?.let { oldListener ->
            oldListener.cleanup()
            textView.removeOnAttachStateChangeListener(oldListener)
        }

        val listener = TypographyAttachListener(textView, weight)
        textView.setTag(R.id.tag_typography_binding, listener)
        textView.addOnAttachStateChangeListener(listener)
        if (ViewCompat.isAttachedToWindow(textView)) {
            listener.subscribe(textView)
        }
    }

    private fun resolveFontProvider(textView: TextView): FontProvider {
        return try {
            FontFamilyController.resolve(textView.context).getFontProvider()
        } catch (_: Exception) {
            SystemFontProvider
        }
    }

    private class TypographyAttachListener(
        private val textView: TextView,
        private val weight: Int
    ) : View.OnAttachStateChangeListener {
        private var job: Job? = null

        override fun onViewAttachedToWindow(v: View) {
            subscribe(v as? TextView ?: textView)
        }

        override fun onViewDetachedFromWindow(v: View) {
            cleanup()
        }

        fun cleanup() {
            job?.cancel()
            job = null
        }

        fun subscribe(tv: TextView) {
            cleanup()
            val owner = tv.findViewTreeLifecycleOwner() ?: (tv.context as? LifecycleOwner)
            if (owner != null) {
                try {
                    val controller = FontFamilyController.resolve(tv.context)
                    job = owner.lifecycleScope.launch {
                        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                            controller.fontProviderFlow.collect { provider ->
                                tv.typeface = provider.getTypeface(weight)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }
}
