package com.nexus.launcher.ui.settings

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import com.nexus.launcher.R
import com.nexus.launcher.data.prefs.NexusSettingsData
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.NexusDesignSystem
import com.nexus.launcher.ui.WallpaperSheetComponents
import com.nexus.launcher.ui.immersive.ImmersiveStatus
import com.nexus.launcher.ui.immersive.ImmersiveStatusBarView
import com.nexus.launcher.ui.immersive.ImmersiveStatusStyle
import com.nexus.launcher.ui.island.IslandCalibrationOverlayView
import com.nexus.launcher.ui.settings.views.NexusSliderRow

/**
 * Real-time Island Size & Position calibration sheet with live camera cutout overlay.
 * Adapts to Immersive Mode and rigorously uses Nexus Design System theme tokens.
 */
object IslandCalibrationSheet {

    fun show(
        context: Context,
        settings: NexusSettingsData,
        onApply: (NexusSettingsData) -> Unit,
    ) {
        val activity = SettingsActivityOf.find(context) ?: return
        val tokens = try {
            ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }
        val dp = context.resources.displayMetrics.density

        var curWidth = settings.islandWidthDp
        var curHeight = settings.islandHeightDp
        var curX = settings.islandXOffsetDp
        var curY = settings.islandYOffsetDp

        val dialog = Dialog(context, android.R.style.Theme_DeviceDefault_NoActionBar)
        val root = FrameLayout(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }

        // 1. Live calibration overlay at top of screen
        val overlay = IslandCalibrationOverlayView(context).apply {
            isImmersive = settings.immersiveMode && settings.statusBarEnabled
            previewWidthDp = curWidth
            previewHeightDp = curHeight
            previewXOffsetDp = curX
            previewYOffsetDp = curY
        }

        // 2. If immersive mode is active, place live status row under overlay
        if (settings.immersiveMode && settings.statusBarEnabled) {
            val statusRow = ImmersiveStatusBarView(activity) { tokens.textPrimary }.apply {
                setItems(
                    ImmersiveStatus.Items(
                        clock = settings.statusShowClock,
                        notifications = settings.statusShowNotifications,
                        wifi = settings.statusShowWifi,
                        signal = settings.statusShowSignal,
                        battery = settings.statusShowBattery,
                    )
                )
                setItemStyle(ImmersiveStatusStyle.of(settings))
                setBar(settings.statusBarHeightDp, settings.statusBarPaddingDp, settings.statusBarBackground)
            }
            root.addView(
                statusRow,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP,
                ),
            )
        }

        root.addView(
            overlay,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )

        // 3. Bottom controls card using unified WallpaperSheetComponents.buildCard
        val card = WallpaperSheetComponents.buildCard(context, dp, tokens).apply {
            setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt())
        }

        // Drag handle
        card.addView(com.nexus.launcher.ui.widgets.NexusEditBottomSheetHelper.createDragHandle(context, tokens, dp))

        // Header
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        val titleLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val title = TextView(context).apply {
            text = context.getString(R.string.island_size_position_title)
            NexusTypeScale.hubRowTitle.bindTo(this, tokens.textPrimary)
        }
        val subtitle = TextView(context).apply {
            text = context.getString(R.string.island_size_position_subtitle)
            NexusTypeScale.caption.bindTo(this, tokens.textSecondary)
        }
        titleLayout.addView(title)
        titleLayout.addView(subtitle)
        header.addView(titleLayout)
        val closeBtn = WallpaperSheetComponents.buildCloseButton(context, dp, tokens) { dialog.dismiss() }
        header.addView(closeBtn)
        card.addView(header)

        // Compact guide text box
        val guideCard = TextView(context).apply {
            text = context.getString(R.string.island_size_position_guide)
            NexusTypeScale.caption.bindTo(this, tokens.textSecondary)
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt())
            background = GradientDrawable().apply {
                setColor(tokens.surfaceRaised)
                cornerRadius = 10f * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (8 * dp).toInt(); bottomMargin = (6 * dp).toInt() }
            layoutParams = lp
        }
        card.addView(guideCard)

        // Sliders container
        val slidersScroll = ScrollView(context).apply {
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
            )
        }
        val slidersLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Slider 1: X Position
        val xRow = buildSliderWithAction(
            context, dp, tokens,
            label = context.getString(R.string.island_x_position),
            actionText = context.getString(R.string.island_center),
            min = -100, max = 100, current = curX,
            onAction = {
                curX = 0
                overlay.previewXOffsetDp = 0
            },
            onChanged = {
                curX = it
                overlay.previewXOffsetDp = it
            }
        )
        slidersLayout.addView(xRow)

        // Slider 2: Y Position
        val yRow = buildSliderWithAction(
            context, dp, tokens,
            label = context.getString(R.string.island_y_position),
            actionText = context.getString(R.string.island_align),
            min = -25, max = 35, current = curY,
            onAction = {
                curY = 0
                overlay.previewYOffsetDp = 0
            },
            onChanged = {
                curY = it
                overlay.previewYOffsetDp = it
            }
        )
        slidersLayout.addView(yRow)

        // Slider 3: Width
        val widthSlider = NexusSliderRow(context).apply {
            configure(
                context.getString(R.string.island_width),
                50, 260, curWidth,
                formatValue = { "${it} dp" }
            )
            applyTokens(tokens)
            applyAccentColor(tokens.accent)
            onValueChanged = {
                curWidth = it
                overlay.previewWidthDp = it
            }
        }
        slidersLayout.addView(widthSlider)

        // Slider 4: Height
        val heightSlider = NexusSliderRow(context).apply {
            configure(
                context.getString(R.string.island_height),
                20, 44, curHeight,
                formatValue = { "${it} dp" }
            )
            applyTokens(tokens)
            applyAccentColor(tokens.accent)
            onValueChanged = {
                curHeight = it
                overlay.previewHeightDp = it
            }
        }
        slidersLayout.addView(heightSlider)
        slidersScroll.addView(slidersLayout)
        card.addView(slidersScroll)

        // Apply Button with theme-consistent styling
        val applyBtn = TextView(context).apply {
            text = context.getString(R.string.island_calibration_apply)
            gravity = Gravity.CENTER
            NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
            background = GradientDrawable().apply {
                setColor(tokens.surfaceRaised)
                cornerRadius = 16f * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.accent)
            }
            setPadding(0, (12 * dp).toInt(), 0, (12 * dp).toInt())
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (10 * dp).toInt() }
            layoutParams = lp
            isClickable = true
            isFocusable = true
            setOnClickListener {
                performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                onApply(
                    settings.copy(
                        islandWidthDp = curWidth,
                        islandHeightDp = curHeight,
                        islandXOffsetDp = curX,
                        islandYOffsetDp = curY,
                    )
                )
                dialog.dismiss()
            }
        }
        card.addView(applyBtn)

        val screenH = context.resources.displayMetrics.heightPixels
        val targetH = (640 * dp).toInt().coerceAtMost((screenH * 0.85f).toInt())
        val cardLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, targetH, Gravity.BOTTOM
        ).apply {
            setMargins((10 * dp).toInt(), 0, (10 * dp).toInt(), (14 * dp).toInt())
        }
        root.addView(card, cardLp)

        dialog.setContentView(
            root,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT),
        )
        dialog.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(0))
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.statusBarColor = Color.TRANSPARENT
            window.navigationBarColor = Color.TRANSPARENT
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
                window.isStatusBarContrastEnforced = false
            }
            com.nexus.launcher.ui.glass.FrostedGlassEngine.applyDialogWindowChrome(window)
            com.nexus.launcher.ui.glass.FloatingSurfaces.applyEditSheetDim(window)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets -> insets }
        ViewCompat.requestApplyInsets(root)
        dialog.show()
    }

    private fun buildSliderWithAction(
        context: Context,
        dp: Float,
        tokens: NexusColorTokens,
        label: String,
        actionText: String,
        min: Int,
        max: Int,
        current: Int,
        onAction: () -> Unit,
        onChanged: (Int) -> Unit,
    ): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, (4 * dp).toInt())
        }
        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * dp).toInt(), (4 * dp).toInt(), (16 * dp).toInt(), 0)
        }
        val labelView = TextView(context).apply {
            text = label
            NexusTypeScale.body.bindTo(this, tokens.textPrimary)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val actionBtn = TextView(context).apply {
            text = actionText
            NexusTypeScale.caption.bindTo(this, tokens.accent)
            background = GradientDrawable().apply {
                setColor(tokens.surfaceRaised)
                cornerRadius = 10f * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
            }
            setPadding((10 * dp).toInt(), (4 * dp).toInt(), (10 * dp).toInt(), (4 * dp).toInt())
            isClickable = true
            isFocusable = true
        }
        topRow.addView(labelView)
        topRow.addView(actionBtn)
        container.addView(topRow)

        val slider = NexusSliderRow(context).apply {
            configure("", min, max, current, formatValue = { "${it} dp" })
            applyTokens(tokens)
            applyAccentColor(tokens.accent)
            onValueChanged = { onChanged(it) }
        }
        actionBtn.setOnClickListener {
            actionBtn.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            slider.setValue(0)
            onAction()
        }
        container.addView(slider)
        return container
    }
}
