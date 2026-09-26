package com.nexus.launcher.ui.settings

import android.app.Dialog
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.widget.doAfterTextChanged
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.glass.FrostedGlassEngine
import com.nexus.launcher.ui.settings.views.NexusNavRow
import com.nexus.launcher.ui.settings.views.NexusSection

class SettingsJumpSheet(
    private val currentPagerIndex: Int,
    private val onPageChosen: (Int) -> Unit,
    /** A setting found by search: its page, and the row label to scroll to on it. */
    private val onControlChosen: (Int, String) -> Unit = { page, _ -> onPageChosen(page) },
) : BottomSheetDialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext()).also { com.nexus.launcher.ui.LandscapeSheets.apply(it) }.apply {
            window?.let { win ->
                WindowCompat.setDecorFitsSystemWindows(win, false)
                win.statusBarColor = Color.TRANSPARENT
                win.navigationBarColor = Color.TRANSPARENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    win.isNavigationBarContrastEnforced = false
                    win.isStatusBarContrastEnforced = false
                }
                win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                FrostedGlassEngine.applyDialogWindowChrome(win)
                win.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                win.setDimAmount(0f)
            }
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
            setOnShowListener { dialog ->
                val d = dialog as BottomSheetDialog
                d.findViewById<View>(com.google.android.material.R.id.touch_outside)?.setBackgroundColor(Color.TRANSPARENT)
                d.findViewById<View>(com.google.android.material.R.id.coordinator)?.setBackgroundColor(Color.TRANSPARENT)
                val sheet = d.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
                if (sheet != null) {
                    sheet.backgroundTintList = null
                    sheet.setBackgroundResource(android.R.color.transparent)
                    sheet.setBackgroundColor(Color.TRANSPARENT)
                    val behavior = BottomSheetBehavior.from(sheet)
                    behavior.isFitToContents = true
                    behavior.skipCollapsed = true
                    behavior.isDraggable = false
                    behavior.state = BottomSheetBehavior.STATE_EXPANDED
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        val dp = resources.displayMetrics.density
        val tokens = try {
            ThemeObserver.currentTokens(requireContext())
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }

        val radiusPx = 24f * dp
        val marginH = (10 * dp).toInt()
        val baseMarginBottom = (8 * dp).toInt()

        val root = FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                setMargins(marginH, 0, marginH, baseMarginBottom)
            }
            background = GradientDrawable().apply {
                val frostedTokens = FrostedGlassEngine.resolveFrostedTokens(tokens)
                val fillAlpha = (FrostedGlassEngine.sheetFillAlpha(1f, 0.70f) * 255f).toInt()
                setColor((frostedTokens.surface and 0x00FFFFFF) or (fillAlpha shl 24))
                cornerRadius = radiusPx
                setStroke((1 * dp).toInt().coerceAtLeast(1), frostedTokens.border)
            }
            clipToOutline = true
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, outline: Outline) {
                    if (v.width <= 0 || v.height <= 0) return
                    outline.setRoundRect(0, 0, v.width, v.height, radiusPx)
                }
            }
            val pad = (16 * dp).toInt()
            setPadding(pad, 0, pad, (16 * dp).toInt())
        }

        root.addView(card)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val bottomMargin = navBars.bottom + baseMarginBottom
            (card.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                if (lp.bottomMargin != bottomMargin) {
                    lp.setMargins(marginH, 0, marginH, bottomMargin)
                    card.layoutParams = lp
                }
            }
            insets
        }

        val handle = View(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams((40 * dp).toInt(), (4 * dp).toInt()).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = (11 * dp).toInt()
                bottomMargin = (10 * dp).toInt()
            }
            background = GradientDrawable().apply {
                cornerRadius = 2f * dp
                setColor(tokens.divider)
            }
        }
        card.addView(handle)

        val title = TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = (10 * dp).toInt() }
            gravity = Gravity.CENTER
            text = getString(R.string.settings_jump_title)
        }
        NexusTypeScale.bodyStrong.bindTo(title, tokens.textPrimary)
        card.addView(title)

        // The jump sheet is the fast path between pages, so it gets the same search the landing
        // page has — including the per-control index, so "spacing" or "badges" lands you on the
        // page that owns it rather than making you remember which page that is.
        val searchField = EditText(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply { bottomMargin = (6 * dp).toInt() }
            hint = getString(R.string.settings_search_hint)
            isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            inputType = InputType.TYPE_CLASS_TEXT
            background = GradientDrawable().apply {
                val frostedTokens = FrostedGlassEngine.resolveFrostedTokens(tokens)
                val fill = (FrostedGlassEngine.sheetFillAlpha(1f, 0.70f) * 255f).toInt()
                setColor((frostedTokens.surfaceRaised and 0x00FFFFFF) or (fill shl 24))
                cornerRadius = 14 * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), frostedTokens.border)
            }
            val padH = (14 * dp).toInt()
            val padV = (10 * dp).toInt()
            setPadding(padH, padV, padH, padV)
            setHintTextColor(tokens.textSecondary)
        }
        NexusTypeScale.body.bindTo(searchField, tokens.textPrimary)
        card.addView(searchField)

        val scroll = ScrollView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
        }
        scroll.addView(content)
        card.addView(scroll)

        renderRows(content, tokens, "")
        searchField.doAfterTextChanged { renderRows(content, tokens, it?.toString().orEmpty()) }
        return root
    }

    /**
     * Empty query shows every page under its group heading; a query collapses that into one flat
     * result list, so there is no hunting through headings for the thing you just typed.
     */
    private fun renderRows(content: LinearLayout, tokens: NexusColorTokens, query: String) {
        content.removeAllViews()

        if (query.isBlank()) {
            SettingsHubGroup.entries.forEach { group ->
                val section = NexusSection(requireContext())
                section.setTitle(getString(group.titleRes))
                section.setRows(
                    SettingsHubCatalog.pagesIn(group).map { page -> pageRow(page, tokens) },
                )
                content.addView(section)
            }
            return
        }

        val result = SettingsHubCatalog.search(requireContext(), query)
        val rows = mutableListOf<View>()
        result.pages.forEach { page -> rows.add(pageRow(page, tokens)) }
        // A matching control is shown under the page that owns it, labelled with the control's
        // own name so the match is obvious.
        result.controls.forEach { control ->
            val page = SettingsHubCatalog.pageAt(control.pagerIndex)
            rows.add(
                NexusNavRow(
                    requireContext(),
                    title = getString(control.titleRes),
                    subtitle = getString(page.titleRes),
                    iconRes = page.iconRes,
                ) {
                    onControlChosen(control.pagerIndex, getString(control.titleRes))
                    dismiss()
                }.apply {
                    setLandingTitle()
                    applyTokens(tokens)
                },
            )
        }

        if (rows.isEmpty()) {
            content.addView(
                TextView(requireContext()).apply {
                    text = getString(R.string.settings_search_no_results)
                    gravity = Gravity.CENTER
                    val pad = (20 * resources.displayMetrics.density).toInt()
                    setPadding(pad, pad, pad, pad)
                    NexusTypeScale.body.bindTo(this, tokens.textSecondary)
                },
            )
            return
        }

        content.addView(
            NexusSection(requireContext()).apply {
                setTitle(getString(R.string.settings_jump_results))
                setRows(rows)
            },
        )
    }

    private fun pageRow(page: SettingsHubPage, tokens: NexusColorTokens): NexusNavRow {
        val isCurrent = page.pagerIndex == currentPagerIndex
        return NexusNavRow(
            requireContext(),
            title = getString(page.titleRes),
            // Was the group name — which the section heading directly above already says, so
            // every row repeated its own heading. The page subtitle says what is actually on it.
            subtitle = getString(page.subtitleRes),
            iconRes = page.iconRes,
            showChevron = !isCurrent,
        ) {
            if (!isCurrent) onPageChosen(page.pagerIndex)
            dismiss()
        }.apply {
            setLandingTitle()
            if (isCurrent) setEndAsCheck(true)
            applyTokens(tokens)
        }
    }

    companion object {
        const val TAG = "SettingsJumpSheet"
    }
}
