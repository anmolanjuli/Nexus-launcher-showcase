package com.nexus.launcher.ui.premium.showcase

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.nexus.launcher.R
import com.nexus.launcher.premium.PremiumFeature
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.glass.NeumorphicSurfaces

/**
 * The Premium page's hero card and value footer, and the card surface every section shares.
 */
internal object PremiumShowcaseSections {

    private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    /**
     * A card in the page's UI style: raised in Neumorphism, otherwise a surface with a hairline and
     * a soft system shadow.
     */
    fun surface(view: View, tokens: NexusColorTokens, radiusDp: Float) {
        val dp = view.resources.displayMetrics.density
        val flat = GradientDrawable().apply {
            cornerRadius = radiusDp * dp
            setColor(tokens.surface)
            setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
        }
        val bg: Drawable? = NeumorphicSurfaces.raisedOr(view, tokens, radiusDp * dp) { flat }
        view.background = bg
        if (bg === flat) {
            view.outlineProvider = ViewOutlineProvider.BACKGROUND
            view.elevation = 3 * dp
        }
    }

    /** A rounded frame that clips a picture to its corners, apart from the card's own shadow. */
    fun pictureFrame(context: Context, tokens: NexusColorTokens, radiusDp: Float): FrameLayout =
        FrameLayout(context).apply {
            background = GradientDrawable().apply {
                cornerRadius = radiusDp * context.resources.displayMetrics.density
                setColor(tokens.surfaceRaised)
            }
            clipToOutline = true
        }

    fun accentButton(context: Context, tokens: NexusColorTokens, label: String, onClick: () -> Unit): TextView {
        val dp = context.resources.displayMetrics.density
        return TextView(context).apply {
            text = label
            gravity = Gravity.CENTER
            // The primary control colours Settings uses everywhere (a selected segment, the Apply
            // bar): textPrimary with bg on it. The accent is left to the pictures.
            NexusTypeScale.bodyStrong.bindTo(this, tokens.bg)
            background = GradientDrawable().apply {
                cornerRadius = 16 * dp
                setColor(tokens.textPrimary)
            }
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
        }
    }

    fun hero(
        context: Context,
        feature: PremiumFeature,
        tokens: NexusColorTokens,
        onPreview: (PremiumFeature) -> Unit,
    ): View {
        val dp = context.resources.displayMetrics.density
        val card = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(MATCH, (280 * dp).toInt()).apply {
                bottomMargin = (16 * dp).toInt()
            }
            surface(this, tokens, 24f)
            setOnClickListener { onPreview(feature) }
        }
        val frame = pictureFrame(context, tokens, 24f)
        card.addView(frame, FrameLayout.LayoutParams(MATCH, MATCH))
        frame.addView(PremiumShowcaseArt.view(context, feature, tokens), FrameLayout.LayoutParams(MATCH, MATCH))
        // A scrim from the background colour, so the title reads over any picture.
        frame.addView(View(context).apply {
            background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(ColorUtils.setAlphaComponent(tokens.bg, 0), ColorUtils.setAlphaComponent(tokens.bg, 0xEE)),
            )
        }, FrameLayout.LayoutParams(MATCH, (180 * dp).toInt(), Gravity.BOTTOM))

        val pad = (20 * dp).toInt()
        val overlay = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
            addView(TextView(context).apply {
                text = context.getString(feature.titleRes)
                NexusTypeScale.title.bindTo(this, tokens.textPrimary)
                textSize = 22f
            })
            addView(TextView(context).apply {
                text = context.getString(feature.descriptionRes)
                NexusTypeScale.caption.bindTo(this, tokens.textSecondary)
                textSize = 14f
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            }, LinearLayout.LayoutParams(MATCH, WRAP).apply { topMargin = (4 * dp).toInt() })
            addView(accentButton(context, tokens, context.getString(R.string.premium_action_preview)) { onPreview(feature) }.apply {
                val h = (10 * dp).toInt()
                setPadding(h * 2, h, h * 2, h)
            }, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = (14 * dp).toInt() })
        }
        frame.addView(overlay, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM))
        return card
    }

    fun footer(
        context: Context,
        tokens: NexusColorTokens,
        premium: Boolean,
        featureCount: Int,
        onUnlock: () -> Unit,
    ): View {
        val dp = context.resources.displayMetrics.density
        val pad = (20 * dp).toInt()
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, pad, pad, pad)
            layoutParams = LinearLayout.LayoutParams(MATCH, WRAP).apply { bottomMargin = (16 * dp).toInt() }
            surface(this, tokens, 24f)

            addView(TextView(context).apply {
                text = context.getString(R.string.premium_showcase_footer_title)
                gravity = Gravity.CENTER
                NexusTypeScale.title.bindTo(this, tokens.textPrimary)
                textSize = 20f
            })
            addView(TextView(context).apply {
                text = if (premium) {
                    context.getString(R.string.premium_showcase_footer_active)
                } else {
                    context.getString(R.string.premium_showcase_footer_count, featureCount)
                }
                gravity = Gravity.CENTER
                NexusTypeScale.caption.bindTo(this, tokens.textSecondary)
                textSize = 14f
            }, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = (4 * dp).toInt() })

            if (premium) return@apply
            addView(accentButton(context, tokens, context.getString(R.string.premium_cta_unlock), onUnlock),
                LinearLayout.LayoutParams(MATCH, (54 * dp).toInt()).apply { topMargin = (18 * dp).toInt() })
            addView(TextView(context).apply {
                text = context.getString(R.string.premium_showcase_cancel)
                gravity = Gravity.CENTER
                NexusTypeScale.caption.bindTo(this, tokens.textSecondary)
                textSize = 12f
            }, LinearLayout.LayoutParams(WRAP, WRAP).apply { topMargin = (10 * dp).toInt() })
        }
    }
}
