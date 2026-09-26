package com.nexus.launcher.ui
import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexus.launcher.data.prefs.NexusSettingsData
import com.nexus.launcher.data.prefs.PreferenceManager
import com.nexus.launcher.data.prefs.SettingsRepository
import com.nexus.launcher.data.repository.AppRepository
import com.nexus.launcher.domain.model.AppModel
import com.nexus.launcher.ui.model.DisplayItem
import com.nexus.launcher.ui.model.LauncherState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import com.nexus.launcher.ui.model.SortType
import com.nexus.launcher.data.prefs.cleanupRemovedMinimalUiKeys
@HiltViewModel
class MainViewModel @Inject constructor(
    val appRepository: AppRepository,
    private val preferenceManager: PreferenceManager,
    private val recentSearchDao: com.nexus.launcher.data.RecentSearchDao,
    internal val settingsRepository: SettingsRepository,
    private val homeScreenDao: com.nexus.launcher.data.HomeScreenDao,
    private val iconPackParser: com.nexus.launcher.ui.icons.IconPackParser,
    private val themedIconFactory: com.nexus.launcher.ui.icons.ThemedIconFactory,
    private val localeController: com.nexus.launcher.locale.LocaleController,
    private val fontFamilyController: com.nexus.launcher.typography.FontFamilyController,
    @ApplicationContext private val context: Context
) : ViewModel() {
    val fontFamily: StateFlow<com.nexus.launcher.typography.AppFontFamily> = fontFamilyController.fontFamily
    val fontSelectionKey: StateFlow<String> = fontFamilyController.fontSelectionKey
    val customFonts: StateFlow<List<com.nexus.launcher.typography.CustomFontEntry>> = fontFamilyController.customFonts
    // Seeded from the repository's last resolved list rather than empty. This view model is
    // per-activity, so a second MainActivity — or one recreated by the system — used to start
    // with no apps and wait for every icon to resolve again before the drawer filled. The resync
    // in init still runs and replaces this, so the seed only ever covers that gap.
    private val _apps = MutableStateFlow<List<AppModel>>(appRepository.lastKnownApps)
    val apps: StateFlow<List<AppModel>> = _apps.asStateFlow()

    private var resyncJob: kotlinx.coroutines.Job? = null

    /**
     * Rebuilds the app list. The only way anything should write [_apps] from the repository.
     *
     * Latest request wins: a new resync cancels the one in flight. The list used to be rebuilt
     * from seven independent call sites with nothing ordering them, so whichever resolve happened
     * to *finish* last won — including one started before an icon pack change had applied, which
     * then overwrote the correct list with stale icons.
     *
     * Synchronized because the package receiver calls it from an IO coroutine while the settings
     * and locale collectors call it from main; the cancel-then-replace must not interleave.
     */
    @Synchronized
    private fun resyncApps() {
        resyncJob?.cancel()
        resyncJob = viewModelScope.launch {
            _apps.value = appRepository.getInstalledApps()
        }
    }
    private var packageReceiver: android.content.BroadcastReceiver? = null
    val invalidatedPackages = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 10)
    private var prefListener: android.content.SharedPreferences.OnSharedPreferenceChangeListener? = null
    private val _sortType = MutableStateFlow(SortType.ALPHABETICAL)
    val sortType: StateFlow<SortType> = _sortType.asStateFlow()
    val drawerCategories = DrawerCategories(context)
    /** Category keys in display order; see [DrawerCategories]. */
    val categories: List<String> get() = drawerCategories.keys.value
    private val _selectedCategory = MutableStateFlow("All")
    val selectedCategory: StateFlow<String> = _selectedCategory.asStateFlow()
    var isGestureNav: Boolean = false
    private val _hiddenApps = MutableStateFlow<Set<String>>(emptySet())
    private val _renamedApps = MutableStateFlow<Map<String, String>>(emptyMap())
    val renamedApps: StateFlow<Map<String, String>> = _renamedApps.asStateFlow()
    private val _swipeUpActions = MutableStateFlow<Map<String, String>>(emptyMap())
    val swipeUpActions: StateFlow<Map<String, String>> = _swipeUpActions.asStateFlow()
    private val _swipeDownActions = MutableStateFlow<Map<String, String>>(emptyMap())
    val swipeDownActions: StateFlow<Map<String, String>> = _swipeDownActions.asStateFlow()
    private val _doubleTapActions = MutableStateFlow<Map<String, String>>(emptyMap())
    val doubleTapActions: StateFlow<Map<String, String>> = _doubleTapActions.asStateFlow()
    val searchQuery = MutableStateFlow("")
    internal val _usagePermissionGranted = MutableStateFlow(false)
    val usagePermissionGranted: StateFlow<Boolean> = _usagePermissionGranted.asStateFlow()
    fun isUsageStatsPermissionGranted(): Boolean {
        return UsageStatsHelper.isUsageStatsPermissionGranted(context)
    }
    private val _recentApps = MutableStateFlow<List<AppModel>>(emptyList())
    val recentApps: StateFlow<List<AppModel>> = _recentApps.asStateFlow()
    internal fun refreshRecentApps() {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val recentPackages = UsageStatsHelper.getRecentAppsFromUsageStats(context)
            val allApps = _apps.value
            val recent = recentPackages.mapNotNull { pkg ->
                allApps.find { it.packageName == pkg }
            }.take(8)
            _recentApps.value = recent
        }
    }
    val newApps: StateFlow<List<AppModel>> = _apps.map { apps ->
        apps.sortedByDescending { it.installTime }.take(8)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun saveRecentApp(packageName: String) {
        if (packageName.isNotBlank()) {
            viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                recentSearchDao.insertRecentSearch(com.nexus.launcher.data.RecentSearchEntity(packageName = packageName, timestamp = System.currentTimeMillis()))
                recentSearchDao.pruneOldEntries()
            }
        }
    }
    val dockedApps: StateFlow<List<DisplayItem>> = _apps.map { appList ->
        DrawerItemsBuilder.suggestedDock(context, appList, drawerCategories)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    @Suppress("UNCHECKED_CAST")
    private val baseDrawerItems: StateFlow<List<AppModel>> = combine(
        _apps, _sortType, _selectedCategory, _hiddenApps, _renamedApps, drawerCategories.keys
    ) { args: Array<Any?> ->
        val appList = args[0] as List<AppModel>
        val sort = args[1] as SortType
        val category = args[2] as String
        val hidden = args[3] as Set<String>
        val renamed = args[4] as Map<String, String>
        val categoryId = if (category == DrawerCategories.ALL) null else drawerCategories.idOf(category)
        DrawerItemsBuilder.prepare(
            appList, hidden, renamed, sort, categoryId, localeController.getEffectiveLocale(context),
            resolveCategory = { drawerCategories.visibleIdFor(it) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    @Suppress("UNCHECKED_CAST")
    private val allDrawerItemsForSearch: StateFlow<List<AppModel>> = combine(
        _apps, _sortType, _hiddenApps, _renamedApps
    ) { args: Array<Any?> ->
        val appList = args[0] as List<AppModel>
        val sort = args[1] as SortType
        val hidden = args[2] as Set<String>
        val renamed = args[3] as Map<String, String>
        DrawerItemsBuilder.prepare(appList, hidden, renamed, sort, categoryId = null, locale = localeController.getEffectiveLocale(context))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    @Suppress("UNCHECKED_CAST")
    val filteredDrawerItems: StateFlow<List<DisplayItem>> = combine(
        baseDrawerItems,
        allDrawerItemsForSearch,
        searchQuery,
        _hiddenApps,
        homeScreenDao.getDrawerFolders(),
        drawerCategories.folderCategories,
        _selectedCategory,
        homeScreenDao.getFolderedPackageNamesFlow(),
        drawerCategories.keys
    ) { args: Array<Any?> ->
        DrawerItemsBuilder.build(
            context = context,
            categories = drawerCategories,
            base = args[0] as List<AppModel>,
            all = args[1] as List<AppModel>,
            query = args[2] as String,
            hidden = args[3] as Set<String>,
            drawerFolderItems = args[4] as List<com.nexus.launcher.data.HomeScreenItem>,
            folderCategories = args[5] as Map<Long, Int>,
            selectedCategory = args[6] as String,
            foldered = (args[7] as List<String>).toSet()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    // ── Settings ────────────────────────────────────────────────────────────
    val nexusSettings: StateFlow<NexusSettingsData> = settingsRepository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), NexusSettingsData())
    private val _uiState = MutableStateFlow(LauncherState.HOME)
    val uiState: StateFlow<LauncherState> = _uiState.asStateFlow()
    init {
        Log.d("NexusData", "NexusData: ViewModel Init Block Reached!")
        // Typing is a fresh intent to find something: never let a category the user picked
        // earlier silently narrow their search results. Done here rather than in a text
        // watcher so every search entry point (the drawer pill's overlay included) is covered,
        // and so the pill's category label follows along through [selectedCategory].
        viewModelScope.launch {
            searchQuery.collect { query ->
                if (query.isNotBlank() && _selectedCategory.value != "All") {
                    _selectedCategory.value = "All"
                }
            }
        }
        viewModelScope.launch {
            // A deleted category cannot stay selected: the drawer would filter to nothing.
            drawerCategories.keys.collect { if (_selectedCategory.value !in it) _selectedCategory.value = DrawerCategories.ALL }
        }
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            settingsRepository.migrateDrawerLayoutIfNeeded()
            settingsRepository.cleanupRemovedMinimalUiKeys(
                context.getSharedPreferences("nexus_prefs", Context.MODE_PRIVATE)
            )
        }
        viewModelScope.launch {
            _hiddenApps.value = preferenceManager.getHiddenApps()
            _renamedApps.value = preferenceManager.getRenamedApps()
            _swipeUpActions.value = preferenceManager.getSwipeUpActions()
            _swipeDownActions.value = preferenceManager.getSwipeDownActions()
            _doubleTapActions.value = preferenceManager.getDoubleTapActions()
            _usagePermissionGranted.value =
                isUsageStatsPermissionGranted()
            // Collect iconPack, iconShape, and iconTheming changes.
            //
            // From the persisted flow, not `nexusSettings`: that one is a stateIn seeded with
            // NexusSettingsData() defaults, so its first value is "no icon pack" whatever is
            // actually saved. Reacting to it called loadPack("none") — and IconPackParser is a
            // process-wide singleton, so every new MainActivity switched the icon pack off for the
            // whole launcher (home, dock, the other activity instance) and resolved the app list
            // with original icons, until the real settings arrived and put the pack back. The
            // drawer then showed original icons flipping to the pack one batch at a time.
            launch {
                settingsRepository.settingsFlow.map { Triple(it.iconPack, it.iconShape, it.iconTheming) }.distinctUntilChanged().collect { (pack, shape, themed) ->
                    iconPackParser.loadPack(pack)
                    com.nexus.launcher.ui.icons.IconResolver.currentShape = shape
                    // Applied here, in the same order as the pack and the shape, rather than left
                    // to ThemedIconFactory's own collector on the same settings flow.
                    //
                    // Those two collectors have no ordering between them, so this one could reach
                    // getInstalledApps() - which resolves every icon - while `enabled` was still
                    // false. The resolver then took the un-themed branch, the results were cached,
                    // and nothing emitted again: turning Themed Icons on appeared to do nothing at
                    // all. Pack and shape never showed the bug because they are written right
                    // here, immediately before the resolve.
                    themedIconFactory.enabled = themed
                    resyncApps()
                }
            }
            // Drawer sort order (persisted in DataStore) drives _sortType
            launch {
                // Persisted flow for the same reason as the icon collector above: the placeholder
                // default sorted a fresh drawer A-Z for a moment before the saved order arrived.
                settingsRepository.settingsFlow.map { it.drawerSortOrder }.distinctUntilChanged().collect { order ->
                    _sortType.value = when (order) {
                        "za" -> SortType.ALPHABETICAL_DESC
                        "new" -> SortType.INSTALL_DATE
                        else -> SortType.ALPHABETICAL
                    }
                }
            }
            // In-app locale changes reload localized app labels from packages
            launch {
                // drop(1): the icon collector above already performs the startup resync, and a
                // second one here raced it — started before the pack was loaded, it could finish
                // last and leave the list holding original icons. Only an actual locale change
                // needs labels re-resolved.
                localeController.appLocale.drop(1).collect { resyncApps() }
            }
            refreshRecentApps()
            _apps.collect { refreshRecentApps() }
        }
        val packageFilter = android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_PACKAGE_REMOVED)
            addAction(android.content.Intent.ACTION_PACKAGE_ADDED)
            addAction(android.content.Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        packageReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(ctx: android.content.Context?, intent: android.content.Intent?) {
                val removedPackage = intent?.data?.schemeSpecificPart
                if (removedPackage != null) {
                    invalidatedPackages.tryEmit(removedPackage)
                }
                viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                    if (intent?.action == android.content.Intent.ACTION_PACKAGE_REMOVED && removedPackage != null) {
                        // Remove all home screen placements of this package
                        homeScreenDao.removeAllByPackage(removedPackage)
                    }
                    resyncApps()
                }
            }
        }
        context.registerReceiver(packageReceiver, packageFilter)
        prefListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "hidden_apps") {
                _hiddenApps.value = preferenceManager.getHiddenApps()
            }
        }.also {
            preferenceManager.registerOnSharedPreferenceChangeListener(it)
        }
    }
    override fun onCleared() {
        super.onCleared()
        packageReceiver?.let {
            try {
                context.unregisterReceiver(it)
            } catch (e: Exception) {
                // Already unregistered
            }
        }
        prefListener?.let {
            preferenceManager.unregisterOnSharedPreferenceChangeListener(it)
        }
    }
    fun handleSwipeUp() {
        _uiState.value = LauncherState.DRAWER
    }
    fun handleSwipeDown() {
        _uiState.value = LauncherState.HOME
        searchQuery.value = "" // Clear search when closing drawer
        _selectedCategory.value = "All" // Reset category
    }
    fun onCategorySelected(category: String) {
        _selectedCategory.value = category
    }
    fun hideApp(packageName: String) {
        preferenceManager.addHiddenApp(packageName)
        _hiddenApps.value = preferenceManager.getHiddenApps()
    }
    fun unhideApp(packageName: String) {
        val current = preferenceManager.getHiddenApps().toMutableSet()
        current.remove(packageName)
        preferenceManager.saveHiddenApps(current)
        _hiddenApps.value = preferenceManager.getHiddenApps()
    }
    fun renameApp(packageName: String, newName: String) {
        preferenceManager.setAppRename(packageName, newName)
        _renamedApps.value = preferenceManager.getRenamedApps()
    }
    fun refreshPerAppPreferences() {
        _hiddenApps.value = preferenceManager.getHiddenApps()
        _renamedApps.value = preferenceManager.getRenamedApps()
        _swipeUpActions.value = preferenceManager.getSwipeUpActions()
        _swipeDownActions.value = preferenceManager.getSwipeDownActions()
        _doubleTapActions.value = preferenceManager.getDoubleTapActions()
        viewModelScope.launch {
            resyncApps()
        }
    }

    fun setCustomIcon(packageName: String, iconData: String?) {
        preferenceManager.setCustomIcon(packageName, iconData)
        viewModelScope.launch {
            resyncApps()
        }
    }

    /** Null clears per-app override so the icon follows the global Theme shape. */
    fun setAppIconShape(packageName: String, shapeId: Int?) {
        preferenceManager.setAppIconShape(packageName, shapeId)
        viewModelScope.launch {
            resyncApps()
        }
    }

    fun getAppIconShape(packageName: String): Int? =
        preferenceManager.getAppIconShape(packageName)

    fun setAppCategory(packageName: String, categoryId: Int) {
        preferenceManager.setAppCategoryOverride(packageName, categoryId)
        viewModelScope.launch {
            resyncApps()
        }
    }

    fun setFolderCategory(folderId: Long, categoryId: Int?) =
        drawerCategories.setFolderCategory(folderId, categoryId, preferenceManager)

    fun getFolderCategory(folderId: Long): Int? = drawerCategories.folderCategories.value[folderId]

    fun addCategory(name: String): String? = drawerCategories.add(name)

    /** Also clears app overrides pointing at it, so apps re-resolve to their automatic category. */
    fun deleteCategory(key: String) {
        drawerCategories.delete(key, preferenceManager)
        viewModelScope.launch { resyncApps() }
    }

    fun restoreDefaultCategories() = drawerCategories.restoreBuiltIns()

    fun setSwipeUpAction(packageName: String, action: String?) {
        preferenceManager.setSwipeUpAction(packageName, action)
        _swipeUpActions.value = preferenceManager.getSwipeUpActions()
    }
    fun setSwipeDownAction(packageName: String, action: String?) {
        preferenceManager.setSwipeDownAction(packageName, action)
        _swipeDownActions.value = preferenceManager.getSwipeDownActions()
    }
    fun setDoubleTapAction(packageName: String, action: String?) {
        preferenceManager.setDoubleTapAction(packageName, action)
        _doubleTapActions.value = preferenceManager.getDoubleTapActions()
    }
}
