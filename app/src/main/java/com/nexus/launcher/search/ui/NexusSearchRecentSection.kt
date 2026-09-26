package com.nexus.launcher.search.ui

import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class NexusSearchRecentSection(
    private val context: Context,
    private val activity: MainActivity,
    private val dp: Float,
    tokens: NexusColorTokens,
    private val onDismiss: () -> Unit
) {
    private val tokenFlow = MutableStateFlow(tokens)

    val newTitle: TextView = TextView(context).apply {
        text = context.getString(R.string.search_section_new_apps)
        setPadding((8 * dp).toInt(), (16 * dp).toInt(), 0, (8 * dp).toInt())
    }.also { NexusTypeScale.caption.bindTo(it, tokens.textSecondary) }

    val newScroll: HorizontalScrollView = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        clipToPadding = false
        setPadding(0, 0, 0, (16 * dp).toInt())
    }

    val newContainer: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
    }

    val recentTitle: TextView = TextView(context).apply {
        text = context.getString(R.string.search_section_recent_apps)
        setPadding((8 * dp).toInt(), (8 * dp).toInt(), 0, (8 * dp).toInt())
    }.also { NexusTypeScale.caption.bindTo(it, tokens.textSecondary) }

    val recentScroll: HorizontalScrollView = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        clipToPadding = false
        setPadding(0, 0, 0, (16 * dp).toInt())
    }

    val recentContainer: LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
    }

    init {
        newScroll.addView(newContainer)
        recentScroll.addView(recentContainer)
    }

    fun bind(scope: CoroutineScope) {
        scope.launch {
            tokenFlow.combine(activity.viewModel.newApps) { tok, apps -> tok to apps }
                .collect { (tok, apps) ->
                    newContainer.removeAllViews()
                    if (apps.isEmpty()) {
                        newTitle.visibility = View.GONE
                        newScroll.visibility = View.GONE
                    } else {
                        newTitle.visibility = View.VISIBLE
                        newScroll.visibility = View.VISIBLE
                        for (app in apps) {
                            newContainer.addView(activity.uiHelpers.createIconLayout(app, tok.textPrimary).apply {
                                setOnClickListener {
                                    activity.viewModel.saveRecentApp(app.packageName)
                                    val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                    if (intent != null) { context.startActivity(intent); onDismiss() }
                                }
                            })
                        }
                    }
                }
        }

        scope.launch {
            tokenFlow.combine(activity.viewModel.recentApps) { tok, apps -> tok to apps }
                .collect { (tok, apps) ->
                    recentContainer.removeAllViews()
                    if (!activity.viewModel.isUsageStatsPermissionGranted()) {
                        recentTitle.visibility = View.VISIBLE
                        recentScroll.visibility = View.VISIBLE
                        val promptView = TextView(context).apply {
                            text = context.getString(R.string.search_enable_recent_apps)
                            setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())
                            setOnClickListener {
                                context.startActivity(Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS))
                                onDismiss()
                            }
                        }
                        NexusTypeScale.caption.bindTo(promptView, tok.textSecondary)
                        recentContainer.addView(promptView)
                        return@collect
                    }
                    if (apps.isEmpty()) {
                        recentTitle.visibility = View.GONE
                        recentScroll.visibility = View.GONE
                    } else {
                        recentTitle.visibility = View.VISIBLE
                        recentScroll.visibility = View.VISIBLE
                        for (app in apps) {
                            recentContainer.addView(activity.uiHelpers.createIconLayout(app, tok.textPrimary).apply {
                                setOnClickListener {
                                    activity.viewModel.saveRecentApp(app.packageName)
                                    val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                    if (intent != null) { context.startActivity(intent); onDismiss() }
                                }
                            })
                        }
                    }
                }
        }
    }

    fun refreshTheme(newTokens: NexusColorTokens) {
        NexusTypeScale.caption.bindTo(newTitle, newTokens.textSecondary)
        NexusTypeScale.caption.bindTo(recentTitle, newTokens.textSecondary)
        tokenFlow.value = newTokens // triggers combine collectors to re-render icons
    }

    fun updateVisibility(queryBlank: Boolean) {
        newTitle.visibility = if (queryBlank && newContainer.childCount > 0) View.VISIBLE else View.GONE
        newScroll.visibility = if (queryBlank && newContainer.childCount > 0) View.VISIBLE else View.GONE
        recentTitle.visibility = if (queryBlank && recentContainer.childCount > 0) View.VISIBLE else View.GONE
        recentScroll.visibility = if (queryBlank && recentContainer.childCount > 0) View.VISIBLE else View.GONE
    }
}
