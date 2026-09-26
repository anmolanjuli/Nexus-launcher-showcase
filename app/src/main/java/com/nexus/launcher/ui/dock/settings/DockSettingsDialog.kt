@file:Suppress("DEPRECATION")
package com.nexus.launcher.ui.dock.settings

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.WindowCompat
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeEntryPoint
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.dock.DockLayout
import com.nexus.launcher.ui.dock.DockLayoutFinder
import com.nexus.launcher.ui.settings.views.NexusSettingsButtons
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.launch

/** Standard bottom sheet dialog for dock configuration. */
class DockSettingsDialog(
    private val repository: DockSettingsRepository,
    private val dockLayout: DockLayout? = null
) : BottomSheetDialogFragment() {

    private var controlsBinder: DockSettingsControlsBinder? = null
    private var footer: NexusSettingsButtons.Footer? = null
    private var previewView: DockSettingsPreviewView? = null
    private var pendingSettings: PendingDockSettings? = null
    private var currentTokens: NexusColorTokens = NexusColorTokens.Dark
    private lateinit var card: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), theme).also { com.nexus.launcher.ui.LandscapeSheets.apply(it) }.apply {
            setCancelable(true)
            setOnCancelListener { attemptDismiss() }
            setOnKeyListener { _, keyCode, event ->
                if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
                    if (event.action == android.view.KeyEvent.ACTION_UP) {
                        attemptDismiss()
                    }
                    true
                } else false
            }
            DockSettingsDecor.applyWindowDecor(this)
            setOnShowListener { dialog ->
                DockSettingsDecor.applyBottomAnchor(dialog as BottomSheetDialog)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val dp = resources.displayMetrics.density
        currentTokens = try {
            ThemeObserver.currentTokens(requireContext())
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }

        val maxCardHeightPx = (resources.displayMetrics.heightPixels * 0.85f).toInt()

        card = object : LinearLayout(requireContext()) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val capped = MeasureSpec.makeMeasureSpec(maxCardHeightPx, MeasureSpec.AT_MOST)
                super.onMeasure(widthMeasureSpec, capped)
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            background = DockSettingsDecor.cardBackground(currentTokens, dp)
            DockSettingsDecor.applyCardOutline(this, dp)
        }

        // Drag Handle
        card.addView(View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                (40 * dp).toInt(), (4 * dp).toInt()
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = (11 * dp).toInt()
                bottomMargin = (10 * dp).toInt()
            }
            background = GradientDrawable().apply {
                setColor(currentTokens.divider)
                cornerRadius = 2f * dp
            }
        })

        // Header Title Row
        val headerTitleRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padH = (16 * dp).toInt()
            setPadding(padH, 0, padH, (8 * dp).toInt())
        }
        val titleText = TextView(requireContext()).apply {
            text = getString(R.string.dock_settings_title)
            NexusTypeScale.title.bindTo(this, currentTokens.textPrimary)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeBtn = ImageView(requireContext()).apply {
            setImageResource(R.drawable.ic_close)
            imageTintList = ColorStateList.valueOf(currentTokens.textSecondary)
            val pad = (6 * dp).toInt()
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams((32 * dp).toInt(), (32 * dp).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(currentTokens.surfaceRaised)
            }
            setOnClickListener {
                it.performHapticFeedback(
                    HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                )
                attemptDismiss()
            }
        }
        headerTitleRow.addView(titleText)
        headerTitleRow.addView(closeBtn)
        card.addView(headerTitleRow)

        pendingSettings = PendingDockSettings(
            maxIcons = repository.maxIcons.value,
            dockHeightDp = repository.dockHeightDp.value,
            iconSizeDp = repository.iconSizeDp.value,
            cornerRadiusDp = repository.cornerRadiusDp.value,
            labelFontSizeSp = repository.labelFontSizeSp.value,
            showLabels = repository.showLabels.value,
            searchInDock = repository.searchInDock.value,
            backgroundMode = repository.backgroundMode.value,
            frostedGradientIndex = repository.frostedGradientIndex.value,
            solidColorArgb = repository.solidColorArgb.value,
            dockBackgroundOpacity = repository.dockBackgroundOpacity.value,
            dockGlassRefraction = repository.dockGlassRefraction.value
        )
        val currentPending = pendingSettings!!

        // Safety net: clear any preview state leaked by a dismissal path that failed to revert.
        revertToRepository()

        // Live Dock Preview Card at top of sheet
        val padH = (16 * dp).toInt()
        val previewCard = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(padH, 0, padH, (8 * dp).toInt())
            }
            background = GradientDrawable().apply {
                val frostedTokens = com.nexus.launcher.ui.glass.FrostedGlassEngine.resolveFrostedTokens(currentTokens)
                val fillAlpha = (com.nexus.launcher.ui.glass.FrostedGlassEngine.sheetFillAlpha(1f, 0.70f) * 255f).toInt()
                setColor((frostedTokens.surfaceRaised and 0x00FFFFFF) or (fillAlpha shl 24))
                cornerRadius = 16 * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), frostedTokens.border)
            }
            val pad = (10 * dp).toInt()
            setPadding(pad, pad, pad, pad)
        }

        val previewTitle = TextView(requireContext()).apply {
            text = getString(com.nexus.launcher.R.string.preview_section_label).uppercase()
            NexusTypeScale.labelSmall.bindTo(this, currentTokens.textSecondary)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (6 * dp).toInt() }
        }
        previewCard.addView(previewTitle)

        val preview = DockSettingsPreviewView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (110 * dp).toInt()
            )
            updateTokens(currentTokens)
            update(currentPending)
        }
        previewView = preview
        previewCard.addView(preview)
        card.addView(previewCard)

        val bodyViews = DockSettingsBodyBuilder.build(requireContext(), currentTokens, dp)

        val refreshApplyEnabled: () -> Unit = {
            val hasChanges = currentPending.hasChanges(repository)
            footer?.applyButton?.isEnabled = hasChanges
            footer?.resetButton?.isEnabled = true
            previewView?.update(currentPending)
            Unit
        }

        controlsBinder = DockSettingsControlsBinder(
            fragment = this,
            bodyViews = bodyViews,
            pendingSettings = currentPending,
            onPendingSettingsChanged = refreshApplyEnabled,
            dock = { liveDock() }
        ).also { it.bind(currentTokens) }

        val maxScrollHeightPx = (resources.displayMetrics.heightPixels * 0.48f).toInt()
        val scroll = object : NestedScrollView(requireContext()) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val capped = MeasureSpec.makeMeasureSpec(maxScrollHeightPx, MeasureSpec.AT_MOST)
                super.onMeasure(widthMeasureSpec, capped)
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(bodyViews.root)
        }
        card.addView(scroll)

        // Sticky Footer
        footer = DockSettingsFooterBinder.buildFooter(
            context = requireContext(),
            dp = dp,
            pendingSettings = currentPending,
            repository = repository,
            coroutineScope = viewLifecycleOwner.lifecycleScope,
            controlsBinder = { controlsBinder },
            dock = { liveDock() },
            onDismiss = { dismissAllowingStateLoss() },
            refreshApplyEnabled = refreshApplyEnabled
        )
        card.addView(footer!!.container)

        refreshApplyEnabled()
        return DockSettingsDecor.wrapInRoot(card, dp)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val backCallback = object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                attemptDismiss()
            }
        }
        (dialog as? androidx.activity.ComponentDialog)?.onBackPressedDispatcher?.addCallback(
            viewLifecycleOwner,
            backCallback
        )
        activity?.onBackPressedDispatcher?.addCallback(viewLifecycleOwner, backCallback)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    val controller = EntryPointAccessors.fromApplication(
                        requireContext().applicationContext,
                        ThemeEntryPoint::class.java
                    ).themeController()
                    controller.currentTokens.collect { tokens ->
                        currentTokens = tokens
                        applyTokens(tokens)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun applyTokens(tokens: NexusColorTokens) {
        val dp = resources.displayMetrics.density
        card.background = DockSettingsDecor.cardBackground(tokens, dp)
        DockSettingsDecor.applyCardOutline(card, dp)
        previewView?.updateTokens(tokens)
        controlsBinder?.applyTokens(tokens)
    }

    private fun attemptDismiss() {
        val pending = pendingSettings ?: run {
            dismissAllowingStateLoss()
            return
        }
        if (pending.hasChanges(repository)) {
            val ctx = requireContext()
            com.nexus.launcher.ui.settings.IconEditConfirmationDialog.show(ctx) {
                revertToRepository()
                dismissAllowingStateLoss()
            }
        } else {
            revertToRepository()
            dismissAllowingStateLoss()
        }
    }

    private fun liveDock(): DockLayout? =
        dockLayout ?: context?.let { DockLayoutFinder.findInActivity(it) }

    private fun revertToRepository() {
        DockSettingsStateReverter.revert(repository, liveDock())
    }

    fun rePin() {
        DockSettingsDecor.rePin(dialog as? BottomSheetDialog)
    }

    override fun onDestroyView() {
        controlsBinder = null
        footer = null
        previewView = null
        pendingSettings = null
        com.nexus.launcher.ui.folder.FolderBlurCoordinator.setWorkspaceBlur(requireContext(), false)
        super.onDestroyView()
    }
}
