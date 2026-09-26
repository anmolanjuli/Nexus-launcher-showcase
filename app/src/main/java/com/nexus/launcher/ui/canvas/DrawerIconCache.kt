package com.nexus.launcher.ui.canvas

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.text.TextUtils
import androidx.core.graphics.drawable.toBitmap
import com.nexus.launcher.ui.model.DisplayItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Pre-sizes drawer app icons into Bitmaps on a worker.
 * Call [schedulePrewarm] only when idle or after drawer snap — never mid-swipe.
 *
 * Bakes from each item's own icon, the same drawable the draw path falls back to while a bitmap is
 * missing. It used to resolve every icon again through IconResolver, which made it a second source
 * of truth: whenever the app list held different icons from what a fresh resolve returned — the
 * list built before the icon pack loaded, say — the drawer first drew the list's icons, then
 * swapped each one for the resolver's as batches of twelve finished baking. Baking the icon it
 * would otherwise draw means a bitmap can only ever change how fast an icon draws, never which
 * icon it is. It also skips re-running the pack lookup and themed bake for every app.
 */
class DrawerIconCache {

    private val bitmapCache = ConcurrentHashMap<String, Bitmap>()
    private val labelCache = ConcurrentHashMap<String, String>()
    private val wrapCache = ConcurrentHashMap<String, IconLabelText.Lines>()
    private var lastIconSize = -1

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)

    val folderFallbackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(0x33, 0xFF, 0xFF, 0xFF)
        style = Paint.Style.FILL
    }

    private val subtitleColor = Color.argb(0x8A, 0xFF, 0xFF, 0xFF)
    fun subtitleColor(): Int = subtitleColor

    private var prewarmingJob: kotlinx.coroutines.Job? = null

    fun cancelPrewarm() {
        prewarmingJob?.cancel()
        prewarmingJob = null
    }

    fun schedulePrewarm(
        context: android.content.Context,
        scope: kotlinx.coroutines.CoroutineScope,
        items: List<DisplayItem>,
        iconSizePx: Int,
        onBatchReady: (() -> Unit)? = null,
        /** Package names to bake first (currently visible drawer rows). */
        priorityPackages: Set<String> = emptySet()
    ) {
        if (iconSizePx <= 0) return
        prewarmingJob?.cancel()
        prewarmingJob = scope.launch(Dispatchers.Default) {
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND)
            if (iconSizePx != lastIconSize) {
                bitmapCache.clear()
                lastIconSize = iconSizePx
            }
            // Only for items with no icon of their own; everything else bakes what it already has.
            val iconResolver by lazy {
                dagger.hilt.android.EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    com.nexus.launcher.ui.icons.IconResolverEntryPoint::class.java
                ).iconResolver()
            }
            val res = context.resources

            val appItems = items.filter {
                it.intent?.action != "nexus.folder.OPEN" && it.intent?.component != null
            }
            // Visible packages first so a slow swipe hits warm bitmaps sooner.
            val ordered = if (priorityPackages.isEmpty()) {
                appItems
            } else {
                appItems.sortedByDescending { priorityPackages.contains(it.intent?.component?.packageName) }
            }
            val activeKeys = mutableSetOf<String>()
            var baked = 0
            for (item in ordered) {
                if (!isActive) break
                val pkg = item.intent?.component?.packageName ?: continue
                activeKeys.add(pkg)
                if (bitmapCache.containsKey(pkg)) continue
                try {
                    // A private copy: toBitmap sets bounds, and the shared instance is being drawn
                    // on the main thread by the drawer, home screen and dock at the same time.
                    val source = item.icon?.constantState?.newDrawable(res)?.mutate()
                        ?: iconResolver.getIcon(pkg, item.intent?.component?.className)
                    bitmapCache[pkg] = source.toBitmap(iconSizePx, iconSizePx)
                    baked++
                    if (onBatchReady != null && baked % 12 == 0) {
                        withContext(Dispatchers.Main) { onBatchReady() }
                    }
                } catch (_: Exception) {
                    // leave uncached — draw path falls back to live drawable
                }
            }
            if (isActive) {
                bitmapCache.keys.retainAll(activeKeys)
                if (onBatchReady != null) {
                    withContext(Dispatchers.Main) { onBatchReady() }
                }
            }
        }
    }

    fun invalidatePackage(packageName: String) {
        bitmapCache.remove(packageName)
    }

    fun clear() {
        bitmapCache.clear()
        labelCache.clear()
        wrapCache.clear()
        lastIconSize = -1
    }

    private val previousBounds = Rect()

    fun drawIcon(
        canvas: Canvas,
        icon: Drawable,
        bounds: Rect,
        cacheKey: String,
        alpha: Int
    ) {
        val bmp = bitmapCache[cacheKey]
        if (bmp != null && !bmp.isRecycled) {
            bitmapPaint.alpha = alpha
            canvas.drawBitmap(bmp, null, bounds, bitmapPaint)
        } else {
            // Restore the Drawable's alpha afterwards. Icon Drawables are shared — the same
            // instance (and, via ConstantState, its siblings) is drawn by the home screen, the
            // dock and folder previews — so leaving a faded value here bleeds into whoever
            // draws it next. While the drawer fades out, every icon on this uncached path was
            // stamping its current alpha onto shared state, which is what read as icons
            // flickering against the ones served from the bitmap cache.
            // HomeScreenBatchDragRenderer already uses this save/restore pattern.
            // The bounds are shared state too, and were not being put back. An icon drawn here
            // left the drawer's geometry on the instance, so whoever drew the same app next —
            // the home screen, the dock, a folder preview — got it at the drawer's size and
            // position for that frame unless it happened to set its own first.
            val previousAlpha = icon.alpha
            previousBounds.set(icon.bounds)
            icon.alpha = alpha
            icon.setBounds(bounds.left, bounds.top, bounds.right, bounds.bottom)
            icon.draw(canvas)
            icon.alpha = previousAlpha
            icon.bounds = previousBounds
        }
    }

    fun ellipsizedLabel(
        raw: String,
        paint: android.text.TextPaint,
        maxWidth: Float,
        textSize: Float
    ): String {
        val key = "$raw|${maxWidth.toInt()}|${textSize.toInt()}"
        return labelCache.getOrPut(key) {
            TextUtils.ellipsize(raw, paint, maxWidth, TextUtils.TruncateAt.END).toString()
        }
    }

    fun wrappedLabel(
        raw: String,
        paint: android.text.TextPaint,
        maxWidth: Float,
        textSize: Float,
        twoLine: Boolean
    ): IconLabelText.Lines {
        val key = "$raw|${maxWidth.toInt()}|${textSize.toInt()}|$twoLine"
        return wrapCache.getOrPut(key) {
            IconLabelText.wrap(raw, paint, maxWidth, twoLine)
        }
    }
}
