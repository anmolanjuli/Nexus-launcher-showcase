package com.nexus.launcher.ui.settings

import android.content.Context
import android.view.View
import com.nexus.launcher.R
import com.nexus.launcher.data.prefs.NexusSettingsData
import com.nexus.launcher.ui.immersive.ImmersiveStatusStyle
import com.nexus.launcher.ui.immersive.StatusBarFonts
import com.nexus.launcher.ui.settings.views.NexusNavRow
import com.nexus.launcher.ui.settings.views.NexusSegmentedRow
import com.nexus.launcher.ui.settings.views.NexusSliderRow
import com.nexus.launcher.ui.settings.views.NexusToggleRow

/**
 * The status row as a whole, above the modules that fill it: whether it is drawn at all, along
 * which edge, how tall, on what, optional fade and per-item pills, and how far in from the sides.
 *
 * The master switch only hides the row; what each module shows stays as it was, so switching it
 * back on brings the same row back.
 */
class StatusBarBehaviorRows(private val context: Context) {

    /** Called the moment the master switch is flipped, before the draft is rebound. */
    var onEnabledChanged: ((Boolean) -> Unit)? = null

    private val enabled = NexusToggleRow(context)
    private val position = NexusSegmentedRow(context)
    private val height = NexusSliderRow(context)
    private val background = NexusSegmentedRow(context)
    private val opacity = NexusSliderRow(context)
    private val fade = NexusToggleRow(context)
    private val pills = NexusToggleRow(context)
    private val padding = NexusSliderRow(context)
    private val font = fontRow()
    private var fontKey = ImmersiveStatusStyle.FONT_FOLLOW
    private var lastEnabled = true
    private var lastBackground = ImmersiveStatusStyle.BACKGROUND_TRANSPARENT

    /** In order, for the section to add. */
    val rows: List<View> = listOf(
        enabled, position, height, background, opacity, fade, pills, padding, font,
    )

    fun bind(s: NexusSettingsData) {
        enabled.configure(
            context.getString(R.string.status_bar_enabled),
            s.statusBarEnabled,
            subtitle = context.getString(R.string.status_bar_enabled_subtitle),
        )
        position.configure(
            context.getString(R.string.status_bar_position),
            listOf(
                ImmersiveStatusStyle.BAR_TOP to context.getString(R.string.status_opt_top),
                ImmersiveStatusStyle.BAR_BOTTOM to context.getString(R.string.status_opt_bottom),
            ),
            s.statusBarPosition, inline = true,
        )
        height.configure(
            label = context.getString(R.string.status_bar_height),
            min = 24, max = 48, value = s.statusBarHeightDp, stepSize = 2f,
            formatValue = { "${it}dp" },
        )
        background.configure(
            context.getString(R.string.status_bar_background),
            listOf(
                ImmersiveStatusStyle.BACKGROUND_TRANSPARENT to context.getString(R.string.status_opt_transparent),
                ImmersiveStatusStyle.BACKGROUND_FROSTED to context.getString(R.string.status_opt_frosted),
                ImmersiveStatusStyle.BACKGROUND_SOLID to context.getString(R.string.status_opt_solid),
            ),
            s.statusBarBackground, inline = true,
        )
        opacity.configure(
            label = context.getString(R.string.status_pill_opacity),
            min = 10, max = 90, value = s.statusPillOpacity, stepSize = 5f,
            formatValue = { "$it%" },
            subtitle = context.getString(R.string.status_pill_opacity_subtitle),
        )
        fade.configure(
            context.getString(R.string.status_bar_fade),
            s.statusBarFade,
            subtitle = context.getString(R.string.status_bar_fade_subtitle),
        )
        pills.configure(
            context.getString(R.string.status_segmented_pills),
            s.statusSegmentedPills,
            subtitle = context.getString(R.string.status_segmented_pills_subtitle),
        )
        padding.configure(
            label = context.getString(R.string.status_bar_padding),
            min = 0, max = 24, value = s.statusBarPaddingDp, stepSize = 2f,
            formatValue = { "${it}dp" },
        )
        fontKey = s.statusFontKey.ifBlank { ImmersiveStatusStyle.FONT_FOLLOW }
        font.setSubtitle(StatusBarFonts.displayName(context, fontKey))
        lastEnabled = s.statusBarEnabled
        lastBackground = s.statusBarBackground
        setChildrenEnabled(s.statusBarEnabled)
    }

    fun setListeners(
        onPatch: ((NexusSettingsData) -> NexusSettingsData) -> Unit,
        isIgnoreCallbacks: () -> Boolean,
    ) {
        enabled.onCheckedChanged = { checked ->
            if (!isIgnoreCallbacks()) {
                lastEnabled = checked
                setChildrenEnabled(checked)
                onEnabledChanged?.invoke(checked)
                onPatch { s -> s.copy(statusBarEnabled = checked) }
            }
        }
        position.onValueChanged = { v ->
            if (!isIgnoreCallbacks()) onPatch { s -> s.copy(statusBarPosition = v) }
        }
        height.onValueChanged = { v ->
            if (!isIgnoreCallbacks()) onPatch { s -> s.copy(statusBarHeightDp = v) }
        }
        background.onValueChanged = { v ->
            if (!isIgnoreCallbacks()) {
                lastBackground = v
                dimDependents()
                onPatch { s -> s.copy(statusBarBackground = v) }
            }
        }
        opacity.onValueChanged = { v ->
            if (!isIgnoreCallbacks()) onPatch { s -> s.copy(statusPillOpacity = v) }
        }
        fade.onCheckedChanged = { checked ->
            if (!isIgnoreCallbacks()) onPatch { s -> s.copy(statusBarFade = checked) }
        }
        pills.onCheckedChanged = { checked ->
            if (!isIgnoreCallbacks()) onPatch { s -> s.copy(statusSegmentedPills = checked) }
        }
        padding.onValueChanged = { v ->
            if (!isIgnoreCallbacks()) onPatch { s -> s.copy(statusBarPaddingDp = v) }
        }
        font.setOnClickListener {
            if (!isIgnoreCallbacks()) openFontPicker(onPatch)
        }
    }

    private fun openFontPicker(onPatch: ((NexusSettingsData) -> NexusSettingsData) -> Unit) {
        val host = SettingsActivityOf.find(context) as? androidx.fragment.app.FragmentActivity ?: return
        NexusSelectionSheet(
            sheetTitle = context.getString(R.string.status_font_sheet_title),
            sheetSubtitle = context.getString(R.string.status_font_sheet_subtitle),
            options = fontOptions(),
            selectedKey = fontKey,
        ) { key ->
            fontKey = key
            font.setSubtitle(StatusBarFonts.displayName(context, key))
            onPatch { s -> s.copy(statusFontKey = key) }
        }.show(host.supportFragmentManager, "status_font_sheet")
    }

    /** With the row off, the rest of its settings dim out of the way but stay readable. */
    private fun setChildrenEnabled(on: Boolean) {
        lastEnabled = on
        listOf(position, height, background, fade, pills, padding, font).forEach { row ->
            StatusRowDimming.apply(row, on)
        }
        dimDependents()
    }

    private fun dimDependents() {
        val fillOn = lastEnabled && lastBackground != ImmersiveStatusStyle.BACKGROUND_TRANSPARENT
        StatusRowDimming.apply(opacity, fillOn)
    }

    private fun fontRow(): NexusNavRow {
        val dp = context.resources.displayMetrics.density
        return NexusNavRow(context).apply {
            minimumHeight = (48 * dp).toInt()
            setContentPadding(
                (16 * dp).toInt(), (8 * dp).toInt(),
                (16 * dp).toInt(), (8 * dp).toInt(),
            )
            setTitle(context.getString(R.string.status_bar_font), bold = false)
            setIcon(R.drawable.ic_fonts)
            setChevronVisible(true)
        }
    }

    private fun fontOptions(): List<SelectionOption<String>> {
        val list = mutableListOf<SelectionOption<String>>()
        list.add(
            SelectionOption(
                key = ImmersiveStatusStyle.FONT_FOLLOW,
                title = context.getString(R.string.status_font_follow),
                subtitle = context.getString(R.string.status_font_follow_subtitle),
                typeface = StatusBarFonts.typeface(context, ImmersiveStatusStyle.FONT_FOLLOW, 400),
            )
        )
        com.nexus.launcher.typography.AppFontFamily.entries.forEach { fam ->
            list.add(
                SelectionOption(
                    key = fam.key,
                    title = context.getString(fam.displayNameRes),
                    typeface = com.nexus.launcher.typography.DynamicFontProvider(context, fam)
                        .getTypeface(android.graphics.Typeface.NORMAL),
                )
            )
        }
        val custom = try {
            com.nexus.launcher.typography.FontFamilyController.resolve(context).customFonts.value
        } catch (_: Exception) {
            emptyList()
        }
        custom.forEach { entry ->
            val tf = try {
                if (java.io.File(entry.filePath).exists()) {
                    android.graphics.Typeface.createFromFile(entry.filePath)
                } else null
            } catch (_: Exception) {
                null
            }
            list.add(
                SelectionOption(
                    key = "custom:${entry.id}",
                    title = entry.name,
                    subtitle = context.getString(R.string.typography_custom_font),
                    typeface = tf,
                )
            )
        }
        return list
    }
}
