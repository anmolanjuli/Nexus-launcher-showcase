package com.nexus.launcher.ui.settings

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.ui.NexusDesignSystem
import com.nexus.launcher.ui.model.GestureAction
import kotlinx.coroutines.launch
import androidx.fragment.app.FragmentManager

object IconEditGestureSection {

    fun build(
        context: Context,
        fragmentManager: FragmentManager,
        dp: Float,
        itemType: Int,
        initialSwipeUp: String,
        initialSwipeDown: String,
        initialDoubleTap: String,
        onSwipeUpChanged: (String) -> Unit,
        onSwipeDownChanged: (String) -> Unit,
        onDoubleTapChanged: (String) -> Unit,
        showDoubleTap: Boolean = true,
        showGesturesLabel: Boolean = true
    ): View {
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (4 * dp).toInt() }
        }

        val tokens = try {
            com.nexus.launcher.theme.ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            com.nexus.launcher.theme.NexusColorTokens.Dark
        }

        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        root.addView(header)
        if (showGesturesLabel) {
            header.addView(TextView(context).apply {
                text = context.getString(com.nexus.launcher.R.string.edit_sheet_gestures)
                com.nexus.launcher.typography.NexusTypeScale.labelSmall.bindTo(
                    this,
                    tokens.textSecondary
                )
                gravity = Gravity.START
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = (8 * dp).toInt() }
            })
        }
        val pill = com.nexus.launcher.ui.premium.PremiumBadges.pill(context, tokens)
        header.addView(pill, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            if (showGesturesLabel) marginStart = (8 * dp).toInt()
            bottomMargin = (8 * dp).toInt()
        })
        com.nexus.launcher.ui.premium.PremiumBadges.bindPill(pill, com.nexus.launcher.premium.PremiumFeature.ADVANCED_GESTURES)

        val group = com.nexus.launcher.ui.settings.views.SettingsSectionGroupView(context)
        group.addChildRow(buildPickerRow(context, fragmentManager, dp, itemType, context.getString(com.nexus.launcher.R.string.edit_sheet_swipe_up), com.nexus.launcher.R.drawable.ic_swipe_up, initialSwipeUp, onSwipeUpChanged))
        group.addChildRow(buildPickerRow(context, fragmentManager, dp, itemType, context.getString(com.nexus.launcher.R.string.edit_sheet_swipe_down), com.nexus.launcher.R.drawable.ic_swipe_down, initialSwipeDown, onSwipeDownChanged))
        if (showDoubleTap) {
            group.addChildRow(buildPickerRow(context, fragmentManager, dp, itemType, context.getString(com.nexus.launcher.R.string.edit_sheet_double_tap), com.nexus.launcher.R.drawable.ic_double_tap, initialDoubleTap, onDoubleTapChanged))
        }
        root.addView(group)

        return root
    }

    private fun buildPickerRow(
        context: Context,
        fragmentManager: FragmentManager,
        dp: Float,
        itemType: Int,
        label: String,
        iconResId: Int,
        initialAction: String,
        onActionChanged: (String) -> Unit
    ): View {
        val tokens = try {
            com.nexus.launcher.theme.ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            com.nexus.launcher.theme.NexusColorTokens.Dark
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setPadding((14 * dp).toInt(), (12 * dp).toInt(), (14 * dp).toInt(), (12 * dp).toInt())
            isClickable = true
            isFocusable = true
        }

        row.addView(android.widget.ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams((22 * dp).toInt(), (22 * dp).toInt()).apply {
                marginEnd = (12 * dp).toInt()
            }
            setImageResource(iconResId)
            imageTintList = android.content.res.ColorStateList.valueOf(tokens.textSecondary)
        })

        val labelView = TextView(context).apply {
            text = label
            com.nexus.launcher.typography.NexusTypeScale.bodyStrong.bindTo(
                this,
                tokens.textPrimary
            )
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(labelView)

        var currentAction = initialAction

        val valueView = TextView(context).apply {
            text = ""
            com.nexus.launcher.typography.NexusTypeScale.body.bindTo(
                this,
                tokens.textSecondary
            )
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            textAlignment = View.TEXT_ALIGNMENT_VIEW_END
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        
        fun updateLabel(actionStr: String) {
            val parts = actionStr.split("::")
            val baseEnum = try { GestureAction.valueOf(parts[0]) } catch(e: Exception) { GestureAction.NONE }
            if (parts.size > 1 && parts[1].isNotBlank()) {
                val target = parts.drop(1).joinToString("::")
                when (baseEnum) {
                    GestureAction.OPEN_SPECIFIC_APP -> {
                        val appName = try {
                            context.packageManager.getApplicationInfo(target, 0)
                                .loadLabel(context.packageManager).toString()
                        } catch (e: Exception) {
                            context.getString(com.nexus.launcher.R.string.edit_sheet_app_uninstalled)
                        }
                        valueView.text = "${baseEnum.getDisplayName(context)} ($appName)"
                    }
                    GestureAction.OPEN_SPECIFIC_FOLDER -> {
                        val folderId = target.toLongOrNull()
                        if (folderId != null) {
                            val dao = dagger.hilt.EntryPoints.get(
                                context.applicationContext,
                                com.nexus.launcher.di.DaoEntryPoint::class.java
                            ).homeScreenDao()
                            // We can use a coroutine to fetch the exact folder name asynchronously
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                val folderName = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    dao.getItemById(folderId.toInt())?.folderTitle?.takeIf { it.isNotBlank() } ?: context.getString(com.nexus.launcher.R.string.edit_sheet_unnamed_folder)
                                }
                                valueView.text = "${baseEnum.getDisplayName(context)} ($folderName)"
                            }
                        } else {
                            valueView.text = "${baseEnum.getDisplayName(context)} ${context.getString(com.nexus.launcher.R.string.edit_sheet_invalid_folder)}"
                        }
                    }
                    GestureAction.OPEN_SPECIFIC_SHORTCUT -> {
                        val shortcutParts = target.split("::")
                        if (shortcutParts.size >= 2) {
                            val pkg = shortcutParts[0]
                            val sid = shortcutParts.drop(1).joinToString("::")
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
                                val label = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                    val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as android.content.pm.LauncherApps
                                    val query = android.content.pm.LauncherApps.ShortcutQuery().apply {
                                        setPackage(pkg)
                                        setShortcutIds(listOf(sid))
                                        setQueryFlags(android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or android.content.pm.LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
                                    }
                                    try {
                                        launcherApps.getShortcuts(query, android.os.Process.myUserHandle())?.firstOrNull()?.shortLabel?.toString()
                                    } catch(e: Exception) { null }
                                } ?: context.getString(com.nexus.launcher.R.string.edit_sheet_shortcut_unavailable)
                                valueView.text = "${baseEnum.getDisplayName(context)} ($label)"
                            }
                        } else {
                            valueView.text = "${baseEnum.getDisplayName(context)} ${context.getString(com.nexus.launcher.R.string.edit_sheet_invalid_shortcut)}"
                        }
                    }
                    else -> valueView.text = "${baseEnum.getDisplayName(context)} ($target)"
                }
            } else {
                valueView.text = baseEnum.getDisplayName(context)
            }
        }
        
        updateLabel(currentAction)
        
        row.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            
            val baseActionStr = currentAction.substringBefore("::")
            val baseEnum = try { GestureAction.valueOf(baseActionStr) } catch(e: Exception) { GestureAction.NONE }
            
            // Clearing a gesture is always free, so a set one still opens; setting one is Premium.
            val feature = com.nexus.launcher.premium.PremiumFeature.ADVANCED_GESTURES
            if (baseEnum == GestureAction.NONE && !com.nexus.launcher.premium.PremiumGate.allow(context, feature)) return@setOnClickListener

            GestureActionPickerSheet(label, itemType, baseEnum) { selectedActionStr ->
                val clearing = selectedActionStr.substringBefore("::") == GestureAction.NONE.name
                if (clearing || com.nexus.launcher.premium.PremiumGate.allow(context, feature)) {
                    currentAction = selectedActionStr
                    updateLabel(selectedActionStr)
                    onActionChanged(selectedActionStr)
                }
            }.show(fragmentManager, "gesture_action_picker_${label.replace(" ", "_")}")
        }
        
        row.addView(valueView)
        return row
    }
}
