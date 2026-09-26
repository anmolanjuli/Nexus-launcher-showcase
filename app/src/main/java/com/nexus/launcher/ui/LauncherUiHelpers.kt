package com.nexus.launcher.ui

import android.content.Context
import android.view.View
import android.view.inputmethod.InputMethodManager
import com.nexus.launcher.ui.model.LauncherState

class LauncherUiHelpers(
    private val activity: MainActivity,
    private val canvasView: com.nexus.launcher.ui.canvas.LauncherCanvasView,
    private val viewModel: MainViewModel
) {
    fun closeSearchMode(
        searchOverlay: android.view.View,
        searchBar: android.widget.EditText,
        newScroll: android.widget.HorizontalScrollView,
        newTitle: android.widget.TextView,
        recentScroll: android.widget.HorizontalScrollView,
        recentTitle: android.widget.TextView
    ) {
        canvasView.isSearchMode = false  // FIRST
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            canvasView.setRenderEffect(null)
        }
        searchOverlay.animate()
            .translationY(300f * activity.resources.displayMetrics.density)
            .alpha(0f)
            .setDuration(250)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .withEndAction { searchOverlay.visibility = View.GONE }
            .start()
            
        searchBar.setText("")
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            canvasView.setRenderEffect(null)
        }
        searchBar.clearFocus()
        canvasView.isSearchMode = false
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(searchBar.windowToken, 0)
        canvasView.showSearchBackground = false
        canvasView.invalidate()
        
        newScroll.visibility = View.VISIBLE
        newTitle.visibility = View.VISIBLE
        recentScroll.visibility = View.VISIBLE
        recentTitle.visibility = View.VISIBLE
    }

    fun openSettings(section: String? = null) {
        if (section == "drawer" || viewModel.uiState.value == LauncherState.DRAWER) {
            activity.preserveDrawerOnResume = true
        }
        val intent = android.content.Intent(activity, com.nexus.launcher.ui.settings.SettingsActivity::class.java).apply {
            if (section != null) {
                putExtra(com.nexus.launcher.ui.settings.SettingsActivity.EXTRA_SECTION, section)
            }
        }
        activity.startActivity(intent)
    }

    fun createIconLayout(app: com.nexus.launcher.domain.model.AppModel, labelColor: Int = android.graphics.Color.WHITE): android.view.View {
        val screenWidth = activity.resources.displayMetrics.widthPixels
        val itemWidth = screenWidth / 8
        
        val container = android.widget.LinearLayout(activity).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            layoutParams = android.widget.LinearLayout.LayoutParams(itemWidth, android.widget.LinearLayout.LayoutParams.WRAP_CONTENT)
            setOnClickListener {
                viewModel.saveRecentApp(app.packageName)
                val intent = activity.packageManager.getLaunchIntentForPackage(app.packageName)
                if (intent != null) {
                    activity.startActivity(intent)
                }
            }
        }
        
        val iconSize = (itemWidth * 0.65f).toInt().coerceAtLeast((32 * activity.resources.displayMetrics.density).toInt())
        val imageView = android.widget.ImageView(activity).apply {
            layoutParams = android.widget.LinearLayout.LayoutParams(iconSize, iconSize)
            setImageDrawable(app.icon)
        }
        
        val textView = android.widget.TextView(activity).apply {
            text = android.text.TextUtils.ellipsize(app.label, paint, iconSize.toFloat() * 1.5f, android.text.TextUtils.TruncateAt.END)
            setTextColor(labelColor)
            textSize = 12f
            setSingleLine(true)
            layoutParams = android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (4 * activity.resources.displayMetrics.density).toInt()
            }
        }
        
        container.addView(imageView)
        container.addView(textView)
        return container
    }
}
