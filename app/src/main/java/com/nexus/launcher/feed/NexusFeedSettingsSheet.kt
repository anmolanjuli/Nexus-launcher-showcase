package com.nexus.launcher.feed

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.NexusDesignSystem
import com.nexus.launcher.ui.folder.FolderBlurCoordinator
import com.nexus.launcher.ui.settings.views.NexusNavRow
import com.nexus.launcher.ui.settings.views.NexusSegmentedRow
import com.nexus.launcher.ui.settings.views.SettingsSectionGroupView

/** Chrome + section layout matches the real Settings screens exactly: pillow-grouped
 *  [SettingsSectionGroupView] cards under a tracked section label, [NexusNavRow] for
 *  navigation — not a hand-rolled imitation. */
class NexusFeedSettingsSheet : BottomSheetDialogFragment() {

    private val dp get() = resources.displayMetrics.density

    override fun onStart() {
        super.onStart()
        context?.let { FolderBlurCoordinator.setWorkspaceBlur(it, true) }
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        context?.let { FolderBlurCoordinator.setWorkspaceBlur(it, false) }
    }

    @Suppress("DEPRECATION")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext()).apply {
            window?.let { win ->
                win.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
                WindowCompat.setDecorFitsSystemWindows(win, false)
                win.statusBarColor = Color.TRANSPARENT
                win.navigationBarColor = Color.TRANSPARENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    win.isNavigationBarContrastEnforced = false
                    win.isStatusBarContrastEnforced = false
                }
                win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                win.setDimAmount(0.72f)
                // Blurs only in Frosted Glass; the dim above carries the separation otherwise.
                com.nexus.launcher.ui.glass.FloatingSurfaces.applyBlurBehind(win)
            }
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
            setOnShowListener { d ->
                val bottomSheet = (d as BottomSheetDialog).findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                bottomSheet?.setBackgroundColor(Color.TRANSPARENT)
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val tokens: NexusColorTokens = try {
            ThemeObserver.currentTokens(requireContext())
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }

        val root = FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            // Was an opaque `surface` slab even under Frosted Glass, over a blur it could not show.
            com.nexus.launcher.ui.glass.FloatingSurfaces.bindSheetCard(this, tokens, 24 * dp, dp)
            setPadding((16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt())
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        card.addView(View(requireContext()).apply {
            background = GradientDrawable().apply { setColor(tokens.divider); cornerRadius = 2 * dp }
            layoutParams = LinearLayout.LayoutParams((36 * dp).toInt(), (4 * dp).toInt()).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = (14 * dp).toInt()
            }
        })

        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (4 * dp).toInt())
        }
        val title = TextView(requireContext()).apply {
            text = getString(R.string.nexus_feed_settings_title)
            NexusTypeScale.title.bindTo(this, tokens.textPrimary)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val closeBtn = ImageView(requireContext()).apply {
            setImageResource(R.drawable.ic_close)
            imageTintList = android.content.res.ColorStateList.valueOf(tokens.textSecondary)
            val pad = (6 * dp).toInt()
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams((32 * dp).toInt(), (32 * dp).toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(tokens.surfaceRaised)
            }
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                dismiss()
            }
        }
        header.addView(title)
        header.addView(closeBtn)
        card.addView(header)

        card.addView(NexusDesignSystem.buildTrackedSectionLabel(requireContext(), getString(R.string.nexus_feed_eink_reading_section)))
        val readingGroup = SettingsSectionGroupView(requireContext()).apply {
            paperMode = NexusFeedEInkCoordinator.isEInkMode(requireContext())
            addView(
                NexusFeedEInkMenuRow.build(
                    context = requireContext(),
                    tokens = tokens,
                    dp = dp,
                    onModeChanged = {}
                )
            )
        }
        card.addView(readingGroup)

        card.addView(NexusDesignSystem.buildTrackedSectionLabel(requireContext(), getString(R.string.nexus_feed_section_label)))

        val sourcesGroup = SettingsSectionGroupView(requireContext()).apply {
            paperMode = NexusFeedEInkCoordinator.isEInkMode(requireContext())
        }
        sourcesGroup.addChildRow(
            NexusNavRow(
                context = requireContext(),
                title = getString(R.string.nexus_feed_manage_sources),
                subtitle = getString(R.string.nexus_feed_manage_sources_desc),
                iconRes = R.drawable.ic_feed_rss,
                showChevron = true
            ) {
                NexusFeedManageSourcesSheet().show(parentFragmentManager, NexusFeedManageSourcesSheet.TAG)
            }
        )
        card.addView(sourcesGroup)

        root.addView(card)

        // Card outer margin (not inner padding) clears the gesture nav bar so the rounded
        // corners stay visible above it — the proven fix, reused exactly.
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            (card.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                lp.bottomMargin = navBars.bottom + (8 * dp).toInt()
                card.layoutParams = lp
            }
            insets
        }

        return root
    }

    companion object {
        const val TAG = "NexusFeedSettingsSheet"
        const val PREFS_NAME = "nexus_feed_prefs"
        const val KEY_REFRESH_INTERVAL = "feed_refresh_interval"
        const val DEFAULT_REFRESH_INTERVAL = "1h"

        fun getRefreshIntervalMs(context: Context): Long {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return when (prefs.getString(KEY_REFRESH_INTERVAL, DEFAULT_REFRESH_INTERVAL)) {
                "15m" -> 15 * 60 * 1000L
                "30m" -> 30 * 60 * 1000L
                "1h" -> 60 * 60 * 1000L
                "6h" -> 6 * 3600 * 1000L
                "12h" -> 12 * 3600 * 1000L
                "24h" -> 24 * 3600 * 1000L
                "off" -> Long.MAX_VALUE
                else -> 60 * 60 * 1000L
            }
        }
    }
}
