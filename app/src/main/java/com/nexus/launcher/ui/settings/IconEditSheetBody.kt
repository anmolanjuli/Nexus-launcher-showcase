package com.nexus.launcher.ui.settings

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import com.nexus.launcher.R
import com.nexus.launcher.ui.NexusDesignSystem
import com.nexus.launcher.ui.icons.IconResolver

/** Scrollable Icon Edit body: preview, rename, gallery/pack actions, gestures. */
object IconEditSheetBody {

    class Parts(
        val root: LinearLayout,
        val previewImage: ImageView,
        val nameInput: EditText,
        var gestureSection: View,
        var shapeSection: View
    )

    fun build(
        context: Context,
        fragmentManager: FragmentManager,
        density: Float,
        packageName: String,
        initialName: String,
        iconResolver: IconResolver,
        swipeUp: String,
        swipeDown: String,
        doubleTap: String,
        initialShapeId: Int,
        onNameChanged: () -> Unit,
        onSwipeUp: (String) -> Unit,
        onSwipeDown: (String) -> Unit,
        onDoubleTap: (String) -> Unit,
        onShapeSelected: (Int) -> Unit
    ): Parts {
        val tokens = try {
            com.nexus.launcher.theme.ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            com.nexus.launcher.theme.NexusColorTokens.Dark
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * density).toInt()
            setPadding(pad, (8 * density).toInt(), pad, (16 * density).toInt())
        }

        val previewImage = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                (72 * density).toInt(), (72 * density).toInt()
            ).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = (12 * density).toInt()
            }
            val effectiveShape = IconEditPreviewHelper.effectivePreviewShape(initialShapeId)
            setImageDrawable(iconResolver.getIcon(packageName, overrideShape = effectiveShape))
        }
        content.addView(previewImage)

        content.addView(TextView(context).apply {
            text = context.getString(R.string.edit_sheet_tap_to_rename)
            com.nexus.launcher.typography.NexusTypeScale.caption.bindTo(
                this,
                tokens.textSecondary
            )
            gravity = Gravity.START
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (4 * density).toInt() }
        })

        val nameInput = EditText(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (20 * density).toInt() }
            setText(initialName)
            com.nexus.launcher.typography.NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
            setHintTextColor(tokens.textSecondary)
            hint = context.getString(R.string.edit_sheet_app_name_hint)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            // A sunken well in Neumorphism, matching the folder sheet's name field.
            background = com.nexus.launcher.ui.glass.NeumorphicSurfaces.field(this, tokens, 14 * density)
            setPadding(
                (16 * density).toInt(), (14 * density).toInt(),
                (16 * density).toInt(), (14 * density).toInt()
            )
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) { onNameChanged() }
            })
        }
        content.addView(nameInput)

        // 1. Appearance Section Card (Shape + Icon Source)
        content.addView(sectionLabel(context, density, context.getString(R.string.edit_sheet_section_appearance)))
        val appearanceCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            // Raised in Neumorphism, like the Settings row groups below it; it was the one flat
            // card left on the sheet.
            background = com.nexus.launcher.ui.glass.NeumorphicSurfaces.card(this, tokens, 14 * density)
            setPadding(0, (12 * density).toInt(), 0, (12 * density).toInt())
        }

        // Shape Section
        val shapeSection = IconEditShapeSection.build(
            context, density, initialShapeId, onShapeSelected
        )
        appearanceCard.addView(shapeSection)

        // Divider
        appearanceCard.addView(View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, (1 * density).toInt().coerceAtLeast(1)
            ).apply {
                setMargins(0, (10 * density).toInt(), 0, (12 * density).toInt())
            }
            setBackgroundColor(tokens.divider)
        })

        // Icon Source Subheader
        val sourceTitle = TextView(context).apply {
            text = context.getString(R.string.edit_sheet_icon_source)
            com.nexus.launcher.typography.NexusTypeScale.labelSmall.bindTo(this, tokens.textSecondary)
            gravity = Gravity.START
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            setPaddingRelative((16 * density).toInt(), 0, (16 * density).toInt(), (8 * density).toInt())
        }
        appearanceCard.addView(sourceTitle)

        val packScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            clipToPadding = false
            setPadding((8 * density).toInt(), 0, (8 * density).toInt(), 0)
        }
        val packRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            tag = "icon_packs_row"
        }
        packScroll.addView(packRow)
        appearanceCard.addView(packScroll)

        content.addView(appearanceCard)

        // 2. Gestures Card
        content.addView(sectionLabel(context, density, context.getString(R.string.edit_sheet_section_gestures)).apply {
            (layoutParams as LinearLayout.LayoutParams).topMargin = (16 * density).toInt()
        })
        val gestureSection = IconEditGestureSection.build(
            context = context,
            fragmentManager = fragmentManager,
            dp = density,
            itemType = 0,
            initialSwipeUp = swipeUp,
            initialSwipeDown = swipeDown,
            initialDoubleTap = doubleTap,
            onSwipeUpChanged = onSwipeUp,
            onSwipeDownChanged = onSwipeDown,
            onDoubleTapChanged = onDoubleTap,
            showDoubleTap = true,
            showGesturesLabel = false
        )
        content.addView(gestureSection)

        return Parts(content, previewImage, nameInput, gestureSection, shapeSection)
    }

    fun populateIconPacks(
        parts: Parts,
        context: Context,
        density: Float,
        packs: List<com.nexus.launcher.ui.icons.IconPackInfo>,
        onPackClick: (String) -> Unit,
        onGalleryClick: () -> Unit
    ) {
        val packRow = parts.root.findViewWithTag<LinearLayout>("icon_packs_row") ?: return
        packRow.removeAllViews()
        val tokens = try {
            com.nexus.launcher.theme.ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            com.nexus.launcher.theme.NexusColorTokens.Dark
        }

        for (pack in packs) {
            val tile = buildPackTile(
                context = context,
                density = density,
                label = pack.label,
                icon = pack.icon,
                iconRes = if (pack.icon == null) R.drawable.ic_apps else 0,
                tokens = tokens
            ) {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                onPackClick(pack.packageName)
            }
            packRow.addView(tile)
        }

        val galleryTile = buildPackTile(
            context = context,
            density = density,
            label = context.getString(R.string.edit_sheet_gallery),
            icon = null,
            iconRes = R.drawable.ic_gallery,
            tokens = tokens
        ) {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            onGalleryClick()
        }
        packRow.addView(galleryTile)
    }

    private fun buildPackTile(
        context: Context,
        density: Float,
        label: String,
        icon: android.graphics.drawable.Drawable?,
        iconRes: Int,
        tokens: com.nexus.launcher.theme.NexusColorTokens,
        onClick: (View) -> Unit
    ): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                (76 * density).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins((4 * density).toInt(), 0, (4 * density).toInt(), 0)
            }
            background = GradientDrawable().apply {
                setColor(tokens.surfaceRaised)
                cornerRadius = 12 * density
                setStroke((1 * density).toInt().coerceAtLeast(1), tokens.divider)
            }
            setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
            setOnClickListener { onClick(this) }

            val iconView = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams((36 * density).toInt(), (36 * density).toInt())
                if (icon != null) {
                    setImageDrawable(icon)
                } else {
                    setImageResource(iconRes)
                    imageTintList = android.content.res.ColorStateList.valueOf(tokens.textPrimary)
                }
            }
            addView(iconView)

            val textView = TextView(context).apply {
                text = label
                com.nexus.launcher.typography.NexusTypeScale.caption.bindTo(this, tokens.textPrimary)
                gravity = Gravity.CENTER
                setSingleLine()
                ellipsize = android.text.TextUtils.TruncateAt.END
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = (4 * density).toInt() }
                setPadding((4 * density).toInt(), 0, (4 * density).toInt(), 0)
            }
            addView(textView)
        }
    }

    fun rebuildGestures(
        parts: Parts,
        context: Context,
        fragmentManager: FragmentManager,
        density: Float,
        swipeUp: String,
        swipeDown: String,
        doubleTap: String,
        onSwipeUp: (String) -> Unit,
        onSwipeDown: (String) -> Unit,
        onDoubleTap: (String) -> Unit
    ) {
        val parent = parts.gestureSection.parent as? LinearLayout ?: return
        val index = parent.indexOfChild(parts.gestureSection)
        if (index < 0) return
        parent.removeViewAt(index)
        parts.gestureSection = IconEditGestureSection.build(
            context = context,
            fragmentManager = fragmentManager,
            dp = density,
            itemType = 0,
            initialSwipeUp = swipeUp,
            initialSwipeDown = swipeDown,
            initialDoubleTap = doubleTap,
            onSwipeUpChanged = onSwipeUp,
            onSwipeDownChanged = onSwipeDown,
            onDoubleTapChanged = onDoubleTap,
            showDoubleTap = true,
            showGesturesLabel = false
        )
        parent.addView(parts.gestureSection, index)
    }

    private fun sectionLabel(context: Context, density: Float, title: String): TextView {
        val tokens = try {
            com.nexus.launcher.theme.ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            com.nexus.launcher.theme.NexusColorTokens.Dark
        }
        return TextView(context).apply {
            text = title
            com.nexus.launcher.typography.NexusTypeScale.labelSmall.bindTo(
                this,
                tokens.textSecondary
            )
            gravity = Gravity.START
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (8 * density).toInt() }
        }
    }
}
