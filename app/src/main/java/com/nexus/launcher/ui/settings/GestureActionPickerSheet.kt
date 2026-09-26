package com.nexus.launcher.ui.settings

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
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
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.WallpaperSheetComponents
import com.nexus.launcher.ui.model.GestureAction

class GestureActionPickerSheet(
    private val title: String,
    private val itemType: Int,
    private val currentAction: GestureAction,
    private val onActionSelected: (String) -> Unit
) : BottomSheetDialogFragment() {

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
                win.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
                androidx.core.view.WindowCompat.setDecorFitsSystemWindows(win, false)
                // Was its own hand-rolled blur-behind + a flat 0.6 window dim, unconverted to the
                // shared frosted-glass component (this sheet predates it) — the card itself was
                // also fully opaque, so this never read as "frosted" at all, just a plain sheet
                // with an unnecessary dim behind it. Matches every other converted sheet now.
                com.nexus.launcher.ui.glass.FrostedGlassEngine.applyDialogWindowChrome(win)
                win.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                win.setDimAmount(0f)
            }
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
            behavior.isDraggable = false
            setOnShowListener { dialog ->
                val d = dialog as BottomSheetDialog
                val bottomSheet = d.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                bottomSheet?.setBackgroundColor(Color.TRANSPARENT)
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

        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            background = GradientDrawable().apply {
                val frostedTokens = com.nexus.launcher.ui.glass.FrostedGlassEngine.resolveFrostedTokens(tokens)
                val fillAlpha = (com.nexus.launcher.ui.glass.FrostedGlassEngine.sheetFillAlpha(1f, 0.70f) * 255f).toInt()
                setColor((frostedTokens.surface and 0x00FFFFFF) or (fillAlpha shl 24))
                cornerRadius = 24f * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), frostedTokens.border)
            }
        }

        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((20 * dp).toInt(), (18 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())

            val titleView = TextView(requireContext()).apply {
                text = title
                gravity = Gravity.START
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            NexusTypeScale.title.bindTo(titleView, tokens.textPrimary)
            addView(titleView)

            addView(WallpaperSheetComponents.buildCloseButton(requireContext(), dp) { dismiss() })
        }
        card.addView(header)

        val scrollView = ScrollView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            isNestedScrollingEnabled = true
        }

        val listLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((12 * dp).toInt(), (4 * dp).toInt(), (12 * dp).toInt(), (20 * dp).toInt())
        }

        val groups = listOf(
            requireContext().getString(com.nexus.launcher.R.string.gesture_group_navigation) to listOf(GestureAction.NEXT_PAGE, GestureAction.PREV_PAGE, GestureAction.OPEN_HOME_EDIT),
            requireContext().getString(com.nexus.launcher.R.string.gesture_group_system) to listOf(GestureAction.EXPAND_NOTIFICATIONS, GestureAction.OPEN_QUICK_SETTINGS, GestureAction.SHOW_RECENTS, GestureAction.TAKE_SCREENSHOT, GestureAction.SHOW_POWER_MENU, GestureAction.SPLIT_SCREEN, GestureAction.LOCK_SCREEN, GestureAction.TOGGLE_FLASHLIGHT),
            requireContext().getString(com.nexus.launcher.R.string.gesture_group_launcher) to listOf(GestureAction.OPEN_APP_DRAWER, GestureAction.OPEN_SEARCH, GestureAction.TOGGLE_LABELS, GestureAction.NONE, GestureAction.OPEN_APP_INFO),
            requireContext().getString(com.nexus.launcher.R.string.gesture_group_shortcuts) to listOf(GestureAction.OPEN_SPECIFIC_APP, GestureAction.OPEN_SPECIFIC_FOLDER, GestureAction.OPEN_SPECIFIC_PAGE, GestureAction.OPEN_SPECIFIC_SHORTCUT)
        )

        val neumorphic = com.nexus.launcher.ui.glass.NeumorphicSurfaces.isActive
        for ((groupName, actions) in groups) {
            val validActions = actions.filter { action ->
                if (itemType == 1) action.appliesToFolder else action.appliesToApp
            }
            if (validActions.isEmpty()) continue

            listLayout.addView(TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = (8 * dp).toInt()
                    marginEnd = (8 * dp).toInt()
                    topMargin = (16 * dp).toInt()
                    bottomMargin = (6 * dp).toInt()
                }
                text = groupName
                NexusTypeScale.labelSmall.bindTo(this, tokens.textSecondary)
                gravity = Gravity.START
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                isAllCaps = true
            })

            val gridLayout = android.widget.GridLayout(requireContext()).apply {
                columnCount = 2
            }

            for (action in validActions) {
                val isSelected = action == currentAction
                val btnLayout = LinearLayout(requireContext()).apply {
                    layoutParams = android.widget.GridLayout.LayoutParams().apply {
                        width = 0
                        height = ViewGroup.LayoutParams.WRAP_CONTENT
                        columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                        setMargins((4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt(), (4 * dp).toInt())
                    }
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    val pad = (14 * dp).toInt()
                    val vPad = (12 * dp).toInt()
                    setPadding(pad, vPad, pad, vPad)

                    // Neumorphism: every action a raised tile, the current one pressed in.
                    // Elsewhere the current one is an inverted (ink) tile.
                    background = when {
                        !neumorphic -> GradientDrawable().apply {
                            cornerRadius = 14f * dp
                            if (isSelected) {
                                setColor(tokens.textPrimary)
                            } else {
                                setColor(tokens.surfaceRaised)
                                setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
                            }
                        }
                        isSelected -> com.nexus.launcher.ui.glass.NeumorphicSurfaces.field(this, tokens, 14f * dp)
                        else -> com.nexus.launcher.ui.glass.NeumorphicSurfaces.card(this, tokens, 14f * dp)
                    }

                    setOnClickListener {
                        it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        when (action) {
                            GestureAction.OPEN_SPECIFIC_APP -> {
                                com.nexus.launcher.ui.picker.GestureTargetAppPickerOverlay.show(requireContext()) { targetStr ->
                                    onActionSelected("${action.name}::$targetStr")
                                }
                                dismiss()
                            }
                            GestureAction.OPEN_SPECIFIC_PAGE -> {
                                GestureTargetPagePickerSheet { targetStr ->
                                    onActionSelected("${action.name}::$targetStr")
                                }.show(parentFragmentManager, "gesture_target_page")
                                dismiss()
                            }
                            GestureAction.OPEN_SPECIFIC_FOLDER -> {
                                GestureTargetFolderPickerSheet { targetStr ->
                                    onActionSelected("${action.name}::$targetStr")
                                }.show(parentFragmentManager, "gesture_target_folder")
                                dismiss()
                            }
                            GestureAction.OPEN_SPECIFIC_SHORTCUT -> {
                                com.nexus.launcher.ui.picker.ShortcutPickerOverlay.show(requireContext()) { shortcut ->
                                    val targetStr = "${shortcut.`package`}::${shortcut.id}"
                                    onActionSelected("${action.name}::$targetStr")
                                }
                                dismiss()
                            }
                            else -> {
                                onActionSelected(action.name)
                                dismiss()
                            }
                        }
                    }
                }

                val iconResId = when (action) {
                    GestureAction.NONE -> com.nexus.launcher.R.drawable.ic_remove
                    GestureAction.OPEN_APP_INFO -> com.nexus.launcher.R.drawable.ic_info
                    GestureAction.TOGGLE_FLASHLIGHT -> com.nexus.launcher.R.drawable.ic_flashlight
                    GestureAction.OPEN_APP_DRAWER -> com.nexus.launcher.R.drawable.ic_apps
                    GestureAction.OPEN_SPECIFIC_APP -> com.nexus.launcher.R.drawable.ic_category
                    GestureAction.OPEN_SPECIFIC_PAGE -> com.nexus.launcher.R.drawable.ic_page
                    GestureAction.OPEN_SPECIFIC_FOLDER -> com.nexus.launcher.R.drawable.ic_folder_solid
                    GestureAction.OPEN_SPECIFIC_SHORTCUT -> com.nexus.launcher.R.drawable.ic_menu_shortcut
                    GestureAction.LOCK_SCREEN -> com.nexus.launcher.R.drawable.ic_wallpaper_lock
                    GestureAction.EXPAND_NOTIFICATIONS -> com.nexus.launcher.R.drawable.ic_bell
                    GestureAction.OPEN_QUICK_SETTINGS -> com.nexus.launcher.R.drawable.ic_settings
                    GestureAction.SHOW_RECENTS -> com.nexus.launcher.R.drawable.ic_menu
                    GestureAction.TAKE_SCREENSHOT -> com.nexus.launcher.R.drawable.ic_camera
                    GestureAction.SHOW_POWER_MENU -> com.nexus.launcher.R.drawable.ic_close
                    GestureAction.OPEN_SEARCH -> com.nexus.launcher.R.drawable.ic_search
                    GestureAction.NEXT_PAGE -> com.nexus.launcher.R.drawable.ic_arrow_forward
                    GestureAction.PREV_PAGE -> com.nexus.launcher.R.drawable.ic_back_arrow
                    GestureAction.TOGGLE_LABELS -> com.nexus.launcher.R.drawable.ic_visibility_off
                    GestureAction.OPEN_HOME_EDIT -> com.nexus.launcher.R.drawable.ic_edit
                    GestureAction.SPLIT_SCREEN -> com.nexus.launcher.R.drawable.ic_grid
                }

                val inverted = isSelected && !neumorphic
                val iconTint = if (inverted) tokens.bg else if (isSelected) tokens.textPrimary else tokens.textSecondary
                val textColor = if (inverted) tokens.bg else tokens.textPrimary

                btnLayout.addView(ImageView(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams((22 * dp).toInt(), (22 * dp).toInt()).apply {
                        marginEnd = (10 * dp).toInt()
                    }
                    setImageResource(iconResId)
                    setColorFilter(iconTint)
                })

                val textContainer = LinearLayout(requireContext()).apply {
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_VERTICAL

                    addView(TextView(requireContext()).apply {
                        text = action.getDisplayName(requireContext())
                        NexusTypeScale.body.bindTo(this, textColor)
                        gravity = Gravity.START
                        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                    })

                    if (action == GestureAction.LOCK_SCREEN) {
                        addView(TextView(requireContext()).apply {
                            text = getString(com.nexus.launcher.R.string.gesture_requires_accessibility)
                            NexusTypeScale.caption.bindTo(this, if (inverted) tokens.bg else tokens.textSecondary)
                            setTypeface(null, android.graphics.Typeface.ITALIC)
                            gravity = Gravity.START
                            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                        })
                    }
                }
                btnLayout.addView(textContainer)
                gridLayout.addView(btnLayout)
            }
            listLayout.addView(gridLayout)
        }

        scrollView.addView(listLayout)
        card.addView(scrollView)
        root.addView(card)

        return root
    }
}
