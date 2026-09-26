package com.nexus.launcher.ui.settings

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.typography.TypefaceWeightMapper

data class SelectionOption<T>(
    val key: T,
    val title: String,
    val subtitle: String? = null,
    @DrawableRes val iconRes: Int? = null,
    val typeface: Typeface? = null,
    val badge: String? = null
)

/**
 * Linear-inspired selection sheet with frosted glass chrome and auto-dismiss on tap.
 */
class NexusSelectionSheet<T>(
    private val sheetTitle: String,
    private val sheetSubtitle: String? = null,
    private val options: List<SelectionOption<T>>,
    private val selectedKey: T,
    private val onSelected: (T) -> Unit
) : BottomSheetDialogFragment() {

    companion object {
        private const val TAG = "NexusSelectionDiag"
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dp = resources.displayMetrics.density
        Log.d(TAG, "[onCreateDialog] Creating BottomSheetDialog. Activity=${activity?.javaClass?.simpleName}")
        return BottomSheetDialog(requireContext()).also { com.nexus.launcher.ui.LandscapeSheets.apply(it) }.apply {
            window?.let { win ->
                Log.d(TAG, "[onCreateDialog] Dialog Window found: $win (hash=${System.identityHashCode(win)})")
                try {
                    WindowCompat.setDecorFitsSystemWindows(win, false)
                    Log.d(TAG, "[onCreateDialog] setDecorFitsSystemWindows(win, false) succeeded")
                } catch (e: Exception) {
                    Log.e(TAG, "[onCreateDialog] setDecorFitsSystemWindows failed", e)
                }

                win.statusBarColor = Color.TRANSPARENT
                win.navigationBarColor = Color.TRANSPARENT

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        Log.d(TAG, "[onCreateDialog] isNavigationBarContrastEnforced before: ${win.isNavigationBarContrastEnforced}")
                        win.isNavigationBarContrastEnforced = false
                        win.isStatusBarContrastEnforced = false
                        Log.d(TAG, "[onCreateDialog] isNavigationBarContrastEnforced after: ${win.isNavigationBarContrastEnforced}")
                    } catch (e: Exception) {
                        Log.e(TAG, "[onCreateDialog] setContrastEnforced failed", e)
                    }
                }

                win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                // Blur-behind now gated on the global UI Style (Default/Neumorphism = no blur)
                // via the shared helper, instead of unconditionally blurring regardless of it —
                // the dim stays a real, always-on 0.72 (this is a modal tap-outside-to-dismiss
                // list picker, same as NexusFeedHeaderMenuDialog/DrawerOverflowMenuDialog).
                com.nexus.launcher.ui.glass.FrostedGlassEngine.applyDialogWindowChrome(win)
                win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                win.setDimAmount(0.72f)
            } ?: Log.e(TAG, "[onCreateDialog] Dialog window is NULL!")

            setOnShowListener { dialog ->
                val d = dialog as BottomSheetDialog
                Log.d(TAG, "[onShow] BottomSheetDialog onShow triggered. Window=${d.window} (hash=${System.identityHashCode(d.window)})")
                val touchOutside = d.findViewById<View>(com.google.android.material.R.id.touch_outside)
                val coordinator = d.findViewById<View>(com.google.android.material.R.id.coordinator)
                val bottomSheet = d.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)

                Log.d(TAG, "[onShow] Views: touchOutside=$touchOutside, coordinator=$coordinator, bottomSheet=$bottomSheet")
                touchOutside?.setBackgroundColor(Color.TRANSPARENT)
                coordinator?.setBackgroundColor(Color.TRANSPARENT)

                if (bottomSheet != null) {
                    bottomSheet.backgroundTintList = null
                    bottomSheet.setBackgroundResource(android.R.color.transparent)
                    val behavior = BottomSheetBehavior.from(bottomSheet)
                    behavior.isFitToContents = true
                    behavior.skipCollapsed = true
                    behavior.isDraggable = false
                    behavior.state = BottomSheetBehavior.STATE_EXPANDED
                    Log.d(TAG, "[onShow] BottomSheetBehavior configured: fitToContents=${behavior.isFitToContents}, state=${behavior.state}, peekHeight=${behavior.peekHeight}")
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
        val tokens = try {
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
                setMargins(marginH, 0, marginH, 0)
            }
            background = GradientDrawable().apply {
                // Was flat opaque `tokens.surface` regardless of UI Style — the window blur-
                // behind above was hand-rolled and unconditional, but the card itself never
                // picked up the shared translucent-fill rule every other converted dialog card
                // uses, so it never actually looked frosted.
                val frostedTokens = com.nexus.launcher.ui.glass.FrostedGlassEngine.resolveFrostedTokens(tokens)
                val fillAlpha = (com.nexus.launcher.ui.glass.FrostedGlassEngine.sheetFillAlpha(1f, 0.70f) * 255f).toInt()
                setColor((frostedTokens.surface and 0x00FFFFFF) or (fillAlpha shl 24))
                cornerRadius = 24f * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), frostedTokens.border)
            }
        }

        // Handle pill
        val handle = View(requireContext()).apply {
            val handleColor = (tokens.textSecondary and 0x00FFFFFF) or (0x4D shl 24)
            background = GradientDrawable().apply {
                setColor(handleColor)
                cornerRadius = 2f * dp
            }
            layoutParams = LinearLayout.LayoutParams((36 * dp).toInt(), (4 * dp).toInt()).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = (12 * dp).toInt()
                bottomMargin = (12 * dp).toInt()
            }
        }
        card.addView(handle)

        // Title + Subtitle Header
        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((24 * dp).toInt(), 0, (24 * dp).toInt(), (12 * dp).toInt())
        }
        val titleView = TextView(requireContext()).apply {
            text = sheetTitle
            textSize = 18f
        }
        NexusTypeScale.hubRowTitle.bindTo(titleView, tokens.textPrimary)
        header.addView(titleView)

        if (!sheetSubtitle.isNullOrBlank()) {
            val subtitleView = TextView(requireContext()).apply {
                text = sheetSubtitle
                textSize = 12.5f
                setPadding(0, (2 * dp).toInt(), 0, 0)
            }
            NexusTypeScale.iconLabel.bindTo(subtitleView, tokens.textSecondary)
            header.addView(subtitleView)
        }
        card.addView(header)

        // Options List in ScrollView
        val scrollView = NestedScrollView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (380 * dp).toInt()
            )
            isNestedScrollingEnabled = true
        }

        val optionsContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, (16 * dp).toInt())
        }

        val outValue = TypedValue()
        requireContext().theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)

        options.forEach { option ->
            val isSelected = option.key == selectedKey
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = (54 * dp).toInt()
                setPadding((24 * dp).toInt(), (10 * dp).toInt(), (24 * dp).toInt(), (10 * dp).toInt())
                foreground = ContextCompat.getDrawable(context, outValue.resourceId)
                isClickable = true
                isFocusable = true

                if (isSelected) {
                    background = GradientDrawable().apply {
                        setColor(tokens.surfaceRaised)
                        cornerRadius = 12f * dp
                    }
                }

                setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    onSelected(option.key)
                    dismiss()
                }
            }

            if (option.iconRes != null && option.iconRes != 0) {
                val iconView = ImageView(requireContext()).apply {
                    setImageResource(option.iconRes)
                    imageTintList = ColorStateList.valueOf(tokens.textPrimary)
                    layoutParams = LinearLayout.LayoutParams((22 * dp).toInt(), (22 * dp).toInt()).apply {
                        marginEnd = (16 * dp).toInt()
                    }
                }
                row.addView(iconView)
            }

            val textLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = (8 * dp).toInt()
                }
            }

            val rowTitle = TextView(requireContext()).apply {
                text = option.title
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                setTextColor(tokens.textPrimary)
                letterSpacing = 0.01f
                typeface = if (option.typeface != null) {
                    option.typeface
                } else {
                    Typeface.create(Typeface.DEFAULT, TypefaceWeightMapper.BOLD)
                }
            }
            textLayout.addView(rowTitle)

            if (!option.subtitle.isNullOrBlank()) {
                val rowSub = TextView(requireContext()).apply {
                    text = option.subtitle
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTextColor(tokens.textSecondary)
                }
                textLayout.addView(rowSub)
            }
            row.addView(textLayout)

            val checkView = ImageView(requireContext()).apply {
                setImageResource(R.drawable.ic_check)
                imageTintList = ColorStateList.valueOf(tokens.textPrimary)
                layoutParams = LinearLayout.LayoutParams((20 * dp).toInt(), (20 * dp).toInt())
                visibility = if (isSelected) View.VISIBLE else View.INVISIBLE
            }
            row.addView(checkView)

            optionsContainer.addView(row)
        }

        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val gestures = insets.getInsets(WindowInsetsCompat.Type.mandatorySystemGestures())
            val bottomMargin = navBars.bottom + (2 * dp).toInt()
            Log.d(TAG, "[InsetsListener] FIRED on view=${v.javaClass.simpleName}! navBars.bottom=${navBars.bottom}, systemBars.bottom=${systemBars.bottom}, gestures.bottom=${gestures.bottom}. Applying card bottomMargin=$bottomMargin")
            (card.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                lp.bottomMargin = bottomMargin
                card.layoutParams = lp
            }
            insets
        }

        scrollView.addView(optionsContainer)
        card.addView(scrollView)
        root.addView(card)

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val dialogWin = dialog?.window
        val activityWin = activity?.window
        Log.d(TAG, "[onViewCreated] dialog.window=$dialogWin (hash=${System.identityHashCode(dialogWin)}), activity.window=$activityWin (hash=${System.identityHashCode(activityWin)})")

        view.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                view.viewTreeObserver.removeOnGlobalLayoutListener(this)
                val decor = dialogWin?.decorView
                val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                Log.d(TAG, "[GlobalLayout] root: h=${view.height}, top=${view.top}, bottom=${view.bottom}")
                Log.d(TAG, "[GlobalLayout] decorView: h=${decor?.height}, top=${decor?.top}, bottom=${decor?.bottom}")
                Log.d(TAG, "[GlobalLayout] bottomSheet: h=${bottomSheet?.height}, top=${bottomSheet?.top}, bottom=${bottomSheet?.bottom}")
            }
        })
    }
}
