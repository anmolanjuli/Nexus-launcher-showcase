package com.nexus.launcher.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import com.nexus.launcher.R
import com.nexus.launcher.ui.folder.FolderAuroraDialogs
import com.nexus.launcher.ui.settings.views.NexusNavRow
import com.nexus.launcher.ui.settings.views.NexusSection
import com.nexus.launcher.ui.settings.views.SettingsSectionGroupView
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class BackupRestoreSettingsFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val dp = resources.displayMetrics.density
        val padH = (16 * dp).toInt()

        val scrollView = NestedScrollView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(padH, 0, padH, (80 * dp).toInt())
        }

        val mainSection = NexusSection(requireContext()).apply {
            isTransparentCard = true
        }

        // 1. Nexus Backup and Restore (Header & Children)
        val headerNexusBackup = NexusNavRow(
            requireContext(),
            title = getString(R.string.backup_page_header_title),
            subtitle = getString(R.string.backup_page_header_subtitle),
            iconRes = R.mipmap.ic_launcher,
            showChevron = false,
            tintIcon = false
        ).apply {
            setContentPadding(0, (12 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt())
        }

        // Backups now live on their own screen as restorable cards; this row is the way in.
        val backupsRow = NexusNavRow(
            requireContext(),
            title = getString(R.string.backups_title),
            subtitle = getString(R.string.backups_subtitle),
            iconRes = R.drawable.ic_backup,
            showChevron = true
        ) {
            requireActivity().supportFragmentManager.beginTransaction()
                .replace(android.R.id.content, BackupsFragment.newInstance())
                .addToBackStack(null)
                .commit()
        }.apply {
            setContentPadding((14 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt())
        }

        val nexusBackupChildren = SettingsSectionGroupView(requireContext()).apply {
            addChildRow(backupsRow)
        }

        val nexusBackupContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(headerNexusBackup)
            addView(nexusBackupChildren)
        }
        mainSection.addRow(nexusBackupContainer)

        // 2. Third-Party Migration (Nova Launcher)
        val importNovaRow = NexusNavRow(
            requireContext(),
            title = getString(R.string.import_nova_title),
            subtitle = getString(R.string.import_nova_subtitle),
            iconRes = R.drawable.ic_nova_launcher,
            showChevron = true
        ) {
            val ctx = requireContext()
            FolderAuroraDialogs.showConfirmation(
                context = ctx,
                title = ctx.getString(R.string.import_nova_dialog_title),
                body = ctx.getString(R.string.import_nova_dialog_message),
                cancelText = ctx.getString(R.string.action_cancel),
                confirmText = ctx.getString(R.string.action_proceed),
                onConfirm = {
                    val activity = requireActivity() as SettingsActivity
                    activity.importNovaBackupLauncher.launch(arrayOf("*/*"))
                }
            )
        }.apply {
            setContentPadding((14 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt())
        }

        val novaContainer = SettingsSectionGroupView(requireContext()).apply {
            addChildRow(importNovaRow)
        }
        mainSection.addRow(novaContainer)

        content.addView(mainSection)
        scrollView.addView(content)

        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(0, top, 0, (16 * dp).toInt())
            insets
        }

        return scrollView
    }
}
