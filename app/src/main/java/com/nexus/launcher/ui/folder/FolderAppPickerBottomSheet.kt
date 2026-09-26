package com.nexus.launcher.ui.folder

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nexus.launcher.R
import com.nexus.launcher.data.HomeScreenItem
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@AndroidEntryPoint
class FolderAppPickerBottomSheet(
    private val folderId: Long,
    private val preloadedApps: List<HomeScreenItem>? = null,
    private val preloadedFolderedPackages: Set<String>? = null,
    private val preloadedTargetPackages: Set<String>? = null,
    private val preloadedInFolderItems: List<HomeScreenItem>? = null,
    private val onAppsSaved: (() -> Unit)? = null
) : BottomSheetDialogFragment() {

    private lateinit var dao: com.nexus.launcher.data.HomeScreenDao
    private lateinit var adapter: FolderAppPickerAdapter
    private var allApps: List<HomeScreenItem> = emptyList()
    private var folderedPackages: Set<String> = emptySet()
    private var targetFolderPackages: Set<String> = emptySet()
    private var inFolderItems: List<HomeScreenItem> = emptyList()
    private val selectedIds = mutableSetOf<Long>()
    private var initialSelectedIds = setOf<Long>()
    private var showAlreadyInFolders = false
    private var searchQuery = ""
    private lateinit var saveBtn: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_folder_app_picker, container, false)
    }

    override fun onStart() {
        super.onStart()
        val d = dialog as? BottomSheetDialog ?: return
        FolderSheetWallpaperDecor.applyWindowBlur(d)
        d.window?.decorView?.post {
            FolderAppPickerDecor.apply(d)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val dp = resources.displayMetrics.density
        val tokens = try {
            com.nexus.launcher.theme.ThemeObserver.currentTokens(requireContext())
        } catch (_: Exception) {
            com.nexus.launcher.theme.NexusColorTokens.Dark
        }

        // Force fixed height to prevent two-stage resizing when data populates
        val targetHeight = (resources.displayMetrics.heightPixels * 0.9).toInt()
        view.layoutParams = android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            targetHeight
        )

        applyNavBarInsets(view)

        val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
            requireContext().applicationContext,
            com.nexus.launcher.di.DaoEntryPoint::class.java
        )
        dao = entryPoint.homeScreenDao()

        val handleView = view.findViewById<View>(R.id.folder_picker_handle)
        handleView?.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(tokens.divider)
            cornerRadius = 2f * dp
        }

        val titleView = view.findViewById<TextView>(R.id.picker_title)
        if (titleView != null) {
            com.nexus.launcher.typography.NexusTypeScale.title.bindTo(titleView, tokens.textPrimary)
        }

        val searchBox = view.findViewById<LinearLayout>(R.id.picker_search_box)
        searchBox?.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(tokens.surfaceRaised)
            cornerRadius = 12f * dp
            setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
        }

        val searchLabel = view.findViewById<TextView>(R.id.picker_search_label)
        if (searchLabel != null) {
            com.nexus.launcher.typography.NexusTypeScale.labelSmall.bindTo(searchLabel, tokens.textSecondary)
        }

        val searchInput = view.findViewById<EditText>(R.id.picker_search_input)
        com.nexus.launcher.typography.NexusTypeScale.body.bindTo(searchInput, tokens.textPrimary)
        searchInput.setHintTextColor(tokens.textSecondary)

        val switchBox = view.findViewById<LinearLayout>(R.id.picker_switch_box)
        switchBox?.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(tokens.surfaceRaised)
            cornerRadius = 12f * dp
            setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
        }

        val switchLabel = view.findViewById<TextView>(R.id.picker_switch_label)
        if (switchLabel != null) {
            com.nexus.launcher.typography.NexusTypeScale.body.bindTo(switchLabel, tokens.textPrimary)
        }

        val switchView = view.findViewById<SwitchCompat>(R.id.show_folder_apps_switch)
        val recycler = view.findViewById<RecyclerView>(R.id.picker_recycler)
        saveBtn = view.findViewById(R.id.save_button)
        saveBtn.background = android.graphics.drawable.GradientDrawable().apply {
            setColor(tokens.textPrimary)
            cornerRadius = 22f * dp
        }
        saveBtn.setTextColor(tokens.surface)
        com.nexus.launcher.typography.NexusTypeScale.bodyStrong.bindTo(saveBtn, tokens.surface)

        val closeBtn = view.findViewById<ImageView>(R.id.btn_close_picker)
        closeBtn?.apply {
            imageTintList = android.content.res.ColorStateList.valueOf(tokens.textSecondary)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(tokens.surfaceRaised)
                cornerRadius = 16f * dp
            }
            setOnClickListener { dismiss() }
        }

        adapter = FolderAppPickerAdapter(selectedIds) { refreshSaveLabel() }
        recycler.layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), 5)
        recycler.adapter = adapter

        saveBtn.setOnClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val packagesToKeepOrAdd = allApps.filter { selectedIds.contains(it.id.toLong()) }.map { it.packageName }.toSet()

                // Apps to remove: currently in folder, but not in packagesToKeepOrAdd
                val appsToRemove = inFolderItems.filter { !packagesToKeepOrAdd.contains(it.packageName) }
                appsToRemove.forEach { app ->
                    dao.removeItemById(app.id)
                }

                // Apps to add: in packagesToKeepOrAdd, but not in targetFolderPackages
                val appsToAdd = allApps.filter { selectedIds.contains(it.id.toLong()) && !targetFolderPackages.contains(it.packageName) }
                val baseTime2 = System.currentTimeMillis()
                appsToAdd.forEachIndexed { index, app ->
                    FolderAppendEngine.appendAppToFolder(app.copy(id = (baseTime2 + index).toInt()), folderId, dao)
                }
                withContext(Dispatchers.Main) {
                    FolderWindowManager.refreshOpenWindowIfShowing(requireContext())
                    onAppsSaved?.invoke()
                    dismiss()
                }
            }
        }

        if (preloadedApps != null && preloadedFolderedPackages != null && preloadedTargetPackages != null && preloadedInFolderItems != null) {
            allApps = preloadedApps
            folderedPackages = preloadedFolderedPackages
            targetFolderPackages = preloadedTargetPackages
            inFolderItems = preloadedInFolderItems
            
            allApps.filter { targetFolderPackages.contains(it.packageName) }.forEach {
                selectedIds.add(it.id.toLong())
            }
            initialSelectedIds = selectedIds.toSet()
            updateList()
            
            dialog?.setCanceledOnTouchOutside(false)
            (dialog as? androidx.activity.ComponentDialog)?.onBackPressedDispatcher?.addCallback(viewLifecycleOwner,
                object : androidx.activity.OnBackPressedCallback(true) {
                    override fun handleOnBackPressed() {
                        if (selectedIds.toSet() != initialSelectedIds) {
                            FolderAuroraDialogs.showDiscard(requireContext()) { dismiss() }
                        } else {
                            dismiss()
                        }
                    }
                })
        } else {
            val glassPanel = view.findViewById<LinearLayout>(R.id.folder_picker_glass_panel)
            val progressBar = android.widget.ProgressBar(requireContext()).apply {
                isIndeterminate = true
                indeterminateTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.WHITE)
                layoutParams = LinearLayout.LayoutParams(
                    (40 * resources.displayMetrics.density).toInt(),
                    (40 * resources.displayMetrics.density).toInt()
                ).apply {
                    gravity = android.view.Gravity.CENTER
                    topMargin = (64 * resources.displayMetrics.density).toInt()
                    bottomMargin = (64 * resources.displayMetrics.density).toInt()
                }
            }
            val recyclerIndex = glassPanel.indexOfChild(recycler)
            glassPanel.addView(progressBar, recyclerIndex)
            recycler.visibility = View.GONE
            
            lifecycleScope.launch(Dispatchers.IO) {
                folderedPackages = FolderAppPickerFilter.folderedPackageNames(dao, folderId)
                targetFolderPackages = FolderAppPickerFilter.targetFolderPackages(dao, folderId)
                inFolderItems = dao.getItemsInFolderSync(folderId)
                val pm = requireContext().packageManager
                val intent = android.content.Intent(android.content.Intent.ACTION_MAIN, null).apply {
                    addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                }
                val allResolveInfo = pm.queryIntentActivities(intent, 0)
                val baseTime = System.currentTimeMillis()
                val newItems = allResolveInfo.mapIndexed { index, resolveInfo ->
                    HomeScreenItem(
                        id = baseTime + index,
                        packageName = resolveInfo.activityInfo.packageName,
                        page = -1, column = 0, row = 0, itemType = 0, containerId = -1L, folderTitle = ""
                    )
                }.distinctBy { it.packageName }.sortedBy { item ->
                    try {
                        val info = pm.getApplicationInfo(item.packageName, 0)
                        pm.getApplicationLabel(info).toString()
                    } catch (_: Exception) {
                        item.packageName
                    }
                }
                withContext(Dispatchers.Main) {
                    allApps = newItems
                    newItems.filter { targetFolderPackages.contains(it.packageName) }.forEach {
                        selectedIds.add(it.id.toLong())
                    }
                    initialSelectedIds = selectedIds.toSet()
                    updateList()
                    
                    glassPanel.getChildAt(glassPanel.indexOfChild(recycler) - 1)?.let { pBar ->
                        pBar.animate().alpha(0f).setDuration(150).withEndAction {
                            glassPanel.removeView(pBar)
                        }.start()
                    }
                    recycler.visibility = View.VISIBLE
                    recycler.alpha = 0f
                    recycler.animate().alpha(1f).setDuration(250).start()
    
                    dialog?.setCanceledOnTouchOutside(false)
                    (dialog as? androidx.activity.ComponentDialog)?.onBackPressedDispatcher?.addCallback(viewLifecycleOwner,
                        object : androidx.activity.OnBackPressedCallback(true) {
                            override fun handleOnBackPressed() {
                                if (selectedIds.toSet() != initialSelectedIds) {
                                    FolderAuroraDialogs.showDiscard(requireContext()) { dismiss() }
                                } else {
                                    dismiss()
                                }
                            }
                        })
                }
            }
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString().orEmpty()
                updateList()
            }
        })

        switchView.setOnCheckedChangeListener { _, isChecked ->
            showAlreadyInFolders = isChecked
            updateList()
        }
    }



    private fun applyNavBarInsets(view: View) {
        val baseBottom = (8 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, baseBottom + nav.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(view)
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        FolderSheetWallpaperDecor.removeWindowBlur(
            this.dialog as? BottomSheetDialog, requireContext())
        FolderAppPickerDecor.onDismiss(requireContext())
        super.onDismiss(dialog)
    }

    private fun updateList() {
        val pm = requireContext().packageManager
        val baseList = FolderAppPickerFilter.filterVisible(
            allApps, folderedPackages, targetFolderPackages, showAlreadyInFolders
        )
        val displayList = FolderAppPickerSearch.filterWithLabels(
            baseList, searchQuery, emptySet()
        ) { item -> FolderAppPickerAdapter.labelFor(pm, item) }
        adapter.submitList(displayList)
        refreshSaveLabel()
    }

    private fun refreshSaveLabel() {
        val packagesToKeepOrAdd = allApps.filter { selectedIds.contains(it.id.toLong()) }.map { it.packageName }.toSet()
        val toAdd = packagesToKeepOrAdd.count { !targetFolderPackages.contains(it) }
        val toRemove = inFolderItems.count { !packagesToKeepOrAdd.contains(it.packageName) }

        saveBtn.text = when {
            toAdd > 0 && toRemove == 0 -> requireContext().resources.getQuantityString(com.nexus.launcher.R.plurals.folder_picker_add_to_folder, toAdd, toAdd)
            toRemove > 0 && toAdd == 0 -> requireContext().resources.getQuantityString(com.nexus.launcher.R.plurals.folder_picker_remove_from_folder, toRemove, toRemove)
            toAdd > 0 && toRemove > 0 -> requireContext().getString(com.nexus.launcher.R.string.folder_picker_add_remove, toAdd, toRemove)
            else -> requireContext().getString(com.nexus.launcher.R.string.folder_picker_no_changes)
        }
    }

    companion object {
        fun preloadAndShow(
            context: android.content.Context,
            fragmentManager: androidx.fragment.app.FragmentManager,
            folderId: Long,
            coroutineScope: kotlinx.coroutines.CoroutineScope,
            onAppsSaved: (() -> Unit)? = null
        ) {
            FolderAppPickerPreloader.preloadAndShow(
                context, fragmentManager, folderId, coroutineScope, onAppsSaved
            )
        }
    }
}
