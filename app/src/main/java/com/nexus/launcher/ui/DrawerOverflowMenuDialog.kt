package com.nexus.launcher.ui

import android.app.Activity
import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.ui.glass.FrostedPanelLayout

/**
 * Drawer overflow menu — same anchored-card UI pattern as NexusFeedHeaderMenuDialog: dim+blur
 * behind, a visible copy of the trigger icon on this dialog's own undimmed layer, tap-to-open/
 * tap-again-to-close on that same icon, back button dismisses ([OverflowMenuController] preserves
 * the `isShowing()`/`dismiss()` contract [MainActivityBackPressRouter] already calls). No accent
 * color anywhere — every color here comes from live theme tokens.
 */
class DrawerOverflowMenuDialog(
    activity: Activity,
    private val anchorView: View,
    private val effectiveLayoutMode: String,
    private val currentListColumns: Int,
    private val currentGridColumns: Int,
    private val currentCategoryLayout: String,
    private val onColumnsSelected: (Int) -> Unit,
    private val onGridColumnsSelected: (Int) -> Unit,
    private val onDrawerModeChanged: (String) -> Unit,
    private val onCategoryLayoutChanged: (String) -> Unit,
    private val onHiddenApps: () -> Unit,
    private val onCreateFolder: () -> Unit,
    private val onDrawerSettings: () -> Unit,
    private val positionRowLabel: String?,
    private val positionRowValue: String,
    private val onPositionSelected: (String) -> Unit,
    private val onDismissed: (() -> Unit)? = null
) : Dialog(activity, android.R.style.Theme_Black_NoTitleBar) {

    private val activity: Activity = activity
    private val dp get() = context.resources.displayMetrics.density
    private var restoreActivityBars: (() -> Unit)? = null

    // Live copies of what the menu shows. The menu stays open while the user changes layout, so it
    // has to track the new values itself rather than read the ones it was opened with.
    private var layoutMode = effectiveLayoutMode
    private var listColumns = currentListColumns
    private var gridColumns = currentGridColumns
    private var categoryLayout = currentCategoryLayout
    private lateinit var menuCard: FrostedPanelLayout
    private lateinit var tokens: NexusColorTokens

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tokens = try {
            ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }

        suppressActivityBarScrim()

        window?.let { win ->
            androidx.core.view.WindowCompat.setDecorFitsSystemWindows(win, false)
            win.statusBarColor = Color.TRANSPARENT
            win.navigationBarColor = Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                win.isNavigationBarContrastEnforced = false
                win.isStatusBarContrastEnforced = false
            }
            win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            // See NexusFeedHeaderMenuDialog — Theme_Black_NoTitleBar is opaque by default.
            win.setFormat(PixelFormat.TRANSLUCENT)
            win.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            // Deliberately no FLAG_BLUR_BEHIND: raising and tearing down a cross-process blur
            // around this menu is what made opening and dismissing it feel heavy. A light dim is
            // enough separation, matching DrawerCategoryDropdown.
            win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            win.setDimAmount(0.35f)
            win.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        }
        setOnDismissListener {
            restoreActivityBars?.invoke()
            onDismissed?.invoke()
        }

        val anchorPos = IntArray(2)
        anchorView.getLocationOnScreen(anchorPos)
        val isRtl = ViewCompat.getLayoutDirection(anchorView) == View.LAYOUT_DIRECTION_RTL

        val root = FrameLayout(context).apply {
            setOnClickListener { dismiss() }
        }

        // Match the real anchor's exact size and padding — not a fixed assumed padding added on
        // top of it — so the overlay copy renders at the identical visible glyph size regardless
        // of which trigger opened it (rail: 36dp/no padding; in-bar: 48dp/12dp padding). Using a
        // fixed 7dp pad here while the real in-bar icon has 12dp made the copy render ~2x bigger.
        val anchorSize = anchorView.width.takeIf { it > 0 } ?: (31 * dp).toInt()
        val anchorHeight = anchorView.height.takeIf { it > 0 } ?: anchorSize
        root.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_menu)
            imageTintList = ColorStateList.valueOf(tokens.textPrimary)
            setPadding(anchorView.paddingLeft, anchorView.paddingTop, anchorView.paddingRight, anchorView.paddingBottom)
            background = GradientDrawable().apply {
                setColor(tokens.surfaceRaised)
                cornerRadius = 999f
            }
            layoutParams = FrameLayout.LayoutParams(anchorSize, anchorHeight).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = anchorPos[0]
                topMargin = anchorPos[1]
            }
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                dismiss()
            }
        })

        val screenWidth = context.resources.displayMetrics.widthPixels
        val cardWidth = (300 * dp).toInt().coerceAtMost(screenWidth - (24 * dp).toInt())
        // Opens away from whichever screen edge the trigger hugs: downward from a top-docked
        // search pill, upward from a bottom-docked one (where a downward menu ran off-screen and
        // left most of its rows unreachable), and beside a mid-height rail trigger as before.
        val placement = AnchoredMenuPlacement.layoutParamsFor(
            context = context,
            anchorView = anchorView,
            anchorPos = anchorPos,
            cardWidth = cardWidth,
            isRtl = isRtl,
            direction = AnchoredMenuPlacement.directionFor(context, anchorPos[1], anchorHeight)
        )

        // A frosted panel, not a window blur. The window blur was removed because raising it made
        // the menu feel heavy, and the card went opaque so the app grid would not show through it
        // — which also stopped it being frosted. A panel frosts itself from its own wallpaper
        // slice, with an opaque base underneath, so it gets the glass back without either cost.
        // Dense, like the drawer's pill: this card sits over the scrolling app grid too.
        menuCard = FrostedPanelLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setCornerRadiusPx(20 * dp)
            applyStyle(tokens, FrostedPanelLayout.Density.DENSE)
            setPadding((18 * dp).toInt(), (16 * dp).toInt(), (18 * dp).toInt(), (16 * dp).toInt())
            layoutParams = placement
            setOnClickListener { /* consume clicks inside the card */ }
        }

        renderContent()

        root.addView(menuCard)
        setContentView(root)
    }

    /**
     * Builds the menu's rows from the live values.
     *
     * Layout choices no longer dismiss the menu: the drawer re-lays out behind it, and the user
     * closes it when they have decided. Switching Grid and List changes which row sits underneath
     * — Grid has a Columns stepper, List has a Style row — so a mode change rebuilds the rows. The
     * rebuild is posted, because it removes the very view whose click is being dispatched.
     *
     * The card keeps its place through the height change: an upward-opening menu is anchored by
     * its bottom edge ([AnchoredMenuPlacement]), so it grows away from its trigger, not over it.
     */
    private fun renderContent() {
        menuCard.removeAllViews()
        DrawerOverflowMenuContent.build(
            context = context,
            dp = dp,
            tokens = tokens,
            menuCard = menuCard,
            effectiveLayoutMode = layoutMode,
            currentListColumns = listColumns,
            currentGridColumns = gridColumns,
            currentCategoryLayout = categoryLayout,
            onColumnsSelected = { cols ->
                if (cols != listColumns) {
                    listColumns = cols
                    layoutMode = "list_$cols"
                    onColumnsSelected(cols)
                }
            },
            onGridColumnsSelected = { cols ->
                gridColumns = cols
                onGridColumnsSelected(cols)
            },
            onDrawerModeChanged = { mode ->
                val next = when (mode) {
                    "grid" -> "grid"
                    "categories" -> "categories"
                    else -> "list_$listColumns"
                }
                if (next != layoutMode) {
                    layoutMode = next
                    onDrawerModeChanged(mode)
                    menuCard.post { renderContent() }
                }
            },
            onCategoryLayoutChanged = { style ->
                if (style != categoryLayout) {
                    categoryLayout = style
                    onCategoryLayoutChanged(style)
                }
            },
            onHiddenApps = { dismiss(); onHiddenApps() },
            onCreateFolder = { dismiss(); onCreateFolder() },
            onDrawerSettings = { dismiss(); onDrawerSettings() },
            positionRowLabel = positionRowLabel,
            positionRowValue = positionRowValue,
            onPositionSelected = { position -> onPositionSelected(position) }
        )
    }

    private fun suppressActivityBarScrim() {
        val win = activity.window
        val prevStatusColor = win.statusBarColor
        val prevNavColor = win.navigationBarColor
        val prevNavContrast = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) win.isNavigationBarContrastEnforced else null
        val prevStatusContrast = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) win.isStatusBarContrastEnforced else null

        win.statusBarColor = Color.TRANSPARENT
        win.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            win.isNavigationBarContrastEnforced = false
            win.isStatusBarContrastEnforced = false
        }

        restoreActivityBars = {
            win.statusBarColor = prevStatusColor
            win.navigationBarColor = prevNavColor
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                prevNavContrast?.let { win.isNavigationBarContrastEnforced = it }
                prevStatusContrast?.let { win.isStatusBarContrastEnforced = it }
            }
        }
    }
}
