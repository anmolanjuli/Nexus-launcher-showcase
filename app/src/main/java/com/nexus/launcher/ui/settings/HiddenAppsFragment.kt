package com.nexus.launcher.ui.settings

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeEntryPoint
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HiddenAppsFragment : Fragment() {

    private val viewModel: HiddenAppsViewModel by viewModels()
    private var currentTokens: NexusColorTokens = NexusColorTokens.Dark
    private lateinit var rootContainer: FrameLayout
    private var frostBackdrop: SettingsFrostBackdropView? = null
    private lateinit var card: LinearLayout
    private lateinit var listContainer: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var backButton: ImageView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val dp = resources.displayMetrics.density
        currentTokens = try { ThemeObserver.currentTokens(requireContext()) } catch (_: Exception) { NexusColorTokens.Dark }

        rootContainer = FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        // Reachable from both MainActivity (drawer overflow menu) and SettingsActivity (Drawer
        // settings page's "Hidden Apps" row) — neither reliably has a live home-screen canvas on
        // screen to blur (SettingsActivity never does; MainActivity's is only visible when this
        // fragment happens to be layered over it). SettingsFrostBackdropView already handles this
        // exact situation (it's what the Settings pages use) by sampling/baking a blurred
        // wallpaper snapshot instead of relying on a live RenderEffect over on-screen content.
        if (com.nexus.launcher.ui.glass.FrostedGlassEngine.isGlobalFrostedGlassEnabled) {
            val backdrop = SettingsFrostBackdropView(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
            frostBackdrop = backdrop
            rootContainer.addView(backdrop)
        }

        card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                val sideMargin = (8 * dp).toInt()
                setMargins(sideMargin, (8 * dp).toInt(), sideMargin, (8 * dp).toInt())
            }
            clipToOutline = true
        }

        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { _, insets ->
            val status = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val sideMargin = (8 * dp).toInt()
            val topMargin = (8 * dp).toInt() + status.top
            val bottomMargin = (8 * dp).toInt() + maxOf(nav.bottom, ime.bottom)
            (card.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                lp.setMargins(sideMargin, topMargin, sideMargin, bottomMargin)
                card.layoutParams = lp
            }
            insets
        }
        ViewCompat.requestApplyInsets(rootContainer)

        // Toolbar
        val toolbar = FrameLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (56 * dp).toInt()
            )
            setPadding((8 * dp).toInt(), 0, (16 * dp).toInt(), 0)
        }

        backButton = ImageView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams((48 * dp).toInt(), (48 * dp).toInt()).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
            }
            setImageResource(R.drawable.ic_back_arrow)
            setPadding((12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt())
            imageTintList = ColorStateList.valueOf(currentTokens.textPrimary)
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
        toolbar.addView(backButton)

        titleView = TextView(requireContext()).apply {
            text = requireContext().getString(com.nexus.launcher.R.string.drawer_settings_hidden_apps_title)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                marginStart = (48 * dp).toInt()
            }
        }
        NexusTypeScale.title.bindTo(viewLifecycleOwner, titleView, currentTokens.textPrimary)
        toolbar.addView(titleView)
        card.addView(toolbar)

        // Scroll content
        val scrollView = ScrollView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
            setBackgroundColor(Color.TRANSPARENT)
        }
        listContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(0, (8 * dp).toInt(), 0, (40 * dp).toInt())
        }
        scrollView.addView(listContainer)
        card.addView(scrollView)
        rootContainer.addView(card)

        applyThemeTokens()

        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.hiddenApps.collect { apps ->
                        renderList(apps, dp)
                    }
                }
                launch {
                    try {
                        val controller = EntryPointAccessors.fromApplication(
                            requireContext().applicationContext,
                            ThemeEntryPoint::class.java
                        ).themeController()
                        controller.currentTokens.collect { tokens ->
                            currentTokens = tokens
                            applyThemeTokens()
                            renderList(viewModel.hiddenApps.value, dp)
                        }
                    } catch (_: Exception) {}
                }
            }
        }

        return rootContainer
    }

    private fun applyThemeTokens() {
        val dp = resources.displayMetrics.density
        val isFrosted = com.nexus.launcher.ui.glass.FrostedGlassEngine.isGlobalFrostedGlassEnabled
        if (isFrosted) {
            val frostedTokens = com.nexus.launcher.ui.glass.FrostedGlassEngine.resolveFrostedTokens(currentTokens)
            val fillAlpha = (com.nexus.launcher.ui.glass.FrostedGlassEngine.sheetFillAlpha(1f, 0.55f) * 255f).toInt()
            card.background = GradientDrawable().apply {
                setColor((frostedTokens.surface and 0x00FFFFFF) or (fillAlpha shl 24))
                cornerRadius = 24 * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), frostedTokens.border)
            }
            rootContainer.setBackgroundColor(Color.TRANSPARENT)
        } else {
            card.background = GradientDrawable().apply {
                setColor(currentTokens.surface)
                cornerRadius = 24 * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), currentTokens.divider)
            }
            rootContainer.setBackgroundColor(currentTokens.bg)
        }
        backButton.imageTintList = ColorStateList.valueOf(currentTokens.textPrimary)
        titleView.setTextColor(currentTokens.textPrimary)
    }

    private fun renderList(apps: List<HiddenAppItem>, dp: Float) {
        listContainer.removeAllViews()
        if (apps.isEmpty()) {
            val empty = TextView(requireContext()).apply {
                text = requireContext().getString(com.nexus.launcher.R.string.hidden_apps_empty)
                gravity = Gravity.CENTER
                setPadding(0, (48 * dp).toInt(), 0, 0)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            NexusTypeScale.body.bindTo(
                viewLifecycleOwner,
                empty,
                currentTokens.textSecondary
            )
            listContainer.addView(empty)
        } else {
            apps.forEach { app ->
                listContainer.addView(buildAppRow(app, dp))
            }
        }
    }

    private fun buildAppRow(
        app: HiddenAppItem,
        dp: Float
    ): View {
        val row = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val iconView = ImageView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                (40 * dp).toInt(),
                (40 * dp).toInt()
            ).apply {
                marginEnd = (16 * dp).toInt()
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageDrawable(app.icon)
        }

        val labelView = TextView(requireContext()).apply {
            text = app.label
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
        }
        NexusTypeScale.body.bindTo(viewLifecycleOwner, labelView, currentTokens.textPrimary)

        val unhideBtn = TextView(requireContext()).apply {
            text = requireContext().getString(com.nexus.launcher.R.string.hidden_apps_unhide)
            gravity = Gravity.CENTER
            val hPad = (14 * dp).toInt()
            val vPad = (6 * dp).toInt()
            setPadding(hPad, vPad, hPad, vPad)
            background = GradientDrawable().apply {
                setColor(currentTokens.surfaceRaised)
                cornerRadius = 14 * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), currentTokens.divider)
            }
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                viewModel.unhideApp(app.packageName)
            }
        }
        NexusTypeScale.labelSmall.bindTo(
            viewLifecycleOwner,
            unhideBtn,
            currentTokens.textPrimary
        )

        row.addView(iconView)
        row.addView(labelView)
        row.addView(unhideBtn)
        return row
    }

    companion object {
        fun newInstance() = HiddenAppsFragment()
    }
}
