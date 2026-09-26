package com.nexus.launcher.ui.picker

import android.content.Context
import android.content.Intent
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nexus.launcher.data.HomeScreenDao
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.di.DaoEntryPoint
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.folder.FolderAppendEngine
import com.nexus.launcher.ui.folder.FolderAppPickerAdapter
import com.nexus.launcher.ui.folder.FolderAppPickerFilter
import com.nexus.launcher.ui.folder.FolderAppPickerSearch
import com.nexus.launcher.ui.folder.FolderAuroraDialogs
import com.nexus.launcher.ui.folder.FolderWindowManager
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Fullscreen themed overlay for adding and removing apps in a folder.
 * Extends [PickerOverlayShell], adopting per-item card design matching ShortcutPickerOverlay.
 */
class FolderAppPickerOverlay(
    context: Context,
    private val folderId: Long,
    private val preloadedApps: List<HomeScreenItem>? = null,
    private val preloadedFolderedPackages: Set<String>? = null,
    private val preloadedTargetPackages: Set<String>? = null,
    private val preloadedInFolderItems: List<HomeScreenItem>? = null,
    private val onAppsSaved: (() -> Unit)? = null
) : PickerOverlayShell(context) {

    private val dao: HomeScreenDao
    private val adapter: FolderAppPickerAdapter
    private val recycler: RecyclerView
    private val saveBtn: TextView

    private var allApps: List<HomeScreenItem> = emptyList()
    private var folderedPackages: Set<String> = emptySet()
    private var targetFolderPackages: Set<String> = emptySet()
    private var inFolderItems: List<HomeScreenItem> = emptyList()
    private val selectedIds = mutableSetOf<Long>()
    private var initialSelectedIds: Set<Long> = emptySet()
    private var searchQuery = ""
    private var showAlreadyInFolders = false

    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    init {
        setTitle(context.getString(com.nexus.launcher.R.string.picker_add_apps_to_folder))
        setSearchHint(context.getString(com.nexus.launcher.R.string.picker_search_apps))

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            DaoEntryPoint::class.java
        )
        dao = entryPoint.homeScreenDao()

        // Toggle row for "Show apps already in other folders"
        val filterRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val padH = (16 * dp).toInt()
            setPadding(padH, (6 * dp).toInt(), padH, (8 * dp).toInt())
        }

        val filterLabel = TextView(context).apply {
            text = context.getString(com.nexus.launcher.R.string.picker_show_other_folder_apps)
            NexusTypeScale.body.bindTo(this, tokens.textSecondary)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val filterSwitch = SwitchCompat(context).apply {
            isChecked = showAlreadyInFolders
            setOnCheckedChangeListener { _, isChecked ->
                showAlreadyInFolders = isChecked
                updateList()
            }
        }

        filterRow.addView(filterLabel)
        filterRow.addView(filterSwitch)
        setCustomHeaderView(filterRow)

        // Main content wrapper hosting recycler and bottom save button
        val contentWrapper = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        adapter = FolderAppPickerAdapter(selectedIds) { refreshSaveLabel() }
        recycler = RecyclerView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            clipToPadding = false
            setPadding((8 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt(), (72 * dp).toInt())
            layoutManager = GridLayoutManager(context, 5)
            adapter = this@FolderAppPickerOverlay.adapter
        }
        contentWrapper.addView(recycler)

        // Floating Save Button at bottom
        saveBtn = TextView(context).apply {
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                (48 * dp).toInt()
            ).apply {
                gravity = Gravity.BOTTOM
                setMargins((16 * dp).toInt(), 0, (16 * dp).toInt(), (8 * dp).toInt())
            }
            gravity = Gravity.CENTER
            NexusTypeScale.bodyStrong.bindTo(this, tokens.surface)
            PickerOverlayStyle.primaryButton(this, tokens, dp)
            setOnClickListener { handleSave() }
        }
        contentWrapper.addView(saveBtn)
        setContent(contentWrapper)

        setOnSearchQueryChanged { query ->
            searchQuery = query.trim()
            updateList()
        }

        initializeData()
    }

    private fun initializeData() {
        if (preloadedApps != null && preloadedFolderedPackages != null &&
            preloadedTargetPackages != null && preloadedInFolderItems != null
        ) {
            allApps = preloadedApps
            folderedPackages = preloadedFolderedPackages
            targetFolderPackages = preloadedTargetPackages
            inFolderItems = preloadedInFolderItems
            selectedIds.clear()
            selectedIds.addAll(inFolderItems.map { it.id.toLong() })
            initialSelectedIds = selectedIds.toSet()
            updateList()
            return
        }

        showLoading(true)
        val mainActivity = findActivity(context) as? com.nexus.launcher.ui.MainActivity
        val cachedAppModels = mainActivity?.viewModel?.apps?.value

        coroutineScope.launch(Dispatchers.IO) {
            val foldered = FolderAppPickerFilter.folderedPackageNames(dao, folderId)
            val targetFolder = FolderAppPickerFilter.targetFolderPackages(dao, folderId)
            val inFolder = dao.getItemsInFolderSync(folderId)

            val locale = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                context.resources.configuration.locales[0]
            } else {
                @Suppress("DEPRECATION")
                context.resources.configuration.locale
            }
            val collator = java.text.Collator.getInstance(locale).apply { strength = java.text.Collator.SECONDARY }

            val apps = if (!cachedAppModels.isNullOrEmpty()) {
                val baseTime = System.currentTimeMillis()
                cachedAppModels
                    .sortedWith(compareBy(collator) { it.label })
                    .mapIndexed { index, app ->
                        HomeScreenItem(
                            id = baseTime + index,
                            packageName = app.packageName,
                            page = -1, column = 0, row = 0, itemType = 0, containerId = -1L, folderTitle = ""
                        )
                    }
            } else {
                val pm = context.packageManager
                val intent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val allResolveInfo = pm.queryIntentActivities(intent, 0)
                val baseTime = System.currentTimeMillis()
                allResolveInfo.mapIndexed { index, resolveInfo ->
                    HomeScreenItem(
                        id = baseTime + index,
                        packageName = resolveInfo.activityInfo.packageName,
                        page = -1, column = 0, row = 0, itemType = 0, containerId = -1L, folderTitle = ""
                    )
                }.distinctBy { it.packageName }
                    // Read the label once per app and sort on it: `sortedBy` calls its selector on
                    // every comparison, so a few hundred apps meant thousands of PackageManager
                    // round trips before the list could appear.
                    .map { item ->
                        item to try {
                            pm.getApplicationLabel(pm.getApplicationInfo(item.packageName, 0)).toString()
                        } catch (_: Exception) {
                            item.packageName
                        }
                    }
                    .sortedWith(compareBy(collator) { it.second })
                    .map { it.first }
            }

            withContext(Dispatchers.Main) {
                allApps = apps
                folderedPackages = foldered
                targetFolderPackages = targetFolder
                inFolderItems = inFolder
                selectedIds.clear()
                selectedIds.addAll(inFolder.map { it.id.toLong() })
                initialSelectedIds = selectedIds.toSet()
                showLoading(false)
                updateList()
            }
        }
    }

    private fun updateList() {
        val filtered = FolderAppPickerFilter.filterVisible(
            allApps,
            folderedPackages,
            targetFolderPackages,
            showAlreadyInFolders
        )

        val pm = context.packageManager
        val searchResults = FolderAppPickerSearch.filterWithLabels(
            filtered,
            searchQuery,
            emptySet()
        ) { item -> FolderAppPickerAdapter.labelFor(pm, item) }

        adapter.submitList(searchResults)
        if (searchResults.isEmpty()) {
            showEmptyState(context.getString(com.nexus.launcher.R.string.picker_no_apps_found))
        } else {
            hideEmptyState()
        }
        refreshSaveLabel()
    }

    private fun refreshSaveLabel() {
        val newlyAdded = (selectedIds - initialSelectedIds).size
        val removed = (initialSelectedIds - selectedIds).size
        saveBtn.text = when {
            newlyAdded > 0 && removed > 0 -> context.getString(com.nexus.launcher.R.string.picker_save_changes_counts, newlyAdded, removed)
            newlyAdded > 0 -> context.resources.getQuantityString(com.nexus.launcher.R.plurals.picker_add_count, newlyAdded, newlyAdded)
            removed > 0 -> {
                if (selectedIds.isEmpty()) context.getString(com.nexus.launcher.R.string.picker_remove_all_apps)
                else context.resources.getQuantityString(com.nexus.launcher.R.plurals.picker_remove_count, removed, removed)
            }
            else -> context.getString(com.nexus.launcher.R.string.action_done)
        }
    }

    private fun handleSave() {
        if (selectedIds == initialSelectedIds) {
            dismiss()
            return
        }
        showLoading(true)
        coroutineScope.launch(Dispatchers.IO) {
            val currentInFolder = dao.getItemsInFolderSync(folderId)
            val toRemove = currentInFolder.filter { !selectedIds.contains(it.id.toLong()) }
            val toAddIds = selectedIds - currentInFolder.map { it.id.toLong() }.toSet()
            val toAdd = allApps.filter { toAddIds.contains(it.id.toLong()) }

            toRemove.forEach { item ->
                dao.removeItemById(item.id)
            }

            for (app in toAdd) {
                FolderAppendEngine.appendAppToFolder(app, folderId, dao, context)
            }

            withContext(Dispatchers.Main) {
                showLoading(false)
                FolderWindowManager.refreshOpenWindowIfShowing(context)
                onAppsSaved?.invoke()
                dismiss()
            }
        }
    }

    override fun canDismissSafely(): Boolean {
        return selectedIds == initialSelectedIds
    }

    override fun onDiscardRequested() {
        FolderAuroraDialogs.showDiscard(context) {
            selectedIds.clear()
            selectedIds.addAll(initialSelectedIds)
            dismiss()
        }
    }

    companion object {
        fun show(
            context: Context,
            folderId: Long,
            preloadedApps: List<HomeScreenItem>? = null,
            preloadedFolderedPackages: Set<String>? = null,
            preloadedTargetPackages: Set<String>? = null,
            preloadedInFolderItems: List<HomeScreenItem>? = null,
            onAppsSaved: (() -> Unit)? = null
        ): FolderAppPickerOverlay {
            val overlay = FolderAppPickerOverlay(
                context = context,
                folderId = folderId,
                preloadedApps = preloadedApps,
                preloadedFolderedPackages = preloadedFolderedPackages,
                preloadedTargetPackages = preloadedTargetPackages,
                preloadedInFolderItems = preloadedInFolderItems,
                onAppsSaved = onAppsSaved
            )
            overlay.show()
            return overlay
        }
    }
}
