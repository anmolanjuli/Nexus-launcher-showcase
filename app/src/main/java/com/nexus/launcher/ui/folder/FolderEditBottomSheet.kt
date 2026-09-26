package com.nexus.launcher.ui.folder

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nexus.launcher.R
import com.nexus.launcher.data.FolderConfig
import com.nexus.launcher.data.HomeScreenDao
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.ui.MainViewModel
import com.nexus.launcher.ui.settings.IconEditGestureSection
import kotlinx.coroutines.CoroutineScope

class FolderEditBottomSheet(
    private val folderItem: HomeScreenItem,
    private val dao: HomeScreenDao,
    private val coroutineScope: CoroutineScope
) : BottomSheetDialogFragment() {

    private var workingConfig: FolderConfig = FolderConfigCodec.parse(folderItem.folderConfigJson)
    private var previewBinder: FolderEditSheetPreviewBinder? = null
    private lateinit var tokens: NexusColorTokens
    private lateinit var panelRoot: LinearLayout
    private lateinit var bodyViews: FolderEditBodyViews

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        isCancelable = false
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext(), theme).also { com.nexus.launcher.ui.LandscapeSheets.apply(it) }.apply {
            behavior.isFitToContents = true
            behavior.skipCollapsed = true
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            setOnShowListener { dlg ->
                val d = dlg as BottomSheetDialog
                d.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                    ?.setBackgroundColor(Color.TRANSPARENT)
                FolderEditSheetDecor.apply(d)
                FolderBlurCoordinator.setWorkspaceBlur(requireContext(), true)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val dp = resources.displayMetrics.density
        val screenH = resources.displayMetrics.heightPixels
        tokens = try {
            ThemeObserver.currentTokens(requireContext())
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }

        val chrome = FolderEditChromeBuilder.build(requireContext(), tokens, dp)
        val root = chrome.root
        panelRoot = chrome.panelRoot
        val closeBtn = chrome.closeBtn

        bodyViews = FolderEditBodyBuilder.build(requireContext(), tokens, dp)
        panelRoot.addView(bodyViews.headerRoot)

        val scrollMax = (screenH * 0.48f).toInt()
        val scroll = object : NestedScrollView(requireContext()) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val capped = MeasureSpec.makeMeasureSpec(scrollMax, MeasureSpec.AT_MOST)
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
        panelRoot.addView(scroll)

        val mainViewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        val folderKey = "folder_${folderItem.id}"
        val upActionStr = mainViewModel.swipeUpActions.value[folderKey] ?: "NONE"
        val downActionStr = mainViewModel.swipeDownActions.value[folderKey] ?: "NONE"
        var pendingSwipeUp: String = upActionStr
        var pendingSwipeDown: String = downActionStr
        // Assigned once the footer builds; every staged mutation re-evaluates dirty state through it.
        var refreshApplyEnabled: () -> Unit = {}

        fun attachGestures() {
            FolderEditGesturesHelper.attach(
                bodyViews, requireContext(), parentFragmentManager, dp,
                pendingSwipeUp, pendingSwipeDown,
                onSwipeUpChanged = { pendingSwipeUp = it; refreshApplyEnabled() },
                onSwipeDownChanged = { pendingSwipeDown = it; refreshApplyEnabled() }
            )
        }
        attachGestures()

        workingConfig = FolderConfigCodec.parse(folderItem.folderConfigJson)
        var gridColumns = workingConfig.gridColumns.coerceIn(2, 10)
        var showLabels = workingConfig.showLabels
        var isExpressive = workingConfig.isExpressive
        var frostedIndex = workingConfig.frostedGradientIndex
        var solidHex: String = workingConfig.solidBackgroundColor ?: workingConfig.backgroundColor ?: "#131822"
        workingConfig = workingConfig.copy(
            gridColumns = gridColumns,
            solidBackgroundColor = solidHex
        )
        val originalTitle = (folderItem.folderTitle ?: "").trim()
        bodyViews.titleEdit.setText(folderItem.folderTitle)

        bodyViews.titleEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                previewBinder?.updateConfig(workingConfig, s?.toString()?.trim().orEmpty())
                refreshApplyEnabled()
            }
        })

        var isUserModified = false

        FolderEditAppearanceBinder.bind(
            requireContext(),
            bodyViews,
            tokens,
            { workingConfig }
        ) { updated ->
            isUserModified = true
            workingConfig = updated
            previewBinder?.updateConfig(workingConfig)
            refreshApplyEnabled()
        }

        FolderEditShapeBinder.bind(
            requireContext(),
            bodyViews,
            { workingConfig },
            { previewBinder }
        ) { updated ->
            isUserModified = true
            workingConfig = updated
            refreshApplyEnabled()
        }

        FolderEditAppearanceBinder.bindSliders(
            bodyViews,
            { workingConfig }
        ) { updated ->
            isUserModified = true
            workingConfig = updated
            previewBinder?.updateConfig(workingConfig)
            refreshApplyEnabled()
        }

        val previewLabels = FolderPreviewOptions.getAvailableLabels(requireContext(), folderItem.spanX, folderItem.spanY)
        // Label on its own row with the options beneath, rather than sharing one line — five
        // styles no longer fit beside the label without scrolling.
        bodyViews.previewRow.configure(getString(R.string.folder_edit_preview_style), previewLabels, workingConfig.previewStyle.toString(), inline = false)
        val openCustomIconPicker = {
            FolderCoverIconPickerSheet(folderItem.id.toLong()) { path: String? ->
                isUserModified = true
                workingConfig = workingConfig.copy(customIconPackage = path)
                previewBinder?.updateConfig(workingConfig)
                refreshApplyEnabled()
            }.show(parentFragmentManager, "FolderCoverIconPickerSheet")
        }
        bodyViews.previewRow.onValueChanged = { value: String ->
            isUserModified = true
            val style = value.toIntOrNull() ?: 0
            workingConfig = workingConfig.copy(previewStyle = style, previewStyleExplicitlySet = true)
            previewBinder?.updateConfig(workingConfig)
            refreshApplyEnabled()
            if (style == 3) openCustomIconPicker()
        }
        bodyViews.previewRow.onValueReselected = { value: String ->
            if ((value.toIntOrNull() ?: 0) == 3) openCustomIconPicker()
        }

        bodyViews.columnsSlider.configure(getString(R.string.folder_edit_columns), 2, 10, gridColumns)
        bodyViews.columnsSlider.onValueChanged = {
            isUserModified = true
            gridColumns = it.coerceIn(2, 10)
            workingConfig = workingConfig.copy(gridColumns = gridColumns)
            previewBinder?.updateLayout(gridColumns, showLabels)
            refreshApplyEnabled()
        }

        bodyViews.labelsToggle.configure(getString(R.string.folder_edit_show_labels), showLabels)
        bodyViews.labelsToggle.onCheckedChanged = {
            isUserModified = true
            showLabels = it
            workingConfig = workingConfig.copy(showLabels = it)
            previewBinder?.updateLayout(gridColumns, showLabels)
            refreshApplyEnabled()
        }

        val backgroundBinder = FolderBackgroundPickerBinder(bodyViews.backgroundContainer) { mode, index, hex ->
            isUserModified = true
            frostedIndex = index
            solidHex = hex ?: solidHex
            workingConfig = workingConfig.copy(
                windowBackgroundMode = mode,
                frostedGradientIndex = index,
                solidBackgroundColor = solidHex
            )
            previewBinder?.updateConfig(workingConfig)
            refreshApplyEnabled()
        }
        backgroundBinder.bind(workingConfig.windowBackgroundMode, frostedIndex, solidHex)

        bodyViews.expressiveToggle.configure(getString(R.string.folder_edit_expressive_gradient), isExpressive)
        bodyViews.expressiveChildContainer.visibility = if (isExpressive) View.VISIBLE else View.GONE
        bodyViews.expressiveToggle.onCheckedChanged = { isChecked ->
            isUserModified = true
            isExpressive = isChecked
            workingConfig = workingConfig.copy(isExpressive = isChecked)
            bodyViews.expressiveChildContainer.visibility = if (isChecked) View.VISIBLE else View.GONE
            FolderEditAppearanceBinder.refresh(requireContext(), bodyViews, tokens, workingConfig)
            FolderEditShapeBinder.refreshShapeUi(
                requireContext(), bodyViews, { workingConfig }, { previewBinder }
            )
            refreshApplyEnabled()
        }

        fun isDirty(): Boolean {
            val curTitle = bodyViews.titleEdit.text.toString().trim()
            val titleChanged = curTitle != originalTitle
            val gesturesChanged = pendingSwipeUp != upActionStr || pendingSwipeDown != downActionStr
            return isUserModified || titleChanged || gesturesChanged
        }

        fun attemptDismissWithGuard() {
            if (isDirty()) {
                FolderAuroraDialogs.showDiscard(requireContext()) {
                    dismiss()
                }
            } else {
                dismiss()
            }
        }

        closeBtn.setOnClickListener {
            it.performHapticFeedback(
                android.view.HapticFeedbackConstants.VIRTUAL_KEY,
                android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
            )
            attemptDismissWithGuard()
        }

        val footer = FolderEditFooterBinder.buildFooter(
            context = requireContext(),
            dp = dp,
            folderItem = folderItem,
            bodyViews = bodyViews,
            dao = dao,
            mainViewModel = mainViewModel,
            coroutineScope = coroutineScope,
            backgroundBinder = backgroundBinder,
            previewBinder = { previewBinder },
            getConfig = { workingConfig },
            setConfig = { workingConfig = it },
            getGridColumns = { gridColumns },
            setGridColumns = { gridColumns = it },
            getShowLabels = { showLabels },
            setShowLabels = { showLabels = it },
            getSolidHex = { solidHex },
            setSolidHex = { solidHex = it },
            getFrostedIndex = { frostedIndex },
            setFrostedIndex = { frostedIndex = it },
            getIconOpacity = { (workingConfig.backgroundOpacity * 100).toInt().coerceIn(0, 100) },
            setIconOpacity = { workingConfig = workingConfig.copy(backgroundOpacity = it / 100f) },
            getWindowOpacity = { (workingConfig.windowBackgroundOpacity * 100).toInt().coerceIn(0, 100) },
            setWindowOpacity = { workingConfig = workingConfig.copy(windowBackgroundOpacity = it / 100f) },
            getIsExpressive = { isExpressive },
            setIsExpressive = { isExpressive = it },
            getPendingSwipeUp = { pendingSwipeUp },
            setPendingSwipeUp = { pendingSwipeUp = it },
            getPendingSwipeDown = { pendingSwipeDown },
            setPendingSwipeDown = { pendingSwipeDown = it },
            originalUp = upActionStr,
            originalDown = downActionStr,
            attachGestures = { attachGestures() },
            onDismiss = { dismiss() },
            refreshApplyEnabled = { refreshApplyEnabled() },
            onUserModified = { isUserModified = true }
        )
        refreshApplyEnabled = { footer.applyButton.isEnabled = isDirty() }
        refreshApplyEnabled()
        panelRoot.addView(footer.container)

        val marginH = (10 * dp).toInt()
        val baseMarginBottom = (8 * dp).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val bottomMargin = com.nexus.launcher.ui.LandscapeSheets.cardBottomMargin(requireContext(), navBars.bottom, baseMarginBottom)
            (panelRoot.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                if (lp.bottomMargin != bottomMargin) {
                    lp.setMargins(marginH, 0, marginH, bottomMargin)
                    panelRoot.layoutParams = lp
                }
            }
            insets
        }

        (dialog as? androidx.activity.ComponentDialog)?.onBackPressedDispatcher?.addCallback(
            viewLifecycleOwner,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = attemptDismissWithGuard()
            }
        )

        root.addView(panelRoot)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.post {
            FolderEditSheetDecor.rePin(dialog as? BottomSheetDialog)
            previewBinder = FolderEditSheetPreviewBinder(
                requireContext(), folderItem, dao, coroutineScope
            ).also { it.attach(bodyViews.previewView, bodyViews.iconPreviewView, workingConfig) }
        }
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        FolderSheetWallpaperDecor.removeWindowBlur(this.dialog as? BottomSheetDialog, requireContext())
        FolderWindowManager.setOpenFolderChromeHidden(false)
        if (!FolderContextMenuLauncher.isShowing()) {
            FolderBlurCoordinator.setWorkspaceBlur(requireContext(), false)
        }
        super.onDismiss(dialog)
    }

    override fun onDestroyView() {
        previewBinder = null
        super.onDestroyView()
    }
}
