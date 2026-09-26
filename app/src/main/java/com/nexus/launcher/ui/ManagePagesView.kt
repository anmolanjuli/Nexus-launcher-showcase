package com.nexus.launcher.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale

/**
 * Manage Pages — premium phone-frame cards over frosted workspace blur.
 * Card chrome: [ManagePagesCardFactory]. Entrance: [ManagePagesEntranceAnimator].
 */
class ManagePagesView(
    context: Context,
    private val density: Float,
    private val accentColor: Int = 0,
    private val topInset: Int,
    private var totalPages: Int,
    private val currentPage: Int,
    private var defaultPage: Int,
    private val pageThumbnails: Map<Int, android.graphics.Bitmap>,
    private val pageItemCounts: Map<Int, Int>,
    private val onDeletePage: (Int) -> Unit,
    private val onSetDefault: (Int) -> Unit,
    private val onAddPage: () -> Unit,
    private val onReorderPages: (from: Int, to: Int) -> Unit,
    private val onDismiss: () -> Unit
) : FrameLayout(context) {

    private val dp = density
    private var isActionLocked = false
    private val tokens: NexusColorTokens = try {
        ThemeObserver.currentTokens(context)
    } catch (_: Exception) {
        NexusColorTokens.Dark
    }

    /** Content width minus side margins — slightly smaller cards for balanced screen presence. */
    private val columnWidthHintPx: Int = run {
        val screenW = resources.displayMetrics.widthPixels
        val contentPad = (48 * dp).toInt()
        val perCardSideMargins = (24 * dp).toInt()
        ((screenW - contentPad) / 2 - perCardSideMargins).coerceAtLeast(1)
    }
    private val subtitleView: TextView
    private val pagesGrid = android.widget.GridLayout(context).apply {
        columnCount = 2
        clipChildren = false
        clipToPadding = false
        layoutParams = LinearLayout.LayoutParams(
            LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
        )
        layoutTransition = android.animation.LayoutTransition().apply {
            setDuration(180)
            enableTransitionType(android.animation.LayoutTransition.CHANGE_APPEARING)
            enableTransitionType(android.animation.LayoutTransition.CHANGE_DISAPPEARING)
        }
    }

    init {
        // Default/Neumorphism: fully opaque theme bg. Frosted Glass: translucent fill over the
        // workspace blur HomeEditController already activates behind this view (setBlurState) —
        // previously this was unconditionally opaque, which hid that blur entirely regardless of
        // UI Style, contradicting this file's own "over frosted workspace blur" doc comment.
        val alpha = (com.nexus.launcher.ui.glass.FrostedGlassEngine.sheetFillAlpha(1f, 0.55f) * 255).toInt()
        setBackgroundColor((alpha shl 24) or (tokens.bg and 0x00FFFFFF))
        val scroll = ScrollView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            isVerticalScrollBarEnabled = false
            clipChildren = false
            clipToPadding = false
        }
        val mainContent = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            setPadding(
                (24 * dp).toInt(), topInset + (12 * dp).toInt(),
                (24 * dp).toInt(), (32 * dp).toInt()
            )
        }
        mainContent.addView(buildTopBar())
        subtitleView = TextView(context).apply {
            NexusTypeScale.body.bindTo(this, tokens.textSecondary)
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (6 * dp).toInt()
                bottomMargin = (18 * dp).toInt()
            }
        }
        mainContent.addView(subtitleView)
        for (i in 0 until totalPages) {
            val card = ManagePagesCardFactory.createPageCard(
                context, dp, tokens, columnWidthHintPx
            )
            wireCardActions(card)
            ManagePagesDragHelper.wireCard(
                card, pagesGrid, { totalPages }, ::handleReorder, ::refreshPageNumbers
            )
            pagesGrid.addView(card)
        }
        pagesGrid.addView(
            ManagePagesCardFactory.createAddCard(
                context, dp, tokens, ::onAddClicked, columnWidthHintPx
            )
        )
        mainContent.addView(pagesGrid)
        scroll.addView(mainContent)
        addView(scroll)
        refreshPageNumbers()
        post {
            ManagePagesEntranceAnimator.play(pagesGrid, dp) { refreshPageNumbers() }
        }
    }

    override fun onDetachedFromWindow() {
        ManagePagesEntranceAnimator.cancel(pagesGrid)
        super.onDetachedFromWindow()
    }

    private fun buildTopBar(): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        addView(TextView(context).apply {
            text = context.getString(com.nexus.launcher.R.string.manage_pages_title)
            NexusTypeScale.title.bindTo(this, tokens.textPrimary)
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
        })
        addView(
            WallpaperSheetComponents.buildCloseButton(context, dp) { onDismiss() }
        )
    }

    private fun onAddClicked() {
        if (isActionLocked) return
        isActionLocked = true
        postDelayed({ isActionLocked = false }, 200)
        performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
        onAddPage()
    }

    private fun wireCardActions(card: FrameLayout) {
        val deleteBtn = card.findViewWithTag<ImageView>(ManagePagesCardFactory.TAG_DELETE)
        val homeBtn = card.findViewWithTag<ImageView>(ManagePagesCardFactory.TAG_SET_HOME)
        deleteBtn?.setOnClickListener {
            if (isActionLocked) return@setOnClickListener
            isActionLocked = true
            postDelayed({ isActionLocked = false }, 200)
            deleteBtn.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            val idx = pagesGrid.indexOfChild(card)
            if (idx == -1) return@setOnClickListener
            if (totalPages == 1) {
                Toast.makeText(context, context.getString(com.nexus.launcher.R.string.toast_cant_delete_last_page), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val itemCount = pageItemCounts[idx] ?: 0
            if (itemCount > 0) {
                com.nexus.launcher.ui.folder.FolderAuroraDialogs.showConfirmation(
                    context = context,
                    title = context.getString(com.nexus.launcher.R.string.dialog_delete_page_title, idx + 1),
                    body = context.resources.getQuantityString(com.nexus.launcher.R.plurals.dialog_delete_page_message, itemCount, itemCount),
                    cancelText = context.getString(com.nexus.launcher.R.string.action_cancel),
                    confirmText = context.getString(com.nexus.launcher.R.string.action_delete),
                    onConfirm = {
                        pagesGrid.removeView(card)
                        totalPages--
                        defaultPage = PageReorderHelper.defaultPageAfterDelete(
                            defaultPage, idx, totalPages
                        )
                        refreshPageNumbers()
                        onDeletePage(idx)
                    }
                )
            } else {
                pagesGrid.removeView(card)
                totalPages--
                defaultPage = PageReorderHelper.defaultPageAfterDelete(
                    defaultPage, idx, totalPages
                )
                refreshPageNumbers()
                onDeletePage(idx)
            }
        }
        homeBtn?.setOnClickListener {
            if (isActionLocked) return@setOnClickListener
            isActionLocked = true
            postDelayed({ isActionLocked = false }, 150)
            val idx = pagesGrid.indexOfChild(card)
            if (idx != -1 && idx != defaultPage) {
                homeBtn.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                defaultPage = idx
                refreshPageNumbers()
                onSetDefault(idx)
            }
        }
    }

    private fun handleReorder(from: Int, to: Int) {
        if (from == to || from !in 0 until totalPages || to !in 0 until totalPages) return
        performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
        onReorderPages(from, to)
    }

    private fun refreshPageNumbers() {
        val homeLabel = defaultPage + 1
        subtitleView.text = when (totalPages) {
            1 -> context.getString(com.nexus.launcher.R.string.manage_pages_subtitle_one, homeLabel)
            else -> context.getString(com.nexus.launcher.R.string.manage_pages_subtitle, totalPages, homeLabel)
        }
        for (i in 0 until pagesGrid.childCount - 1) {
            val card = pagesGrid.getChildAt(i) as? FrameLayout ?: continue
            ManagePagesCardFactory.applyCardState(
                card = card,
                pageIndex = i,
                thumb = pageThumbnails[i],
                isCurrent = i == currentPage,
                isDefault = i == defaultPage,
                density = dp,
                tokens = tokens
            )
        }
    }
}
