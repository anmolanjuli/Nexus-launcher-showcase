package com.nexus.launcher.ui.settings

import android.content.Context
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.settings.views.NexusNavRow
import com.nexus.launcher.ui.settings.views.NexusSegmentedRow
import com.nexus.launcher.ui.settings.views.NotificationBadgeColorPicker
import com.nexus.launcher.ui.settings.views.SettingsSectionGroupView

class NotificationBadgesSection(
    private val context: Context,
    val onAppBadgeChanged: (Int) -> Unit,
    val onFolderBadgeChanged: (Int) -> Unit,
    val onBadgeColorChanged: (String?) -> Unit
) {
    val badgesHeader: NexusNavRow
    val badgesContainer: SettingsSectionGroupView
    val segmentedAppBadges: NexusSegmentedRow
    val segmentedFolderBadges: NexusSegmentedRow
    val badgeColorPicker: NotificationBadgeColorPicker
    val badgeColorSubtitle: TextView

    var isBadgesExpanded: Boolean = true

    init {
        val dp = context.resources.displayMetrics.density
        val badgeOptions = listOf(
            "0" to context.getString(R.string.badge_opt_off),
            "1" to context.getString(R.string.badge_opt_dot),
            "2" to context.getString(R.string.badge_opt_count)
        )

        segmentedAppBadges = NexusSegmentedRow(context).apply {
            configure(context.getString(R.string.badge_app_icon_title), badgeOptions, "0", inline = true)
            onValueChanged = { valueKey ->
                val style = valueKey.toIntOrNull() ?: 0
                onAppBadgeChanged(style)
            }
        }

        val tokens = try { ThemeObserver.currentTokens(context) } catch (_: Exception) { NexusColorTokens.Dark }
        val appBadgeSubtitle = TextView(context).apply {
            text = context.getString(R.string.badge_app_icon_subtitle)
            textSize = 12.5f
            setPadding((16 * dp).toInt(), 0, (16 * dp).toInt(), (8 * dp).toInt())
        }
        NexusTypeScale.iconLabel.bindTo(appBadgeSubtitle, tokens.textSecondary)

        val appBadgeBlock = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(segmentedAppBadges)
            addView(appBadgeSubtitle)
        }

        segmentedFolderBadges = NexusSegmentedRow(context).apply {
            configure(context.getString(R.string.badge_folder_title), badgeOptions, "0", inline = true)
            // Off stays free; a folder showing badges at all is Premium.
            setPremiumOptions(com.nexus.launcher.premium.PremiumFeature.NOTIFICATION_BADGES) { it != "0" }
            onValueChanged = { valueKey ->
                val style = valueKey.toIntOrNull() ?: 0
                onFolderBadgeChanged(style)
            }
        }

        val folderSubtitle = TextView(context).apply {
            text = context.getString(R.string.badge_folder_subtitle)
            textSize = 12.5f
            setPadding((16 * dp).toInt(), 0, (16 * dp).toInt(), (8 * dp).toInt())
        }
        NexusTypeScale.iconLabel.bindTo(folderSubtitle, tokens.textSecondary)

        val folderBadgeBlock = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(segmentedFolderBadges)
            addView(folderSubtitle)
        }

        // Notification Badge Color Row + Swatches (Inline)
        val badgeColorTitle = TextView(context).apply {
            text = context.getString(R.string.settings_section_badge_color)
            isSingleLine = true
        }
        val badgeColorPill = com.nexus.launcher.ui.premium.PremiumBadges.pill(context, tokens)
        com.nexus.launcher.ui.premium.PremiumBadges.bindPill(badgeColorPill, com.nexus.launcher.premium.PremiumFeature.NOTIFICATION_BADGES)
        val badgeColorTitleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            addView(badgeColorTitle)
            addView(badgeColorPill, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = (8 * dp).toInt() })
        }
        badgeColorSubtitle = TextView(context).apply {
            text = context.getString(R.string.badge_color_custom_hint, context.getString(R.string.badge_color_red))
            textSize = 12.5f
        }
        NexusTypeScale.body.bindTo(badgeColorTitle, tokens.textPrimary)
        NexusTypeScale.iconLabel.bindTo(badgeColorSubtitle, tokens.textSecondary)

        val badgeTextContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (12 * dp).toInt()
            }
            addView(badgeColorTitleRow)
            addView(badgeColorSubtitle)
        }

        badgeColorPicker = NotificationBadgeColorPicker(context).apply {
            // The default red stays free; every other colour, custom included, is Premium.
            allowPick = { hex ->
                hex == fixedColors.first() || com.nexus.launcher.premium.PremiumGate.allow(context, com.nexus.launcher.premium.PremiumFeature.NOTIFICATION_BADGES)
            }
            onColorSelected = { hex ->
                onBadgeColorChanged(hex)
                updateBadgeColorSubtitle(hex)
            }
        }

        val badgeColorBlock = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            setPadding((16 * dp).toInt(), (10 * dp).toInt(), (16 * dp).toInt(), (10 * dp).toInt())
            addView(badgeTextContainer)
            addView(badgeColorPicker)
        }

        badgesContainer = SettingsSectionGroupView(context).apply {
            visibility = View.VISIBLE
            addChildRow(appBadgeBlock)
            addChildRow(folderBadgeBlock)
            addChildRow(badgeColorBlock)
        }

        badgesHeader = NexusNavRow(
            context,
            title = context.getString(R.string.badge_header_title),
            subtitle = context.getString(R.string.badge_opt_off),
            iconRes = R.drawable.ic_notification,
            showChevron = true
        ) {
            isBadgesExpanded = !isBadgesExpanded
            badgesContainer.visibility = if (isBadgesExpanded) View.VISIBLE else View.GONE
            badgesHeader.setChevronRotation(if (isBadgesExpanded) 180f else 0f)
        }.apply {
            setContentPadding(0, (12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt())
            setChevronRotation(180f, animate = false)
        }
    }

    fun updateBadgeColorSubtitle(hex: String?) {
        val label = when (hex?.uppercase()) {
            "#E5484D" -> context.getString(R.string.badge_color_red)
            "#4CAF50" -> context.getString(R.string.badge_color_green)
            "#2196F3" -> context.getString(R.string.badge_color_blue)
            null -> context.getString(R.string.badge_color_red_default)
            else -> hex
        }
        badgeColorSubtitle.text = context.getString(R.string.badge_color_custom_hint, label)
    }

    fun formatBadgeSubtitle(appStyle: Int, folderStyle: Int): String {
        val app = if (appStyle == 1) context.getString(R.string.badge_opt_dot) else if (appStyle == 2) context.getString(R.string.badge_opt_count) else context.getString(R.string.badge_opt_off)
        val folder = if (folderStyle == 1) context.getString(R.string.badge_opt_dot) else if (folderStyle == 2) context.getString(R.string.badge_opt_count) else context.getString(R.string.badge_opt_off)
        return context.getString(R.string.badge_summary_format, app, folder)
    }
}
