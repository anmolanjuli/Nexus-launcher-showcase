package com.nexus.launcher.ui.dock

import android.content.Context
import android.content.Intent
import android.view.DragEvent
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.folder.FolderDragHandoffHelper
import com.nexus.launcher.ui.model.DisplayItem
import java.util.concurrent.ConcurrentHashMap

object DockDropExtraction {

    /** Preserved launch intents keyed by package (PWAs, shortcuts, ClipData payloads). */
    private val preservedLaunchIntents = ConcurrentHashMap<String, Intent>()

    fun slotIndex(isLandscape: Boolean, width: Int, height: Int, x: Float, y: Float): Int {
        if (width == 0 || height == 0) return 0
        return if (isLandscape) {
            (y / (height / 5f)).toInt().coerceIn(0, 4)
        } else {
            (x / (width / 5f)).toInt().coerceIn(0, 4)
        }
    }

    fun extractItemFromDragEvent(context: Context, event: DragEvent): HomeScreenItem? {
        when (val state = event.localState) {
            is HomeScreenItem -> {
                state.launchIntent?.let { cacheIntent(state.packageName, it) }
                return state
            }
            is DisplayItem -> return displayItemToHomeScreenItem(state)
        }
        val clipData = event.clipData ?: return null
        if (clipData.itemCount == 0) return null
        return extractFromClipItem(context, clipData.getItemAt(0))
    }

    fun peekLaunchIntent(packageName: String): Intent? =
        preservedLaunchIntents[packageName]?.let { Intent(it) }

    fun toDisplayItem(context: Context, item: HomeScreenItem): DisplayItem? {
        if (item.itemType == 1) {
            val intent = Intent(FolderDragHandoffHelper.FOLDER_OPEN_ACTION).apply {
                putExtra(FolderDragHandoffHelper.FOLDER_ID_EXTRA, item.resolveFolderContentsId())
                putExtra("folderTitle", item.folderTitle)
            }
            return DisplayItem(
                com.nexus.launcher.ui.folder.FolderContextMenuLauncher.folderDisplayName(context, item),
                null,
                intent,
                null
            )
        }
        val pm = context.packageManager
        val intent = item.launchIntent?.let { Intent(it) }
            ?: peekLaunchIntent(item.packageName)
            ?: pm.getLaunchIntentForPackage(item.packageName)
            ?: return null
        val label = try {
            pm.getApplicationLabel(pm.getApplicationInfo(item.packageName, 0)).toString()
        } catch (_: Exception) {
            item.packageName
        }
        val icon = try {
            pm.getApplicationIcon(item.packageName)
        } catch (_: Exception) {
            null
        }
        return DisplayItem(label, icon, intent, null)
    }

    private fun extractFromClipItem(
        context: Context,
        clipItem: android.content.ClipData.Item
    ): HomeScreenItem? {
        val intent = clipItem.intent
        if (intent != null) {
            val pkg = intent.component?.packageName
                ?: intent.`package`
                ?: intent.data?.schemeSpecificPart?.substringBefore('/')
                ?: "unknown"
            return createDockItemFromPackage(pkg, Intent(intent))
        }
        val rawText = clipItem.text?.toString()?.trim()
        if (!rawText.isNullOrEmpty() && rawText.contains('.')) {
            val pm = context.packageManager
            val launchIntent = pm.getLaunchIntentForPackage(rawText)
            if (launchIntent != null) {
                return createDockItemFromPackage(rawText, launchIntent)
            }
        }
        if (!rawText.isNullOrEmpty()) {
            return displayItemForPackage(context, rawText)?.let { displayItemToHomeScreenItem(it) }
        }
        return null
    }

    private fun createDockItemFromPackage(packageName: String, launchIntent: Intent? = null): HomeScreenItem {
        val intentCopy = launchIntent?.let { Intent(it) }
        intentCopy?.let { cacheIntent(packageName, it) }
        return HomeScreenItem(
            packageName = packageName,
            page = 0,
            column = 0,
            row = 0
        ).apply { this.launchIntent = intentCopy }
    }

    private fun cacheIntent(packageName: String, intent: Intent) {
        preservedLaunchIntents[packageName] = Intent(intent)
    }

    private fun displayItemToHomeScreenItem(item: DisplayItem): HomeScreenItem? {
        val intent = item.intent ?: return null
        FolderDragHandoffHelper.folderIdFromIntent(intent)?.let { folderId ->
            return HomeScreenItem(
                id = folderId.toInt(),
                packageName = "folder_$folderId",
                page = -2,
                column = 0,
                row = 0,
                itemType = 1
            )
        }
        val pkg = intent.component?.packageName ?: intent.`package` ?: return null
        return createDockItemFromPackage(pkg, intent)
    }

    private fun displayItemForPackage(context: Context, pkg: String): DisplayItem? {
        val pm = context.packageManager
        val intent = pm.getLaunchIntentForPackage(pkg) ?: return null
        val label = try {
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        } catch (_: Exception) {
            pkg
        }
        val icon = try {
            pm.getApplicationIcon(pkg)
        } catch (_: Exception) {
            null
        }
        return DisplayItem(label, icon, intent, null)
    }
}
