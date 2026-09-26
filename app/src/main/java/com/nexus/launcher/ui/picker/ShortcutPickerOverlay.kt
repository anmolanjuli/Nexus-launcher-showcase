package com.nexus.launcher.ui.picker

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.Process
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nexus.launcher.ui.folder.ShortcutAdapterItem
import com.nexus.launcher.ui.folder.ShortcutPickerAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Fullscreen overlay implementation for selecting application shortcuts.
 * Builds on [PickerOverlayShell], retaining the per-item card visual design.
 */
class ShortcutPickerOverlay(
    context: Context,
    private val onShortcutSelected: (ShortcutInfo) -> Unit
) : PickerOverlayShell(context) {

    private val recycler: RecyclerView
    private val adapter: ShortcutPickerAdapter
    private var allGroups = mutableMapOf<String, List<ShortcutInfo>>()
    private val expandedPackages = mutableSetOf<String>()
    private var searchJob: Job? = null
    private var currentFilter: String = ""

    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    init {
        setTitle(context.getString(com.nexus.launcher.R.string.picker_select_shortcut))
        setSearchHint(context.getString(com.nexus.launcher.R.string.picker_search_shortcuts))

        recycler = RecyclerView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            clipToPadding = false
            layoutManager = LinearLayoutManager(context)
        }

        adapter = ShortcutPickerAdapter(
            allGroupsProvider = { allGroups },
            expandedPackages = expandedPackages,
            onShortcutSelected = { shortcut ->
                onShortcutSelected(shortcut)
                dismiss()
            },
            onDismiss = { dismiss() },
            onItemToggled = { updateList() }
        )
        recycler.adapter = adapter
        setContent(recycler)

        setOnSearchQueryChanged { query ->
            searchJob?.cancel()
            searchJob = coroutineScope.launch {
                delay(200)
                currentFilter = query.trim().lowercase()
                updateList()
            }
        }

        loadShortcuts()
    }

    private fun loadShortcuts() {
        showSpinner()
        coroutineScope.launch(Dispatchers.IO) {
            val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as? LauncherApps
            val pm = context.packageManager
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            val resolveInfos = pm.queryIntentActivities(intent, 0)
            val packages = resolveInfos.map { it.activityInfo.packageName }.distinct()

            val shortcuts = mutableListOf<ShortcutInfo>()
            if (launcherApps != null) {
                for (pkg in packages) {
                    val query = LauncherApps.ShortcutQuery().apply {
                        setQueryFlags(
                            LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST
                        )
                        setPackage(pkg)
                    }
                    try {
                        val appShortcuts = launcherApps.getShortcuts(query, Process.myUserHandle())
                        if (appShortcuts != null) {
                            shortcuts.addAll(appShortcuts)
                        }
                    } catch (_: Exception) {}
                }
            }

            val nexusShortcuts = com.nexus.launcher.ui.widgets.shortcutbox.NexusBuiltinShortcuts.getAllShortcuts(context)
            allGroups = shortcuts.groupBy { it.`package` }.filter { it.value.isNotEmpty() }.toMutableMap()
            if (nexusShortcuts.isNotEmpty()) {
                allGroups[context.packageName] = nexusShortcuts
                expandedPackages.add(context.packageName)
            }

            withContext(Dispatchers.Main) {
                hideSpinner()
                updateList()
            }
        }
    }

    private fun updateList() {
        val pm = context.packageManager
        val displayList = mutableListOf<ShortcutAdapterItem.Header>()

        allGroups.forEach { (pkg, shortcuts) ->
            val isNexus = pkg == context.packageName
            val appLabel = if (isNexus) {
                context.getString(com.nexus.launcher.R.string.nexus_shortcuts_title)
            } else {
                try {
                    pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
                } catch (_: Exception) {
                    pkg
                }
            }

            val matchesFilter = if (currentFilter.isEmpty()) {
                true
            } else {
                appLabel.lowercase().contains(currentFilter) ||
                    shortcuts.any { (it.shortLabel?.toString() ?: it.id).lowercase().contains(currentFilter) }
            }

            if (matchesFilter) {
                val tokens = try {
                    com.nexus.launcher.theme.ThemeObserver.currentTokens(context)
                } catch (_: Exception) {
                    com.nexus.launcher.theme.NexusColorTokens.Dark
                }
                val appIcon = if (isNexus) {
                    androidx.core.content.ContextCompat.getDrawable(context, com.nexus.launcher.R.drawable.ic_menu_shortcut)?.mutate()?.apply {
                        setTint(tokens.textPrimary)
                    } ?: try { pm.getApplicationIcon(pkg) } catch (_: Exception) { null }
                } else {
                    try { pm.getApplicationIcon(pkg) } catch (_: Exception) { null }
                }
                val isExpanded = if (currentFilter.isNotEmpty()) true else expandedPackages.contains(pkg)
                displayList.add(ShortcutAdapterItem.Header(pkg, appLabel, appIcon, isExpanded))
            }
        }

        displayList.sortWith { a, b ->
            val aIsNexus = a.packageName == context.packageName
            val bIsNexus = b.packageName == context.packageName
            when {
                aIsNexus && !bIsNexus -> -1
                !aIsNexus && bIsNexus -> 1
                else -> a.appLabel.compareTo(b.appLabel, ignoreCase = true)
            }
        }
        adapter.submitList(displayList)

        if (displayList.isEmpty()) {
            if (currentFilter.isEmpty()) {
                showEmptyState(
                    title = context.getString(com.nexus.launcher.R.string.picker_no_shortcuts_title),
                    subtitle = context.getString(com.nexus.launcher.R.string.picker_no_shortcuts_desc)
                )
            } else {
                showEmptyState(
                    title = context.getString(com.nexus.launcher.R.string.picker_no_results),
                    subtitle = context.getString(com.nexus.launcher.R.string.picker_no_shortcuts_match, currentFilter)
                )
            }
        } else {
            hideEmptyState()
        }
    }

    companion object {
        fun show(
            context: Context,
            onShortcutSelected: (ShortcutInfo) -> Unit
        ): ShortcutPickerOverlay {
            val overlay = ShortcutPickerOverlay(context, onShortcutSelected)
            overlay.show()
            return overlay
        }
    }
}
