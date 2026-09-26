package com.nexus.launcher.ui.settings

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.nexus.launcher.R
import com.nexus.launcher.locale.AppLocale
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeEntryPoint
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.AppFontFamily
import com.nexus.launcher.typography.DynamicFontProvider
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.NexusDesignSystem
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Builds a compact two-column live preview row for font family and language settings in [ThemeSettingsFragment].
 * Fully token-reactive with crisp visibility in both Light and Dark modes.
 */
object ThemePreviewCards {

    /** Not a string resource: a translation into a non-Latin script would defeat its purpose. */
    private const val LATIN_SPECIMEN = "Aa Bb Gg Qq  0123456789"

    /**
     * One preview card for font and language together.
     *
     * They were two side-by-side cards, each with its own Apply button — so the font was shown
     * against Latin filler while the language was shown in the system typeface, and neither card
     * ever showed the combination you were actually choosing. One card renders the locale's own
     * sample sentence *in* the candidate typeface, which is the thing being decided.
     *
     * No Apply button: selections commit as they are made, like every other control in Settings.
     */
    class CombinedPreviewHolder(
        val container: View,
        private val cardBg: GradientDrawable,
        private val labelView: TextView,
        private val directionBadge: TextView,
        private val sampleText: TextView,
        private val sampleDigits: TextView,
    ) {
        fun update(
            fontKey: String,
            locale: AppLocale,
            customFonts: List<com.nexus.launcher.typography.CustomFontEntry>,
            context: Context,
        ) {
            val typeface = DynamicFontProvider(container.context, fontKey, customFonts)
                .getTypeface(Typeface.NORMAL)
            sampleText.typeface = typeface
            sampleDigits.typeface = typeface

            // The specimen line is hardcoded Latin rather than a string resource, deliberately.
            // Every bundled font is Latin-only, so under Nepali, Arabic or Hebrew the localized
            // sentence renders through the system fallback and the chosen font has no visible
            // effect at all — which read as the font preview being broken. A Latin specimen keeps
            // the typeface demonstrable in any app language, and the localized line below still
            // shows the language.
            sampleDigits.text = LATIN_SPECIMEN

            sampleText.text = when (locale) {
                AppLocale.SYSTEM -> context.getString(R.string.preview_locale_sample_system)
                AppLocale.ENGLISH -> context.getString(R.string.preview_locale_sample_english)
                AppLocale.SPANISH -> context.getString(R.string.preview_locale_sample_spanish)
                AppLocale.PORTUGUESE_BR -> context.getString(R.string.preview_locale_sample_portuguese_br)
                AppLocale.HINDI -> context.getString(R.string.preview_locale_sample_hindi)
                AppLocale.INDONESIAN -> context.getString(R.string.preview_locale_sample_indonesian)
                AppLocale.GERMAN -> context.getString(R.string.preview_locale_sample_german)
                AppLocale.FRENCH -> context.getString(R.string.preview_locale_sample_french)
                AppLocale.NEPALI -> context.getString(R.string.preview_locale_sample_nepali)
                AppLocale.ARABIC -> context.getString(R.string.preview_locale_sample_arabic)
                AppLocale.HEBREW -> context.getString(R.string.preview_locale_sample_hebrew)
            }
            val isRtl = locale == AppLocale.ARABIC || locale == AppLocale.HEBREW
            sampleText.textDirection = if (isRtl) View.TEXT_DIRECTION_RTL else View.TEXT_DIRECTION_LTR
            sampleText.gravity = if (isRtl) Gravity.END else Gravity.START
            directionBadge.text = context.getString(
                if (isRtl) R.string.preview_direction_badge_rtl else R.string.preview_direction_badge_ltr
            )
        }

        fun applyTokens(tokens: NexusColorTokens) {
            cardBg.setColor(tokens.surfaceRaised)
            cardBg.setStroke((1 * container.resources.displayMetrics.density).toInt(), tokens.divider)
            labelView.setTextColor(tokens.textSecondary)
            directionBadge.setTextColor(tokens.textSecondary)
            sampleDigits.setTextColor(tokens.textPrimary)
            sampleText.setTextColor(tokens.textSecondary)
        }

    }

    fun buildCombinedPreviewCard(context: Context): CombinedPreviewHolder {
        val dp = context.resources.displayMetrics.density
        val tokens = try { ThemeObserver.currentTokens(context) } catch (_: Exception) { NexusColorTokens.Dark }

        val cardBg = GradientDrawable().apply {
            setColor(tokens.surfaceRaised)
            cornerRadius = 16f * dp
            setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
        }

        val labelRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val labelView = TextView(context).apply {
            text = context.getString(R.string.preview_section_label)
            textSize = 11f
            isAllCaps = true
            letterSpacing = 0.08f
            setTextColor(tokens.textSecondary)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val directionBadge = TextView(context).apply {
            textSize = 10f
            isAllCaps = true
            letterSpacing = 0.06f
            setTextColor(tokens.textSecondary)
        }
        labelRow.addView(labelView)
        labelRow.addView(directionBadge)

        // Specimen first, at the larger size: the page is for choosing a typeface, and this is
        // the line that always shows it. The localized sentence sits under it.
        val sampleDigits = TextView(context).apply {
            text = LATIN_SPECIMEN
            textSize = 18f
            setTextColor(tokens.textPrimary)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (10 * dp).toInt() }
        }
        val sampleText = TextView(context).apply {
            textSize = 14f
            setTextColor(tokens.textSecondary)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (6 * dp).toInt() }
        }

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = cardBg
            clipToOutline = true
            val pad = (14 * dp).toInt()
            setPadding(pad, (12 * dp).toInt(), pad, (14 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (8 * dp).toInt() }
            addView(labelRow)
            addView(sampleDigits)
            addView(sampleText)
        }

        val holder = CombinedPreviewHolder(
            card, cardBg, labelView, directionBadge, sampleText, sampleDigits
        )
        // Keeps the card following theme changes, as the two-column row did.
        attachThemeListener(card, holder)
        return holder
    }

    private fun attachThemeListener(view: View, holder: CombinedPreviewHolder) {
        var job: Job? = null
        view.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                val owner: LifecycleOwner = v.findViewTreeLifecycleOwner() ?: return
                job?.cancel()
                job = owner.lifecycleScope.launch {
                    owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                        try {
                            val controller = EntryPointAccessors.fromApplication(
                                v.context.applicationContext,
                                ThemeEntryPoint::class.java
                            ).themeController()
                            controller.currentTokens.collect { tokens ->
                                holder.applyTokens(tokens)
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
