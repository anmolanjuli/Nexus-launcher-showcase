package com.nexus.launcher.ui.settings

import android.animation.LayoutTransition
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nexus.launcher.R
import com.nexus.launcher.ui.settings.views.NexusNavRow
import com.nexus.launcher.ui.settings.views.NexusSection
import com.nexus.launcher.ui.settings.views.SettingsSectionGroupView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NotificationsSettingsFragment : Fragment() {

    private val viewModel: SettingsViewModel by activityViewModels()

    private lateinit var badgesSection: NotificationBadgesSection
    // Per view, not per fragment: ViewPager2 rebuilds this fragment's view when paged back to,
    // and a section kept from the last view still has that view as its parent (crash on addRow).
    private lateinit var historySection: NotificationHistorySection

    private lateinit var permissionsHeader: NexusNavRow
    private lateinit var notificationAccessRow: NexusNavRow
    private lateinit var storageAccessRow: NexusNavRow
    private lateinit var accessibilityRow: NexusNavRow
    private lateinit var appUsageRow: NexusNavRow
    private lateinit var locationRow: NexusNavRow

    private var isPermissionsExpanded = true
    private var ignoreCallbacks = false

    private val storagePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        updatePermissionStatus()
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        updatePermissionStatus()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val dp = resources.displayMetrics.density
        val padH = (16 * dp).toInt()
        val childIndent = (8 * dp).toInt()

        val scrollView = NestedScrollView(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setPadding(padH, 0, padH, (80 * dp).toInt())
            layoutTransition = LayoutTransition()
        }

        val mainSection = NexusSection(requireContext()).apply {
            isTransparentCard = true
        }

        // 1. Notification Badges Section (Accordion, App/Folder styles, Badge Color Picker)
        badgesSection = NotificationBadgesSection(
            context = requireContext(),
            onAppBadgeChanged = { style ->
                if (!ignoreCallbacks) {
                    viewModel.patchDraft { s -> s.copy(badgeStyleApp = style) }
                    updateBadgeHeaderSubtitle()
                }
            },
            onFolderBadgeChanged = { style ->
                if (!ignoreCallbacks) {
                    viewModel.patchDraft { s -> s.copy(badgeStyleFolder = style) }
                    updateBadgeHeaderSubtitle()
                }
            },
            onBadgeColorChanged = { hex ->
                viewModel.setNotificationBadgeColor(hex)
            }
        )
        mainSection.addRow(badgesSection.badgesHeader)
        mainSection.addRow(badgesSection.badgesContainer)

        // The launcher's own notification sheet: keep cleared notifications, and for how long.
        historySection = NotificationHistorySection(requireContext())
        historySection.setListeners(
            onPatch = { transform -> viewModel.patchDraft(transform = transform) },
            isIgnoreCallbacks = { ignoreCallbacks },
        )
        mainSection.addRow(historySection.container)

        // 2. System & Permissions Section (Accordion with ic_admin, functional toggles)
        notificationAccessRow = NexusNavRow(
            requireContext(),
            title = getString(R.string.notification_access_title),
            subtitle = getString(R.string.notification_access_desc),
            iconRes = R.drawable.ic_notification,
            showChevron = false
        ) {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }.apply {
            setTitleBold(false)
            setToggle(visible = true, checked = false)
            setContentPadding(0, (10 * dp).toInt(), (8 * dp).toInt(), (10 * dp).toInt())
        }

        storageAccessRow = NexusNavRow(
            requireContext(),
            title = getString(R.string.storage_access_title),
            subtitle = getString(R.string.storage_access_desc),
            iconRes = R.drawable.ic_gallery,
            showChevron = false
        ) {
            if (!NotificationPermissionStatus.hasStoragePermission(requireContext())) {
                if (Build.VERSION.SDK_INT >= 33) {
                    storagePermissionLauncher.launch(arrayOf(android.Manifest.permission.READ_MEDIA_IMAGES))
                } else {
                    storagePermissionLauncher.launch(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE))
                }
            } else {
                openAppDetailsSettings()
            }
        }.apply {
            setTitleBold(false)
            setToggle(visible = true, checked = false)
            setContentPadding(0, (10 * dp).toInt(), (8 * dp).toInt(), (10 * dp).toInt())
        }

        accessibilityRow = NexusNavRow(
            requireContext(),
            title = getString(R.string.accessibility_service_title),
            subtitle = getString(R.string.accessibility_service_desc),
            iconRes = R.drawable.ic_gesture,
            showChevron = false
        ) {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }.apply {
            setTitleBold(false)
            setToggle(visible = true, checked = false)
            setContentPadding(0, (10 * dp).toInt(), (8 * dp).toInt(), (10 * dp).toInt())
        }

        appUsageRow = NexusNavRow(
            requireContext(),
            title = getString(R.string.app_usage_title),
            subtitle = getString(R.string.app_usage_desc),
            iconRes = R.drawable.ic_apps,
            showChevron = false
        ) {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }.apply {
            setTitleBold(false)
            setToggle(visible = true, checked = false)
            setContentPadding(0, (10 * dp).toInt(), (8 * dp).toInt(), (10 * dp).toInt())
        }

        locationRow = NexusNavRow(
            requireContext(),
            title = getString(R.string.location_access_title),
            subtitle = getString(R.string.location_access_desc),
            iconRes = R.drawable.ic_explore,
            showChevron = false
        ) {
            if (!NotificationPermissionStatus.hasLocationPermission(requireContext())) {
                locationPermissionLauncher.launch(
                    arrayOf(
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            } else {
                openAppDetailsSettings()
            }
        }.apply {
            setTitleBold(false)
            setToggle(visible = true, checked = false)
            setContentPadding(0, (10 * dp).toInt(), (8 * dp).toInt(), (10 * dp).toInt())
        }

        val permissionsContainer = SettingsSectionGroupView(requireContext()).apply {
            visibility = View.VISIBLE
            addChildRow(notificationAccessRow)
            addChildRow(storageAccessRow)
            addChildRow(accessibilityRow)
            addChildRow(appUsageRow)
            addChildRow(locationRow)
        }

        permissionsHeader = NexusNavRow(
            requireContext(),
            title = getString(R.string.system_permissions_title),
            subtitle = getString(R.string.permissions_granted_format, 0, 5),
            iconRes = R.drawable.ic_admin,
            showChevron = true
        ) {
            isPermissionsExpanded = !isPermissionsExpanded
            permissionsContainer.visibility = if (isPermissionsExpanded) View.VISIBLE else View.GONE
            permissionsHeader.setChevronRotation(if (isPermissionsExpanded) 180f else 0f)
        }.apply {
            setContentPadding(0, (12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt())
            setChevronRotation(180f, animate = false)
        }

        mainSection.addRow(permissionsHeader)
        mainSection.addRow(permissionsContainer)

        content.addView(mainSection)
        scrollView.addView(content)

        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            content.setPadding(padH, (16 * dp).toInt(), padH, systemBars.bottom + (80 * dp).toInt())
            insets
        }

        return scrollView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.ensureDraftReady()
            bindDraft()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.bindEpoch.collect { bindDraft() } }
                launch {
                    viewModel.currentNotificationBadgeColor.collect { hex ->
                        badgesSection.badgeColorPicker.setSelectedColor(hex)
                        badgesSection.updateBadgeColorSubtitle(hex)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStatus()
    }

    private fun openAppDetailsSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${requireContext().packageName}")
        }
        startActivity(intent)
    }

    private fun bindDraft() {
        val s = viewModel.draft.value ?: return
        ignoreCallbacks = true
        historySection.bind(s)
        badgesSection.segmentedAppBadges.setSelectedValue(s.badgeStyleApp.toString())
        badgesSection.segmentedFolderBadges.setSelectedValue(s.badgeStyleFolder.toString())
        updateBadgeHeaderSubtitle()
        ignoreCallbacks = false
    }

    private fun updateBadgeHeaderSubtitle() {
        val appStyle = viewModel.draft.value?.badgeStyleApp ?: 0
        val folderStyle = viewModel.draft.value?.badgeStyleFolder ?: 0
        badgesSection.badgesHeader.setSubtitle(badgesSection.formatBadgeSubtitle(appStyle, folderStyle))
    }

    private fun updatePermissionStatus() {
        val ctx = context ?: return
        val notifGranted = NotificationPermissionStatus.hasNotificationAccess(ctx)
        val storageGranted = NotificationPermissionStatus.hasStoragePermission(ctx)
        val accessGranted = NotificationPermissionStatus.isAccessibilityEnabled()
        val appUsageGranted = NotificationPermissionStatus.hasAppUsagePermission(ctx)
        val locationGranted = NotificationPermissionStatus.hasLocationPermission(ctx)

        notificationAccessRow.setToggleChecked(notifGranted)
        storageAccessRow.setToggleChecked(storageGranted)
        accessibilityRow.setToggleChecked(accessGranted)
        appUsageRow.setToggleChecked(appUsageGranted)
        locationRow.setToggleChecked(locationGranted)

        val enabledLabel = ctx.getString(R.string.permission_status_enabled)
        val notifStatus = NotificationPermissionStatus.getStatusLabel(ctx, notifGranted)
        val storageStatus = NotificationPermissionStatus.getStatusLabel(ctx, storageGranted)
        val accessStatus = NotificationPermissionStatus.getStatusLabel(ctx, accessGranted, enabledLabel)
        val usageStatus = NotificationPermissionStatus.getStatusLabel(ctx, appUsageGranted)
        val locationStatus = NotificationPermissionStatus.getStatusLabel(ctx, locationGranted)

        notificationAccessRow.setSubtitle("${getString(R.string.notification_access_desc)} • $notifStatus")
        storageAccessRow.setSubtitle("${getString(R.string.storage_access_desc)} • $storageStatus")
        accessibilityRow.setSubtitle("${getString(R.string.accessibility_service_desc)} • $accessStatus")
        appUsageRow.setSubtitle("${getString(R.string.app_usage_desc)} • $usageStatus")
        locationRow.setSubtitle("${getString(R.string.location_access_desc)} • $locationStatus")

        val total = 5
        val count = NotificationPermissionStatus.getGrantedCount(ctx)
        permissionsHeader.setSubtitle(getString(R.string.permissions_granted_format, count, total))
    }
}
