package com.nexus.launcher.reader.doc

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
import com.nexus.launcher.ui.glass.FloatingSurfaces

/**
 * Nexus-themed floating top menu card dialog for PDF reader settings:
 * Reading Mode toggle (Continuous vs Page Turn) and PDF Theme adaptation (Original, Paper Tint, Invert).
 */
class NexusDocumentMenuCardDialog(
    context: Context,
    private val anchorView: View?,
    private val activeMode: PdfReadingModeStore.Mode,
    private val isEInk: Boolean,
    private val activePdfTheme: PdfThemeModeStore.ThemeMode? = null,
    private val activeFitMode: PdfPageFitStore.FitMode? = null,
    private val isEpub: Boolean = false,
    private val onThemeSelected: ((PdfThemeModeStore.ThemeMode) -> Unit)? = null,
    private val onFitModeSelected: ((PdfPageFitStore.FitMode) -> Unit)? = null,
    private val onModeSelected: (PdfReadingModeStore.Mode) -> Unit
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

        val anchorPos = IntArray(2)
        anchorView?.getLocationOnScreen(anchorPos)

        val cardWidth = (330 * dp).toInt().coerceAtMost(
            context.resources.displayMetrics.widthPixels - (32 * dp).toInt()
        )

        val topMarginPx = if (anchorView != null) {
            anchorPos[1] + anchorView.height + (8 * dp).toInt()
        } else {
            (70 * dp).toInt()
        }

        val rightMarginPx = (16 * dp).toInt()

        val menuCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = FloatingSurfaces.sheetCard(tokens, 20 * dp, dp)
            setPadding((18 * dp).toInt(), (18 * dp).toInt(), (18 * dp).toInt(), (16 * dp).toInt())
            layoutParams = FrameLayout.LayoutParams(cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = topMarginPx
                rightMargin = rightMarginPx
            }
            setOnClickListener { /* Consume clicks inside card */ }
        }

        // Header
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (12 * dp).toInt())

            val title = TextView(context).apply {
                text = context.getString(R.string.nexus_pdf_mode_title)
                NexusTypeScale.title.bindTo(this, tokens.textPrimary)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            addView(title)

            val closeBtn = ImageView(context).apply {
                setImageResource(R.drawable.ic_close)
                imageTintList = ColorStateList.valueOf(tokens.textSecondary)
                val p = (4 * dp).toInt()
                setPadding(p, p, p, p)
                layoutParams = LinearLayout.LayoutParams((28 * dp).toInt(), (28 * dp).toInt())
                setOnClickListener {
                    performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    dismiss()
                }
            }
            addView(closeBtn)
        }
        menuCard.addView(headerRow)

        // Reading Mode Options
        val optContinuous = buildOptionRow(
            title = context.getString(R.string.nexus_pdf_mode_continuous),
            desc = context.getString(R.string.nexus_pdf_mode_continuous_desc),
            isSelected = activeMode == PdfReadingModeStore.Mode.CONTINUOUS_SCROLL,
            tokens = tokens
        ) {
            onModeSelected(PdfReadingModeStore.Mode.CONTINUOUS_SCROLL)
            dismiss()
        }
        menuCard.addView(optContinuous)

        if (!isEpub) {
            val optPageTurn = buildOptionRow(
                title = context.getString(R.string.nexus_pdf_mode_page_turn),
                desc = context.getString(R.string.nexus_pdf_mode_page_turn_desc),
                isSelected = activeMode == PdfReadingModeStore.Mode.PAGE_TURN,
                tokens = tokens
            ) {
                onModeSelected(PdfReadingModeStore.Mode.PAGE_TURN)
                dismiss()
            }
            menuCard.addView(optPageTurn)
        }

        // Page Fit Options (PDF only)
        if (activeFitMode != null && onFitModeSelected != null) {
            addDivider(menuCard, tokens)

            val fitHeader = TextView(context).apply {
                text = context.getString(R.string.nexus_pdf_fit_title)
                NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
                setPadding(0, 0, 0, (8 * dp).toInt())
            }
            menuCard.addView(fitHeader)

            // Fit: Smart Crop (Recommended)
            val optSmartCrop = buildOptionRow(
                title = context.getString(R.string.nexus_pdf_fit_smart_crop),
                desc = context.getString(R.string.nexus_pdf_fit_smart_crop_desc),
                isSelected = activeFitMode == PdfPageFitStore.FitMode.SMART_CROP,
                tokens = tokens
            ) {
                onFitModeSelected.invoke(PdfPageFitStore.FitMode.SMART_CROP)
                dismiss()
            }
            menuCard.addView(optSmartCrop)

            // Fit: Fit Width
            val optFitWidth = buildOptionRow(
                title = context.getString(R.string.nexus_pdf_fit_width),
                desc = context.getString(R.string.nexus_pdf_fit_width_desc),
                isSelected = activeFitMode == PdfPageFitStore.FitMode.FIT_WIDTH,
                tokens = tokens
            ) {
                onFitModeSelected.invoke(PdfPageFitStore.FitMode.FIT_WIDTH)
                dismiss()
            }
            menuCard.addView(optFitWidth)

            // Fit: Fit Page
            val optFitPage = buildOptionRow(
                title = context.getString(R.string.nexus_pdf_fit_page),
                desc = context.getString(R.string.nexus_pdf_fit_page_desc),
                isSelected = activeFitMode == PdfPageFitStore.FitMode.FIT_PAGE,
                tokens = tokens
            ) {
                onFitModeSelected.invoke(PdfPageFitStore.FitMode.FIT_PAGE)
                dismiss()
            }
            menuCard.addView(optFitPage)
        }

        // PDF Theme Adaptation Options (PDF only)
        if (activePdfTheme != null && onThemeSelected != null) {
            addDivider(menuCard, tokens)

            val themeHeader = TextView(context).apply {
                text = context.getString(R.string.nexus_pdf_theme_title)
                NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
                setPadding(0, 0, 0, (8 * dp).toInt())
            }
            menuCard.addView(themeHeader)

            // Theme: Original
            val optOriginal = buildOptionRow(
                title = context.getString(R.string.nexus_pdf_theme_original),
                desc = context.getString(R.string.nexus_pdf_theme_original_desc),
                isSelected = activePdfTheme == PdfThemeModeStore.ThemeMode.ORIGINAL,
                tokens = tokens
            ) {
                onThemeSelected.invoke(PdfThemeModeStore.ThemeMode.ORIGINAL)
                dismiss()
            }
            menuCard.addView(optOriginal)

            // Theme: Paper Tint
            val optPaperTint = buildOptionRow(
                title = context.getString(R.string.nexus_pdf_theme_paper_tint),
                desc = context.getString(R.string.nexus_pdf_theme_paper_tint_desc),
                isSelected = activePdfTheme == PdfThemeModeStore.ThemeMode.PAPER_TINT,
                tokens = tokens
            ) {
                onThemeSelected.invoke(PdfThemeModeStore.ThemeMode.PAPER_TINT)
                dismiss()
            }
            menuCard.addView(optPaperTint)

            // Theme: Invert Colors
            val optInvert = buildOptionRow(
                title = context.getString(R.string.nexus_pdf_theme_invert_colors),
                desc = context.getString(R.string.nexus_pdf_theme_invert_colors_desc),
                isSelected = activePdfTheme == PdfThemeModeStore.ThemeMode.INVERT_COLORS,
                tokens = tokens
            ) {
                onThemeSelected.invoke(PdfThemeModeStore.ThemeMode.INVERT_COLORS)
                dismiss()
            }
            menuCard.addView(optInvert)
        }

        root.addView(menuCard)
        setContentView(root)
    }

    private fun addDivider(menuCard: LinearLayout, tokens: NexusColorTokens) {
        val divider = View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (1 * dp).toInt().coerceAtLeast(1)
            ).apply {
                topMargin = (8 * dp).toInt()
                bottomMargin = (12 * dp).toInt()
            }
            setBackgroundColor(tokens.divider)
        }
        menuCard.addView(divider)
    }

    private fun buildOptionRow(
        title: String,
        desc: String,
        isSelected: Boolean,
        tokens: NexusColorTokens,
        onClick: () -> Unit
    ): View {
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
            background = GradientDrawable().apply {
                setColor(if (isSelected) tokens.surfaceRaised else Color.TRANSPARENT)
                cornerRadius = 12 * dp
                if (isSelected) {
                    setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
                }
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (4 * dp).toInt()
            }

            val textCol = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = (8 * dp).toInt()
                }

                val titleView = TextView(context).apply {
                    text = title
                    NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
                }
                addView(titleView)

                val descView = TextView(context).apply {
                    text = desc
                    NexusTypeScale.caption.bindTo(this, tokens.textSecondary)
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = (2 * dp).toInt()
                    }
                }
                addView(descView)
            }
            addView(textCol)

            if (isSelected) {
                val check = ImageView(context).apply {
                    setImageResource(R.drawable.ic_check)
                    imageTintList = ColorStateList.valueOf(tokens.textPrimary)
                    layoutParams = LinearLayout.LayoutParams((18 * dp).toInt(), (18 * dp).toInt())
                }
                addView(check)
            }

            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            }
        }
    }
}
