package com.nexus.launcher.ui.picker

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.NestedScrollView
import com.nexus.launcher.R
import com.nexus.launcher.domain.model.AppModel
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Fullscreen themed overlay for selecting a target app for icon gestures.
 * Extends [PickerOverlayShell], adopting per-item card design matching ShortcutPickerOverlay.
 */
class GestureTargetAppPickerOverlay(
    context: Context,
    private val onTargetSelected: (String) -> Unit
) : PickerOverlayShell(context) {

    private val scrollView: NestedScrollView
    private val listLayout: LinearLayout

    private var allApps: List<AppModel> = emptyList()
    private var filteredApps: List<AppModel> = emptyList()

    private var searchJob: Job? = null
    private var loadedCount = 0
    private val chunkSize = 40

    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    init {
        setTitle(context.getString(com.nexus.launcher.R.string.picker_select_app))
        setSearchHint(context.getString(com.nexus.launcher.R.string.picker_search_apps))

        scrollView = NestedScrollView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            clipToPadding = false
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                    val child = getChildAt(0)
                    if (child != null) {
                        val diff = child.bottom - (height + scrollY)
                        if (diff <= 200 * dp && scrollY > oldScrollY) {
                            loadNextChunk()
                        }
                    }
                }
            }
        }

        listLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
            setPadding(0, (4 * dp).toInt(), 0, (16 * dp).toInt())
        }

        scrollView.addView(listLayout)
        setContent(scrollView)

        setOnSearchQueryChanged { query ->
            searchJob?.cancel()
            searchJob = coroutineScope.launch {
                delay(200)
                filterApps(query.trim())
            }
        }

        loadApps()
    }

    private fun loadApps() {
        showSpinner()
        val act = findActivity(context)
        val viewModel = (act as? MainActivity)?.viewModel

        val locale = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
        val collator = java.text.Collator.getInstance(locale).apply { strength = java.text.Collator.SECONDARY }

        if (viewModel != null) {
            coroutineScope.launch {
                viewModel.apps.collect { apps ->
                    if (allApps.isEmpty() && apps.isNotEmpty()) {
                        allApps = apps.sortedWith(compareBy(collator) { it.label })
                        filteredApps = allApps
                        hideSpinner()
                        loadedCount = 0
                        listLayout.removeAllViews()
                        loadNextChunk()
                    }
                }
            }
        } else {
            coroutineScope.launch(Dispatchers.IO) {
                val pm = context.packageManager
                val intent = android.content.Intent(android.content.Intent.ACTION_MAIN, null).apply {
                    addCategory(android.content.Intent.CATEGORY_LAUNCHER)
                }
                val resolveInfos = pm.queryIntentActivities(intent, 0)
                val loaded = resolveInfos.map { ri ->
                    val label = ri.loadLabel(pm).toString()
                    val icon = ri.loadIcon(pm)
                    AppModel(
                        packageName = ri.activityInfo.packageName,
                        className = ri.activityInfo.name,
                        label = label,
                        icon = icon,
                        installTime = System.currentTimeMillis()
                    )
                }.distinctBy { it.packageName }.sortedWith(compareBy(collator) { it.label })

                allApps = loaded
                filteredApps = loaded

                withContext(Dispatchers.Main) {
                    hideSpinner()
                    loadedCount = 0
                    listLayout.removeAllViews()
                    loadNextChunk()
                }
            }
        }
    }

    private fun filterApps(query: String) {
        val q = query.lowercase(Locale.getDefault())
        filteredApps = if (q.isEmpty()) {
            allApps
        } else {
            allApps.filter { it.label.lowercase(Locale.getDefault()).contains(q) }
        }
        listLayout.removeAllViews()
        loadedCount = 0
        loadNextChunk()
        scrollView.scrollTo(0, 0)

        if (filteredApps.isEmpty()) {
            showEmptyState(context.getString(com.nexus.launcher.R.string.picker_no_apps_found), context.getString(com.nexus.launcher.R.string.picker_no_apps_match, query))
        } else {
            hideEmptyState()
        }
    }

    private fun loadNextChunk() {
        if (loadedCount >= filteredApps.size) return

        val end = (loadedCount + chunkSize).coerceAtMost(filteredApps.size)
        val chunk = filteredApps.subList(loadedCount, end)
        loadedCount = end

        for (app in chunk) {
            val container = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.VERTICAL
                setPadding((16 * dp).toInt(), (4 * dp).toInt(), (16 * dp).toInt(), (4 * dp).toInt())
            }

            val card = LinearLayout(context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                PickerOverlayStyle.choiceCard(this, tokens, dp)
                val padH = (14 * dp).toInt()
                val padV = (12 * dp).toInt()
                setPadding(padH, padV, padH, padV)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    onTargetSelected(app.packageName)
                    dismiss()
                }
            }

            val iconView = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams((36 * dp).toInt(), (36 * dp).toInt()).apply {
                    marginEnd = (14 * dp).toInt()
                }
                setImageDrawable(app.icon)
            }
            card.addView(iconView)

            val labelView = TextView(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                text = app.label
                NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
            }
            card.addView(labelView)

            val chevron = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams((18 * dp).toInt(), (18 * dp).toInt())
                setImageResource(R.drawable.ic_chevron_forward)
                imageTintList = android.content.res.ColorStateList.valueOf(tokens.textSecondary)
            }
            card.addView(chevron)

            container.addView(card)
            listLayout.addView(container)
        }
    }

    companion object {
        fun show(
            context: Context,
            onTargetSelected: (String) -> Unit
        ): GestureTargetAppPickerOverlay {
            val overlay = GestureTargetAppPickerOverlay(context, onTargetSelected)
            overlay.show()
            return overlay
        }
    }
}
