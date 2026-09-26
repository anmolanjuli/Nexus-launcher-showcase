package com.nexus.launcher.ui.picker

import android.content.Context
import android.content.Intent
import com.nexus.launcher.R
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.domain.model.AppModel
import com.nexus.launcher.ui.MainActivity
import com.nexus.launcher.ui.folder.FolderAppPickerAdapter
import com.nexus.launcher.ui.folder.FolderAppPickerSearch
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Fullscreen themed overlay for selecting an application to populate an App Box slot.
 * Was its own bespoke 4-column card-grid UI, visually inconsistent with the Folder and Home
 * Screen app pickers — now shares the exact same [FolderAppPickerAdapter] (5-column grid, same
 * icon/label/checkmark card style) instead. No Save button/selection-count pill here: tapping any
 * app commits it immediately and dismisses, since an App Box slot only ever takes one app — unlike
 * Folder/Home Screen picking, which is genuinely multi-select and needs an explicit confirm step.
 * Extends [PickerOverlayShell].
 */
class AppBoxPickerOverlay(
    context: Context,
    private val onAppSelected: (AppModel) -> Unit
) : PickerOverlayShell(context) {

    private val adapter: FolderAppPickerAdapter
    private val recycler: RecyclerView
    private val selectedIds = mutableSetOf<Long>()

    private var allAppModels: List<AppModel> = emptyList()
    private var allItems: List<HomeScreenItem> = emptyList()
    private var searchQuery = ""
    private var committed = false

    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    init {
        setTitle(context.getString(R.string.picker_select_app))
        setSearchHint(context.getString(R.string.picker_search_apps))

        adapter = FolderAppPickerAdapter(selectedIds, maxSelectable = 1) { onSelectionChanged() }

        recycler = RecyclerView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            clipToPadding = false
            setPadding((8 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt(), (24 * dp).toInt())
            layoutManager = GridLayoutManager(context, 5)
            adapter = this@AppBoxPickerOverlay.adapter
        }
        setContent(recycler)

        setOnSearchQueryChanged { query ->
            searchQuery = query.trim()
            updateList()
        }

        loadApps()
    }

    /** A slot only ever holds one app — commit and dismiss the instant a tap registers a
     *  selection, rather than waiting on a separate confirm step. */
    private fun onSelectionChanged() {
        if (committed) return
        val selectedId = selectedIds.firstOrNull() ?: return
        val item = allItems.firstOrNull { it.id.toLong() == selectedId } ?: return
        val app = allAppModels.firstOrNull { it.packageName == item.packageName } ?: return
        committed = true
        onAppSelected(app)
        dismiss()
    }

    private fun loadApps() {
        showSpinner()
        val act = findActivity(context)
        val viewModel = (act as? MainActivity)?.viewModel
        val locale = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
        val collator = java.text.Collator.getInstance(locale).apply { strength = java.text.Collator.SECONDARY }

        if (viewModel != null) {
            coroutineScope.launch {
                viewModel.apps.collect { apps ->
                    if (allAppModels.isEmpty() && apps.isNotEmpty()) {
                        setLoadedApps(apps.sortedWith(compareBy(collator) { it.label }))
                    }
                }
            }
        } else {
            coroutineScope.launch(Dispatchers.IO) {
                val pm = context.packageManager
                val intent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val loaded = pm.queryIntentActivities(intent, 0).map { ri ->
                    AppModel(
                        packageName = ri.activityInfo.packageName,
                        className = ri.activityInfo.name,
                        label = ri.loadLabel(pm).toString(),
                        icon = ri.loadIcon(pm),
                        installTime = System.currentTimeMillis()
                    )
                }.distinctBy { it.packageName }.sortedWith(compareBy(collator) { it.label })

                withContext(Dispatchers.Main) { setLoadedApps(loaded) }
            }
        }
    }

    private fun setLoadedApps(apps: List<AppModel>) {
        allAppModels = apps
        val baseTime = System.currentTimeMillis()
        allItems = apps.mapIndexed { index, app ->
            HomeScreenItem(
                id = baseTime + index,
                packageName = app.packageName,
                page = -1, column = 0, row = 0, itemType = 0, containerId = -1L, folderTitle = ""
            )
        }
        hideSpinner()
        updateList()
    }

    private fun updateList() {
        val pm = context.packageManager
        val searchResults = FolderAppPickerSearch.filterWithLabels(
            allItems,
            searchQuery,
            emptySet()
        ) { item -> FolderAppPickerAdapter.labelFor(pm, item) }

        adapter.submitList(searchResults)
        if (searchResults.isEmpty()) {
            showEmptyState(context.getString(R.string.app_not_found), searchQuery)
        } else {
            hideEmptyState()
        }
    }

    companion object {
        fun show(
            context: Context,
            onAppSelected: (AppModel) -> Unit
        ): AppBoxPickerOverlay {
            val overlay = AppBoxPickerOverlay(context, onAppSelected)
            overlay.show()
            return overlay
        }
    }
}
