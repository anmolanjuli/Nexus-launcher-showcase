package com.nexus.launcher.ui.picker

import android.content.Context
import android.content.Intent
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nexus.launcher.data.HomeScreenDao
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.di.DaoEntryPoint
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.folder.FolderAppPickerAdapter
import com.nexus.launcher.ui.folder.FolderAppPickerSearch
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Fullscreen themed overlay for selecting and adding apps to the Home Screen.
 * Extends [PickerOverlayShell], providing a 5-column grid layout and pinned search bar.
 */
class HomeScreenAppPickerOverlay(
    context: Context,
    private val maxSelectable: Int = -1,
    private val onWorkspaceAppsSelected: ((List<HomeScreenItem>) -> Unit)? = null
) : PickerOverlayShell(context) {

    private val dao: HomeScreenDao
    private val adapter: FolderAppPickerAdapter
    private val recycler: RecyclerView
    private val saveBtn: TextView

    private var allApps: List<HomeScreenItem> = emptyList()
    private val selectedIds = mutableSetOf<Long>()
    private var searchQuery = ""

    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    init {
        setTitle(context.getString(com.nexus.launcher.R.string.picker_add_apps_to_home))
        setSearchHint(context.getString(com.nexus.launcher.R.string.picker_search_apps))

        val entryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            DaoEntryPoint::class.java
        )
        dao = entryPoint.homeScreenDao()

        val contentWrapper = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        adapter = FolderAppPickerAdapter(selectedIds, maxSelectable = maxSelectable) { refreshSaveLabel() }
        recycler = RecyclerView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            clipToPadding = false
            setPadding((8 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt(), (72 * dp).toInt())
            layoutManager = GridLayoutManager(context, 5)
            adapter = this@HomeScreenAppPickerOverlay.adapter
        }
        contentWrapper.addView(recycler)

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
        val targetActivity = findActivity(context) as? com.nexus.launcher.ui.MainActivity
        val cachedAppModels = targetActivity?.viewModel?.apps?.value

        val locale = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
        val collator = java.text.Collator.getInstance(locale).apply { strength = java.text.Collator.SECONDARY }

        if (!cachedAppModels.isNullOrEmpty()) {
            val baseTime = System.currentTimeMillis()
            allApps = cachedAppModels
                .sortedWith(compareBy(collator) { it.label })
                .mapIndexed { index, app ->
                    HomeScreenItem(
                        id = baseTime + index,
                        packageName = app.packageName,
                        page = -1, column = 0, row = 0, itemType = 0, containerId = -1L, folderTitle = ""
                    )
                }
            showLoading(false)
            updateList()
            return
        }

        showLoading(true)
        coroutineScope.launch(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val allResolveInfo = pm.queryIntentActivities(intent, 0)
            val baseTime = System.currentTimeMillis()
            val apps = allResolveInfo.mapIndexed { index, resolveInfo ->
                HomeScreenItem(
                    id = baseTime + index,
                    packageName = resolveInfo.activityInfo.packageName,
                    page = -1, column = 0, row = 0, itemType = 0, containerId = -1L, folderTitle = ""
                )
            }.distinctBy { it.packageName }
                // The label is read once per app and sorted on, rather than inside the comparison:
                // `sortedBy` calls its selector on every comparison, so a few hundred apps meant a
                // few thousand PackageManager round trips before the list could appear.
                .map { item ->
                    item to try {
                        pm.getApplicationLabel(pm.getApplicationInfo(item.packageName, 0)).toString()
                    } catch (_: Exception) {
                        item.packageName
                    }
                }
                .sortedWith(compareBy(collator) { it.second })
                .map { it.first }

            withContext(Dispatchers.Main) {
                allApps = apps
                showLoading(false)
                updateList()
            }
        }
    }

    private fun updateList() {
        val pm = context.packageManager
        val searchResults = FolderAppPickerSearch.filterWithLabels(
            allApps,
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
        // Was `saveBtn.alpha = 0.5f` for the disabled state — that dims the WHOLE composited
        // view, including its own opaque pill background, letting the app grid underneath bleed
        // through at 50% and making the label hard to read against app icons. Keep the view
        // fully opaque always; convey "disabled" purely through color instead (dimmer fill,
        // secondary text) so the pill itself stays legible regardless of what's behind it.
        val count = selectedIds.size
        if (count == 0) {
            saveBtn.text = context.getString(com.nexus.launcher.R.string.folder_app_picker_title)
            PickerOverlayStyle.setPrimaryEnabled(saveBtn, tokens, enabled = false)
        } else {
            saveBtn.text = context.resources.getQuantityString(com.nexus.launcher.R.plurals.picker_add_count_to_home, count, count)
            PickerOverlayStyle.setPrimaryEnabled(saveBtn, tokens, enabled = true)
        }
    }

    private fun handleSave() {
        val selected = allApps.filter { selectedIds.contains(it.id.toLong()) }
        if (selected.isNotEmpty()) {
            val toAdd = if (maxSelectable > 0) selected.take(maxSelectable) else selected
            onWorkspaceAppsSelected?.invoke(toAdd)
        }
        dismiss()
    }

    companion object {
        fun show(
            context: Context,
            maxSelectable: Int = -1,
            onWorkspaceAppsSelected: ((List<HomeScreenItem>) -> Unit)? = null
        ): HomeScreenAppPickerOverlay {
            val overlay = HomeScreenAppPickerOverlay(
                context = context,
                maxSelectable = maxSelectable,
                onWorkspaceAppsSelected = onWorkspaceAppsSelected
            )
            overlay.show()
            return overlay
        }
    }
}
