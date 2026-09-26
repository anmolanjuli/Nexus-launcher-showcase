package com.nexus.launcher.reader

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.premium.PremiumFeature
import com.nexus.launcher.premium.PremiumGate

/**
 * Builds and manages the minimal top toolbar for NexusReaderActivity.
 * Includes back navigation, source label, mode pill switcher, bookmark, share, and open in browser actions.
 */
class NexusReaderToolbarBuilder(
    private val context: Context,
    private val onBackClick: () -> Unit,
    private val onModeSelected: (NexusReaderThemeHelper.ReaderDisplayMode) -> Unit,
    private val onBookmarkClick: () -> Unit,
    private val onShareClick: () -> Unit,
    private val onBrowserClick: () -> Unit
) {
    private val dp = context.resources.displayMetrics.density

    lateinit var toolbarRoot: LinearLayout
        private set
    private lateinit var bookmarkIconView: ImageView
    private lateinit var modeSelectorContainer: LinearLayout
    private var activePalette: NexusReaderThemeHelper.ReaderPalette? = null
    private var activeMode = NexusReaderThemeHelper.ReaderDisplayMode.READER

    fun updateInsets(statusBarHeight: Int) {
        if (!::toolbarRoot.isInitialized) return
        toolbarRoot.setPadding((8 * dp).toInt(), statusBarHeight, (12 * dp).toInt(), 0)
        val lp = toolbarRoot.layoutParams
        if (lp != null) {
            lp.height = statusBarHeight + (54 * dp).toInt()
            toolbarRoot.layoutParams = lp
        }
    }

    fun build(
        statusBarHeight: Int,
        palette: NexusReaderThemeHelper.ReaderPalette,
        sourceName: String,
        initialMode: NexusReaderThemeHelper.ReaderDisplayMode,
        isBookmarked: Boolean
    ): LinearLayout {
        activePalette = palette
        activeMode = initialMode

        toolbarRoot = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((8 * dp).toInt(), statusBarHeight, (12 * dp).toInt(), 0)
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, statusBarHeight + (54 * dp).toInt())
            background = ColorDrawable(palette.surface)

            // Back button
            val backBtn = ImageView(context).apply {
                setImageResource(R.drawable.ic_chevron_left)
                imageTintList = ColorStateList.valueOf(palette.textPrimary)
                val p = (10 * dp).toInt()
                setPadding(p, p, p, p)
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onBackClick()
                }
            }
            addView(backBtn, LinearLayout.LayoutParams((44 * dp).toInt(), (44 * dp).toInt()))

            // Source Label
            val sourceView = TextView(context).apply {
                text = sourceName.ifBlank { context.getString(R.string.nexus_reader_mode_reader) }
                textSize = 15f
                setTextColor(palette.textPrimary)
                typeface = if (palette.isEInk) Typeface.MONOSPACE else Typeface.DEFAULT_BOLD
                maxLines = 1
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = (6 * dp).toInt()
                }
            }
            addView(sourceView)

            // Mode Selector Pill Row
            modeSelectorContainer = buildModeSelector(palette, activeMode)
            addView(modeSelectorContainer)

            // Bookmark Action
            bookmarkIconView = ImageView(context).apply {
                setImageResource(if (isBookmarked) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark)
                imageTintList = ColorStateList.valueOf(palette.textPrimary)
                val p = (10 * dp).toInt()
                setPadding(p, p, p, p)
                setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onBookmarkClick()
                }
            }
            addView(bookmarkIconView, LinearLayout.LayoutParams((40 * dp).toInt(), (40 * dp).toInt()))

            // Share Action
            val shareBtn = ImageView(context).apply {
                setImageResource(R.drawable.ic_share)
                imageTintList = ColorStateList.valueOf(palette.textPrimary)
                val p = (10 * dp).toInt()
                setPadding(p, p, p, p)
                setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onShareClick()
                }
            }
            addView(shareBtn, LinearLayout.LayoutParams((40 * dp).toInt(), (40 * dp).toInt()))

            // Open in Browser
            val browserBtn = ImageView(context).apply {
                setImageResource(R.drawable.ic_globe)
                imageTintList = ColorStateList.valueOf(palette.textPrimary)
                val p = (10 * dp).toInt()
                setPadding(p, p, p, p)
                setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onBrowserClick()
                }
            }
            addView(browserBtn, LinearLayout.LayoutParams((40 * dp).toInt(), (40 * dp).toInt()))
        }

        return toolbarRoot
    }

    private fun buildModeSelector(
        palette: NexusReaderThemeHelper.ReaderPalette,
        currentMode: NexusReaderThemeHelper.ReaderDisplayMode
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((4 * dp).toInt(), (2 * dp).toInt(), (4 * dp).toInt(), (2 * dp).toInt())
            background = GradientDrawable().apply {
                setColor(palette.surfaceRaised)
                cornerRadius = 14 * dp
            }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (30 * dp).toInt()).apply {
                marginEnd = (4 * dp).toInt()
            }

            // Two pills, not three. Reader and E-Ink are the same extracted view — E-Ink is
            // Reader on paper — so with E-Ink Paper Mode on they render identically and offering
            // both is a choice between a thing and itself. The pair on offer is whichever two
            // actually differ: paper or the publisher's page, or clean text or the publisher's page.
            val paperMode = com.nexus.launcher.feed.NexusFeedEInkCoordinator.isEInkMode(context)
            val cleanMode = if (paperMode) {
                NexusReaderThemeHelper.ReaderDisplayMode.EINK
            } else {
                NexusReaderThemeHelper.ReaderDisplayMode.READER
            }
            val cleanLabel = if (paperMode) {
                context.getString(R.string.nexus_reader_mode_eink)
            } else {
                context.getString(R.string.nexus_reader_mode_reader)
            }
            addView(createModePill(cleanLabel, cleanMode, palette, currentMode))
            addView(createModePill(context.getString(R.string.nexus_reader_mode_original), NexusReaderThemeHelper.ReaderDisplayMode.ORIGINAL, palette, currentMode))
        }
    }

    private fun createModePill(
        label: String,
        mode: NexusReaderThemeHelper.ReaderDisplayMode,
        palette: NexusReaderThemeHelper.ReaderPalette,
        currentMode: NexusReaderThemeHelper.ReaderDisplayMode
    ): TextView {
        val isSelected = currentMode == mode
        return TextView(context).apply {
            text = label
            textSize = 11f
            setTextColor(if (isSelected) palette.textPrimary else palette.textSecondary)
            typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            gravity = Gravity.CENTER
            setPadding((8 * dp).toInt(), 0, (8 * dp).toInt(), 0)
            if (isSelected) {
                background = GradientDrawable().apply {
                    setColor(palette.surface)
                    cornerRadius = 12 * dp
                }
            }
            setOnClickListener {
                if (mode == NexusReaderThemeHelper.ReaderDisplayMode.EINK &&
                    !com.nexus.launcher.feed.NexusFeedEInkCoordinator.isEInkMode(context) &&
                    !PremiumGate.allow(context, PremiumFeature.EINK_FEED)) {
                    return@setOnClickListener
                }
                performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onModeSelected(mode)
            }
        }
    }

    fun updatePalette(palette: NexusReaderThemeHelper.ReaderPalette, currentMode: NexusReaderThemeHelper.ReaderDisplayMode) {
        activePalette = palette
        activeMode = currentMode
        toolbarRoot.background = ColorDrawable(palette.surface)

        val parent = modeSelectorContainer.parent as? ViewGroup
        val idx = parent?.indexOfChild(modeSelectorContainer) ?: -1
        if (parent != null && idx != -1) {
            parent.removeViewAt(idx)
            modeSelectorContainer = buildModeSelector(palette, currentMode)
            parent.addView(modeSelectorContainer, idx)
        }
    }

    fun setBookmarked(isBookmarked: Boolean) {
        if (::bookmarkIconView.isInitialized) {
            bookmarkIconView.setImageResource(if (isBookmarked) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark)
        }
    }
}
