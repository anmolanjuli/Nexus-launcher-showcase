package com.nexus.launcher.ui.icons

import android.graphics.Bitmap
import android.util.LruCache
import javax.inject.Inject
import javax.inject.Singleton

/** LRU cache for baked themed-icon bitmaps — populated only on IO/cache workers. */
@Singleton
class ThemedIconCache @Inject constructor() {

    // Byte-budgeted, not count-budgeted: 256 entries at 256px was ~64 MB of retained bitmaps,
    // enough to push a low-RAM device into OOM mid-bake.
    private val cache = object : LruCache<String, Bitmap>(MAX_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun buildKey(packageName: String, themeKey: String, sizePx: Int): String =
        "$packageName:$themeKey:$sizePx"

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }

    fun clear() {
        cache.evictAll()
    }

    companion object {
        private const val MAX_BYTES = 24 * 1024 * 1024
    }
}
