package com.nexus.launcher.feed

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.WindowCompat
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.settings.views.NexusElasticSwitch
import com.nexus.launcher.ui.settings.views.NexusSegmentedRow

/** Anchored dropdown menu below the Feed header's menu icon (not a generic top-left guess). */
class NexusFeedHeaderMenuDialog(
    context: Context,
    private val anchorView: View,
    private var isHeadlineOnly: Boolean,
    private val onHeadlineOnlyChanged: (Boolean) -> Unit,
    private val onOpenManageSources: () -> Unit,
    private val onEInkModeChanged: (() -> Unit)? = null,
    private val onDismissed: (() -> Unit)? = null
) : Dialog(context, android.R.style.Theme_Black_NoTitleBar) {
    // Theme_Translucent_NoTitleBar sets windowIsFloating=true — floating Dialog windows do not
    // reliably extend FLAG_DIM_BEHIND/FLAG_BLUR_BEHIND under the status/nav bar cutout regions
    // even with setDecorFitsSystemWindows(false) + setLayout(MATCH_PARENT, MATCH_PARENT); that
    // was the actual source of the "three fragments" seam. Every other fixed dialog this session
    // (IconPackBrowseSheet, NexusSelectionSheet, ...) works because BottomSheetDialog is
    // non-floating by construction. Theme_Black_NoTitleBar is non-floating for the same reason,
    // and — unlike the _Fullscreen variant — does not hide the status bar outright.

    private val dp get() = context.resources.displayMetrics.density

    /**
     * The system paints its own translucent status/nav-bar protection scrim on the Activity
     * window whenever contrast enforcement is on — that scrim is a different tone than the Feed
     * page's own background and reads as a visible seam once this dialog's blur samples the
     * composited screen (the reported "three fragments"). Suppressed only while this dialog is
     * open, restored on dismiss — not a permanent app-wide change.
     */
    private var restoreActivityBars: (() -> Unit)? = null

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val isEInk = NexusFeedEInkCoordinator.isEInkMode(context)
        val tokens: NexusColorTokens = if (isEInk) {
            NexusFeedEInkCoordinator.getTokens(context)
        } else {
            try {
                ThemeObserver.currentTokens(context)
            } catch (_: Exception) {
                NexusColorTokens.Dark
            }
        }

        suppressActivityBarScrim()

        window?.let { win ->
            WindowCompat.setDecorFitsSystemWindows(win, false)
            win.statusBarColor = Color.TRANSPARENT
            win.navigationBarColor = Color.TRANSPARENT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                win.isNavigationBarContrastEnforced = false
                win.isStatusBarContrastEnforced = false
            }
            win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            win.setFormat(PixelFormat.TRANSLUCENT)
            win.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
            win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            win.setDimAmount(0.72f)
            if (!isEInk) {
                com.nexus.launcher.ui.glass.FloatingSurfaces.applyBlurBehind(win)
            }
            win.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        }
        setOnDismissListener {
            restoreActivityBars?.invoke()
            onDismissed?.invoke()
        }

        val anchorPos = IntArray(2)
        anchorView.getLocationOnScreen(anchorPos)

        val root = FrameLayout(context).apply {
            setOnClickListener { dismiss() }
        }

        val anchorSize = anchorView.width.takeIf { it > 0 } ?: (31 * dp).toInt()
        val anchorIconPad = (7 * dp).toInt()
        root.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_menu)
            imageTintList = ColorStateList.valueOf(tokens.textPrimary)
            setPadding(anchorIconPad, anchorIconPad, anchorIconPad, anchorIconPad)
            background = GradientDrawable().apply {
                setColor(tokens.surfaceRaised)
                cornerRadius = if (isEInk) 0f else 999f
                if (isEInk) {
                    setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
                }
            }
            layoutParams = FrameLayout.LayoutParams(anchorSize + anchorIconPad * 2, anchorSize + anchorIconPad * 2).apply {
                gravity = Gravity.TOP or Gravity.START
                leftMargin = anchorPos[0] - anchorIconPad
                topMargin = anchorPos[1] - anchorIconPad
            }
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                dismiss()
            }
        })

        val cardWidth = (360 * dp).toInt().coerceAtMost(
            context.resources.displayMetrics.widthPixels - (24 * dp).toInt()
        )
        val menuCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = if (isEInk) {
                GradientDrawable().apply {
                    setColor(tokens.surface)
                    cornerRadius = 0f
                    setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
                }
            } else {
                // Set by bindSheetCard below, which also follows the system's blur state.
                null
            }
            setPadding((18 * dp).toInt(), (16 * dp).toInt(), (18 * dp).toInt(), (16 * dp).toInt())
            if (!isEInk) com.nexus.launcher.ui.glass.FloatingSurfaces.bindSheetCard(this, tokens, 20 * dp, dp)
            layoutParams = FrameLayout.LayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.START
                topMargin = anchorPos[1] + anchorView.height + (8 * dp).toInt()
                leftMargin = anchorPos[0].coerceAtMost(
                    (context.resources.displayMetrics.widthPixels - cardWidth - (12 * dp).toInt())
                ).coerceAtLeast((12 * dp).toInt())
            }
            setOnClickListener { /* Consume clicks inside menu card */ }
        }

        menuCard.addView(TextView(context).apply {
            text = context.getString(R.string.nexus_feed_options_title)
            typeface = if (isEInk) android.graphics.Typeface.MONOSPACE else android.graphics.Typeface.DEFAULT_BOLD
            NexusTypeScale.title.bindTo(this, tokens.textPrimary)
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            setPadding(0, 0, 0, (12 * dp).toInt())
        })

        val headlineRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (6 * dp).toInt(), 0, (8 * dp).toInt())

            val textCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = (10 * dp).toInt()
                }
                addView(TextView(context).apply {
                    text = context.getString(R.string.nexus_feed_headline_only)
                    NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                })
                addView(TextView(context).apply {
                    text = context.getString(R.string.nexus_feed_headline_only_desc)
                    NexusTypeScale.caption.bindTo(this, tokens.textSecondary)
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = (2 * dp).toInt()
                    }
                })
            }
            addView(textCol)

            val toggle = NexusElasticSwitch(context).apply {
                paperMode = isEInk
                applyTokens(tokens)
                setChecked(isHeadlineOnly, animate = false)
                onCheckedChange = { checked ->
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    isHeadlineOnly = checked
                    onHeadlineOnlyChanged(checked)
                }
            }
            addView(toggle)
        }
        menuCard.addView(headlineRow)

        val einkSection = NexusFeedEInkMenuRow.build(
            context = context,
            tokens = tokens,
            dp = dp,
            onModeChanged = { onEInkModeChanged?.invoke() }
        )
        menuCard.addView(einkSection)

        menuCard.addView(View(context).apply {
            background = ColorDrawable(tokens.divider)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (1 * dp).toInt()).apply {
                topMargin = (6 * dp).toInt(); bottomMargin = (8 * dp).toInt()
            }
        })

        val feedPrefs = context.getSharedPreferences(NexusFeedTimeRefreshHelper.PREFS_NAME, Context.MODE_PRIVATE)
        val currentInterval = feedPrefs.getString(
            NexusFeedTimeRefreshHelper.KEY_REFRESH_INTERVAL,
            NexusFeedTimeRefreshHelper.DEFAULT_REFRESH_INTERVAL
        ) ?: NexusFeedTimeRefreshHelper.DEFAULT_REFRESH_INTERVAL
        // NexusSegmentedRow's built-in icon renders on its own line above the label (its only
        // other layout mode, "inline", would squeeze the label AND the 7-way control onto one
        // row instead) — a manual icon+label header, matching every other row in this menu,
        // keeps icon and label on the same line while the control still gets the full card width.
        val refreshHeaderRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (6 * dp).toInt(), 0, (6 * dp).toInt())
            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_refresh)
                imageTintList = ColorStateList.valueOf(tokens.textPrimary)
                layoutParams = LinearLayout.LayoutParams((20 * dp).toInt(), (20 * dp).toInt()).apply {
                    marginEnd = (10 * dp).toInt()
                }
            })
            addView(TextView(context).apply {
                text = context.getString(R.string.nexus_feed_refresh_interval_label)
                NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            })
        }
        menuCard.addView(refreshHeaderRow)

        val refreshRow = NexusSegmentedRow(context).apply {
            paperMode = isEInk
            configure(
                label = "",
                options = listOf(
                    "15m" to "15m", "30m" to "30m", "1h" to "1h",
                    "6h" to "6h", "12h" to "12h", "24h" to "24h",
                    "off" to context.getString(R.string.nexus_feed_refresh_off)
                ),
                initialValue = currentInterval
            )
            setContentPadding(0, 0, 0, (2 * dp).toInt())
            applyTokens(tokens)
            onValueChanged = { newVal ->
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                feedPrefs.edit().putString(NexusFeedTimeRefreshHelper.KEY_REFRESH_INTERVAL, newVal).apply()
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (4 * dp).toInt()
            }
        }
        menuCard.addView(refreshRow)

        menuCard.addView(View(context).apply {
            background = ColorDrawable(tokens.divider)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (1 * dp).toInt()).apply {
                topMargin = (6 * dp).toInt(); bottomMargin = (8 * dp).toInt()
            }
        })

        val manageRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (6 * dp).toInt(), 0, (6 * dp).toInt())
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                dismiss()
                onOpenManageSources()
            }

            addView(ImageView(context).apply {
                setImageResource(R.drawable.ic_settings)
                imageTintList = ColorStateList.valueOf(tokens.textPrimary)
                layoutParams = LinearLayout.LayoutParams((22 * dp).toInt(), (22 * dp).toInt()).apply {
                    marginEnd = (12 * dp).toInt()
                }
            })

            val textCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(context).apply {
                    text = context.getString(R.string.nexus_feed_manage_sources)
                    NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                })
                addView(TextView(context).apply {
                    text = context.getString(R.string.nexus_feed_manage_sources_desc)
                    NexusTypeScale.caption.bindTo(this, tokens.textSecondary)
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = (2 * dp).toInt()
                    }
                })
            }
            addView(textCol)
        }
        menuCard.addView(manageRow)

        root.addView(menuCard)
        setContentView(root)
    }

    @Suppress("DEPRECATION")
    private fun suppressActivityBarScrim() {
        val activity = context as? Activity ?: return
        val win = activity.window
        val prevStatusColor = win.statusBarColor
        val prevNavColor = win.navigationBarColor
        val prevNavContrast = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) win.isNavigationBarContrastEnforced else null
        val prevStatusContrast = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) win.isStatusBarContrastEnforced else null

        win.statusBarColor = Color.TRANSPARENT
        win.navigationBarColor = Color.TRANSPARENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            win.isNavigationBarContrastEnforced = false
            win.isStatusBarContrastEnforced = false
        }

        restoreActivityBars = {
            win.statusBarColor = prevStatusColor
            win.navigationBarColor = prevNavColor
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                prevNavContrast?.let { win.isNavigationBarContrastEnforced = it }
                prevStatusContrast?.let { win.isStatusBarContrastEnforced = it }
            }
        }
    }
}
