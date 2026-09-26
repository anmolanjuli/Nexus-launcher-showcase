package com.nexus.launcher.ui.settings

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import com.nexus.launcher.ui.icons.IconResolverEntryPoint
import com.nexus.launcher.ui.icons.IconShapeMasker
import com.nexus.launcher.ui.icons.ThemedIconFactory
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Sample icons baked through [ThemedIconFactory] for [IconsPreviewView].
 *
 * The preview had no themed path at all: with no icon pack selected it fell back to
 * [SettingsPreviewIconRenderer], which draws shape-only placeholders and knows nothing about
 * theming — so turning Themed Icons on changed the home screen but left the preview identical.
 *
 * Bakes and then masks with the current shape, the same two steps `IconResolver` performs on the
 * themed path, so the preview shows what the launcher will actually draw rather than an
 * approximation of it.
 */
object IconPreviewThemedLoader {

    /**
     * The same five slots the pack preview fills, so switching between a pack and themed icons
     * compares like with like. Each entry is a list of candidates; the first installed one wins.
     */
    private val SAMPLE_CANDIDATES = listOf(
        listOf("com.google.android.GoogleCamera", "com.android.camera", "com.android.camera2"),
        listOf("com.google.android.apps.photos", "com.android.gallery3d"),
        listOf("com.android.settings"),
        listOf("com.google.android.deskclock", "com.android.deskclock"),
        listOf("com.google.android.dialer", "com.android.dialer", "com.android.chrome"),
    )

    suspend fun loadPreviewIcons(
        context: Context,
        shape: Int,
    ): List<Bitmap?> = withContext(Dispatchers.IO) {
        val factory = runCatching {
            EntryPointAccessors.fromApplication(
                context.applicationContext,
                IconResolverEntryPoint::class.java,
            ).themedIconFactory()
        }.getOrNull() ?: return@withContext List(SAMPLE_CANDIDATES.size) { null }

        val density = context.resources.displayMetrics.density
        val sizePx = ThemedIconFactory.targetBitmapPx(density)
        val packages = resolveSamplePackages(context)

        packages.map { pkg ->
            if (pkg == null) return@map null
            runCatching {
                // Bake, then mask — `IconResolver` does exactly this, and skipping the mask would
                // show the factory's own plate shape rather than the shape the user picked.
                val themed = factory.createThemedDrawable(pkg, sizePx)
                val maskShape = if (shape == -1) ThemedIconFactory.THEMED_DEFAULT_SHAPE else shape
                toBitmap(IconShapeMasker(themed, maskShape), sizePx)
            }.getOrNull()
        }
    }

    /**
     * First installed candidate per slot, then any launchable app to fill the gaps — a device
     * without Google's apps would otherwise show a mostly empty preview.
     */
    private fun resolveSamplePackages(context: Context): List<String?> {
        val pm = context.packageManager
        fun installed(pkg: String): Boolean = runCatching {
            pm.getLaunchIntentForPackage(pkg) != null
        }.getOrDefault(false)

        val chosen = SAMPLE_CANDIDATES.map { candidates -> candidates.firstOrNull { installed(it) } }
        if (chosen.none { it == null }) return chosen

        val fallback = runCatching {
            pm.queryIntentActivities(
                Intent(Intent.ACTION_MAIN, null).addCategory(Intent.CATEGORY_LAUNCHER),
                0,
            ).map { it.activityInfo.packageName }.distinct()
        }.getOrDefault(emptyList())

        val used = chosen.filterNotNull().toMutableSet()
        return chosen.map { slot ->
            slot ?: fallback.firstOrNull { it !in used }?.also { used.add(it) }
        }
    }

    private fun toBitmap(drawable: Drawable, sizePx: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, sizePx, sizePx)
        drawable.draw(canvas)
        return bitmap
    }
}
