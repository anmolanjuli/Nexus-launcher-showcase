package com.nexus.launcher.ui

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.folder.FolderAuroraDialogs

/**
 * Picks a drawer category icon from [CategoryIconCatalog]: as a dialog of its own ([show]), or as
 * a scrolling row inside another dialog ([row]). Icons only, tinted from the theme, with the
 * current choice filled in so it reads at a glance.
 */
object CategoryIconPicker {

    private const val COLUMNS = 6

    fun show(
        context: Context, title: String, selected: String, defaultName: String,
        onClosed: (() -> Unit)? = null, onPick: (String) -> Unit,
    ) {
        val tokens = ThemeObserver.currentTokens(context)
        val dp = context.resources.displayMetrics.density
        val dialog = Dialog(context)
        FolderAuroraDialogs.applyDialogBlur(context, dialog, onClosed)

        val grid = GridLayout(context).apply {
            columnCount = COLUMNS
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL,
            )
        }
        CategoryIconCatalog.icons.forEach { (name, res) ->
            grid.addView(cell(context, tokens, res, name == selected) {
                dialog.dismiss()
                onPick(name)
            })
        }

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * dp).toInt()
            setPadding(pad, pad, pad, (8 * dp).toInt())
            background = FolderAuroraDialogs.dialogCardBackground(tokens, dp)
            addView(TextView(context).apply {
                text = title
                NexusTypeScale.title.bindTo(this, tokens.textPrimary)
                setPadding(0, 0, 0, (14 * dp).toInt())
            })
            addView(object : ScrollView(context) {
                // Capped so a long catalog scrolls inside the card instead of outgrowing the screen.
                override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                    val cap = (context.resources.displayMetrics.heightPixels * 0.5f).toInt()
                    super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST))
                }
            }.apply {
                isVerticalScrollBarEnabled = false
                addView(FrameLayout(context).apply { addView(grid) })
            })
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.END
                setPadding(0, (8 * dp).toInt(), 0, 0)
                if (selected != defaultName) {
                    addView(textButton(context, tokens.textSecondary, R.string.category_icon_default) {
                        dialog.dismiss()
                        onPick(defaultName)
                    })
                }
                addView(textButton(context, tokens.textSecondary, R.string.action_cancel) { dialog.dismiss() })
            })
        }
        dialog.setContentView(root)
        dialog.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout((context.resources.displayMetrics.widthPixels * 0.88f).toInt(), WindowManager.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
    }

    /** A one-line scrolling choice for embedding; [current] returns the picked icon's name. */
    class Row(val view: View, private val picked: () -> String) {
        val current: String get() = picked()
    }

    fun row(context: Context, initial: String): Row {
        val tokens = ThemeObserver.currentTokens(context)
        val dp = context.resources.displayMetrics.density
        var selected = initial
        val strip = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        fun rebuild() {
            strip.removeAllViews()
            CategoryIconCatalog.icons.forEach { (name, res) ->
                strip.addView(cell(context, tokens, res, name == selected) {
                    selected = name
                    rebuild()
                })
            }
        }
        rebuild()
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            addView(strip)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .apply { topMargin = (14 * dp).toInt() }
        }
        return Row(scroll) { selected }
    }

    private fun cell(context: Context, tokens: NexusColorTokens, res: Int, isSelected: Boolean, onClick: () -> Unit): View {
        val dp = context.resources.displayMetrics.density
        val size = (44 * dp).toInt()
        return FrameLayout(context).apply {
            layoutParams = ViewGroup.MarginLayoutParams(size, size).apply {
                val m = (3 * dp).toInt()
                setMargins(m, m, m, m)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (isSelected) tokens.textPrimary else tokens.surfaceRaised)
                if (!isSelected) setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
            }
            addView(ImageView(context).apply {
                setImageResource(res)
                imageTintList = ColorStateList.valueOf(if (isSelected) tokens.surface else tokens.textPrimary)
                layoutParams = FrameLayout.LayoutParams((22 * dp).toInt(), (22 * dp).toInt(), Gravity.CENTER)
            })
            isSelected.also { this.isSelected = it }
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                onClick()
            }
        }
    }

    private fun textButton(context: Context, color: Int, labelRes: Int, onClick: () -> Unit) = TextView(context).apply {
        text = context.getString(labelRes)
        NexusTypeScale.bodyStrong.bindTo(this, color)
        val pad = (12 * context.resources.displayMetrics.density).toInt()
        setPadding(pad, pad, pad, pad)
        setOnClickListener { onClick() }
    }
}
