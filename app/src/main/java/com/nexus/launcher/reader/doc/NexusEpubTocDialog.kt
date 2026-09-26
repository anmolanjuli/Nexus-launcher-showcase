package com.nexus.launcher.reader.doc

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
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
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.WindowCompat
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.glass.FloatingSurfaces

/**
 * Aurora Glass Table of Contents drawer dialog for EPUB reading.
 * Lists chapters with jump-to capability, active chapter indicator, and E-Ink paper adaptation.
 */
class NexusEpubTocDialog(
    context: Context,
    private val chapters: List<Pair<Int, String>>,
    private val currentChapterIndex: Int,
    private val isEInk: Boolean,
    private val onChapterSelected: (Int) -> Unit
) : Dialog(context, android.R.style.Theme_Black_NoTitleBar) {

    private val dp = context.resources.displayMetrics.density

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val tokens: NexusColorTokens = try {
            ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }

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
            win.setDimAmount(0.65f)
            FloatingSurfaces.applyBlurBehind(win)
            win.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        }

        val root = FrameLayout(context).apply {
            setOnClickListener { dismiss() }
        }

        val cardWidth = (340 * dp).toInt().coerceAtMost(
            context.resources.displayMetrics.widthPixels - (32 * dp).toInt()
        )
        val maxCardHeight = (context.resources.displayMetrics.heightPixels * 0.75f).toInt()

        val cardLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = FloatingSurfaces.sheetCard(tokens, 20 * dp, dp)
            setPadding((18 * dp).toInt(), (18 * dp).toInt(), (18 * dp).toInt(), (18 * dp).toInt())
            layoutParams = FrameLayout.LayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER
            }
            setOnClickListener { /* Consume inside clicks */ }
        }

        // Header Row
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (14 * dp).toInt())

            val title = TextView(context).apply {
                text = context.getString(R.string.nexus_epub_toc_title)
                NexusTypeScale.title.bindTo(this, tokens.textPrimary)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            addView(title)

            val closeBtn = ImageView(context).apply {
                setImageResource(R.drawable.ic_close)
                imageTintList = ColorStateList.valueOf(tokens.textSecondary)
                val p = (6 * dp).toInt()
                setPadding(p, p, p, p)
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    dismiss()
                }
            }
            addView(closeBtn, LinearLayout.LayoutParams((32 * dp).toInt(), (32 * dp).toInt()))
        }
        cardLayout.addView(header)

        // Content
        if (chapters.isEmpty()) {
            val emptyText = TextView(context).apply {
                text = context.getString(R.string.nexus_epub_toc_empty)
                NexusTypeScale.body.bindTo(this, tokens.textSecondary)
                setPadding(0, (24 * dp).toInt(), 0, (24 * dp).toInt())
                gravity = Gravity.CENTER
            }
            cardLayout.addView(emptyText)
        } else {
            val scrollView = ScrollView(context).apply {
                isVerticalScrollBarEnabled = true
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                ).apply {
                    val contentEstHeight = (chapters.size * 48 * dp).toInt()
                    height = contentEstHeight.coerceAtMost(maxCardHeight - (80 * dp).toInt())
                }
            }

            val listContainer = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }

            chapters.forEach { (index, chapterTitle) ->
                val isCurrent = index == currentChapterIndex
                val row = buildChapterRow(index, chapterTitle, isCurrent, tokens)
                listContainer.addView(row)
            }

            scrollView.addView(listContainer)
            cardLayout.addView(scrollView)
        }

        root.addView(cardLayout)
        setContentView(root)
    }

    private fun buildChapterRow(
        chapterIndex: Int,
        title: String,
        isCurrent: Boolean,
        tokens: NexusColorTokens
    ): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (4 * dp).toInt()
            }

            background = GradientDrawable().apply {
                if (isCurrent) {
                    setColor(if (isEInk) tokens.divider else 0x227EB8D4)
                    setStroke((1.5f * dp).toInt().coerceAtLeast(1), tokens.accent)
                } else {
                    setColor(tokens.surfaceRaised)
                }
                cornerRadius = if (isEInk) 0f else 8 * dp
            }

            val badge = TextView(context).apply {
                text = "${chapterIndex + 1}"
                textSize = 11f
                typeface = if (isEInk) Typeface.MONOSPACE else Typeface.DEFAULT_BOLD
                setTextColor(if (isCurrent) tokens.accent else tokens.textSecondary)
                setPadding(0, 0, (10 * dp).toInt(), 0)
            }
            addView(badge)

            val label = TextView(context).apply {
                text = title
                textSize = 14f
                typeface = if (isEInk) Typeface.SERIF else (if (isCurrent) Typeface.DEFAULT_BOLD else Typeface.DEFAULT)
                setTextColor(if (isCurrent) tokens.accent else tokens.textPrimary)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            addView(label)

            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                dismiss()
                onChapterSelected(chapterIndex)
            }
        }
    }
}
