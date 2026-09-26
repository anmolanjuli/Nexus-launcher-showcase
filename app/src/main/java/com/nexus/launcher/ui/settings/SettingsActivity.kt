package com.nexus.launcher.ui.settings

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.nexus.launcher.R
import com.nexus.launcher.data.HomeScreenDao
import com.nexus.launcher.data.prefs.PreferenceManager
import com.nexus.launcher.data.prefs.SettingsRepository
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.backup.BackupImporter
import com.nexus.launcher.ui.backup.NovaImporter
import com.nexus.launcher.ui.dock.settings.DockSettingsRepository
import com.nexus.launcher.ui.folder.FolderAuroraDialogs
import com.nexus.launcher.ui.glass.UiStyleCoordinator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class SettingsActivity : AppCompatActivity() {

    @Inject lateinit var dao: HomeScreenDao
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var dockSettingsRepository: DockSettingsRepository
    @Inject lateinit var preferenceManager: PreferenceManager
    @Inject lateinit var themeController: com.nexus.launcher.theme.ThemeController
    @Inject lateinit var fontFamilyController: com.nexus.launcher.typography.FontFamilyController
    @Inject lateinit var feedDao: com.nexus.launcher.data.FeedDao
    @Inject lateinit var localeController: com.nexus.launcher.locale.LocaleController

    /**
     * Restores a backup the Backups screen already resolved to a document URI — the same path the
     * file picker takes, minus the picking.
     */
    fun restoreBackup(uri: android.net.Uri) {
        lifecycleScope.launch {
            try {
                BackupImporter.restore(
                    this@SettingsActivity, uri, dao, settingsRepository,
                    dockSettingsRepository, preferenceManager,
                    themeController, fontFamilyController, feedDao, localeController,
                )
                Toast.makeText(this@SettingsActivity, getString(R.string.toast_backup_imported), Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@SettingsActivity, getString(R.string.toast_import_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importBackupLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            lifecycleScope.launch {
                try {
                    BackupImporter.restore(
                        this@SettingsActivity, uri, dao, settingsRepository,
                        dockSettingsRepository, preferenceManager,
                        themeController, fontFamilyController, feedDao, localeController,
                    )
                    Toast.makeText(this@SettingsActivity, getString(R.string.toast_backup_imported), Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@SettingsActivity, getString(R.string.toast_import_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val importNovaBackupLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            lifecycleScope.launch {
                try {
                    NovaImporter.restore(
                        this@SettingsActivity, uri, dao, settingsRepository, preferenceManager,
                    )
                    Toast.makeText(this@SettingsActivity, getString(R.string.toast_nova_backup_imported), Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this@SettingsActivity, getString(R.string.toast_nova_import_failed, e.message ?: ""), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val viewModel: SettingsViewModel by viewModels()
    private var showingCategoryList = true
    private lateinit var categoryContainer: LinearLayout
    private lateinit var fragmentContainer: LinearLayout
    private lateinit var pager: ViewPager2
    private lateinit var pageTitleView: TextView
    private lateinit var pageIconView: ImageView
    private lateinit var jumpButton: ImageView
    private lateinit var applyBar: SettingsApplyBarBinder
    private var currentTokens: NexusColorTokens = NexusColorTokens.Dark

    private val backPressedCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (supportFragmentManager.backStackEntryCount > 0) {
                supportFragmentManager.popBackStack()
            } else if (!showingCategoryList) {
                requestLeavePager()
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    private var frostBackdropView: SettingsFrostBackdropView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        // Before super: no-preview theme so relaunch does not flash windowBackground.
        // After super, before setContentView: bitmap backdrop + decor overlay already in place.
        SettingsLocaleTransition.prepareIncoming(this)
        super.onCreate(savedInstanceState)
        SettingsLocaleTransition.installEarly(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        setContentView(R.layout.activity_settings)
        // Content inflation can reorder decor children — keep the locale cover on top.
        SettingsLocaleTransition.coverIfPending(this)

        frostBackdropView = findViewById(R.id.settings_frost_backdrop)
        categoryContainer = findViewById(R.id.settings_category_container)
        fragmentContainer = findViewById(R.id.settings_fragment_container)
        pager = findViewById(R.id.settings_pager)
        pageTitleView = findViewById(R.id.settings_page_title)
        NexusTypeScale.title.bindTo(this, pageTitleView)
        pageIconView = findViewById(R.id.settings_page_icon)
        jumpButton = findViewById(R.id.btn_jump_pages)
        val categoryView = findViewById<SettingsCategoryView>(R.id.settings_category_view)
        val hubInset = findViewById<View>(R.id.settings_hub_inset)
        val fragmentHeader = findViewById<View>(R.id.fragment_header)

        findViewById<View>(R.id.btn_back_arrow).setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
            requestLeavePager()
        }
        jumpButton.setOnClickListener {
            it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
            openJumpSheet()
        }

        applyBar = SettingsApplyBarBinder(this)
        applyBar.bind(
            pagerIndex = { pager.currentItem },
            tokens = { currentTokens },
            onApply = {
                if (viewModel.hasUnsaved.value) viewModel.applyDraft()
            },
        )
        lifecycleScope.launch { viewModel.ensureDraftReady() }
        onBackPressedDispatcher.addCallback(this, backPressedCallback)

        pager.adapter = SettingsPagerAdapter(this)
        pager.isUserInputEnabled = false
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updatePageHeader(position)
                applyBar.onPageSelected(position)
            }
        })
        categoryView.onCategoryTapped = { position -> openHubTarget(position) }
        categoryView.onControlTapped = { position, label -> openHubTarget(position, label) }

        SettingsActivityChrome.installInsets(hubInset, fragmentHeader, categoryContainer, fragmentContainer)

        SettingsActivityChrome.collectThemeAndDraft(
            activity = this,
            viewModel = viewModel,
            settingsRepository = settingsRepository,
            applyBar = applyBar,
            jumpButton = jumpButton,
            pageIconView = pageIconView,
            pageTitleView = pageTitleView,
            frostBackdropView = frostBackdropView,
            fragmentContainer = fragmentContainer,
            categoryContainer = categoryContainer,
            onTokens = { currentTokens = it },
        )

        // A saved page wins over the launch intent: this Activity is recreated by things the user
        // did *inside* it - picking a language calls AppCompatDelegate.setApplicationLocales, and
        // a UI Style change recreates deliberately - and without this, every one of those dropped
        // them back on the hub, several taps from where they were working.
        // Locale cover is already on the decor from installEarly — do not wait until here.
        val savedPage = savedInstanceState?.takeIf { it.getBoolean(STATE_IN_PAGE, false) }
            ?.getInt(STATE_PAGE_INDEX, -1) ?: -1
        val targetIndex = if (savedPage >= 0) savedPage else SettingsHubCatalog.sectionIndex(intent.getStringExtra(EXTRA_SECTION))
        if (targetIndex >= 0) showFragmentView(targetIndex) else showCategoryList()
    }

    override fun onPause() {
        SettingsLocaleTransition.suppressTransitionAnim(this)
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(STATE_IN_PAGE, !showingCategoryList)
        if (!showingCategoryList) outState.putInt(STATE_PAGE_INDEX, pager.currentItem)
    }

    /**
     * Re-localises this screen in place, without letting the system restart it.
     *
     * `SettingsActivity` declares `configChanges="locale|layoutDirection"`, so a language change
     * arrives here instead of destroying and rebuilding the Activity. That is the whole fix: the
     * restart was the flicker — a screen recording showed roughly 100ms of pure black between two
     * otherwise identical frames — and no cover could hide it, because every cover was a view
     * inside the Activity being torn down. It died with the thing it was covering.
     *
     * With the Activity alive, a plain crossfade works: hold the outgoing pixels, swap the
     * localised views underneath, fade through.
     */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        SettingsLocaleTransition.captureThen(this) { relocaliseInPlace() }
    }

    private fun relocaliseInPlace() {
        val page = pager.currentItem

        // Fragments cache their strings in views built at inflation, so the adapter is replaced
        // to force fresh ones rather than trying to re-set every label.
        pager.adapter = SettingsPagerAdapter(this)
        pager.setCurrentItem(page, false)
        updatePageHeader(page)

        // Same reasoning for the hub list, which builds its rows programmatically.
        val stale = findViewById<SettingsCategoryView>(R.id.settings_category_view)
        val parent = stale?.parent as? ViewGroup
        if (stale != null && parent != null) {
            val index = parent.indexOfChild(stale)
            val params = stale.layoutParams
            parent.removeViewAt(index)
            parent.addView(
                SettingsCategoryView(this).apply {
                    id = R.id.settings_category_view
                    layoutParams = params
                    onCategoryTapped = { position -> openHubTarget(position) }
                    onControlTapped = { position, label -> openHubTarget(position, label) }
                    applyTokens(currentTokens)
                },
                index,
            )
        }

        // Colours are unaffected by a locale change and the theme collector still owns them, so
        // only the localised views are rebuilt here.
        SettingsLocaleTransition.revealWhenReady(this)
    }

    override fun onResume() {
        super.onResume()
        SettingsLocaleTransition.revealWhenReady(this)
        findViewById<SettingsCategoryView>(R.id.settings_category_view)?.refreshDefaultLauncherState()
    }

    override fun onDestroy() {
        UiStyleCoordinator.removeListener(UiStyleCoordinator.LISTENER_SETTINGS)
        super.onDestroy()
    }

    private fun updatePageHeader(position: Int) {
        val page = SettingsHubCatalog.pageAt(position)
        pageTitleView.text = getString(page.titleRes)
        NexusTypeScale.title.bindTo(this, pageTitleView)
        pageTitleView.setTextColor(currentTokens.textPrimary)
        pageIconView.setImageResource(page.iconRes)
        pageIconView.imageTintList = ColorStateList.valueOf(currentTokens.textPrimary)
    }

    private fun openJumpSheet() {
        if (supportFragmentManager.findFragmentByTag(SettingsJumpSheet.TAG) != null) return
        SettingsJumpSheet(pager.currentItem, { dest -> openHubTarget(dest) }) { dest, label -> openHubTarget(dest, label) }
            .show(supportFragmentManager, SettingsJumpSheet.TAG)
    }

    /**
     * Single entry point for anything that names a hub target — the landing list, the jump
     * sheet, and search results within either. Most targets are pager positions; Dock is a
     * bottom sheet, so it is routed rather than paged to.
     */
    private fun openHubTarget(position: Int, revealLabel: String? = null) {
        if (position == SettingsHubCatalog.PAGER_DOCK_DIALOG) showDockSettings() else jumpToPage(position)
        if (revealLabel != null) {
            SettingsSearchReveal.revealOnPage(this, position, revealLabel, currentTokens.accent, DOCK_SETTINGS_TAG)
        }
    }

    private fun showDockSettings() {
        if (supportFragmentManager.findFragmentByTag(DOCK_SETTINGS_TAG) != null) return
        hideIme()
        // No DockLayout to pass: there is no dock on screen here, and the dialog already treats
        // it as optional and falls back to its own preview.
        com.nexus.launcher.ui.dock.settings.DockSettingsDialog(dockSettingsRepository)
            .show(supportFragmentManager, DOCK_SETTINGS_TAG)
    }

    fun jumpToPage(position: Int) {
        if (position == SettingsHubCatalog.PAGER_DOCK_DIALOG) {
            showDockSettings()
            return
        }
        hideIme()
        if (showingCategoryList) {
            showFragmentView(position)
        } else {
            pager.setCurrentItem(position, false)
            updatePageHeader(position)
            applyBar.onPageSelected(position)
        }
    }

    private fun requestLeavePager() {
        if (viewModel.hasUnsaved.value) {
            FolderAuroraDialogs.showConfirmation(
                context = this,
                title = getString(R.string.dialog_discard_title),
                body = getString(R.string.dialog_discard_message),
                cancelText = getString(R.string.action_cancel),
                confirmText = getString(R.string.action_save),
                onCancel = {
                    viewModel.discardDraft()
                    showCategoryList()
                },
                onConfirm = {
                    viewModel.applyDraft()
                    showCategoryList()
                },
            )
        } else {
            showCategoryList()
        }
    }

    private fun showCategoryList() {
        hideIme()
        showingCategoryList = true
        categoryContainer.visibility = View.VISIBLE
        fragmentContainer.visibility = View.GONE
        backPressedCallback.isEnabled = false
    }

    private fun showFragmentView(position: Int) {
        hideIme()
        showingCategoryList = false
        categoryContainer.visibility = View.GONE
        fragmentContainer.visibility = View.VISIBLE
        pager.setCurrentItem(position, false)
        updatePageHeader(position)
        applyBar.onPageSelected(position)
        backPressedCallback.isEnabled = true
    }

    private fun hideIme() {
        val imm = getSystemService(InputMethodManager::class.java) ?: return
        val token = currentFocus?.windowToken ?: window.decorView.windowToken
        imm.hideSoftInputFromWindow(token, 0)
    }

    companion object {
        const val EXTRA_SECTION = "extra_section"
        private const val DOCK_SETTINGS_TAG = "DockSettings"
        private const val STATE_IN_PAGE = "settings_in_page"
        private const val STATE_PAGE_INDEX = "settings_page_index"
    }
}
