package com.nexus.launcher.ui.widgets.mosaic

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.NexusDesignSystem
import com.nexus.launcher.ui.folder.FolderBlurCoordinator

/** Temporary full-size presentation of one live Mosaic widget. Never persisted. */
class MosaicFocusController(
    private val activity: android.app.Activity,
    private val mainContainer: FrameLayout
) {
    private data class Active(
        val mosaic: LivingMosaicView,
        val children: List<MosaicChild>,
        val overlay: FrameLayout,
        val card: FrameLayout,
        val body: MosaicFocusSwipeContainer,
        var tile: MosaicFocusedTile,
        var index: Int,
        var sourceBounds: Rect,
        var isChanging: Boolean = false
    )

    private val density get() = activity.resources.displayMetrics.density
    private var active: Active? = null

    fun isShowing(): Boolean = active != null

    fun open(item: HomeScreenItem, mosaic: LivingMosaicView, index: Int) {
        if (active != null) return
        val children = MosaicConfig.parse(item.folderConfigJson).currentChildren()
        val child = children.getOrNull(index) ?: return
        if (child.appWidgetId == -1) return
        val sourceBounds = mosaic.childHost.tileBounds(index) ?: return
        sourceBounds.offset(mosaic.left, mosaic.top)
        val tile = mosaic.childHost.detachTile(index) ?: return
        val snapshot = createTileSnapshot(tile)
        mosaic.isEnabled = false
        val overlay = buildOverlay()
        val card = buildCard(sourceBounds)
        val body = MosaicFocusSwipeContainer(
            activity,
            onDrag = { deltaX -> active?.let { dragFocus(it, deltaX) } },
            onRelease = { deltaX -> active?.let { releaseFocusDrag(it, deltaX) } },
            onCancel = { active?.let(::restoreFocusDrag) }
        )
        snapshot?.let { body.addView(it, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        )) }
        card.addView(buildHeader())
        card.addView(body, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ).apply { topMargin = (48f * density).toInt() })
        prepareOpen(card, sourceBounds)
        overlay.addView(card)
        mainContainer.addView(overlay)
        val state = Active(mosaic, children, overlay, card, body, tile, index, sourceBounds)
        active = state
        body.post {
            if (active !== state) return@post
            body.cameraDistance = 12_000f * density
            mosaic.childHost.showFocusedTile(tile, body, body.width, body.height)
            if (snapshot == null) {
                animateOpen(state)
                return@post
            }
            tile.cell.alpha = 0f
            body.postDelayed({
                if (active !== state) return@postDelayed
                animateOpen(state)
                tile.cell.animate().alpha(1f).setDuration(280L).start()
                snapshot.animate().alpha(0f).setDuration(280L).withEndAction {
                    body.removeView(snapshot)
                }.start()
            }, 180L)
        }
        FolderBlurCoordinator.setWorkspaceBlur(activity, true)
    }

    fun close() {
        val state = active ?: return
        active = null
        val cardWidth = state.card.width.coerceAtLeast(1)
        val cardHeight = state.card.height.coerceAtLeast(1)
        val targetX = state.sourceBounds.exactCenterX() - state.card.x - cardWidth / 2f
        val targetY = state.sourceBounds.exactCenterY() - state.card.y - cardHeight / 2f
        state.card.animate()
            .scaleX(state.sourceBounds.width() / cardWidth.toFloat())
            .scaleY(state.sourceBounds.height() / cardHeight.toFloat())
            .translationX(targetX).translationY(targetY)
            .setDuration(220L)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                state.mosaic.childHost.attachFocusedTile(state.tile)
                state.mosaic.isEnabled = true
                mainContainer.removeView(state.overlay)
                FolderBlurCoordinator.setWorkspaceBlur(activity, false)
            }.start()
    }

    private fun buildOverlay(): FrameLayout = FrameLayout(activity).apply {
        val base = Color.parseColor(NexusDesignSystem.COLOR_BASE)
        setBackgroundColor(Color.argb(110, Color.red(base), Color.green(base), Color.blue(base)))
        isClickable = true
        setOnClickListener { close() }
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        )
    }

    private fun buildCard(sourceBounds: Rect): FrameLayout {
        val card = FrameLayout(activity).apply {
            isClickable = true
            background = GradientDrawable().apply {
                cornerRadius = 24f * density
                setColor(Color.parseColor(NexusDesignSystem.COLOR_BASE))
                setStroke((1f * density).toInt().coerceAtLeast(1),
                    Color.parseColor(NexusDesignSystem.COLOR_GLASS_BORDER))
            }
        }
        val width = (mainContainer.width * 0.74f).toInt()
        val height = (mainContainer.height * 0.54f * 0.60f).toInt()
        val edgeMargin = (8f * density).toInt()
        val gap = (12f * density).toInt()
        val safeTop = (mainContainer.rootWindowInsets?.systemWindowInsetTop ?: 0) + edgeMargin
        val left = (sourceBounds.exactCenterX() - width / 2)
            .coerceIn(
                edgeMargin.toFloat(),
                (mainContainer.width - width - edgeMargin).coerceAtLeast(edgeMargin).toFloat()
            )
            .toInt()
        val top = (sourceBounds.top - height - gap).coerceAtLeast(safeTop)
        card.layoutParams = FrameLayout.LayoutParams(width, height, Gravity.TOP or Gravity.START).apply {
            leftMargin = left
            topMargin = top
        }
        return card
    }

    private fun createTileSnapshot(tile: MosaicFocusedTile): ImageView? {
        val width = tile.cell.width
        val height = tile.cell.height
        if (width <= 0 || height <= 0) return null
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        tile.cell.draw(Canvas(bitmap))
        return ImageView(activity).apply {
            setImageBitmap(bitmap)
            scaleType = ImageView.ScaleType.FIT_XY
        }
    }

    private fun buildHeader(): LinearLayout = LinearLayout(activity).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding((16f * density).toInt(), 0, (8f * density).toInt(), 0)
        addView(TextView(activity).apply {
            text = activity.getString(com.nexus.launcher.R.string.mosaic_focus_title)
            com.nexus.launcher.typography.NexusTypeScale.title.bindTo(
                this,
                Color.parseColor(NexusDesignSystem.COLOR_TEXT_PRIMARY)
            )
            gravity = Gravity.CENTER_VERTICAL
        }, LinearLayout.LayoutParams(0, FrameLayout.LayoutParams.MATCH_PARENT, 1f))
        addView(openAppAction())
        addView(closeAction())
        layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, (48f * density).toInt()
        )
    }

    private fun openAppAction(): TextView = TextView(activity).apply {
        text = activity.getString(com.nexus.launcher.R.string.mosaic_open_app)
        com.nexus.launcher.typography.NexusTypeScale.bodyStrong.bindTo(
            this,
            Color.parseColor(NexusDesignSystem.COLOR_ACCENT)
        )
        gravity = Gravity.CENTER
        background = NexusDesignSystem.buildGlassRowBackground(activity, density)
        setOnClickListener { openFocusedProvider() }
        layoutParams = LinearLayout.LayoutParams((76f * density).toInt(), (32f * density).toInt()).apply {
            marginEnd = (8f * density).toInt()
        }
    }

    private fun closeAction(): ImageView = ImageView(activity).apply {
        setImageResource(com.nexus.launcher.R.drawable.ic_close)
        setColorFilter(Color.parseColor(NexusDesignSystem.COLOR_TEXT_SECONDARY))
        contentDescription = activity.getString(com.nexus.launcher.R.string.action_close)
        setPadding((10f * density).toInt(), (10f * density).toInt(), (10f * density).toInt(), (10f * density).toInt())
        setOnClickListener { close() }
        layoutParams = LinearLayout.LayoutParams((40f * density).toInt(), (40f * density).toInt())
    }

    private fun openFocusedProvider() {
        val state = active ?: return
        val packageName = state.children.getOrNull(state.index)?.providerPackage ?: return
        val intent = activity.packageManager.getLaunchIntentForPackage(packageName) ?: return
        activity.startActivity(intent)
    }

    /** Reassert the Focus Window after the provider app returns to the launcher. */
    fun restoreAfterActivityReturn() {
        val state = active ?: return
        mainContainer.post {
            if (active !== state) return@post
            FolderBlurCoordinator.setWorkspaceBlur(activity, true)
            state.mosaic.childHost.showFocusedTile(
                state.tile, state.body, state.body.width, state.body.height
            )
            state.body.requestLayout()
            state.body.invalidate()
        }
    }

    private fun prepareOpen(card: FrameLayout, source: Rect) {
        val lp = card.layoutParams as FrameLayout.LayoutParams
        val width = lp.width.coerceAtLeast(1)
        val height = lp.height.coerceAtLeast(1)
        card.pivotX = width / 2f
        card.pivotY = height / 2f
        card.scaleX = source.width() / width.toFloat()
        card.scaleY = source.height() / height.toFloat()
        card.translationX = source.exactCenterX() - lp.leftMargin - width / 2f
        card.translationY = source.exactCenterY() - lp.topMargin - height / 2f
    }

    private fun animateOpen(state: Active) {
        state.card.animate().cancel()
        state.card.animate().scaleX(1f).scaleY(1f).translationX(0f).translationY(0f)
            .setDuration(320L).setInterpolator(DecelerateInterpolator(1.5f)).start()
    }

    private fun showIndex(state: Active, wanted: Int) {
        if (state.isChanging) return
        if (state.children.isEmpty()) return
        val next = ((wanted % state.children.size) + state.children.size) % state.children.size
        if (next == state.index || state.children[next].appWidgetId == -1) return
        state.isChanging = true
        val direction = if (wanted > state.index) -1f else 1f
        val travel = state.body.width * 0.46f * direction
        state.body.animate()
            .translationX(travel)
            .translationZ(-8f * density)
            .rotationY(10f * direction)
            .scaleX(0.92f).scaleY(0.92f)
            .alpha(0f)
            .setDuration(170L)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
            state.mosaic.childHost.attachFocusedTile(state.tile)
            val nextSource = state.mosaic.childHost.tileBounds(next) ?: run {
                restoreBodyAfterFailedChange(state)
                return@withEndAction
            }
            nextSource.offset(state.mosaic.left, state.mosaic.top)
            val nextTile = state.mosaic.childHost.detachTile(next) ?: run {
                restoreBodyAfterFailedChange(state)
                return@withEndAction
            }
            state.tile = nextTile
            state.index = next
            state.sourceBounds = nextSource
            state.mosaic.childHost.showFocusedTile(nextTile, state.body, state.body.width, state.body.height)
            LivingMosaicHaptics.confirm(state.card)
            state.body.translationX = -travel * 0.72f
            state.body.translationZ = -10f * density
            state.body.rotationY = -12f * direction
            state.body.scaleX = 0.90f
            state.body.scaleY = 0.90f
            state.body.alpha = 0f
            state.body.post {
                state.body.animate()
                    .translationX(0f).translationZ(0f).rotationY(0f)
                    .scaleX(1f).scaleY(1f).alpha(1f)
                    .setDuration(260L)
                    .setInterpolator(DecelerateInterpolator()).withEndAction {
                        state.isChanging = false
                    }.start()
            }
        }.start()
    }

    private fun restoreBodyAfterFailedChange(state: Active) {
        state.mosaic.childHost.detachTile(state.index)?.let { tile ->
            state.tile = tile
            state.mosaic.childHost.showFocusedTile(tile, state.body, state.body.width, state.body.height)
        }
        state.body.animate().translationX(0f).translationZ(0f).rotationY(0f)
            .scaleX(1f).scaleY(1f).alpha(1f).setDuration(150L).withEndAction {
            state.isChanging = false
        }.start()
    }

    private fun dragFocus(state: Active, deltaX: Float) {
        if (state.isChanging) return
        val width = state.body.width.coerceAtLeast(1).toFloat()
        val progress = (kotlin.math.abs(deltaX) / width).coerceIn(0f, 0.42f)
        state.body.translationX = deltaX * 0.78f
        state.body.translationZ = -8f * density * progress
        state.body.rotationY = -12f * deltaX / width
        state.body.scaleX = 1f - 0.10f * progress
        state.body.scaleY = state.body.scaleX
        state.body.alpha = 1f - 0.25f * progress
    }

    private fun restoreFocusDrag(state: Active) {
        if (state.isChanging) return
        state.body.animate().translationX(0f).translationZ(0f).rotationY(0f)
            .scaleX(1f).scaleY(1f).alpha(1f).setDuration(180L)
            .setInterpolator(DecelerateInterpolator()).start()
    }

    private fun releaseFocusDrag(state: Active, deltaX: Float) {
        val threshold = 48f * density
        if (kotlin.math.abs(deltaX) < threshold) {
            restoreFocusDrag(state)
            return
        }
        showIndex(state, if (deltaX < 0f) state.index + 1 else state.index - 1)
    }
}
