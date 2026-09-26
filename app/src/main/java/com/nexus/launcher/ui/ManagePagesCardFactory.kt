package com.nexus.launcher.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.typography.NexusTypeScale

/**
 * Premium phone-frame page cards + ghost Add card for Manage Pages.
 * View tags: [TAG_THUMB], [TAG_PAGE_NUM], [TAG_HOME_BADGE], [TAG_DELETE], [TAG_SET_HOME].
 */
object ManagePagesCardFactory {

    const val TAG_THUMB = "mp_thumb"
    const val TAG_PAGE_NUM = "mp_page_num"
    const val TAG_HOME_BADGE = "mp_home_badge"
    const val TAG_DELETE = "mp_delete"
    const val TAG_SET_HOME = "mp_set_home"

    /** Hairline rim only — almost flush, still a visible phone frame. */
    private const val BEZEL_INSET_DP = 1f
    private const val BEZEL_RADIUS_DP = 16f
    private const val SCREEN_RADIUS_DP = 14.5f
    private const val CHIP_SIZE_DP = 34f
    private const val CHIP_CORNER_DP = 12f
    private const val PILL_CORNER_DP = 12f
    const val CURRENT_SCALE = 1.03f

    fun cardGridParams(density: Float, columnWidthHintPx: Int = 0) =
        android.widget.GridLayout.LayoutParams().apply {
            width = 0
            // Prefer aspect-locked height so GridLayout gets a real row size on first pass
            height = if (columnWidthHintPx > 0) {
                (columnWidthHintPx * ManagePagesThumbSpec.HEIGHT_OVER_WIDTH).toInt()
            } else {
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            }
            columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
            setMargins(
                (8 * density).toInt(), (8 * density).toInt(),
                (8 * density).toInt(), (16 * density).toInt()
            )
        }

    fun createPageCard(
        context: Context,
        density: Float,
        tokens: NexusColorTokens,
        columnWidthHintPx: Int = 0
    ): ManagePagesPhoneFrame {
        val card = ManagePagesPhoneFrame(context).apply {
            layoutParams = cardGridParams(density, columnWidthHintPx)
            background = bezelDrawable(density, tokens, selected = false)
            elevation = 4f * density
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        }

        val inset = (BEZEL_INSET_DP * density).toInt().coerceAtLeast(1)
        val screen = FrameLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply { setMargins(inset, inset, inset, inset) }
            background = GradientDrawable().apply {
                setColor(tokens.surface)
                cornerRadius = SCREEN_RADIUS_DP * density
            }
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        }

        screen.addView(ImageView(context).apply {
            tag = TAG_THUMB
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
        })

        // Home-indicator hairline sits on the screen (not in a fat bezel strip)
        screen.addView(View(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                (32 * density).toInt(), (3.5f * density).toInt()
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                bottomMargin = (5 * density).toInt()
            }
            background = GradientDrawable().apply {
                setColor(tokens.textSecondary)
                cornerRadius = 2f * density
            }
        })

        screen.addView(pageNumberPill(context, density, tokens))
        screen.addView(homeBadgePill(context, density, tokens))
        screen.addView(actionChip(
            context, density, tokens, TAG_DELETE,
            R.drawable.ic_delete,
            tokens.danger,
            Gravity.TOP or Gravity.END
        ))
        screen.addView(actionChip(
            context, density, tokens, TAG_SET_HOME,
            R.drawable.ic_wallpaper_home,
            tokens.textSecondary,
            Gravity.BOTTOM or Gravity.END
        ))

        card.addView(screen)
        return card
    }

    fun createAddCard(
        context: Context,
        density: Float,
        tokens: NexusColorTokens,
        onClick: () -> Unit,
        columnWidthHintPx: Int = 0
    ): ManagePagesPhoneFrame {
        val card = ManagePagesPhoneFrame(context).apply {
            layoutParams = cardGridParams(density, columnWidthHintPx)
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = BEZEL_RADIUS_DP * density
                setStroke(
                    (1.5f * density).toInt().coerceAtLeast(1),
                    tokens.divider,
                    8f * density,
                    6f * density
                )
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }
        }
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        content.addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_add)
            imageTintList = ColorStateList.valueOf(tokens.textPrimary)
            layoutParams = LinearLayout.LayoutParams(
                (36 * density).toInt(), (36 * density).toInt()
            ).apply { gravity = Gravity.CENTER_HORIZONTAL }
        })
        content.addView(TextView(context).apply {
            text = context.getString(com.nexus.launcher.R.string.mosaic_add_page)
            NexusTypeScale.body.bindTo(this, tokens.textSecondary)
            gravity = Gravity.CENTER
            includeFontPadding = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = (4 * density).toInt()
            }
        })
        card.addView(content)
        return card
    }

    fun applyCardState(
        card: FrameLayout,
        pageIndex: Int,
        thumb: android.graphics.Bitmap?,
        isCurrent: Boolean,
        isDefault: Boolean,
        density: Float,
        tokens: NexusColorTokens
    ) {
        card.findViewWithTag<ImageView>(TAG_THUMB)?.setImageBitmap(thumb)
        card.findViewWithTag<TextView>(TAG_PAGE_NUM)?.text = (pageIndex + 1).toString()
        card.findViewWithTag<TextView>(TAG_HOME_BADGE)?.visibility =
            if (isDefault) View.VISIBLE else View.GONE
        val setHome = card.findViewWithTag<ImageView>(TAG_SET_HOME)
        setHome?.imageTintList = ColorStateList.valueOf(
            if (isDefault) tokens.textPrimary else tokens.textSecondary
        )
        card.background = bezelDrawable(density, tokens, selected = isCurrent)
        card.elevation = (if (isCurrent) 8f else 4f) * density
        // Don't fight the entrance stagger (alpha still rising)
        if (card.alpha >= 0.99f) {
            val targetScale = if (isCurrent) CURRENT_SCALE else 1f
            if (kotlin.math.abs(card.scaleX - targetScale) > 0.01f) {
                card.animate().scaleX(targetScale).scaleY(targetScale).setDuration(160).start()
            }
        }
    }

    private fun bezelDrawable(density: Float, tokens: NexusColorTokens, selected: Boolean) =
        GradientDrawable().apply {
            setColor(tokens.surface)
            cornerRadius = BEZEL_RADIUS_DP * density
            val strokeColor = if (selected) tokens.textPrimary else tokens.divider
            val strokeW = ((if (selected) 2f else 1f) * density).toInt().coerceAtLeast(1)
            setStroke(strokeW, strokeColor)
        }

    /** Dense plate — stays readable on bright page thumbs. */
    private fun nexusChromePlate(tokens: NexusColorTokens, density: Float, cornerDp: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(tokens.surfaceRaised)
            cornerRadius = cornerDp * density
            setStroke(
                (1.5f * density).toInt().coerceAtLeast(1),
                tokens.divider
            )
        }

    private fun pageNumberPill(context: Context, density: Float, tokens: NexusColorTokens): TextView =
        TextView(context).apply {
            tag = TAG_PAGE_NUM
            NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
            background = nexusChromePlate(tokens, density, PILL_CORNER_DP)
            elevation = 2f * density
            val h = (10 * density).toInt()
            val v = (5 * density).toInt()
            setPadding(h, v, h, v)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                setMargins((8 * density).toInt(), (8 * density).toInt(), 0, 0)
            }
        }

    private fun homeBadgePill(context: Context, density: Float, tokens: NexusColorTokens): TextView =
        TextView(context).apply {
            tag = TAG_HOME_BADGE
            text = context.getString(com.nexus.launcher.R.string.manage_pages_home_badge)
            NexusTypeScale.labelSmall.bindTo(this, tokens.textPrimary)
            background = GradientDrawable().apply {
                setColor(tokens.surfaceRaised)
                cornerRadius = PILL_CORNER_DP * density
                setStroke(
                    (1.5f * density).toInt().coerceAtLeast(1),
                    tokens.divider
                )
            }
            elevation = 2f * density
            val h = (11 * density).toInt()
            val v = (5 * density).toInt()
            setPadding(h, v, h, v)
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.START
                setMargins((8 * density).toInt(), 0, 0, (10 * density).toInt())
            }
        }

    private fun actionChip(
        context: Context,
        density: Float,
        tokens: NexusColorTokens,
        tag: String,
        iconRes: Int,
        tint: Int,
        gravity: Int
    ): ImageView {
        val size = (CHIP_SIZE_DP * density).toInt()
        return ImageView(context).apply {
            this.tag = tag
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(tint)
            val pad = (7 * density).toInt()
            setPadding(pad, pad, pad, pad)
            background = nexusChromePlate(tokens, density, CHIP_CORNER_DP)
            elevation = 2f * density
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                this.gravity = gravity
                val m = (7 * density).toInt()
                setMargins(m, m, m, m)
            }
            isClickable = true
            isFocusable = true
        }
    }
}
