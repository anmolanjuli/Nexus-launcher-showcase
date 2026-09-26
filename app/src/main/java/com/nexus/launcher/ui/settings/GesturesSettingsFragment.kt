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
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.nexus.launcher.R
import com.nexus.launcher.ui.model.GestureAction
import com.nexus.launcher.ui.settings.views.NexusNavRow
import com.nexus.launcher.ui.settings.views.NexusSection
import com.nexus.launcher.ui.settings.views.SettingsSectionGroupView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

private val TWO_FINGER = com.nexus.launcher.premium.PremiumFeature.ADVANCED_GESTURES

@AndroidEntryPoint
class GesturesSettingsFragment : Fragment() {

    private val viewModel: SettingsViewModel by activityViewModels()

    private lateinit var rowSwipeUp: NexusNavRow
    private lateinit var rowSwipeDown: NexusNavRow
    private lateinit var rowTwoFingerSwipeUp: NexusNavRow
    private lateinit var rowTwoFingerSwipeDown: NexusNavRow

    private var currentGlobalSwipeUp: String = "OPEN_APP_DRAWER"
    private var currentGlobalSwipeDown: String = "OPEN_SEARCH"
    private var currentGlobalTwoFingerSwipeUp: String = "NONE"
    private var currentGlobalTwoFingerSwipeDown: String = "NONE"

    // Two-finger swipes are Premium; one-finger swipes stay free. Clearing a set one is always free.
    private fun mayOpenTwoFinger(current: GestureAction): Boolean =
        current != GestureAction.NONE || com.nexus.launcher.premium.PremiumGate.allow(requireContext(), TWO_FINGER)

    private fun mayApplyTwoFinger(selected: String): Boolean =
        selected.substringBefore("::") == GestureAction.NONE.name || com.nexus.launcher.premium.PremiumGate.allow(requireContext(), TWO_FINGER)

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

        val childStartPad = (20 * dp).toInt()

        // 1. One Finger Swipe (Header & Children)
        val headerOneFinger = NexusNavRow(
            requireContext(),
            title = getString(R.string.gestures_one_finger_title),
            subtitle = getString(R.string.gestures_one_finger_desc),
            iconRes = R.drawable.ic_one,
            showChevron = false
        ).apply {
            setContentPadding(0, (12 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt())
        }

        rowSwipeUp = NexusNavRow(
            requireContext(),
            title = getString(R.string.gesture_swipe_up),
            subtitle = getActionDisplayName(currentGlobalSwipeUp),
            iconRes = R.drawable.ic_swipe_up,
            showChevron = true
        ) {
            val action = try { GestureAction.valueOf(currentGlobalSwipeUp) } catch (_: Exception) { GestureAction.OPEN_APP_DRAWER }
            GestureActionPickerSheet(getString(R.string.gesture_swipe_up), 0, action) { selected ->
                currentGlobalSwipeUp = selected
                viewModel.patchDraft { s -> s.copy(globalSwipeUp = selected) }
                rowSwipeUp.setSubtitle(getActionDisplayName(selected))
            }.show(parentFragmentManager, "global_gesture_picker")
        }.apply {
            setContentPadding((14 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt())
        }

        rowSwipeDown = NexusNavRow(
            requireContext(),
            title = getString(R.string.gesture_swipe_down),
            subtitle = getActionDisplayName(currentGlobalSwipeDown),
            iconRes = R.drawable.ic_swipe_down,
            showChevron = true
        ) {
            val action = try { GestureAction.valueOf(currentGlobalSwipeDown) } catch (_: Exception) { GestureAction.OPEN_SEARCH }
            GestureActionPickerSheet(getString(R.string.gesture_swipe_down), 0, action) { selected ->
                currentGlobalSwipeDown = selected
                viewModel.patchDraft { s -> s.copy(globalSwipeDown = selected) }
                rowSwipeDown.setSubtitle(getActionDisplayName(selected))
            }.show(parentFragmentManager, "global_gesture_picker")
        }.apply {
            setContentPadding((14 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt())
        }

        val oneFingerChildren = SettingsSectionGroupView(requireContext()).apply {
            addChildRow(rowSwipeUp)
            addChildRow(rowSwipeDown)
        }

        val oneFingerContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(headerOneFinger)
            addView(oneFingerChildren)
        }
        mainSection.addRow(oneFingerContainer)

        // 2. Two Finger Swipe (Header & Children)
        val headerTwoFinger = NexusNavRow(
            requireContext(),
            title = getString(R.string.gestures_two_finger_title),
            subtitle = getString(R.string.gestures_two_finger_desc),
            iconRes = R.drawable.ic_two,
            showChevron = false
        ).apply {
            setContentPadding(0, (12 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt())
            val row = this
            com.nexus.launcher.ui.premium.PremiumBadges.bind(row) {
                row.setBadge(
                    if (com.nexus.launcher.ui.premium.PremiumBadges.isShown(TWO_FINGER)) row.context.getString(R.string.premium_badge) else null
                )
            }
        }

        rowTwoFingerSwipeUp = NexusNavRow(
            requireContext(),
            title = getString(R.string.gesture_two_finger_swipe_up),
            subtitle = getActionDisplayName(currentGlobalTwoFingerSwipeUp),
            iconRes = R.drawable.ic_twofingersup,
            showChevron = true
        ) {
            val action = try { GestureAction.valueOf(currentGlobalTwoFingerSwipeUp) } catch (_: Exception) { GestureAction.NONE }
            if (!mayOpenTwoFinger(action)) return@NexusNavRow
            GestureActionPickerSheet(getString(R.string.gesture_two_finger_swipe_up), 0, action) { selected ->
                if (mayApplyTwoFinger(selected)) {
                    currentGlobalTwoFingerSwipeUp = selected
                    viewModel.patchDraft { s -> s.copy(globalTwoFingerSwipeUp = selected) }
                    rowTwoFingerSwipeUp.setSubtitle(getActionDisplayName(selected))
                }
            }.show(parentFragmentManager, "global_gesture_picker")
        }.apply {
            setContentPadding((14 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt())
        }

        rowTwoFingerSwipeDown = NexusNavRow(
            requireContext(),
            title = getString(R.string.gesture_two_finger_swipe_down),
            subtitle = getActionDisplayName(currentGlobalTwoFingerSwipeDown),
            iconRes = R.drawable.ic_twofingersdown,
            showChevron = true
        ) {
            val action = try { GestureAction.valueOf(currentGlobalTwoFingerSwipeDown) } catch (_: Exception) { GestureAction.NONE }
            if (!mayOpenTwoFinger(action)) return@NexusNavRow
            GestureActionPickerSheet(getString(R.string.gesture_two_finger_swipe_down), 0, action) { selected ->
                if (mayApplyTwoFinger(selected)) {
                    currentGlobalTwoFingerSwipeDown = selected
                    viewModel.patchDraft { s -> s.copy(globalTwoFingerSwipeDown = selected) }
                    rowTwoFingerSwipeDown.setSubtitle(getActionDisplayName(selected))
                }
            }.show(parentFragmentManager, "global_gesture_picker")
        }.apply {
            setContentPadding((14 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt())
        }

        val twoFingerChildren = SettingsSectionGroupView(requireContext()).apply {
            addChildRow(rowTwoFingerSwipeUp)
            addChildRow(rowTwoFingerSwipeDown)
        }

        val twoFingerContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(headerTwoFinger)
            addView(twoFingerChildren)
        }
        mainSection.addRow(twoFingerContainer)

        content.addView(mainSection)
        scrollView.addView(content)

        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { v, insets ->
            val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(0, top, 0, (16 * dp).toInt())
            insets
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.ensureDraftReady()
            bindDraft()
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.bindEpoch.collect {
                bindDraft()
            }
        }

        return scrollView
    }

    private fun bindDraft() {
        val s = viewModel.draft.value ?: return
        currentGlobalSwipeUp = s.globalSwipeUp
        currentGlobalSwipeDown = s.globalSwipeDown
        currentGlobalTwoFingerSwipeUp = s.globalTwoFingerSwipeUp
        currentGlobalTwoFingerSwipeDown = s.globalTwoFingerSwipeDown

        rowSwipeUp.setSubtitle(getActionDisplayName(currentGlobalSwipeUp))
        rowSwipeDown.setSubtitle(getActionDisplayName(currentGlobalSwipeDown))
        rowTwoFingerSwipeUp.setSubtitle(getActionDisplayName(currentGlobalTwoFingerSwipeUp))
        rowTwoFingerSwipeDown.setSubtitle(getActionDisplayName(currentGlobalTwoFingerSwipeDown))
    }

    private fun getActionDisplayName(actionKey: String): String {
        val ctx = context ?: return "None"
        return try {
            val action = GestureAction.valueOf(actionKey)
            action.getDisplayName(ctx)
        } catch (_: Exception) {
            GestureAction.NONE.getDisplayName(ctx)
        }
    }
}
