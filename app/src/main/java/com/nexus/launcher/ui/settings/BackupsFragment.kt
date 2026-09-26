package com.nexus.launcher.ui.settings

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.nexus.launcher.R
import com.nexus.launcher.data.prefs.SettingsRepository
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.backup.BackupCardFactory
import com.nexus.launcher.ui.backup.BackupCatalog
import com.nexus.launcher.ui.backup.BackupDetailDialog
import com.nexus.launcher.ui.backup.BackupExportLauncher
import com.nexus.launcher.ui.backup.BackupFolderLauncher
import com.nexus.launcher.ui.folder.FolderAuroraDialogs
import com.nexus.launcher.ui.glass.FrostedGlassEngine
import com.nexus.launcher.ui.settings.views.NexusNavRow
import com.nexus.launcher.ui.settings.views.SettingsSectionGroupView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Browsable backups: a two-column grid of home screens you can restore, laid out like Manage
 * Pages. Tapping a tile opens it at a readable size; long-pressing offers to delete it.
 *
 * A full-screen page in its own right — same toolbar/card chrome as [HiddenAppsFragment], since
 * both are pushed onto `android.R.id.content` and would otherwise render straight over whatever
 * settings page launched them.
 */
@AndroidEntryPoint
class BackupsFragment : Fragment() {

    @Inject lateinit var settingsRepository: SettingsRepository

    private var tokens: NexusColorTokens = NexusColorTokens.Dark
    private var dp = 1f

    private lateinit var rootContainer: FrameLayout
    private lateinit var card: LinearLayout
    private lateinit var backButton: ImageView
    private lateinit var titleView: TextView
    private lateinit var locationGroup: View
    private lateinit var locationRow: NexusNavRow
    private lateinit var sectionHeader: TextView
    private lateinit var grid: GridLayout
    private lateinit var emptyView: View

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        dp = resources.displayMetrics.density
        tokens = try {
            ThemeObserver.currentTokens(requireContext())
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }

        rootContainer = FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        if (FrostedGlassEngine.isGlobalFrostedGlassEnabled) {
            rootContainer.addView(SettingsFrostBackdropView(requireContext()).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
                )
            })
        }

        card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            clipToOutline = true
        }

        ViewCompat.setOnApplyWindowInsetsListener(rootContainer) { _, insets ->
            val status = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val side = (8 * dp).toInt()
            (card.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                lp.setMargins(side, (8 * dp).toInt() + status.top, side, (8 * dp).toInt() + nav.bottom)
                card.layoutParams = lp
            }
            insets
        }
        ViewCompat.requestApplyInsets(rootContainer)

        card.addView(buildToolbar())
        card.addView(buildScrollBody())
        rootContainer.addView(card)
        applyThemeTokens()
        return rootContainer
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun buildToolbar(): View {
        val toolbar = FrameLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, (56 * dp).toInt()
            )
            setPadding((8 * dp).toInt(), 0, (8 * dp).toInt(), 0)
        }
        backButton = ImageView(requireContext()).apply {
            setImageResource(R.drawable.ic_back_arrow)
            val pad = (12 * dp).toInt()
            setPadding(pad, pad, pad, pad)
            imageTintList = ColorStateList.valueOf(tokens.textPrimary)
            layoutParams = FrameLayout.LayoutParams((48 * dp).toInt(), (48 * dp).toInt()).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
            }
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }
        toolbar.addView(backButton)

        titleView = TextView(requireContext()).apply {
            text = getString(R.string.backups_title)
            NexusTypeScale.title.bindTo(this, tokens.textPrimary)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                marginStart = (56 * dp).toInt()
            }
        }
        toolbar.addView(titleView)

        return toolbar
    }

    private fun buildScrollBody(): View {
        val scroll = ScrollView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
            )
            isVerticalScrollBarEnabled = false
            setBackgroundColor(Color.TRANSPARENT)
            clipChildren = false
            clipToPadding = false
        }
        val body = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((14 * dp).toInt(), (4 * dp).toInt(), (14 * dp).toInt(), (40 * dp).toInt())
            clipChildren = false
        }

        locationRow = NexusNavRow(
            requireContext(),
            title = getString(R.string.backup_location_title),
            subtitle = getString(R.string.backup_location_none),
            iconRes = R.drawable.ic_backup,
            showChevron = true
        ) { pickFolder() }.apply {
            setContentPadding((14 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt())
        }
        locationGroup = SettingsSectionGroupView(requireContext()).apply { addChildRow(locationRow) }
        body.addView(locationGroup)

        val backupNowRow = NexusNavRow(
            requireContext(),
            title = getString(R.string.backup_create_action),
            subtitle = getString(R.string.backup_subtitle),
            iconRes = R.drawable.ic_backup,
            showChevron = true
        ) { backUpNow() }.apply {
            setContentPadding((14 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt())
        }
        body.addView(SettingsSectionGroupView(requireContext()).apply { addChildRow(backupNowRow) })

        sectionHeader = TextView(requireContext()).apply {
            text = getString(R.string.backups_existing_header)
            NexusTypeScale.sectionLabel.bindTo(this, tokens.textSecondary)
            setPadding((6 * dp).toInt(), (22 * dp).toInt(), 0, (8 * dp).toInt())
        }
        body.addView(sectionHeader)

        emptyView = buildEmptyState()
        body.addView(emptyView)

        grid = GridLayout(requireContext()).apply {
            columnCount = 2
            clipChildren = false
            clipToPadding = false
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        body.addView(grid)

        scroll.addView(body)
        return scroll
    }

    private fun buildEmptyState(): View = LinearLayout(requireContext()).apply {
        orientation = LinearLayout.VERTICAL
        setPadding((6 * dp).toInt(), (16 * dp).toInt(), (6 * dp).toInt(), (16 * dp).toInt())
        addView(TextView(requireContext()).apply {
            text = getString(R.string.backup_empty_title)
            NexusTypeScale.hubRowTitle.bindTo(this, tokens.textPrimary)
        })
        addView(TextView(requireContext()).apply {
            text = getString(R.string.backup_empty_body)
            NexusTypeScale.caption.bindTo(this, tokens.textSecondary)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (6 * dp).toInt() }
        })
    }

    private fun applyThemeTokens() {
        if (FrostedGlassEngine.isGlobalFrostedGlassEnabled) {
            val frosted = FrostedGlassEngine.resolveFrostedTokens(tokens)
            val fill = (FrostedGlassEngine.sheetFillAlpha(1f, 0.55f) * 255f).toInt()
            card.background = GradientDrawable().apply {
                setColor((frosted.surface and 0x00FFFFFF) or (fill shl 24))
                cornerRadius = 24 * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), frosted.border)
            }
            rootContainer.setBackgroundColor(Color.TRANSPARENT)
        } else {
            card.background = GradientDrawable().apply {
                setColor(tokens.surface)
                cornerRadius = 24 * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
            }
            rootContainer.setBackgroundColor(tokens.bg)
        }
    }

    private fun pickFolder() {
        BackupFolderLauncher.pick(requireActivity() as androidx.activity.ComponentActivity) { uri ->
            if (uri == null) return@pick
            viewLifecycleOwner.lifecycleScope.launch {
                settingsRepository.updateBackupFolderUri(uri)
                refresh()
            }
        }
    }

    private fun backUpNow() {
        viewLifecycleOwner.lifecycleScope.launch {
            val treeUri = settingsRepository.snapshot().backupFolderUri
            if (!BackupFolderLauncher.isUsable(requireContext(), treeUri)) {
                toast(getString(R.string.backup_pick_folder_first))
                pickFolder()
                return@launch
            }
            BackupExportLauncher.exportToFolder(
                requireActivity() as androidx.activity.ComponentActivity, treeUri
            ) { ok ->
                toast(getString(if (ok) R.string.backup_created else R.string.backup_create_failed))
                if (ok) refresh()
            }
        }
    }

    private var refreshGeneration = 0

    /** Only the newest refresh draws: back from the folder picker, onResume's refresh (old folder)
     *  and the pick's own (new folder) race, and the older one landing last left a stale grid. */
    private fun refresh() {
        if (!isAdded) return
        val generation = ++refreshGeneration
        viewLifecycleOwner.lifecycleScope.launch {
            val treeUri = settingsRepository.snapshot().backupFolderUri
            // Always shown — the one place to see and change where backups go (it used to hide).
            locationRow.setSubtitle(BackupFolderLauncher.locationSubtitle(requireContext(), treeUri))
            val entries = withContext(Dispatchers.IO) {
                BackupCatalog.list(requireContext(), treeUri)
            }
            if (!isAdded || generation != refreshGeneration) return@launch
            renderGrid(entries)
        }
    }

    private fun renderGrid(entries: List<BackupCatalog.Entry>) {
        grid.removeAllViews()
        val hasEntries = entries.isNotEmpty()
        emptyView.visibility = if (hasEntries) View.GONE else View.VISIBLE
        sectionHeader.visibility = if (hasEntries) View.VISIBLE else View.GONE
        if (!hasEntries) return

        grid.post {
            if (!isAdded) return@post
            val available = grid.width.takeIf { it > 0 } ?: return@post
            val columnWidth = available / 2
            grid.removeAllViews()
            entries.forEach { entry ->
                grid.addView(
                    BackupCardFactory.createTile(
                        context = requireContext(),
                        density = dp,
                        tokens = tokens,
                        entry = entry,
                        columnWidthPx = columnWidth,
                        onOpen = { BackupDetailDialog(requireContext(), entry) { confirmRestore(entry) }.show() },
                        onRestore = { confirmRestore(entry) },
                        onLongPress = { confirmDelete(entry) }
                    )
                )
            }
        }
    }

    private fun confirmRestore(entry: BackupCatalog.Entry) {
        FolderAuroraDialogs.showConfirmation(
            context = requireContext(),
            title = getString(R.string.backup_restore_confirm_title),
            body = getString(R.string.backup_restore_confirm_body, entry.label),
            cancelText = getString(R.string.action_cancel),
            confirmText = getString(R.string.backup_restore_action),
            onConfirm = { (requireActivity() as SettingsActivity).restoreBackup(entry.uri) }
        )
    }

    private fun confirmDelete(entry: BackupCatalog.Entry) {
        val ctx = requireContext()
        FolderAuroraDialogs.showConfirmation(
            context = ctx,
            title = getString(R.string.backup_delete_confirm_title),
            body = getString(R.string.backup_delete_confirm_body, entry.label),
            cancelText = getString(R.string.action_cancel),
            confirmText = getString(R.string.backup_menu_delete),
            onConfirm = {
                viewLifecycleOwner.lifecycleScope.launch {
                    val ok = withContext(Dispatchers.IO) { BackupCatalog.delete(ctx, entry) }
                    if (ok) toast(getString(R.string.backup_deleted))
                    refresh()
                }
            }
        )
    }

    private fun toast(message: String) {
        if (isAdded) Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        fun newInstance() = BackupsFragment()
    }
}
