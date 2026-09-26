package com.nexus.launcher.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.os.LocaleList
import com.nexus.launcher.domain.model.AppModel
import com.nexus.launcher.locale.LocaleObserver
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

import com.nexus.launcher.domain.search.CategoryEngine
import com.nexus.launcher.data.prefs.PreferenceManager

@Singleton
class AppRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val categoryEngine: CategoryEngine,
    private val preferenceManager: PreferenceManager,
    private val iconResolver: com.nexus.launcher.ui.icons.IconResolver
) : AppRepository {

    companion object {
        /** Concurrent icon bakes per resync — bounded so a themed cold start can't saturate
         *  every core while the UI thread is trying to inflate. */
        private const val ICON_PARALLELISM = 4
    }

    /** Written from the IO resync, read on the main thread by a new view model — hence volatile. */
    @Volatile
    private var lastResolved: List<AppModel> = emptyList()

    /**
     * Bumped at the start of every resolve; only the most recently *started* one may publish to
     * [lastResolved]. A cancelled resolve can still run its body to the end — the icon work is
     * blocking — and without this it would overwrite a newer list on its way out.
     */
    private val generation = java.util.concurrent.atomic.AtomicLong()

    override val lastKnownApps: List<AppModel>
        get() = lastResolved

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override suspend fun getInstalledApps(): List<AppModel> = withContext(Dispatchers.IO) {
        val ticket = generation.incrementAndGet()
        val packageManager = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val resolvedActivities = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()))
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        }
        
        android.util.Log.d("NexusDebug", "Total apps found by PackageManager: ${resolvedActivities.size}")
        
        val effectiveLocale = LocaleObserver.getEffectiveLocale(context)
        val normalizedLocale = if (effectiveLocale.language.equals("ne", ignoreCase = true) && effectiveLocale.country.isEmpty()) {
            Locale("ne", "NP")
        } else effectiveLocale

        // Labels share a per-package Resources cache and stay on this thread; icon resolution
        // (pack lookup, themed bake) is the expensive part, so it fans out across Default.
        // Previously every resync — pack apply, locale, rename, cold start — baked ~200 icons
        // sequentially on one IO thread, which is the multi-second stall before the drawer fills.
        val resourcesCache = mutableMapOf<String, Resources?>()
        val labelled = resolvedActivities.map { resolveInfo ->
            Triple(
                resolveInfo,
                resolveLocalizedLabel(resolveInfo, packageManager, normalizedLocale, resourcesCache),
                resolveInfo.activityInfo.packageName
            )
        }

        val iconDispatcher = Dispatchers.Default.limitedParallelism(ICON_PARALLELISM)
        kotlinx.coroutines.coroutineScope {
            labelled.map { (resolveInfo, label, packageName) ->
                async(iconDispatcher) {
                    val className = resolveInfo.activityInfo.name

                    // Never let a single icon failure drop the app from the list — fall back
                    // to the plain launcher icon for that one entry.
                    val icon = try {
                        iconResolver.getIcon(packageName, className)
                    } catch (e: Exception) {
                        android.util.Log.w("AppRepository", "Icon resolve failed for $packageName, using system icon", e)
                        try { resolveInfo.loadIcon(packageManager) } catch (_: Exception) { packageManager.defaultActivityIcon }
                    }

                    val installTime = try {
                        packageManager.getPackageInfo(packageName, 0).firstInstallTime
                    } catch (e: Exception) {
                        0L
                    }

                    AppModel(
                        packageName = packageName,
                        className = className,
                        label = label,
                        icon = icon,
                        installTime = installTime,
                        categoryId = preferenceManager.getAppCategoryOverride(packageName)
                            // The app's own declared category (games above all) and its name
                            // both feed the sort — see CategoryEngine.
                            ?: categoryEngine.categorize(
                                packageName, label, resolveInfo.activityInfo.applicationInfo
                            )
                    )
                }
            }.awaitAll()
        }.also { if (ticket == generation.get()) lastResolved = it }
    }

    private fun resolveLocalizedLabel(
        resolveInfo: ResolveInfo,
        packageManager: PackageManager,
        locale: Locale,
        resourcesCache: MutableMap<String, Resources?>
    ): String {
        val nonLocalized = resolveInfo.nonLocalizedLabel?.toString()
        if (!nonLocalized.isNullOrBlank()) return nonLocalized

        val labelRes = if (resolveInfo.activityInfo.labelRes != 0) {
            resolveInfo.activityInfo.labelRes
        } else {
            resolveInfo.activityInfo.applicationInfo.labelRes
        }

        val packageName = resolveInfo.activityInfo.packageName

        if (labelRes != 0) {
            try {
                val localizedRes = resourcesCache.getOrPut(packageName) {
                    val pkgContext = context.createPackageContext(packageName, 0)
                    val config = Configuration(pkgContext.resources.configuration)
                    config.setLocales(LocaleList(locale))
                    pkgContext.createConfigurationContext(config).resources
                }
                if (localizedRes != null) {
                    val text = localizedRes.getString(labelRes).trim()
                    if (text.isNotBlank()) {
                        if (hasNativeScript(text, locale.language)) {
                            return text
                        }
                        val fallback = LocalizedAppNameFallback.getFallbackLabel(packageName, locale.language)
                        if (fallback != null) return fallback
                        return text
                    }
                }
            } catch (_: Exception) {
                // Fallback to PackageManager loadLabel if target package context cannot be constructed
            }
        }

        val fallback = LocalizedAppNameFallback.getFallbackLabel(packageName, locale.language)
        if (fallback != null) return fallback

        return resolveInfo.loadLabel(packageManager)?.toString()
            ?: resolveInfo.activityInfo.name
            ?: packageName
    }

    private fun hasNativeScript(text: String, lang: String): Boolean {
        return when (lang.lowercase()) {
            "ne" -> text.any { it in '\u0900'..'\u097F' }
            "ar" -> text.any { it in '\u0600'..'\u06FF' }
            "iw", "he" -> text.any { it in '\u0590'..'\u05FF' }
            else -> false
        }
    }
}
