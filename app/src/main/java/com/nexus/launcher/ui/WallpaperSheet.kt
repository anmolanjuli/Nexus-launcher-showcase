package com.nexus.launcher.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.canvas.CanvasRenderer
import com.nexus.launcher.ui.settings.views.GradientBuilder

/** Full overlay bottom sheet for wallpaper selection, gradient customization, and live treatment. */
class WallpaperSheet(
    context: Context,
    private val viewModel: MainViewModel,
    private val lifecycleOwner: LifecycleOwner,
    private val density: Float,
    private val canvasRenderer: CanvasRenderer?,
    private val dockView: View?,
    /** When set, opens directly on this wallpaper-type tab instead of the usual default-to-
     *  Gradient — used by the Frosted Glass capture deep link from Nexus Settings > Appearance. */
    private val initialTab: String? = null,
    private val onDismiss: () -> Unit
) : FrameLayout(context) {

    private val tokens: NexusColorTokens = try {
        ThemeObserver.currentTokens(context)
    } catch (_: Exception) {
        NexusColorTokens.Dark
    }

    private lateinit var gradientSection: LinearLayout
    private lateinit var gallerySection: LinearLayout
    private lateinit var othersSection: LinearLayout
    private lateinit var systemSection: LinearLayout
    private lateinit var gradientBuilder: GradientBuilder
    private lateinit var wallpaperTypeRow: LinearLayout
    private lateinit var previewView: WallpaperPreviewView
    private lateinit var tabBinder: WallpaperSheetTabBinder
    private var ignoreCallbacks = false
    private var isDismissed = false
    private var onDismissFired = false

    private var stagedBlur = 0f
    private var stagedTintHue = 0f
    private var stagedTintStrength = 0f
    private var treatmentController: WallpaperTreatmentControls.TreatmentController? = null

    private var pendingSelection: PendingWallpaper? = null
    private lateinit var applyController: WallpaperApplyController
    private lateinit var applyButton: TextView

    private var frostBg: View? = null
    private var uiTab: String = "gradient"
    private var openedToDefaultTab = false

    init {
        // Was unconditionally opaque `tokens.bg`, which completely hid the workspace blur
        // HomeEditController.showWallpaperSheet() already activates (a REAL RenderEffect blur on
        // canvasView itself, via canvasView.setBlurState(true)) behind this view. First attempt
        // at this fix also attached WallpaperSheetFrost's own blurBg scrim on top of the
        // translucent root — but that's a SECOND darkening layer stacked over the first
        // (root scrim + blurBg's own ~50% black scrim), which compounds toward flat/opaque
        // instead of reading as more frosted (the exact "two layers stacked crushes toward black"
        // trap FrostedGlassEngine's own dialog-chrome doc comment warns about) — and this sheet,
        // unlike NexusSearchOverlay (which has no independent blur source and genuinely needs
        // WallpaperSheetFrost as its ONLY blur mechanism), already has that real external blur to
        // sit over. A single translucent root layer is the complete fix here, same as
        // ManagePagesView (which sits over the exact same externally-activated workspace blur).
        // frostBg/refreshFrost are pre-existing scaffolding kept as-is but intentionally unused.
        applyRootBackground()
        isClickable = true
        isFocusable = true

        applyController = WallpaperApplyController(
            context,
            (context as ComponentActivity).lifecycleScope,
            onApplyHomeScreen = { pending, blur, tintColor, tintStrength ->
                when (pending) {
                    is PendingWallpaper.System -> viewModel.updateWallpaperType("system")
                    is PendingWallpaper.Gradient -> {
                        viewModel.updateWallpaperType("gradient")
                        viewModel.updateWallpaperGradient(pending.start, pending.end, pending.direction)
                    }
                    is PendingWallpaper.Gallery -> {
                        viewModel.updateWallpaperType("gallery")
                        viewModel.updateWallpaperGallery(pending.path)
                    }
                }
                viewModel.updateWallpaperTreatment(blur, tintColor, tintStrength)
                applyController.syncSystemWallpaper(pending, blur, tintColor, tintStrength)
                pendingSelection = null
                updateApplyButtonState()
            },
            onDismissSheet = { dismiss() }
        )

        // Sized by WallpaperSheetLayout: fills whatever the preview block leaves.
        val scrollView = ScrollView(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isNestedScrollingEnabled = true
            isFillViewport = true
        }

        val previewContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        previewView = WallpaperPreviewView.createFramed(context, density)

        val treatmentControls = WallpaperTreatmentControls.build(context, density, previewView, tokens) { blur, hue, strength ->
            if (!ignoreCallbacks) {
                stagedBlur = blur
                stagedTintHue = hue
                stagedTintStrength = strength
                previewView.applyLiveTreatment(blur, hue, strength)

                if (pendingSelection == null) {
                    val s = viewModel.nexusSettings.value
                    pendingSelection = when (uiTab) {
                        "gallery" -> PendingWallpaper.Gallery(s.wallpaperGalleryPath)
                        else -> PendingWallpaper.Gradient(
                            s.wallpaperGradientStart,
                            s.wallpaperGradientEnd,
                            s.wallpaperGradientDirection
                        )
                    }
                    updateApplyButtonState()
                }
            }
        }
        treatmentController = treatmentControls.tag as WallpaperTreatmentControls.TreatmentController
        previewContainer.addView(treatmentControls)

        applyButton = WallpaperSheetComponents.buildApplyButton(context, density, tokens) {
            pendingSelection?.let {
                val tintColor = if (stagedTintStrength > 0f) {
                    String.format("#%02x%06X", (stagedTintStrength * 255).toInt(), (Color.HSVToColor(floatArrayOf(stagedTintHue, 1f, 1f)) and 0xFFFFFF))
                } else "#00000000"
                // Dim and tint are Premium; keeping the treatment already applied never is.
                val s = viewModel.nexusSettings.value
                val newEffect = (stagedBlur > 0f || stagedTintStrength > 0f) &&
                    (stagedBlur != s.wallpaperBlur || !tintColor.equals(s.wallpaperTintColor, ignoreCase = true))
                if (newEffect && !com.nexus.launcher.premium.PremiumGate.allow(context, com.nexus.launcher.premium.PremiumFeature.WALLPAPER_EFFECTS)) return@let
                applyController.showApplyChoiceDialog(it, stagedBlur, tintColor, stagedTintStrength)
            }
        }
        previewContainer.addView(applyButton)


        val card = WallpaperSheetComponents.buildCard(context, density, tokens)

        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (16 * density).toInt() }
        }

        val wallpaperTitle = TextView(context).apply {
            text = context.getString(R.string.title_wallpaper)
            NexusTypeScale.title.bindTo(this, tokens.textPrimary)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val closeBtn = WallpaperSheetComponents.buildCloseButton(context, density, tokens) { dismiss() }

        titleRow.addView(wallpaperTitle)
        titleRow.addView(closeBtn)
        card.addView(titleRow)

        wallpaperTypeRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (16 * density).toInt() }
            background = GradientDrawable().apply {
                val frostedTokens = com.nexus.launcher.ui.glass.FrostedGlassEngine.resolveFrostedTokens(tokens)
                val fillAlpha = (com.nexus.launcher.ui.glass.FrostedGlassEngine.sheetFillAlpha(1f, 0.70f) * 255f).toInt()
                setColor((frostedTokens.surface and 0x00FFFFFF) or (fillAlpha shl 24))
                cornerRadius = 22 * density
                setStroke((1 * density).toInt().coerceAtLeast(1), frostedTokens.border)
            }
            clipToOutline = true
        }

        gradientSection = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        gradientBuilder = GradientBuilder(context)
        gradientBuilder.onGradientChanged = { s, e, d ->
            if (!ignoreCallbacks) {
                pendingSelection = PendingWallpaper.Gradient(s, e, d)
                treatmentController?.setTreatment(0f, 0f, 0f)
                updateApplyButtonState()
                previewView.showGradient(s, e, d)
            }
        }
        gradientSection.addView(gradientBuilder)

        gallerySection = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val recentsView = WallpaperGalleryRecents.build(
            context,
            lifecycleOwner as ComponentActivity,
            density,
            tokens,
            onCopied = { path ->
                if (!ignoreCallbacks) {
                    pendingSelection = PendingWallpaper.Gallery(path)
                    treatmentController?.setTreatment(0f, 0f, 0f)
                    updateApplyButtonState()
                    previewView.showGallery(path)
                }
            },
            onMoreClicked = { tabBinder.handleGalleryChip(forcePicker = true, tokens = tokens) }
        )
        gallerySection.addView(recentsView)

        othersSection = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        val othersTab = WallpaperOthersTab.build(context, density, tokens) {
            viewModel.updateWallpaperType("system")
        }
        othersSection.addView(othersTab)

        systemSection = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        systemSection.addView(
            WallpaperFrostCaptureTab.build(context, lifecycleOwner, density, tokens) { this }
        )

        tabBinder = WallpaperSheetTabBinder(
            context = context,
            lifecycleOwner = lifecycleOwner,
            viewModel = viewModel,
            density = density,
            wallpaperTypeRow = wallpaperTypeRow,
            gradientSection = gradientSection,
            gallerySection = gallerySection,
            othersSection = othersSection,
            systemSection = systemSection,
            previewView = previewView,
            getTreatmentController = { treatmentController },
            isIgnoreCallbacks = { ignoreCallbacks },
            getPendingSelection = { pendingSelection },
            setPendingSelection = { pendingSelection = it },
            updateApplyButtonState = ::updateApplyButtonState,
            getUiTab = { uiTab },
            setUiTab = { uiTab = it }
        )

        card.addView(wallpaperTypeRow)
        card.addView(gradientSection)
        card.addView(gallerySection)
        card.addView(othersSection)
        card.addView(systemSection)

        scrollView.addView(card)
        addView(WallpaperSheetLayout.build(context, previewContainer, scrollView))

        if (initialTab == "system") {
            // Mark opened-to-default up front so the settings collector (setupLifecycle, below)
            // doesn't override this with its usual "always land on Gradient" behavior.
            openedToDefaultTab = true
            tabBinder.openOnSystemTab(tokens)
        }

        ViewCompat.setOnApplyWindowInsetsListener(scrollView) { v, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
            v.setPadding(0, 0, 0, bottom + (16 * density).toInt())
            insets
        }

        setupLifecycle()
    }

    private fun setupLifecycle() {
        WallpaperSheetSettingsBind.start(
            lifecycleOwner = lifecycleOwner,
            viewModel = viewModel,
            gradientBuilder = gradientBuilder,
            previewView = previewView,
            getUiTab = { uiTab },
            isOpenedToDefault = { openedToDefaultTab },
            markOpenedToDefault = { openedToDefaultTab = true },
            openOnGradientTab = { s -> tabBinder.openOnGradientTab(s, tokens) },
            setIgnoreCallbacks = { ignoreCallbacks = it },
            getTreatmentController = { treatmentController },
            onStagedTreatment = { blur, hue, strength ->
                stagedBlur = blur
                stagedTintHue = hue
                stagedTintStrength = strength
            },
            refreshFrost = ::refreshFrost,
            frostBg = { frostBg }
        )
    }

    private fun refreshFrost(blurBg: View) =
        WallpaperSheetFrost.refresh(blurBg, canvasRenderer, dockView)

    /** Default/Neumorphism: fully opaque theme bg. Frosted Glass: translucent fill over the
     *  workspace blur activated behind this sheet, plus the live blurred-wallpaper backdrop
     *  attached in init. */
    private fun applyRootBackground() {
        val alpha = (com.nexus.launcher.ui.glass.FrostedGlassEngine.sheetFillAlpha(1f, 0.55f) * 255).toInt()
        setBackgroundColor((alpha shl 24) or (tokens.bg and 0x00FFFFFF))
    }

    private fun updateApplyButtonState() {
        WallpaperSheetComponents.updateApplyButtonState(applyButton, pendingSelection != null)
    }

    fun dismiss() {
        if (isDismissed) return
        isDismissed = true
        animate().alpha(0f).setDuration(200).withEndAction {
            (parent as? ViewGroup)?.removeView(this)
            fireOnDismissOnce()
        }.start()
    }

    private fun fireOnDismissOnce() {
        if (onDismissFired) return
        onDismissFired = true
        onDismiss()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        // Safety net: onDismiss() clears the canvas's RenderEffect blur that showWallpaperSheet()
        // turns on when this sheet opens — and since the App Drawer draws as part of the same
        // canvas's onDraw, an un-cleared blur here blurs the whole home screen AND the drawer.
        // The normal path only clears it via the fade-out animation's withEndAction, which can
        // simply not fire if this view is detached before the animation completes (a parent
        // teardown cancelling the animator, a config change, etc.) — leaving the blur stuck until
        // something unrelated (like a lock/unlock cycle) happens to reset it. Detach is the one
        // event guaranteed to fire regardless of how this view leaves the window, so it's the
        // right place for a guaranteed-once cleanup call.
        fireOnDismissOnce()
    }
}
