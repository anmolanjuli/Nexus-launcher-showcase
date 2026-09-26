package com.nexus.launcher.ui.settings

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
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
import com.nexus.launcher.ui.icons.IconPackInfo

class IconPackSelectorSheet(
    private val packs: List<IconPackInfo>,
    private val onPackSelected: (String) -> Unit
) : BottomSheetDialogFragment() {

    private lateinit var currentTokens: NexusColorTokens

    override fun onStart() {
        super.onStart()
        context?.let { com.nexus.launcher.ui.folder.FolderBlurCoordinator.setWorkspaceBlur(it, true) }
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        context?.let { com.nexus.launcher.ui.folder.FolderBlurCoordinator.setWorkspaceBlur(it, false) }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dp = resources.displayMetrics.density
        return BottomSheetDialog(requireContext()).also { com.nexus.launcher.ui.LandscapeSheets.apply(it) }.apply {
            window?.let { win ->
                WindowCompat.setDecorFitsSystemWindows(win, false)
                win.statusBarColor = Color.TRANSPARENT
                win.navigationBarColor = Color.TRANSPARENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    win.isNavigationBarContrastEnforced = false
                    win.isStatusBarContrastEnforced = false
                }
                win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                // Was its own hand-rolled FLAG_DIM_BEHIND(0.72) + FLAG_BLUR_BEHIND(25dp), never
                // converted to the shared frosted-glass component.
                com.nexus.launcher.ui.glass.FrostedGlassEngine.applyDialogWindowChrome(win)
                win.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                win.setDimAmount(0f)
            }
            setOnShowListener { dialog ->
                val d = dialog as BottomSheetDialog
                d.findViewById<View>(com.google.android.material.R.id.touch_outside)?.setBackgroundColor(Color.TRANSPARENT)
                d.findViewById<View>(com.google.android.material.R.id.coordinator)?.setBackgroundColor(Color.TRANSPARENT)
                val bottomSheet = d.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
                if (bottomSheet != null) {
                    bottomSheet.backgroundTintList = null
                    bottomSheet.setBackgroundResource(android.R.color.transparent)
                    val behavior = BottomSheetBehavior.from(bottomSheet)
                    behavior.isFitToContents = true
                    behavior.skipCollapsed = true
                    behavior.state = BottomSheetBehavior.STATE_EXPANDED
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val dp = resources.displayMetrics.density
        currentTokens = try {
            ThemeObserver.currentTokens(requireContext())
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }

        val root = FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val marginH = (10 * dp).toInt()
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(marginH, (8 * dp).toInt(), marginH, 0)
            }
            background = GradientDrawable().apply {
                val frostedTokens = com.nexus.launcher.ui.glass.FrostedGlassEngine.resolveFrostedTokens(currentTokens)
                val fillAlpha = (com.nexus.launcher.ui.glass.FrostedGlassEngine.sheetFillAlpha(1f, 0.70f) * 255f).toInt()
                setColor((frostedTokens.surface and 0x00FFFFFF) or (fillAlpha shl 24))
                cornerRadius = 24f * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), frostedTokens.border)
            }
            clipToOutline = true
        }

        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((20 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())

            val titleView = TextView(requireContext()).apply {
                text = getString(com.nexus.launcher.R.string.icon_pack_select_title)
                NexusTypeScale.title.bindTo(this, currentTokens.textPrimary)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            }
            addView(titleView)

            val closeBtn = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams((32 * dp).toInt(), (32 * dp).toInt())
                setImageResource(R.drawable.ic_close)
                imageTintList = ColorStateList.valueOf(currentTokens.textSecondary)
                setPadding((6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt())
                background = GradientDrawable().apply {
                    setColor(currentTokens.surfaceRaised)
                    cornerRadius = 16 * dp
                    setStroke((1 * dp).toInt().coerceAtLeast(1), currentTokens.divider)
                }
                setOnClickListener { dismiss() }
            }
            addView(closeBtn)
        }
        card.addView(header)

        val scrollView = ScrollView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val listLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt())
        }

        for (pack in packs) {
            val btnLayout = buildRow(requireContext(), dp, pack, currentTokens) {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                onPackSelected(pack.packageName)
                dismiss()
            }
            listLayout.addView(btnLayout)
        }

        scrollView.addView(listLayout)
        card.addView(scrollView)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val imeBars = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomMargin = maxOf(navBars.bottom, imeBars.bottom) + (8 * dp).toInt()
            (card.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                lp.bottomMargin = bottomMargin
                card.layoutParams = lp
            }
            insets
        }

        root.addView(card)
        return root
    }

    companion object {
        fun buildRow(
            context: Context,
            dp: Float,
            pack: IconPackInfo,
            tokens: NexusColorTokens,
            onClick: (View) -> Unit
        ): LinearLayout {
            val btnLayout = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, (4 * dp).toInt(), 0, (4 * dp).toInt())
                }
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
                background = GradientDrawable().apply {
                    setColor(tokens.surface)
                    cornerRadius = 14 * dp
                    setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
                }
                isClickable = true
                isFocusable = true
                setOnClickListener { onClick(it) }
            }

            val iconView = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams((36 * dp).toInt(), (36 * dp).toInt()).apply {
                    marginEnd = (16 * dp).toInt()
                }
                val icon = pack.icon
                if (icon != null) {
                    setImageDrawable(icon)
                } else {
                    setImageResource(R.drawable.ic_apps)
                    imageTintList = ColorStateList.valueOf(tokens.textPrimary)
                }
            }
            btnLayout.addView(iconView)

            val textView = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                text = pack.label
                NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            }
            btnLayout.addView(textView)
            return btnLayout
        }
    }
}
