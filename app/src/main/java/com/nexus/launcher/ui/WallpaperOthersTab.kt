@file:Suppress("DEPRECATION")
package com.nexus.launcher.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.ColorStateList
import android.graphics.drawable.RippleDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Grid of third-party wallpaper selector activities. */
object WallpaperOthersTab {

    fun build(
        context: Context,
        density: Float,
        tokens: NexusColorTokens = try { ThemeObserver.currentTokens(context) } catch (_: Exception) { NexusColorTokens.Dark },
        onAppLaunched: () -> Unit
    ): View {
        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            isNestedScrollingEnabled = true
        }

        val grid = GridLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            columnCount = 4
            val pad = (16 * density).toInt()
            setPadding(pad, pad, pad, pad)
        }
        scrollView.addView(grid)

        CoroutineScope(Dispatchers.IO).launch {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_SET_WALLPAPER)
            val flags = PackageManager.MATCH_DEFAULT_ONLY or 0
            val activities: List<ResolveInfo> = try {
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    pm.queryIntentActivities(
                        intent,
                        PackageManager.ResolveInfoFlags.of(flags.toLong())
                    )
                } else {
                    @Suppress("DEPRECATION")
                    pm.queryIntentActivities(intent, flags)
                }
            } catch (e: Exception) {
                emptyList()
            }

            val others = activities.filter { it.activityInfo.packageName != context.packageName }

            withContext(Dispatchers.Main) {
                if (others.isEmpty()) {
                    val emptyText = TextView(context).apply {
                        text = context.getString(com.nexus.launcher.R.string.wallpaper_no_other_apps)
                        NexusTypeScale.body.bindTo(this, tokens.textSecondary)
                        gravity = Gravity.CENTER
                        layoutParams = GridLayout.LayoutParams().apply {
                            width = ViewGroup.LayoutParams.MATCH_PARENT
                            height = ViewGroup.LayoutParams.WRAP_CONTENT
                            setMargins(0, (24 * density).toInt(), 0, (24 * density).toInt())
                            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 4)
                        }
                    }
                    grid.addView(emptyText)
                    return@withContext
                }

                val iconSize = (56 * density).toInt()
                val itemPad = (8 * density).toInt()

                others.forEach { resolveInfo ->
                    val appView = LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER
                        setPadding(itemPad, itemPad, itemPad, itemPad)
                        background = RippleDrawable(
                            ColorStateList.valueOf(tokens.divider),
                            null, null
                        )
                        isClickable = true
                        isFocusable = true

                        layoutParams = GridLayout.LayoutParams().apply {
                            width = 0
                            height = ViewGroup.LayoutParams.WRAP_CONTENT
                            columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                            rowSpec = GridLayout.spec(GridLayout.UNDEFINED)
                            setMargins(itemPad, itemPad, itemPad, itemPad)
                        }

                        setOnClickListener {
                            it.performHapticFeedback(
                                HapticFeedbackConstants.VIRTUAL_KEY,
                                HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
                            )
                            val launchIntent = Intent(Intent.ACTION_SET_WALLPAPER).apply {
                                setClassName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name)
                            }
                            try {
                                context.startActivity(launchIntent)
                                onAppLaunched()
                            } catch (e: Exception) {
                                try {
                                    val fallbackIntent = pm.getLaunchIntentForPackage(resolveInfo.activityInfo.packageName)
                                    if (fallbackIntent != null) {
                                        context.startActivity(fallbackIntent)
                                        onAppLaunched()
                                    }
                                } catch (e2: Exception) {
                                    e2.printStackTrace()
                                }
                            }
                        }
                    }

                    val icon = ImageView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
                        scaleType = ImageView.ScaleType.FIT_CENTER
                        try {
                            setImageDrawable(resolveInfo.loadIcon(pm))
                        } catch (_: Exception) { }
                    }
                    appView.addView(icon)

                    val label = TextView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        ).apply {
                            topMargin = (4 * density).toInt()
                        }
                        gravity = Gravity.CENTER
                        NexusTypeScale.caption.bindTo(this, tokens.textPrimary)
                        maxLines = 1
                        ellipsize = TextUtils.TruncateAt.END
                        try {
                            text = resolveInfo.loadLabel(pm)
                        } catch (_: Exception) {
                            text = resolveInfo.activityInfo.packageName
                        }
                    }
                    appView.addView(label)

                    grid.addView(appView)
                }
            }
        }

        return scrollView
    }
}
