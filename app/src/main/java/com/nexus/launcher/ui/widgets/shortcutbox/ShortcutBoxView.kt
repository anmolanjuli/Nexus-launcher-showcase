package com.nexus.launcher.ui.widgets.shortcutbox

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Process
import android.view.MotionEvent
import android.widget.FrameLayout
import android.widget.Toast
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.nexus.launcher.R
import com.nexus.launcher.data.HomeItemTypes
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.ui.MainActivity
import com.nexus.launcher.ui.canvas.HomeScreenIconCache
import com.nexus.launcher.ui.picker.ShortcutPickerOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Container view for Shortcut / App Box hosted in [WidgetOverlayLayout]. */
class ShortcutBoxView(context: Context) : FrameLayout(context) {

    private val renderer = ShortcutBoxRenderer(context)
    private var boundItem: HomeScreenItem? = null
    private var config = ShortcutBoxConfig()
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
    private var currentSlotDialog: ShortcutBoxSlotMenuDialog? = null
    private var hiddenSlotIndex: Int? = null
    private var isHoverAcceptance = false
    private var hoverAnimator: android.animation.ValueAnimator? = null

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
        hoverAnimator = android.animation.ValueAnimator.ofFloat(scaleX, targetScale).apply {
            duration = 180L
            interpolator = android.view.animation.OvershootInterpolator(1.2f)
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
        val animator = android.animation.ValueAnimator.ofFloat(1.04f, 0.97f, 1.0f).apply {
            duration = 320L
            interpolator = android.view.animation.DecelerateInterpolator()
            addUpdateListener {
                val s = it.animatedValue as Float
                scaleX = s
                scaleY = s
                invalidate()
            }
        }
        animator.start()
    }

    private val touchRouter = ShortcutBoxTouchRouter(
        host = this,
        renderer = renderer,
        configProvider = { config },
        membersProvider = { members.associateBy { it.column } },
        isMoveModeProvider = { isMoveModeActive },
        onEmptySlotTapped = { slot -> handleEmptySlotTapped(slot) },
        onMemberTapped = { member -> handleMemberTapped(member) },
        onMemberLongPressed = { member, slot, rx, ry -> handleMemberLongPressed(member, slot, rx, ry) },
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

    fun applyLiveConfig(newConfig: ShortcutBoxConfig) {
        config = newConfig
        invalidate()
    }

    fun bind(item: HomeScreenItem, childMembers: List<HomeScreenItem>) {
        boundItem = item
        config = ShortcutBoxConfig.parse(item.folderConfigJson)
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
        ShortcutPickerOverlay.show(act) { shortcut ->
            val scope = act.lifecycleScope
            val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
                context.applicationContext,
                com.nexus.launcher.di.DaoEntryPoint::class.java
            )
            ShortcutBoxOperations.addShortcutToSlot(
                scope = scope,
                dao = entryPoint.homeScreenDao(),
                boxId = item.id.toLong(),
                slotIndex = slotIndex,
                shortcut = shortcut
            )
        }
    }

    private fun handleMemberTapped(member: HomeScreenItem) {
        if (member.itemType == HomeItemTypes.SHORTCUT) {
            val shortcutId = try {
                JSONObject(member.folderConfigJson).optString("shortcutId")
            } catch (_: Exception) { "" }

            if (member.packageName == context.packageName || NexusBuiltinShortcuts.isBuiltin(shortcutId)) {
                NexusBuiltinShortcuts.execute(context, shortcutId)
                postDelayed({ invalidate() }, 120)
                postDelayed({ invalidate() }, 400)
                return
            }

            try {
                val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                launcherApps.startShortcut(member.packageName, shortcutId, null, null, Process.myUserHandle())
            } catch (e: Exception) {
                Toast.makeText(context, context.getString(R.string.toast_cannot_launch_shortcut), Toast.LENGTH_SHORT).show()
            }
        } else {
            val intent = context.packageManager.getLaunchIntentForPackage(member.packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
        }
    }

    private fun handleMemberLongPressed(member: HomeScreenItem, slotIndex: Int, rawX: Float, rawY: Float) {
        currentSlotDialog?.dismiss()
        val title = labelsBySlot[slotIndex] ?: member.folderTitle
        val dialog = ShortcutBoxSlotMenuDialog(
            context = context,
            slotTitle = title,
            onChangeShortcut = { handleEmptySlotTapped(slotIndex) },
            onRemoveFromBox = { removeMemberFromSlot(member) },
            onOpenWidgetSettings = { onContainerLongPress?.invoke() }
        )
        currentSlotDialog = dialog
        dialog.show()
    }

    private fun removeMemberFromSlot(member: HomeScreenItem) {
        val act = context as? MainActivity ?: return
        val entryPoint = dagger.hilt.android.EntryPointAccessors.fromApplication(
            context.applicationContext,
            com.nexus.launcher.di.DaoEntryPoint::class.java
        )
        act.lifecycleScope.launch(Dispatchers.IO) {
            entryPoint.homeScreenDao().removeItemById(member.id)
        }
    }

    private fun getHomeScreenViewModel(): com.nexus.launcher.ui.HomeScreenViewModel? {
        val act = context as? androidx.lifecycle.ViewModelStoreOwner ?: return null
        return androidx.lifecycle.ViewModelProvider(act)[com.nexus.launcher.ui.HomeScreenViewModel::class.java]
    }

    private var draggingMember: HomeScreenItem? = null

    private fun handleMemberDragStarted(member: HomeScreenItem, slotIndex: Int, rawX: Float, rawY: Float) {
        draggingMember = member
        currentSlotDialog?.dismiss()
        currentSlotDialog = null
        hiddenSlotIndex = slotIndex
        invalidate()

        val act = context as? MainActivity ?: return
        val item = boundItem ?: return
        val hsv = getHomeScreenViewModel() ?: return

        val pm = context.packageManager
        val intent = if (member.itemType == HomeItemTypes.SHORTCUT) {
            val shortcutId = try {
                JSONObject(member.folderConfigJson).optString("shortcutId")
            } catch (_: Exception) { "" }
            Intent().apply {
                setPackage(member.packageName)
                putExtra("package_name", member.packageName)
                putExtra("dragged_from_folder_id", item.id.toLong())
                putExtra("dragged_folder_item_id", member.id.toLong())
                putExtra("shortcutId", shortcutId)
            }
        } else {
            (pm.getLaunchIntentForPackage(member.packageName) ?: Intent()).apply {
                setPackage(member.packageName)
                putExtra("package_name", member.packageName)
                putExtra("dragged_from_folder_id", item.id.toLong())
                putExtra("dragged_folder_item_id", member.id.toLong())
            }
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
        ShortcutBoxIconResolver.resolve(context, members, iconsBySlot, labelsBySlot)
    }
}
