package com.nexus.launcher.ui.folder

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.settings.views.NexusSegmentedRow
import com.nexus.launcher.ui.settings.views.NexusSliderRow
import com.nexus.launcher.ui.settings.views.NexusToggleRow
import com.nexus.launcher.ui.settings.views.SettingsSectionGroupView

class FolderEditBodyViews(
    val root: LinearLayout,
    val headerRoot: LinearLayout,
    val previewView: FolderEditPreviewView,
    val iconPreviewView: FolderEditIconPreviewView,
    val titleEdit: EditText,
    val shapeThumbs: FolderShapeThumbnailRow,
    val refractionSlider: NexusSliderRow,
    val previewRow: NexusSegmentedRow,
    val columnsSlider: NexusSliderRow,
    val iconOpacitySlider: FolderEditVerticalOpacitySlider,
    val iconOpacityValueText: TextView,
    val windowOpacitySlider: FolderEditVerticalOpacitySlider,
    val windowOpacityValueText: TextView,
    val labelsToggle: NexusToggleRow,
    val expressiveToggle: NexusToggleRow,
    val expressiveChildContainer: LinearLayout,
    val backgroundContainer: LinearLayout,
    val gesturesContainer: ViewGroup
)

object FolderEditBodyBuilder {

    fun build(context: Context, tokens: NexusColorTokens, dp: Float): FolderEditBodyViews {
        val padH = (16 * dp).toInt()

        // Pinned Header Container (Dual Previews + Title)
        val headerRoot = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padH, (4 * dp).toInt(), padH, 0)
        }

        // Dual Preview Header Container
        val previewContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (12 * dp).toInt() }
        }

        // Left Card: Folder Window Preview (1.6x width) + Opacity Slider
        val windowPreviewCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.6f).apply {
                marginEnd = (6 * dp).toInt()
            }
            background = com.nexus.launcher.ui.glass.NeumorphicSurfaces.card(this, tokens, 16 * dp)
            val pad = (10 * dp).toInt()
            setPadding(pad, pad, pad, pad)
        }

        val windowHeaderRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (6 * dp).toInt()
            }
        }
        val windowTitle = TextView(context).apply {
            text = context.getString(R.string.folder_edit_window)
            NexusTypeScale.labelSmall.bindTo(this, tokens.textSecondary)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val windowOpacityValueText = TextView(context).apply {
            text = "100%"
            NexusTypeScale.caption.bindTo(this, tokens.textPrimary)
            gravity = Gravity.END
        }
        windowHeaderRow.addView(windowTitle)
        windowHeaderRow.addView(windowOpacityValueText)

        val windowContentRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (120 * dp).toInt())
        }
        val previewView = FolderEditPreviewView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }
        val windowOpacitySlider = FolderEditVerticalOpacitySlider(context).apply {
            layoutParams = LinearLayout.LayoutParams((32 * dp).toInt(), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                marginStart = (6 * dp).toInt()
            }
            setLabel(context.getString(R.string.folder_edit_window_opacity))
            applyTokens(tokens)
        }
        windowContentRow.addView(previewView)
        windowContentRow.addView(windowOpacitySlider)

        windowPreviewCard.addView(windowHeaderRow)
        windowPreviewCard.addView(windowContentRow)

        // Right Card: Folder Icon Preview (1.0x width) + Opacity Slider
        val iconPreviewCard = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f).apply {
                marginStart = (6 * dp).toInt()
            }
            background = com.nexus.launcher.ui.glass.NeumorphicSurfaces.card(this, tokens, 16 * dp)
            val pad = (10 * dp).toInt()
            setPadding(pad, pad, pad, pad)
        }

        val iconHeaderRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (6 * dp).toInt()
            }
        }
        val iconTitle = TextView(context).apply {
            text = context.getString(R.string.folder_edit_icon)
            NexusTypeScale.labelSmall.bindTo(this, tokens.textSecondary)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val iconOpacityValueText = TextView(context).apply {
            text = "0%"
            NexusTypeScale.caption.bindTo(this, tokens.textPrimary)
            gravity = Gravity.END
        }
        iconHeaderRow.addView(iconTitle)
        iconHeaderRow.addView(iconOpacityValueText)

        val iconContentRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (120 * dp).toInt())
        }
        val iconPreviewView = FolderEditIconPreviewView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        }
        val iconOpacitySlider = FolderEditVerticalOpacitySlider(context).apply {
            layoutParams = LinearLayout.LayoutParams((32 * dp).toInt(), ViewGroup.LayoutParams.MATCH_PARENT).apply {
                marginStart = (6 * dp).toInt()
            }
            setLabel(context.getString(R.string.folder_edit_icon_opacity))
            applyTokens(tokens)
        }
        iconContentRow.addView(iconPreviewView)
        iconContentRow.addView(iconOpacitySlider)

        iconPreviewCard.addView(iconHeaderRow)
        iconPreviewCard.addView(iconContentRow)

        previewContainer.addView(windowPreviewCard)
        previewContainer.addView(iconPreviewCard)
        headerRoot.addView(previewContainer)

        // Title Input
        val titleEdit = EditText(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (8 * dp).toInt() }
            NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
            setHintTextColor(tokens.textSecondary)
            hint = context.getString(R.string.folder_edit_name_hint)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            // A sunken well in Neumorphism: an input pressed into the surface reads as "type here".
            background = com.nexus.launcher.ui.glass.NeumorphicSurfaces.field(this, tokens, 14 * dp)
            val pad = (14 * dp).toInt()
            setPadding(pad, pad, pad, pad)
        }
        headerRoot.addView(titleEdit)

        // Scrolling Settings Content
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padH, 0, padH, (16 * dp).toInt())
        }

        // 1. Appearance Section (Shape, Preview Style, Expressive Gradient)
        content.addView(sectionHeader(context, context.getString(R.string.folder_edit_section_appearance), tokens, dp))

        val appearanceGroup = SettingsSectionGroupView(context)
        val refractionSlider = NexusSliderRow(context)
        val shapeThumbs = FolderShapeThumbnailRow(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val previewRow = NexusSegmentedRow(context)
        val expressiveToggle = NexusToggleRow(context)
        val expressiveChildContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }
        val backgroundContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (12 * dp).toInt()
            setPadding(pad, 0, pad, pad)
        }
        expressiveChildContainer.addView(backgroundContainer)

        appearanceGroup.addChildRow(refractionSlider)
        appearanceGroup.addChildRow(shapeThumbs)
        appearanceGroup.addChildRow(previewRow)
        appearanceGroup.addChildRow(expressiveToggle)
        appearanceGroup.addChildRow(expressiveChildContainer)
        content.addView(appearanceGroup)

        // 2. Layout Section
        content.addView(sectionHeader(context, context.getString(R.string.folder_edit_section_layout), tokens, dp))
        val layoutGroup = SettingsSectionGroupView(context)
        val columnsSlider = NexusSliderRow(context)
        val labelsToggle = NexusToggleRow(context)
        layoutGroup.addChildRow(columnsSlider)
        layoutGroup.addChildRow(labelsToggle)
        content.addView(layoutGroup)

        // 3. Gestures Section
        content.addView(sectionHeader(context, context.getString(R.string.folder_edit_section_gestures), tokens, dp))
        val gesturesContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        content.addView(gesturesContainer)

        return FolderEditBodyViews(
            root = content,
            headerRoot = headerRoot,
            previewView = previewView,
            iconPreviewView = iconPreviewView,
            titleEdit = titleEdit,
            shapeThumbs = shapeThumbs,
            refractionSlider = refractionSlider,
            previewRow = previewRow,
            columnsSlider = columnsSlider,
            iconOpacitySlider = iconOpacitySlider,
            iconOpacityValueText = iconOpacityValueText,
            windowOpacitySlider = windowOpacitySlider,
            windowOpacityValueText = windowOpacityValueText,
            labelsToggle = labelsToggle,
            expressiveToggle = expressiveToggle,
            expressiveChildContainer = expressiveChildContainer,
            backgroundContainer = backgroundContainer,
            gesturesContainer = gesturesContainer
        )
    }

    private fun sectionHeader(context: Context, label: String, tokens: NexusColorTokens, dp: Float): TextView {
        return TextView(context).apply {
            text = label.uppercase()
            NexusTypeScale.labelSmall.bindTo(this, tokens.textSecondary)
            gravity = Gravity.START
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            setPadding(0, (14 * dp).toInt(), 0, (6 * dp).toInt())
        }
    }
}
