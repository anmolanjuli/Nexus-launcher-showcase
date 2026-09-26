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
import com.nexus.launcher.R
import com.nexus.launcher.premium.PremiumFeature
import com.nexus.launcher.premium.PremiumGate
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeEntryPoint
import com.nexus.launcher.ui.glass.FrostedGlassEngine
import com.nexus.launcher.ui.settings.views.NexusSection
import com.nexus.launcher.ui.settings.views.NexusToggleRow
import com.nexus.launcher.ui.settings.views.SettingsSectionGroupView
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

/** Settings → Layout → Immersive Mode: the master switch and the status row it owns. */
@AndroidEntryPoint
class ImmersiveSettingsFragment : Fragment() {

    private val viewModel: SettingsViewModel by activityViewModels()
    // Per view, not per fragment: a ViewPager2 page rebuilds its view, and a row kept from the
    // last view still has that view as its parent ("child already has a parent" on add).
    private lateinit var statusSection: ImmersiveStatusSettingsSection
    private lateinit var toggleImmersive: NexusToggleRow
    private var ignoreCallbacks = false

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?,
    ): View {
        statusSection = ImmersiveStatusSettingsSection(requireContext())
        val dp = resources.displayMetrics.density
        val padH = (16 * dp).toInt()

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
        }

        toggleImmersive = NexusToggleRow(requireContext())
        val modeGroup = SettingsSectionGroupView(requireContext()).apply {
            addChildRow(toggleImmersive)
        }
        val modeSection = NexusSection(requireContext()).apply {
            isTransparentCard = true
            addRow(modeGroup)
        }

        content.addView(modeSection)
        content.addView(statusSection.section)
        scrollView.addView(content)
        return scrollView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        statusSection.setListeners({ transform -> patch(transform) }, { ignoreCallbacks })
        toggleImmersive.onCheckedChanged = { checked ->
            if (!ignoreCallbacks) {
                if (!checked || PremiumGate.allow(requireContext(), PremiumFeature.IMMERSIVE_HOME)) {
                    patch { s -> s.copy(immersiveMode = checked) }
                    bindDraft()
                } else {
                    toggleImmersive.setChecked(false)
                }
            }
        }
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

    private fun patch(
        transform: (com.nexus.launcher.data.prefs.NexusSettingsData) ->
            com.nexus.launcher.data.prefs.NexusSettingsData,
    ) {
        viewModel.patchDraft(transform = transform)
    }

    private fun bindDraft() {
        val pending = viewModel.draft.value ?: return
        ignoreCallbacks = true
        toggleImmersive.configure(
            getString(R.string.home_settings_immersive),
            pending.immersiveMode,
            subtitle = getString(R.string.home_settings_immersive_subtitle),
        )
        statusSection.bind(pending)
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
