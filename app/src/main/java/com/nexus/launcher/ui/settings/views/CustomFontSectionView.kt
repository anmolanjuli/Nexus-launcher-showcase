package com.nexus.launcher.ui.settings.views

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeEntryPoint
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.CustomFontEntry
import com.nexus.launcher.typography.NexusTypeScale
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Renders custom font chips and an "+ Add Font" action button within theme settings.
 * Monochrome design with full token reactivity in Light and Dark modes.
 */
class CustomFontSectionView(context: Context) : LinearLayout(context) {

    private val dp = resources.displayMetrics.density
    private val chipsContainer: LinearLayout
    private val scrollView: HorizontalScrollView
    private val labelView: TextView
    private val addBtnView: TextView
    private val addBtnBg = GradientDrawable()
    private var currentTokens: NexusColorTokens = NexusColorTokens.Dark

    var onAddFontClicked: (() -> Unit)? = null
    var onFontSelected: ((String) -> Unit)? = null
    var onFontDeleted: ((String) -> Unit)? = null

    private var customFonts: List<CustomFontEntry> = emptyList()
    private var selectedKey: String = ""

    init {
        orientation = VERTICAL
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
            topMargin = (10 * dp).toInt()
        }

        currentTokens = try {
            ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }

        val headerRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            gravity = Gravity.CENTER_VERTICAL
        }

        labelView = TextView(context).apply {
            text = context.getString(R.string.font_custom_section).uppercase()
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        }
        NexusTypeScale.labelSmall.bindTo(labelView, currentTokens.textSecondary)

        addBtnBg.setColor(currentTokens.surfaceRaised)
        addBtnBg.cornerRadius = 8 * dp
        addBtnBg.setStroke((1 * dp).toInt(), currentTokens.divider)

        addBtnView = TextView(context).apply {
            text = context.getString(R.string.font_action_add_custom)
            background = addBtnBg
            val hPad = (10 * dp).toInt()
            val vPad = (4 * dp).toInt()
            setPadding(hPad, vPad, hPad, vPad)
            setOnClickListener {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                // Importing is Premium; fonts already imported stay selectable.
                if (com.nexus.launcher.premium.PremiumGate.allow(context, com.nexus.launcher.premium.PremiumFeature.CUSTOM_FONTS)) onAddFontClicked?.invoke()
            }
        }
        NexusTypeScale.caption.bindTo(addBtnView, currentTokens.textPrimary)

        headerRow.addView(labelView)
        val pill = com.nexus.launcher.ui.premium.PremiumBadges.pill(context, currentTokens)
        headerRow.addView(pill, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
            marginStart = (8 * dp).toInt()
        })
        com.nexus.launcher.ui.premium.PremiumBadges.bindPill(pill, com.nexus.launcher.premium.PremiumFeature.CUSTOM_FONTS)
        headerRow.addView(View(context), LayoutParams(0, 0, 1f))
        headerRow.addView(addBtnView)
        addView(headerRow)

        chipsContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = ViewGroup.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        }

        scrollView = HorizontalScrollView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = (6 * dp).toInt()
            }
            isHorizontalScrollBarEnabled = false
            overScrollMode = OVER_SCROLL_NEVER
            addView(chipsContainer)
        }
        addView(scrollView)

        setupThemeBinding()
    }

    fun applyTokens(tokens: NexusColorTokens) {
        currentTokens = tokens
        labelView.setTextColor(tokens.textSecondary)
        addBtnBg.setColor(tokens.surfaceRaised)
        addBtnBg.setStroke((1 * dp).toInt(), tokens.divider)
        addBtnView.setTextColor(tokens.textPrimary)
        render(customFonts, selectedKey)
    }

    fun render(fonts: List<CustomFontEntry>, candidateKey: String) {
        customFonts = fonts
        selectedKey = candidateKey
        chipsContainer.removeAllViews()

        if (fonts.isEmpty()) {
            return
        }

        for (font in fonts) {
            val fontKey = "custom:${font.id}"
            val isSelected = fontKey == candidateKey
            val chip = buildChip(font, isSelected)
            chipsContainer.addView(chip)
        }
    }

    private fun buildChip(font: CustomFontEntry, isSelected: Boolean): View {
        val chip = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                marginEnd = (6 * dp).toInt()
            }
            background = GradientDrawable().apply {
                if (isSelected) {
                    setColor(currentTokens.surfaceRaised)
                    setStroke((1.5f * dp).toInt(), currentTokens.textPrimary)
                } else {
                    setColor(currentTokens.surface)
                    setStroke((1 * dp).toInt(), currentTokens.divider)
                }
                cornerRadius = 10 * dp
            }
            val hPad = (8 * dp).toInt()
            val vPad = (4 * dp).toInt()
            setPadding(hPad, vPad, (4 * dp).toInt(), vPad)
        }

        val displayName = if (font.name.length > 12) font.name.take(11) + "…" else font.name

        val nameView = TextView(context).apply {
            text = displayName
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            maxWidth = (85 * dp).toInt()
            setOnClickListener {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                onFontSelected?.invoke("custom:${font.id}")
            }
        }
        NexusTypeScale.caption.bindTo(
            nameView,
            if (isSelected) currentTokens.textPrimary else currentTokens.textSecondary
        )

        val deleteBtn = TextView(context).apply {
            text = "✕"
            setPadding((6 * dp).toInt(), (2 * dp).toInt(), (4 * dp).toInt(), (2 * dp).toInt())
            setOnClickListener {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                showDeleteDialog(font)
            }
        }
        NexusTypeScale.labelSmall.bindTo(deleteBtn, currentTokens.textSecondary)

        chip.addView(nameView)
        chip.addView(deleteBtn)
        return chip
    }

    private fun showDeleteDialog(font: CustomFontEntry) {
        val dialogView = LinearLayout(context).apply {
            orientation = VERTICAL
            val pad = (20 * dp).toInt()
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                setColor(currentTokens.surface)
                cornerRadius = 16 * dp
                setStroke((1 * dp).toInt(), currentTokens.divider)
            }
        }

        val titleView = TextView(context).apply {
            text = context.getString(R.string.dialog_delete_font_title)
            setPadding(0, 0, 0, (8 * dp).toInt())
        }
        NexusTypeScale.bodyStrong.bindTo(titleView, currentTokens.textPrimary)

        val msgView = TextView(context).apply {
            text = context.getString(R.string.dialog_delete_font_message, font.name)
            setPadding(0, 0, 0, (20 * dp).toInt())
        }
        NexusTypeScale.caption.bindTo(msgView, currentTokens.textSecondary)

        val buttonsRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.END
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        var dialog: AlertDialog? = null

        val cancelBtn = TextView(context).apply {
            text = context.getString(android.R.string.cancel)
            setPadding((14 * dp).toInt(), (8 * dp).toInt(), (14 * dp).toInt(), (8 * dp).toInt())
            setOnClickListener {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                dialog?.dismiss()
            }
        }
        NexusTypeScale.caption.bindTo(cancelBtn, currentTokens.textSecondary)

        val colorBlind = com.nexus.launcher.theme.ThemeObserver.currentColorBlindMode(context)
        val deleteBtn = TextView(context).apply {
            text = context.getString(R.string.action_delete)
            setPadding((14 * dp).toInt(), (8 * dp).toInt(), (14 * dp).toInt(), (8 * dp).toInt())
            if (colorBlind != com.nexus.launcher.theme.ColorBlindMode.NONE) {
                background = android.graphics.drawable.GradientDrawable().apply {
                    cornerRadius = 8 * dp
                    setStroke((1.5f * dp).toInt(), currentTokens.danger)
                    setColor(Color.TRANSPARENT)
                }
            }
            setOnClickListener {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                onFontDeleted?.invoke(font.id)
                dialog?.dismiss()
            }
        }
        NexusTypeScale.bodyStrong.bindTo(deleteBtn, currentTokens.danger)

        buttonsRow.addView(cancelBtn)
        buttonsRow.addView(deleteBtn)

        dialogView.addView(titleView)
        dialogView.addView(msgView)
        dialogView.addView(buttonsRow)

        dialog = AlertDialog.Builder(context, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setView(dialogView)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
    }

    private fun setupThemeBinding() {
        var job: Job? = null
        addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                val owner: LifecycleOwner = (v as? CustomFontSectionView)?.findViewTreeLifecycleOwner() ?: return
                job?.cancel()
                job = owner.lifecycleScope.launch {
                    owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        try {
                            val controller = EntryPointAccessors.fromApplication(
                                v.context.applicationContext,
                                ThemeEntryPoint::class.java
                            ).themeController()
                            controller.currentTokens.collect { tokens ->
                                applyTokens(tokens)
                            }
                        } catch (_: Exception) {}
                    }
                }
            }

            override fun onViewDetachedFromWindow(v: View) {
                job?.cancel()
                job = null
            }
        })
    }
}
