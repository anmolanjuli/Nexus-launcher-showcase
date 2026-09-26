package com.nexus.launcher.ui.settings

import android.animation.LayoutTransition
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeEntryPoint
import com.nexus.launcher.ui.glass.FrostedGlassEngine
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

/** Settings → Appearance → Nexus Island. */
@AndroidEntryPoint
class IslandSettingsFragment : Fragment() {

    private val viewModel: SettingsViewModel by activityViewModels()
    private lateinit var binder: IslandSettingsBinder
    private var ignoreCallbacks = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val dp = resources.displayMetrics.density
        val padH = (16 * dp).toInt()
        binder = IslandSettingsBinder(requireContext())
        val scrollView = NestedScrollView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT,
            )
            isFillViewport = true
        }
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            setPadding(padH, 0, padH, (80 * dp).toInt())
            layoutTransition = LayoutTransition()
            addView(binder.mainSection)
            addView(binder.triggerSection)
        }
        scrollView.addView(content)
        return scrollView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binder.setupListeners(
            { transform ->
                viewModel.patchDraft(transform = transform)
                bindDraft()
            },
            { ignoreCallbacks },
        )
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.ensureDraftReady()
            bindDraft()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.bindEpoch.collect { bindDraft() }
            }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    val controller = EntryPointAccessors.fromApplication(
                        requireContext().applicationContext,
                        ThemeEntryPoint::class.java,
                    ).themeController()
                    controller.currentTokens.collect { tokens ->
                        applyPageBackground(tokens, view as NestedScrollView)
                    }
                } catch (_: Exception) { }
            }
        }
    }

    private fun bindDraft() {
        val pending = viewModel.draft.value ?: return
        ignoreCallbacks = true
        binder.bind(pending)
        ignoreCallbacks = false
    }

    private fun applyPageBackground(tokens: NexusColorTokens, scrollView: NestedScrollView) {
        if (FrostedGlassEngine.isGlobalFrostedGlassEnabled) {
            scrollView.setBackgroundColor(Color.TRANSPARENT)
        } else {
            scrollView.setBackgroundColor(tokens.bg)
        }
    }
}
