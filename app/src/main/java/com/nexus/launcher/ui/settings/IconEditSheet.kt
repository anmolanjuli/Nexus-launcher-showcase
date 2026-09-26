package com.nexus.launcher.ui.settings

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nexus.launcher.ui.MainViewModel
import com.nexus.launcher.ui.NexusDesignSystem
import com.nexus.launcher.ui.icons.IconPackDetector
import com.nexus.launcher.ui.icons.IconResolver
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class IconEditSheet(
    private val packageName: String,
    private val initialName: String,
    private val viewModel: MainViewModel
) : BottomSheetDialogFragment() {

    @Inject lateinit var iconPackDetector: IconPackDetector
    @Inject lateinit var iconResolver: IconResolver

    private lateinit var body: IconEditSheetBody.Parts
    private lateinit var panelRoot: LinearLayout
    private lateinit var applyBtn: TextView

    private var pendingCustomIcon: String? = null
    private var isIconPending = false
    private var isResetPending = false
    private var pendingSwipeUp: String? = null
    private var pendingSwipeDown: String? = null
    private var pendingDoubleTap: String? = null
    private var initialShapeId: Int = IconShapeTileRow.FOLLOW_GLOBAL
    private var pendingShapeId: Int = IconShapeTileRow.FOLLOW_GLOBAL

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), theme).also { com.nexus.launcher.ui.LandscapeSheets.apply(it) }.apply {
            // Short sheet — content-height, bottom-aligned (not folder's 88% top-fill)
            behavior.isFitToContents = true
            behavior.skipCollapsed = true
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            setOnShowListener { dlg ->
                val d = dlg as BottomSheetDialog
                d.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                    ?.setBackgroundColor(Color.TRANSPARENT)
                d.behavior.isHideable = !hasUnsavedChanges()
                d.findViewById<View>(com.google.android.material.R.id.touch_outside)
                    ?.setOnClickListener { handleExitAttempt() }
                IconEditSheetDecor.apply(d, panelRoot)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.let { d ->
            IconEditSheetDecor.onStart(requireContext(), d)
            // Second pass after start — ensures footer is flush on first frame
            panelRoot.post { IconEditSheetDecor.rePin(d) }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val dp = resources.displayMetrics.density
        val screenH = resources.displayMetrics.heightPixels
        val tokens = try {
            com.nexus.launcher.theme.ThemeObserver.currentTokens(requireContext())
        } catch (_: Exception) {
            com.nexus.launcher.theme.NexusColorTokens.Dark
        }

        val root = FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val marginH = (10 * dp).toInt()
        panelRoot = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(marginH, 0, marginH, (8 * dp).toInt())
            }
            background = GradientDrawable().apply {
                setColor(tokens.surface)
                cornerRadius = 24f * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
            }
            clipToOutline = true
        }

        panelRoot.addView(View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                (40 * dp).toInt(), (4 * dp).toInt()
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = (11 * dp).toInt()
                bottomMargin = (10 * dp).toInt()
            }
            background = GradientDrawable().apply {
                setColor(tokens.divider)
                cornerRadius = 2f * dp
            }
        })

        panelRoot.addView(TextView(requireContext()).apply {
            text = requireContext().getString(com.nexus.launcher.R.string.icon_edit_title)
            com.nexus.launcher.typography.NexusTypeScale.title.bindTo(this, tokens.textPrimary)
            setPadding((20 * dp).toInt(), 0, (20 * dp).toInt(), (8 * dp).toInt())
        })

        initialShapeId = viewModel.getAppIconShape(packageName)
            ?: IconShapeTileRow.FOLLOW_GLOBAL
        pendingShapeId = initialShapeId

        body = IconEditSheetBody.build(
            context = requireContext(),
            fragmentManager = parentFragmentManager,
            density = dp,
            packageName = packageName,
            initialName = initialName,
            iconResolver = iconResolver,
            swipeUp = viewModel.swipeUpActions.value[packageName] ?: "NONE",
            swipeDown = viewModel.swipeDownActions.value[packageName] ?: "NONE",
            doubleTap = viewModel.doubleTapActions.value[packageName] ?: "NONE",
            initialShapeId = initialShapeId,
            onNameChanged = { updateHideability() },
            onSwipeUp = { pendingSwipeUp = it; updateHideability() },
            onSwipeDown = { pendingSwipeDown = it; updateHideability() },
            onDoubleTap = { pendingDoubleTap = it; updateHideability() },
            onShapeSelected = { id ->
                pendingShapeId = id
                isResetPending = false
                refreshPreviewFromPending()
            }
        )

        // Cap body height so sheet wraps content and stays bottom-anchored
        val scrollMax = (screenH * 0.55f).toInt()
        val scroll = object : NestedScrollView(requireContext()) {
            override fun measureChildWithMargins(
                child: View,
                parentWidthMeasureSpec: Int,
                widthUsed: Int,
                parentHeightMeasureSpec: Int,
                heightUsed: Int
            ) {
                val capped = MeasureSpec.makeMeasureSpec(scrollMax, MeasureSpec.AT_MOST)
                super.measureChildWithMargins(child, parentWidthMeasureSpec, widthUsed, capped, heightUsed)
            }
        }.apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            isFillViewport = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(body.root)
        }
        panelRoot.addView(scroll)

        val footer = com.nexus.launcher.ui.settings.views.NexusSettingsButtons.buildFooter(
            context = requireContext(),
            dp = dp,
            onReset = { confirmReset(dp) },
            onApply = { applyAndDismiss() }
        )
        applyBtn = footer.applyButton
        applyBtn.isEnabled = hasUnsavedChanges()
        panelRoot.addView(footer.container)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val bottomMargin = com.nexus.launcher.ui.LandscapeSheets.cardBottomMargin(requireContext(), navBars.bottom, (8 * dp).toInt())
            (panelRoot.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                if (lp.bottomMargin != bottomMargin) {
                    lp.setMargins(marginH, 0, marginH, bottomMargin)
                    panelRoot.layoutParams = lp
                }
            }
            insets
        }

        root.addView(panelRoot)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (dialog as? androidx.activity.ComponentDialog)
            ?.onBackPressedDispatcher
            ?.addCallback(viewLifecycleOwner, object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = handleExitAttempt()
            })

        lifecycleScope.launch {
            val packs = iconPackDetector.getAvailableIconPacks()
            withContext(Dispatchers.Main) {
                if (isAdded && ::body.isInitialized) {
                    IconEditSheetBody.populateIconPacks(
                        parts = body,
                        context = requireContext(),
                        density = resources.displayMetrics.density,
                        packs = packs,
                        onPackClick = { packPkg ->
                            IconPackBrowseSheet(packPkg) { overrideData ->
                                isIconPending = true
                                isResetPending = false
                                pendingCustomIcon = overrideData
                                refreshPreviewFromPending()
                            }.show(parentFragmentManager, "icon_pack_browse")
                        },
                        onGalleryClick = {
                            IconGallerySource.launchPicker(requireActivity(), packageName) { path ->
                                if (path != null) {
                                    isIconPending = true
                                    isResetPending = false
                                    pendingCustomIcon = path
                                    refreshPreviewFromPending()
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    override fun onDestroyView() {
        IconEditSheetDecor.onDismiss(requireContext(), dialog as? BottomSheetDialog)
        super.onDestroyView()
    }

    private fun handleExitAttempt() {
        if (hasUnsavedChanges()) {
            IconEditConfirmationDialog.show(requireContext()) { dismiss() }
        } else {
            dismiss()
        }
    }

    private fun confirmReset(dp: Float) {
        IconEditConfirmationDialog.showResetConfirmation(requireContext()) {
            isIconPending = false
            isResetPending = true
            pendingCustomIcon = null
            body.nameInput.setText(initialName)
            pendingSwipeUp = "NONE"
            pendingSwipeDown = "NONE"
            pendingDoubleTap = "NONE"
            pendingShapeId = IconShapeTileRow.FOLLOW_GLOBAL
            IconEditShapeSection.refresh(body.shapeSection, pendingShapeId)
            IconEditSheetBody.rebuildGestures(
                parts = body,
                context = requireContext(),
                fragmentManager = parentFragmentManager,
                density = dp,
                swipeUp = "NONE",
                swipeDown = "NONE",
                doubleTap = "NONE",
                onSwipeUp = { pendingSwipeUp = it; updateHideability() },
                onSwipeDown = { pendingSwipeDown = it; updateHideability() },
                onDoubleTap = { pendingDoubleTap = it; updateHideability() }
            )
            refreshPreviewFromPending()
            updateHideability()
        }
    }

    private fun applyAndDismiss() {
        // Haptic now fires on the actual Apply button (NexusSettingsButtons), not here.
        val newName = body.nameInput.text.toString().trim()
        if (newName.isNotEmpty() && newName != initialName) {
            viewModel.renameApp(packageName, newName)
        }
        if (isResetPending) {
            viewModel.setCustomIcon(packageName, null)
            viewModel.setAppIconShape(packageName, null)
        } else {
            if (isIconPending) viewModel.setCustomIcon(packageName, pendingCustomIcon)
            if (pendingShapeId != initialShapeId) {
                val shapeToSave = if (pendingShapeId == IconShapeTileRow.FOLLOW_GLOBAL) null
                else pendingShapeId
                viewModel.setAppIconShape(packageName, shapeToSave)
            }
        }
        if (pendingSwipeUp != null) viewModel.setSwipeUpAction(packageName, pendingSwipeUp)
        if (pendingSwipeDown != null) viewModel.setSwipeDownAction(packageName, pendingSwipeDown)
        if (pendingDoubleTap != null) viewModel.setDoubleTapAction(packageName, pendingDoubleTap)
        dismiss()
    }

    private fun effectivePreviewShape(): Int =
        IconEditPreviewHelper.effectivePreviewShape(pendingShapeId)

    private fun refreshPreviewFromPending() {
        val shape = effectivePreviewShape()
        if (isResetPending) {
            body.previewImage.setImageDrawable(
                iconResolver.getIcon(
                    packageName,
                    overrideShape = shape,
                    skipCustomOverride = true
                )
            )
            updateHideability()
            return
        }
        val custom = pendingCustomIcon
        if (custom != null) {
            val resolved = IconEditPreviewHelper.resolveCustomDrawable(
                requireContext(), resources, custom, shape
            )
            if (resolved != null) {
                body.previewImage.setImageDrawable(resolved)
                updateHideability()
                return
            }
        }
        body.previewImage.setImageDrawable(
            iconResolver.getIcon(packageName, overrideShape = shape)
        )
        updateHideability()
    }

    private fun updateHideability() {
        val hasChanges = hasUnsavedChanges()
        (dialog as? BottomSheetDialog)?.behavior?.isHideable = !hasChanges
        if (::applyBtn.isInitialized) applyBtn.isEnabled = hasChanges
    }



    private fun hasUnsavedChanges(): Boolean {
        if (!::body.isInitialized) return false
        val newName = body.nameInput.text.toString().trim()
        val sUp = pendingSwipeUp != null &&
            pendingSwipeUp != (viewModel.swipeUpActions.value[packageName] ?: "NONE")
        val sDn = pendingSwipeDown != null &&
            pendingSwipeDown != (viewModel.swipeDownActions.value[packageName] ?: "NONE")
        val sDt = pendingDoubleTap != null &&
            pendingDoubleTap != (viewModel.doubleTapActions.value[packageName] ?: "NONE")
        val shapeChanged = pendingShapeId != initialShapeId
        return isIconPending || isResetPending || shapeChanged ||
            (newName.isNotEmpty() && newName != initialName) || sUp || sDn || sDt
    }
}
