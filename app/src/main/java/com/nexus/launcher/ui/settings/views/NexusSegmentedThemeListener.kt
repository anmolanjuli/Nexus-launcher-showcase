package com.nexus.launcher.ui.settings.views

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nexus.launcher.feed.NexusFeedEInkCoordinator
import com.nexus.launcher.theme.ThemeEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Handles lifecycle-aware theme token observation for [NexusSegmentedRow],
 * ensuring it respects E-Ink mode paper tokens when active.
 */
class NexusSegmentedThemeListener(
    private val row: NexusSegmentedRow
) : View.OnAttachStateChangeListener {

    private var job: Job? = null

    override fun onViewAttachedToWindow(v: View) {
        subscribe(v as? NexusSegmentedRow ?: row)
    }

    override fun onViewDetachedFromWindow(v: View) {
        cleanup()
    }

    fun cleanup() {
        job?.cancel()
        job = null
    }

    fun subscribe(target: NexusSegmentedRow) {
        cleanup()
        val owner = target.findViewTreeLifecycleOwner() ?: (target.context as? LifecycleOwner)
        if (owner != null) {
            try {
                val controller = EntryPointAccessors.fromApplication(
                    target.context.applicationContext,
                    ThemeEntryPoint::class.java
                ).themeController()
                job = owner.lifecycleScope.launch {
                    owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        controller.currentTokens.collect { tokens ->
                            val effectiveTokens = if (target.paperMode) {
                                NexusFeedEInkCoordinator.getTokens(target.context)
                            } else {
                                tokens
                            }
                            target.applyTokens(effectiveTokens)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }
}
