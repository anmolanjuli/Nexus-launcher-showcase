package com.nexus.launcher.reader.doc

import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.reader.NexusReaderThemeHelper

/**
 * Builds and animates auto-hiding top and bottom toolbars for the Document Reader with E-Ink styling.
 */
class NexusDocumentToolbarBuilder(
    private val context: Context,
    private val onBackClick: () -> Unit,
    private val onCloseClick: () -> Unit,
    private val onReadingModeChange: ((PdfReadingModeStore.Mode) -> Unit)? = null,
    private val onThemeChange: ((PdfThemeModeStore.ThemeMode) -> Unit)? = null,
    private val onFitModeChange: ((PdfPageFitStore.FitMode) -> Unit)? = null,
    private val onTocClick: (() -> Unit)? = null
) {
    private val dp = context.resources.displayMetrics.density
    private var activePdfTheme: PdfThemeModeStore.ThemeMode? = null
    private var activeFitMode: PdfPageFitStore.FitMode? = null

    lateinit var topToolbar: LinearLayout
        private set
    lateinit var bottomToolbar: LinearLayout
        private set

    private lateinit var titleView: TextView
    private lateinit var statusTextView: TextView

    /** The margin line shown while the toolbars are hidden; built with the bottom toolbar. */
    var immersiveStatus: NexusReaderImmersiveStatus? = null
        private set
    private lateinit var progressBar: ProgressBar
    var areToolbarsVisible = true
        private set

    fun updateInsets(statusBarHeight: Int, navigationBarHeight: Int) {
        if (::topToolbar.isInitialized) {
            topToolbar.setPadding((8 * dp).toInt(), statusBarHeight, (12 * dp).toInt(), 0)
            val lp = topToolbar.layoutParams as? FrameLayout.LayoutParams
            if (lp != null) {
                lp.height = statusBarHeight + (54 * dp).toInt()
                topToolbar.layoutParams = lp
            }
        }
        if (::bottomToolbar.isInitialized) {
            bottomToolbar.setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), navigationBarHeight + (8 * dp).toInt())
        }
        immersiveStatus?.applyInsets(navigationBarHeight)
    }

    fun buildTopToolbar(
        statusBarHeight: Int,
        palette: NexusReaderThemeHelper.ReaderPalette,
        documentTitle: String,
        currentPdfMode: PdfReadingModeStore.Mode? = null,
        currentPdfTheme: PdfThemeModeStore.ThemeMode? = null,
        currentPdfFitMode: PdfPageFitStore.FitMode? = null,
        showToc: Boolean = false,
        isEpub: Boolean = false
    ): LinearLayout {
        activePdfTheme = currentPdfTheme
        activeFitMode = currentPdfFitMode
        topToolbar = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((8 * dp).toInt(), statusBarHeight, (12 * dp).toInt(), 0)
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                statusBarHeight + (54 * dp).toInt(),
                Gravity.TOP
            )
            background = GradientDrawable().apply {
                setColor(palette.surface)
                setStroke((1 * dp).toInt().coerceAtLeast(1), palette.divider)
            }

            // Back button
            val backBtn = ImageView(context).apply {
                setImageResource(R.drawable.ic_back_arrow)
                setColorFilter(palette.textPrimary, PorterDuff.Mode.SRC_IN)
                imageTintList = ColorStateList.valueOf(palette.textPrimary)
                val p = (10 * dp).toInt()
                setPadding(p, p, p, p)
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onBackClick()
                }
            }
            addView(backBtn, LinearLayout.LayoutParams((44 * dp).toInt(), (44 * dp).toInt()))

            // Document Title
            titleView = TextView(context).apply {
                text = documentTitle
                textSize = 15f
                setTextColor(palette.textPrimary)
                typeface = if (palette.isEInk) Typeface.SERIF else Typeface.DEFAULT_BOLD
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = (6 * dp).toInt()
                    marginEnd = (8 * dp).toInt()
                }
            }
            addView(titleView)

            // Table of Contents button (EPUB)
            if (showToc && onTocClick != null) {
                val tocBtn = ImageView(context).apply {
                    setImageResource(R.drawable.ic_toc)
                    setColorFilter(palette.textPrimary, PorterDuff.Mode.SRC_IN)
                    imageTintList = ColorStateList.valueOf(palette.textPrimary)
                    contentDescription = context.getString(R.string.nexus_epub_toc_description)
                    val p = (10 * dp).toInt()
                    setPadding(p, p, p, p)
                    setOnClickListener {
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onTocClick.invoke()
                    }
                }
                addView(tocBtn, LinearLayout.LayoutParams((40 * dp).toInt(), (40 * dp).toInt()))
            }

            // PDF Mode Selector (Overflow Menu)
            if (currentPdfMode != null) {
                val modeBtn = ImageView(context).apply {
                    setImageResource(R.drawable.ic_menu)
                    setColorFilter(palette.textPrimary, PorterDuff.Mode.SRC_IN)
                    imageTintList = ColorStateList.valueOf(palette.textPrimary)
                    contentDescription = context.getString(R.string.nexus_pdf_mode_title)
                    val p = (10 * dp).toInt()
                    setPadding(p, p, p, p)
                    setOnClickListener {
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        showReadingModeDialog(it, currentPdfMode, palette.isEInk, activePdfTheme, activeFitMode, isEpub)
                    }
                }
                addView(modeBtn, LinearLayout.LayoutParams((40 * dp).toInt(), (40 * dp).toInt()))
            }

            // Close button
            val closeBtn = ImageView(context).apply {
                setImageResource(R.drawable.ic_close)
                setColorFilter(palette.textPrimary, PorterDuff.Mode.SRC_IN)
                imageTintList = ColorStateList.valueOf(palette.textPrimary)
                contentDescription = context.getString(R.string.nexus_doc_overflow_close)
                val p = (10 * dp).toInt()
                setPadding(p, p, p, p)
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    onCloseClick()
                }
            }
            addView(closeBtn, LinearLayout.LayoutParams((40 * dp).toInt(), (40 * dp).toInt()))
        }
        return topToolbar
    }

    fun updatePdfTheme(theme: PdfThemeModeStore.ThemeMode) {
        activePdfTheme = theme
        immersiveStatus?.applyTheme(theme)
    }

    fun updatePdfFitMode(mode: PdfPageFitStore.FitMode) {
        activeFitMode = mode
    }

    private fun showReadingModeDialog(
        anchorView: View,
        activeMode: PdfReadingModeStore.Mode,
        isEInk: Boolean,
        theme: PdfThemeModeStore.ThemeMode?,
        fitMode: PdfPageFitStore.FitMode?,
        isEpub: Boolean = false
    ) {
        NexusDocumentMenuCardDialog(
            context = context,
            anchorView = anchorView,
            activeMode = activeMode,
            isEInk = isEInk,
            activePdfTheme = theme,
            activeFitMode = fitMode,
            isEpub = isEpub,
            onThemeSelected = { newTheme ->
                activePdfTheme = newTheme
                onThemeChange?.invoke(newTheme)
            },
            onFitModeSelected = { newFit ->
                activeFitMode = newFit
                onFitModeChange?.invoke(newFit)
            },
            onModeSelected = { newMode ->
                onReadingModeChange?.invoke(newMode)
            }
        ).show()
    }

    fun buildBottomToolbar(
        navigationBarHeight: Int,
        palette: NexusReaderThemeHelper.ReaderPalette,
        initialStatus: String
    ): LinearLayout {
        bottomToolbar = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), navigationBarHeight + (8 * dp).toInt())
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
            background = GradientDrawable().apply {
                setColor(palette.surface)
                setStroke((1 * dp).toInt().coerceAtLeast(1), palette.divider)
            }

            // Visual Progress Bar
            progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                isIndeterminate = false
                max = 100
                progress = 0
                progressDrawable = ColorDrawable(palette.textPrimary)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (3 * dp).toInt()).apply {
                    bottomMargin = (6 * dp).toInt()
                }
            }
            addView(progressBar)

            // Status Text (Page X of Y or Reading)
            statusTextView = TextView(context).apply {
                text = initialStatus
                textSize = 12f
                typeface = if (palette.isEInk) Typeface.MONOSPACE else Typeface.DEFAULT
                setTextColor(palette.textSecondary)
                gravity = Gravity.CENTER
            }
            addView(statusTextView)
        }
        immersiveStatus = NexusReaderImmersiveStatus(context, palette).apply {
            applyInsets(navigationBarHeight)
        }
        return bottomToolbar
    }

    fun updateStatus(status: String, progressPercent: Int) {
        if (::statusTextView.isInitialized) {
            statusTextView.text = status
        }
        immersiveStatus?.setPageStatus(status)
        if (::progressBar.isInitialized) {
            progressBar.progress = progressPercent.coerceIn(0, 100)
        }
    }

    fun showToolbars() {
        if (areToolbarsVisible) return
        areToolbarsVisible = true
        immersiveStatus?.hide()
        topToolbar.animate().translationY(0f).setDuration(160).start()
        bottomToolbar.animate().translationY(0f).setDuration(160).start()
    }

    fun hideToolbars() {
        if (!areToolbarsVisible) return
        areToolbarsVisible = false
        immersiveStatus?.show()
        topToolbar.animate().translationY(-topToolbar.height.toFloat()).setDuration(160).start()
        bottomToolbar.animate().translationY(bottomToolbar.height.toFloat()).setDuration(160).start()
    }

    fun toggleToolbars() {
        if (areToolbarsVisible) hideToolbars() else showToolbars()
    }
}
