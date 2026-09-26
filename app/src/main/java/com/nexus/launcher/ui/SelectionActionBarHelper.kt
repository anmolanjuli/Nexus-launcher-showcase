package com.nexus.launcher.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.model.SelectionSource
import com.nexus.launcher.ui.model.SelectionState

class SelectionActionBarHelper(
    private val activity: MainActivity,
    private val mainContainer: FrameLayout,
    private val oldSelectionActionBar: LinearLayout,
    private val viewModel: MainViewModel,
    private val homeScreenViewModel: HomeScreenViewModel,
    private val currentPage: () -> Int
) {

    private var pillView: LinearLayout? = null
    private var createFolderBtn: TextView? = null
    private var removeBtn: TextView? = null
    private var uninstallBtn: TextView? = null
    private var pinBtn: TextView? = null
    private var selectedCountText: TextView? = null
    private var lastSource: SelectionSource? = null

    fun isShowing(): Boolean = pillView != null

    fun buildSelectionActionBar(state: SelectionState.Selecting) {
        oldSelectionActionBar.visibility = View.GONE
        hideSelectionActionBar()
        lastSource = state.source

        val dp = activity.resources.displayMetrics.density
        val decorView = activity.window.decorView as FrameLayout
        decorView.clipChildren = false
        decorView.clipToPadding = false

        val topMarginVal = calculatePillTopMargin(decorView, dp, state.source)

        pillView = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
            elevation = 64f
            translationZ = 64f
            androidx.core.view.ViewCompat.setZ(this, 64f)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#CC0D1117"))
                cornerRadius = 28f * dp
                setStroke((1 * dp).toInt(), Color.parseColor(NexusDesignSystem.COLOR_GLASS_BORDER))
            }
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                topMargin = topMarginVal
                marginStart = (24 * dp).toInt()
                marginEnd = (24 * dp).toInt()
            }
            androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(this) { _, _ ->
                val currentLp = layoutParams as? FrameLayout.LayoutParams
                val newMargin = calculatePillTopMargin(decorView, dp, state.source)
                if (currentLp != null && currentLp.topMargin != newMargin) {
                    currentLp.topMargin = newMargin
                    layoutParams = currentLp
                }
                androidx.core.view.WindowInsetsCompat.CONSUMED
            }
        }

        val closeBtn = ImageView(activity).apply {
            setImageResource(R.drawable.ic_close)
            imageTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams((24 * dp).toInt(), (24 * dp).toInt()).apply {
                marginEnd = (12 * dp).toInt()
            }
            setOnClickListener {
                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                homeScreenViewModel.clearSelection()
            }
        }
        pillView?.addView(closeBtn)

        selectedCountText = TextView(activity).apply {
            textSize = 14f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = (16 * dp).toInt() }
        }
        pillView?.addView(selectedCountText)

        removeBtn = actionText(dp, activity.getString(R.string.action_remove), Color.WHITE) {
            val currentState = homeScreenViewModel.selectionState.value as? SelectionState.Selecting ?: return@actionText
            if (currentState.source == SelectionSource.HOME_SCREEN) {
                homeScreenViewModel.bulkRemoveFromHomeScreen(currentState)
            } else {
                // Removing from the drawer hides; hiding is Premium.
                if (!com.nexus.launcher.premium.PremiumGate.allow(activity, com.nexus.launcher.premium.PremiumFeature.HIDDEN_APPS)) return@actionText
                currentState.selectedPackages.forEach { pkg -> viewModel.hideApp(pkg) }
                homeScreenViewModel.clearSelectionKeepMode()
            }
        }
        pillView?.addView(removeBtn)

        createFolderBtn = actionText(dp, activity.getString(R.string.action_create_folder), Color.WHITE) {
            val currentState = homeScreenViewModel.selectionState.value as? SelectionState.Selecting ?: return@actionText
            val selectedItems = homeScreenViewModel.homeScreenItems.value.filter { it.id in currentState.selectedIds }
            val apps = selectedItems.count { it.itemType == 0 }
            val folders = selectedItems.count { it.itemType == 1 }
            if (apps < 2 || folders > 0) return@actionText
            if (currentState.source == SelectionSource.HOME_SCREEN) {
                homeScreenViewModel.createFolderFromSelection(currentState, currentPage(), activity)
            } else {
                homeScreenViewModel.createDrawerFolderFromSelection(currentState)
            }
        }
        pillView?.addView(createFolderBtn)

        uninstallBtn = actionText(dp, activity.getString(R.string.action_uninstall), Color.parseColor(NexusDesignSystem.COLOR_DANGER)) {
            val currentState = homeScreenViewModel.selectionState.value as? SelectionState.Selecting ?: return@actionText
            val id = currentState.selectedIds.firstOrNull() ?: return@actionText
            val pkg = homeScreenViewModel.homeScreenItems.value.firstOrNull { it.id == id }?.packageName ?: return@actionText
            val intent = android.content.Intent(android.content.Intent.ACTION_DELETE).apply {
                data = android.net.Uri.parse("package:$pkg")
            }
            activity.startActivity(intent)
            homeScreenViewModel.clearSelectionKeepMode()
        }
        pillView?.addView(uninstallBtn)

        pinBtn = actionText(dp, activity.getString(R.string.action_pin), Color.WHITE) {
            val currentState = homeScreenViewModel.selectionState.value as? SelectionState.Selecting ?: return@actionText
            pinSelectedAppsToHome(currentState.selectedPackages)
            homeScreenViewModel.clearSelectionKeepMode()
        }
        pillView?.addView(pinBtn)

        decorView.removeView(pillView)
        decorView.addView(pillView)
        applyBarVisibility(state, homeScreenViewModel.homeScreenItems.value)
    }

    fun updatePillForSelection(
        count: Int,
        appCount: Int,
        folderCount: Int,
        state: SelectionState.Selecting
    ) {
        selectedCountText?.text = activity.resources.getQuantityString(R.plurals.selection_count, count, count)
        createFolderBtn?.isEnabled = appCount >= 2 && folderCount == 0
        createFolderBtn?.alpha = if (appCount >= 2 && folderCount == 0) 1f else 0.4f
        applyBarVisibility(state, homeScreenViewModel.homeScreenItems.value)
    }

    private fun applyBarVisibility(state: SelectionState.Selecting, items: List<HomeScreenItem>) {
        val selectedItems = items.filter { it.id in state.selectedIds }
        val appCount = selectedItems.count { it.itemType == 0 }
        val folderCount = selectedItems.count { it.itemType == 1 }
        val isHome = state.source == SelectionSource.HOME_SCREEN

        removeBtn?.visibility = if (isHome) View.VISIBLE else View.GONE
        uninstallBtn?.visibility = if (isHome && state.selectedIds.size == 1 && appCount == 1) {
            View.VISIBLE
        } else {
            View.GONE
        }
        createFolderBtn?.visibility = if (isHome) {
            if (folderCount > 0) View.GONE else View.VISIBLE
        } else {
            View.VISIBLE
        }
        pinBtn?.visibility = if (!isHome) View.VISIBLE else View.GONE
    }

    private fun actionText(dp: Float, label: String, color: Int, onClick: () -> Unit): TextView {
        return TextView(activity).apply {
            text = label
            textSize = 14f
            setTextColor(color)
            setPadding((8 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt())
            setOnClickListener {
                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            }
        }
    }

    fun hideSelectionActionBar() {
        pillView?.let { (activity.window.decorView as FrameLayout).removeView(it) }
        pillView = null
        lastSource = null
    }

    fun pinSelectedAppsToHome(packageNames: Set<String>) {
        val currentItems = homeScreenViewModel.homeScreenItems.value
        val occupiedCells = currentItems.map { it.column to it.row }.toMutableSet()
        val settings = viewModel.nexusSettings.value
        val columns = settings.homeColumns
        val rows = settings.homeRows
        for (pkg in packageNames) {
            var placed = false
            outer@ for (row in rows - 1 downTo 0) {
                for (col in columns - 1 downTo 0) {
                    if (col to row !in occupiedCells) {
                        homeScreenViewModel.pinToHome(pkg, currentPage(), col, row)
                        occupiedCells.add(col to row)
                        placed = true
                        break@outer
                    }
                }
            }
            if (!placed) break
        }
    }

    private fun calculatePillTopMargin(decorView: FrameLayout, dp: Float, source: SelectionSource): Int {
        val insets = androidx.core.view.ViewCompat.getRootWindowInsets(decorView)
        val statusBarsTop = insets?.getInsets(androidx.core.view.WindowInsetsCompat.Type.statusBars())?.top ?: 0
        val cutoutTop = insets?.getInsets(androidx.core.view.WindowInsetsCompat.Type.displayCutout())?.top ?: 0
        val safeCutoutBottom = maxOf(statusBarsTop, cutoutTop, (36 * dp).toInt())
        val minTop = safeCutoutBottom + (8 * dp).toInt()
        if (source != SelectionSource.HOME_SCREEN) {
            return minTop
        }
        val cardTop = com.nexus.launcher.ui.canvas.SelectionModeCardTrack.cardTopPx(activity.canvasView)
        val pillHeight = (42 * dp).toInt()
        val maxTop = (cardTop - pillHeight - (6 * dp).toInt()).toInt()
        return if (maxTop > minTop) {
            minTop + (maxTop - minTop) / 2
        } else {
            minTop
        }
    }
}
