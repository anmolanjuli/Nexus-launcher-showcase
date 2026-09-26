package com.nexus.launcher.ui.widgets.appbox

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.view.MotionEvent
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.Toast
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import com.nexus.launcher.data.HomeItemTypes
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.MainActivity
import com.nexus.launcher.ui.picker.AppBoxPickerOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Container view for App Box hosted in [WidgetOverlayLayout]. */
class AppBoxView(context: Context) : FrameLayout(context) {

    private val renderer = AppBoxRenderer(context)
    private var boundItem: HomeScreenItem? = null
    private var config = AppBoxConfig()
    private var members: List<HomeScreenItem> = emptyList()
    private val iconsBySlot = mutableMapOf<Int, Drawable?>()
    private val labelsBySlot = mutableMapOf<Int, String>()

    private var onContainerLongPress: (() -> Unit)? = null

    var onLongPressDetected: (() -> Unit)?
        get() = onContainerLongPress
        set(value) { onContainerLongPress = value }
    var onDragStarted: (() -> Unit)? = null
    var onDragMoved: ((Float, Float) -> Unit)? = null
    var onDropDetected: ((Float, Float) -> Unit)? = null
    var onBodyTappedInMoveMode: (() -> Unit)? = null

    private var isMoveModeActive = false
    private var isResizeModeActive = false
    private var hiddenSlotIndex: Int? = null
    private var isHoverAcceptance = false
    private var hoverAnimator: ValueAnimator? = null

    fun setMoveModeActive(active: Boolean) {
        isMoveModeActive = active
    }

    fun setResizeModeActive(active: Boolean) {
        isResizeModeActive = active
    }

    fun setAcceptanceHover(active: Boolean) {
        if (isHoverAcceptance == active) return
        isHoverAcceptance = active
        hoverAnimator?.cancel()
        val targetScale = if (active) 1.04f else 1.0f
        hoverAnimator = ValueAnimator.ofFloat(scaleX, targetScale).apply {
            duration = 180L
            interpolator = OvershootInterpolator(1.2f)
            addUpdateListener {
                val s = it.animatedValue as Float
                scaleX = s
                scaleY = s
                invalidate()
            }
            start()
        }
    }

    fun triggerDropPulse(success: Boolean) {
        isHoverAcceptance = false
        hoverAnimator?.cancel()
        val animator = ValueAnimator.ofFloat(1.04f, 0.97f, 1.0f).apply {
            duration = 320L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                val s = it.animatedValue as Float
                scaleX = s
                scaleY = s
                invalidate()
            }
        }
        animator.start()
    }

    private val touchRouter = AppBoxTouchRouter(
        host = this,
        renderer = renderer,
        configProvider = { config },
        membersProvider = { members.associateBy { it.column } },
        isMoveModeProvider = { isMoveModeActive },
        onEmptySlotTapped = { slot -> handleEmptySlotTapped(slot) },
        onMemberTapped = { member -> handleMemberTapped(member) },
        onMemberDragStarted = { member, slot, rx, ry -> handleMemberDragStarted(member, slot, rx, ry) },
        onMemberDragMoved = { x, y -> handleMemberDragMoved(x, y) },
        onMemberDragEnded = { x, y -> handleMemberDragEnded(x, y) },
        onMemberDragCancelled = { handleMemberDragCancelled() },
        onContainerLongPressed = { onContainerLongPress?.invoke() },
        onDragStarted = { onDragStarted },
        onDragMoved = { onDragMoved },
        onDropDetected = { onDropDetected },
        onBodyTappedInMoveMode = { onBodyTappedInMoveMode }
    )

    init {
        setWillNotDraw(false)
        isClickable = true
        isFocusable = true
    }

    fun currentItem(): HomeScreenItem? = boundItem

    fun membersMap(): Map<Int, HomeScreenItem> = members.associateBy { it.column }
    fun iconsMap(): Map<Int, Drawable?> = iconsBySlot
    fun labelsMap(): Map<Int, String> = labelsBySlot

    fun applyLiveConfig(newConfig: AppBoxConfig) {
        config = newConfig
        invalidate()
    }

    fun bind(item: HomeScreenItem, childMembers: List<HomeScreenItem>) {
        boundItem = item
        config = AppBoxConfig.parse(item.folderConfigJson)
        members = childMembers
        loadIcons()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val item = boundItem ?: return
        renderer.draw(
            canvas = canvas,
            width = width.toFloat(),
            height = height.toFloat(),
            spanX = item.spanX,
            spanY = item.spanY,
            config = config,
            membersBySlot = members.associateBy { it.column },
            iconsBySlot = iconsBySlot,
            labelsBySlot = labelsBySlot,
            hiddenSlot = hiddenSlotIndex,
            isHoverGlow = isHoverAcceptance,
            hostView = this
        )
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return touchRouter.onTouchEvent(event)
    }

    private fun handleEmptySlotTapped(slotIndex: Int) {
        val act = context as? MainActivity ?: return
        val item = boundItem ?: return
        AppBoxPickerOverlay.show(act) { app ->
            val scope = act.lifecycleScope
            val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
                context.applicationContext,
                com.nexus.launcher.di.DaoEntryPoint::class.java
            )
            AppBoxOperations.addAppToSlot(
                scope = scope,
                dao = entryPoint.homeScreenDao(),
                boxId = item.id.toLong(),
                slotIndex = slotIndex,
                packageName = app.packageName,
                label = app.label
            )
        }
    }

    private fun handleMemberTapped(member: HomeScreenItem) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(member.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                Toast.makeText(context, context.getString(com.nexus.launcher.R.string.app_not_found), Toast.LENGTH_SHORT).show()
            }
        } catch (_: Exception) {
            Toast.makeText(context, context.getString(com.nexus.launcher.R.string.app_launch_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun getHomeScreenViewModel(): com.nexus.launcher.ui.HomeScreenViewModel? {
        val act = context as? ViewModelStoreOwner ?: return null
        return ViewModelProvider(act)[com.nexus.launcher.ui.HomeScreenViewModel::class.java]
    }

    private var draggingMember: HomeScreenItem? = null

    private fun handleMemberDragStarted(member: HomeScreenItem, slotIndex: Int, rawX: Float, rawY: Float) {
        draggingMember = member
        hiddenSlotIndex = slotIndex
        invalidate()

        val act = context as? MainActivity ?: return
        val item = boundItem ?: return
        val hsv = getHomeScreenViewModel() ?: return

        val pm = context.packageManager
        val intent = (pm.getLaunchIntentForPackage(member.packageName) ?: Intent()).apply {
            setPackage(member.packageName)
            putExtra("package_name", member.packageName)
            putExtra("dragged_from_folder_id", item.id.toLong())
            putExtra("dragged_folder_item_id", member.id.toLong())
        }

        val label = labelsBySlot[slotIndex] ?: member.folderTitle
        val icon = iconsBySlot[slotIndex]
        val displayItem = com.nexus.launcher.ui.model.DisplayItem(
            label = label,
            icon = icon,
            intent = intent,
            categoryName = null
        )

        com.nexus.launcher.ui.folder.FolderDragState.isDraggingFromFolder = true
        hsv.startDrag(displayItem, rawX, rawY, com.nexus.launcher.ui.model.DragSource.HOME_SCREEN)
    }

    private fun handleMemberDragMoved(rawX: Float, rawY: Float) {
        getHomeScreenViewModel()?.updateDragPosition(rawX, rawY)
    }

    private fun handleMemberDragEnded(rawX: Float, rawY: Float) {
        val fromMember = draggingMember
        draggingMember = null
        hiddenSlotIndex = null
        invalidate()

        val act = context as? MainActivity ?: return
        val hsv = getHomeScreenViewModel()
        val item = boundItem

        if (item != null && fromMember != null) {
            val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
                context.applicationContext,
                com.nexus.launcher.di.DaoEntryPoint::class.java
            )
            val handledInternal = com.nexus.launcher.ui.widgets.BoxMemberDropHelper.handleMemberDrop(
                hostView = this,
                rawX = rawX,
                rawY = rawY,
                boxId = item.id.toLong(),
                draggingMember = fromMember,
                getSlotAt = { lx, ly -> renderer.getSlotAt(lx, ly, width.toFloat(), height.toFloat(), config) },
                dao = entryPoint.homeScreenDao(),
                scope = act.lifecycleScope
            )
            if (handledInternal) {
                hsv?.cancelDrag()
                com.nexus.launcher.ui.folder.FolderDragState.isDraggingFromFolder = false
                return
            }
        }

        if (hsv != null) {
            val loc = IntArray(2)
            act.canvasView.getLocationOnScreen(loc)
            val localX = rawX - loc[0]
            val localY = rawY - loc[1]
            val cell = com.nexus.launcher.ui.canvas.CanvasHitTestHelper.getCellAtDrop(act.canvasView, localX, localY)
            if (cell != null) {
                hsv.dropItem(
                    page = act.canvasView.currentPage,
                    col = cell.first,
                    row = cell.second
                )
            } else {
                hsv.cancelDrag()
            }
        }
        com.nexus.launcher.ui.folder.FolderDragState.isDraggingFromFolder = false
    }

    private fun handleMemberDragCancelled() {
        draggingMember = null
        hiddenSlotIndex = null
        invalidate()
        getHomeScreenViewModel()?.cancelDrag()
        com.nexus.launcher.ui.folder.FolderDragState.isDraggingFromFolder = false
    }

    private fun loadIcons() {
        val pm = context.packageManager
        val resolver = try {
            dagger.hilt.android.EntryPointAccessors.fromApplication(
                context.applicationContext,
                com.nexus.launcher.ui.icons.IconResolverEntryPoint::class.java
            ).iconResolver()
        } catch (_: Exception) {
            null
        }

        for (member in members) {
            val slot = member.column
            try {
                val icon = resolver?.getIcon(
                    packageName = member.packageName,
                    className = member.launchIntent?.component?.className
                ) ?: pm.getApplicationIcon(member.packageName)
                iconsBySlot[slot] = icon
                val appInfo = pm.getApplicationInfo(member.packageName, 0)
                labelsBySlot[slot] = pm.getApplicationLabel(appInfo).toString()
            } catch (_: Exception) {
                iconsBySlot[slot] = pm.defaultActivityIcon
                labelsBySlot[slot] = member.packageName
            }
        }
    }
}
