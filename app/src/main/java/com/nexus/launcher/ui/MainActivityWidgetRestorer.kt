package com.nexus.launcher.ui

import android.content.Context
import android.util.Log
import android.widget.Toast
import com.nexus.launcher.R
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.folder.FolderAuroraDialogs
import com.nexus.launcher.ui.widgets.AppWidgetController
import com.nexus.launcher.ui.widgets.RebindOutcome

/**
 * Handles deferred rebinding of restored widgets and mosaic tiles following a backup restore.
 */
object MainActivityWidgetRestorer {

    suspend fun restoreWidgets(
        context: Context,
        homeScreenViewModel: HomeScreenViewModel,
        appWidgetController: AppWidgetController,
        pending: List<HomeScreenItem>,
        /** Rebuilds the widget overlay once every restored id is real. */
        onRebound: (() -> Unit)? = null
    ) {
        val widgetOutcomes = appWidgetController.rebindRestoredWidgets(pending)
        val mosaicOutcomes = appWidgetController.rebindRestoredMosaics(pending)
        // Every restored widget now has a freshly allocated host id. The overlay still holds
        // views built against the ids from before the restore, so without this a slot can keep
        // rendering the wrong provider until some later event happens to rebuild it.
        onRebound?.invoke()
        // Then ask the providers to paint again. finalizeRestoredWidget already broadcast an
        // update per widget, but that fired before the host views for the new ids existed, so
        // the RemoteViews had nothing to land on and the slot sat on its initialLayout until a
        // scheduled update came round minutes later.
        val boundIds = widgetOutcomes.filterValues { it == RebindOutcome.BOUND }.keys.map { it.id }.toSet()
        // Grouped by provider and addressed to that component. A single package-scoped broadcast
        // would hand every id to every Nexus provider, each painting its own layout over the rest.
        homeScreenViewModel.getAllItemsSnapshot()
            .filter { it.id in boundIds && it.appWidgetId != -1 && !it.providerClassName.isNullOrBlank() }
            .groupBy { android.content.ComponentName(it.packageName, it.providerClassName!!) }
            .forEach { (provider, items) ->
                context.sendBroadcast(
                    android.content.Intent(android.appwidget.AppWidgetManager.ACTION_APPWIDGET_UPDATE).apply {
                        component = provider
                        putExtra(
                            android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_IDS,
                            items.map { it.appWidgetId }.toIntArray()
                        )
                    }
                )
            }
        val errors = mutableListOf<String>()
        val pm = context.packageManager

        widgetOutcomes.forEach { (item, outcome) ->
            if (outcome != RebindOutcome.BOUND) {
                val pkg = item.packageName
                val name = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }
                val reason = if (outcome == RebindOutcome.PROVIDER_NOT_INSTALLED) {
                    Log.w(
                        "WidgetGhostCleanup",
                        "Removing uninstalled restored widget id=${item.id}, pkg=${item.packageName}, provider=${item.providerClassName}, pos=(page=${item.page}, col=${item.column}, row=${item.row})"
                    )
                    homeScreenViewModel.deleteItem(item)
                    context.getString(R.string.widget_error_not_installed)
                } else context.getString(R.string.widget_error_needs_permission)
                errors.add("• $name: $reason")
            }
        }

        mosaicOutcomes.forEach { (_, children) ->
            children.forEach { (pkgCls, outcome) ->
                if (outcome != RebindOutcome.BOUND) {
                    val pkg = pkgCls.substringBefore('/')
                    val name = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }
                    val reason = if (outcome == RebindOutcome.PROVIDER_NOT_INSTALLED) {
                        context.getString(R.string.widget_error_not_installed)
                    } else {
                        context.getString(R.string.widget_error_needs_permission)
                    }
                    errors.add("• $name: $reason")
                }
            }
        }

        if (errors.isEmpty()) {
            Toast.makeText(context, context.getString(R.string.toast_widget_restore_complete), Toast.LENGTH_SHORT).show()
        } else {
            FolderAuroraDialogs.showReport(
                context,
                context.getString(R.string.dialog_restore_attention_title),
                context.getString(R.string.dialog_restore_attention_message) + errors.joinToString("\n")
            )
        }
    }
}
