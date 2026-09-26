package com.nexus.launcher.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale

/**
 * The drawer's category picker, opened from the left segment of the Split Search Pill.
 *
 * A plain [PopupWindow] rather than a dialog: no dim, no `FLAG_BLUR_BEHIND`, no extra activity
 * window. Selecting a category used to sit behind a cross-process blur being raised and torn
 * down around a dialog, which made the filter feel delayed and the transition forced — this
 * shows and dismisses within a frame, so the grid re-filters immediately.
 *
 * Opens downward from a top-docked pill and upward from a bottom-docked one, via
 * [AnchoredMenuPlacement]. Because nothing dims behind it, the card is drawn fully opaque.
 *
 * Below the list sit "New category" and "Edit categories". Edit mode swaps each row's check for
 * a delete button (All has none: it is the unfiltered view) and offers restoring the built-ins
 * when any were deleted. In edit mode a hint line says what is tappable: each icon sits in a
 * round button that opens the icon picker, and tapping a name renames it. Every action closes
 * the dropdown and hands off to the caller.
 */
class DrawerCategoryDropdown(
    private val context: Context,
    private val anchorView: View,
    private val categories: List<String>,
    private val selectedCategory: String,
    private val onCategorySelected: (String) -> Unit,
    private val onDismissed: (() -> Unit)? = null,
    private val onAddCategory: (() -> Unit)? = null,
    private val onDeleteCategory: ((String) -> Unit)? = null,
    private val onSortApps: (() -> Unit)? = null,
    private val canRestoreDefaults: Boolean = false,
    private val onRestoreDefaults: (() -> Unit)? = null,
    /** Edit mode: the category whose icon was tapped. */
    private val onChangeIcon: ((String) -> Unit)? = null,
    /** Edit mode: the category whose name was tapped. */
    private val onRename: ((String) -> Unit)? = null,
    /** Reopened after a manage dialog closes, so the user stays in edit mode until Done. */
    startEditing: Boolean = false,
) {
    private var editing = startEditing

    private val dp = context.resources.displayMetrics.density
    private var popup: PopupWindow? = null

    val isShowing: Boolean get() = popup?.isShowing == true

    fun dismiss() {
        popup?.dismiss()
    }

    fun show() {
        val tokens: NexusColorTokens = try {
            ThemeObserver.currentTokens(context)
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }

        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (6 * dp).toInt(), 0, (6 * dp).toInt())
        }
        val card = CappedScrollView(
            context, (context.resources.displayMetrics.heightPixels * 0.5f).toInt()
        ).apply {
            isVerticalScrollBarEnabled = false
            background = GradientDrawable().apply {
                // Opaque: with no scrim behind the popup, a translucent card would read the
                // drawer's app grid straight through the category labels.
                setColor(tokens.surfaceRaised or 0xFF000000.toInt())
                cornerRadius = 18 * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
            }
            clipToOutline = true
            addView(list)
        }

        val cardWidth = (220 * dp).toInt().coerceAtMost(
            context.resources.displayMetrics.widthPixels - (24 * dp).toInt()
        )
        val window = PopupWindow(card, cardWidth, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            // A non-null background is what makes outside taps and the back button dismiss.
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
            isFocusable = true
            elevation = 12 * dp
            setOnDismissListener { onDismissed?.invoke() }
        }
        popup = window

        fillList(list, tokens, window)

        val anchorPos = IntArray(2)
        anchorView.getLocationOnScreen(anchorPos)
        val isRtl = anchorView.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val anchorHeight = anchorView.height.takeIf { it > 0 } ?: (48 * dp).toInt()
        val direction = AnchoredMenuPlacement.directionFor(context, anchorPos[1], anchorHeight)

        // Measured up front so an upward-opening popup knows where its own top edge lands.
        card.measure(
            View.MeasureSpec.makeMeasureSpec(cardWidth, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val pos = AnchoredMenuPlacement.screenPositionFor(
            context, anchorView, anchorPos, cardWidth, card.measuredHeight, isRtl, direction
        )

        window.showAtLocation(anchorView, Gravity.NO_GRAVITY, pos[0], pos[1])
    }

    private fun fillList(list: LinearLayout, tokens: NexusColorTokens, window: PopupWindow) {
        list.removeAllViews()
        if (editing) {
            list.addView(TextView(context).apply {
                text = context.getString(R.string.category_edit_hint)
                NexusTypeScale.caption.bindTo(this, tokens.textSecondary)
                setPadding((18 * dp).toInt(), (4 * dp).toInt(), (18 * dp).toInt(), (6 * dp).toInt())
            })
        }
        categories.forEach { category ->
            val onDelete: (() -> Unit)? = if (editing && category != DrawerCategories.ALL) {
                { window.dismiss(); onDeleteCategory?.invoke(category); Unit }
            } else null
            list.addView(buildRow(tokens, category, category == selectedCategory && !editing, onDelete) {
                window.dismiss()
                if (editing) onRename?.invoke(category) else onCategorySelected(category)
            }.apply {
                // The leading icon is its own target in edit mode: a round button, so it reads as
                // something to press rather than decoration.
                if (editing) (this as ViewGroup).getChildAt(0).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(tokens.surface)
                        setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.textSecondary)
                    }
                    (this as? ImageView)?.imageTintList = ColorStateList.valueOf(tokens.textPrimary)
                    val pad = (6 * dp).toInt()
                    setPadding(pad, pad, pad, pad)
                    layoutParams = LinearLayout.LayoutParams((32 * dp).toInt(), (32 * dp).toInt())
                        .apply { marginEnd = (10 * dp).toInt() }
                    setOnClickListener {
                        it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        window.dismiss()
                        onChangeIcon?.invoke(category)
                    }
                }
            })
        }
        if (onAddCategory == null && onDeleteCategory == null) return
        list.addView(View(context).apply {
            setBackgroundColor(tokens.divider)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (1 * dp).toInt().coerceAtLeast(1))
                .apply { setMargins(0, (6 * dp).toInt(), 0, (6 * dp).toInt()) }
        })
        if (!editing && onAddCategory != null) {
            list.addView(actionRow(tokens, R.drawable.ic_add, R.string.category_new) { window.dismiss(); onAddCategory.invoke() })
        }
        if (!editing && onSortApps != null) {
            list.addView(actionRow(tokens, R.drawable.ic_category, R.string.category_sort_title) {
                window.dismiss(); onSortApps.invoke()
            })
        }
        if (editing && canRestoreDefaults && onRestoreDefaults != null) {
            list.addView(actionRow(tokens, R.drawable.ic_restore, R.string.category_restore_defaults) {
                window.dismiss(); onRestoreDefaults.invoke()
            })
        }
        if (onDeleteCategory != null) {
            val (icon, label) = if (editing) R.drawable.ic_check to R.string.action_done else R.drawable.ic_edit to R.string.category_edit
            list.addView(actionRow(tokens, icon, label) {
                editing = !editing
                fillList(list, tokens, window)
            })
        }
    }

    private fun actionRow(tokens: NexusColorTokens, iconRes: Int, labelRes: Int, onClick: () -> Unit): View =
        buildRow(tokens, label = context.getString(labelRes), iconRes = iconRes, isSelected = false, onDelete = null, onClick = onClick)

    private fun buildRow(
        tokens: NexusColorTokens, category: String, isSelected: Boolean, onDelete: (() -> Unit)?, onClick: () -> Unit
    ): View = buildRow(
        tokens, DrawerSearchPillBuilder.categoryLabelText(context, category), DrawerCategories.iconFor(context, category),
        isSelected, onDelete, onClick,
    )

    private fun buildRow(
        tokens: NexusColorTokens, label: String, iconRes: Int, isSelected: Boolean,
        onDelete: (() -> Unit)?, onClick: () -> Unit
    ): View {
        val ripple = TypedValue().also {
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
        }.resourceId
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((18 * dp).toInt(), (12 * dp).toInt(), (18 * dp).toInt(), (12 * dp).toInt())
            // Neumorphism: the chosen category is pressed into the card, inset from its edges.
            if (isSelected && com.nexus.launcher.ui.glass.NeumorphicSurfaces.isActive) {
                background = android.graphics.drawable.InsetDrawable(
                    com.nexus.launcher.ui.glass.NeumorphicSurfaces.field(this, tokens, 12 * dp),
                    (10 * dp).toInt(), (4 * dp).toInt(), (10 * dp).toInt(), (4 * dp).toInt(),
                )
            } else if (ripple != 0) setBackgroundResource(ripple)
            addView(ImageView(context).apply {
                setImageResource(iconRes)
                imageTintList = ColorStateList.valueOf(if (isSelected) tokens.textPrimary else tokens.textSecondary)
                layoutParams = LinearLayout.LayoutParams((18 * dp).toInt(), (18 * dp).toInt())
                    .apply { marginEnd = (12 * dp).toInt() }
            })
            addView(TextView(context).apply {
                text = label
                NexusTypeScale.body.bindTo(
                    this, if (isSelected) tokens.textPrimary else tokens.textSecondary
                )
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            if (isSelected) {
                addView(ImageView(context).apply {
                    setImageResource(R.drawable.ic_check)
                    imageTintList = ColorStateList.valueOf(tokens.textPrimary)
                    layoutParams = LinearLayout.LayoutParams((18 * dp).toInt(), (18 * dp).toInt())
                })
            }
            if (onDelete != null) {
                addView(ImageView(context).apply {
                    setImageResource(R.drawable.ic_delete)
                    imageTintList = ColorStateList.valueOf(tokens.danger)
                    val pad = (4 * dp).toInt()
                    setPadding(pad, pad, pad, pad)
                    layoutParams = LinearLayout.LayoutParams((28 * dp).toInt(), (28 * dp).toInt())
                    contentDescription = context.getString(R.string.action_delete)
                    setOnClickListener {
                        it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onDelete()
                    }
                })
            }
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
        }
    }

    /** [ScrollView] measures WRAP_CONTENT unbounded; this caps it so a long list scrolls inside
     *  the card instead of growing the card past the screen. */
    private class CappedScrollView(
        context: Context, private val maxHeightPx: Int
    ) : ScrollView(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            super.onMeasure(
                widthMeasureSpec,
                MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.AT_MOST)
            )
        }
    }
}
