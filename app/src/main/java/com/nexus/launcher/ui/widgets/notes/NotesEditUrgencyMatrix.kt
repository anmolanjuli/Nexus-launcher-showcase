package com.nexus.launcher.ui.widgets.notes

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.ui.widgets.mosaic.LivingMosaicHaptics

/**
 * Builds the 2x2 Eisenhower urgency matrix picker for NotesEditSheet.
 */
object NotesEditUrgencyMatrix {

    fun buildMatrix(
        context: Context,
        dp: Float,
        tokens: NexusColorTokens,
        initialUrgency: String,
        onUrgencySelected: (String) -> Unit
    ): Pair<LinearLayout, (String) -> Unit> {
        val quadrantGrid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val topQuadrantRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        val bottomQuadrantRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                topMargin = (8 * dp).toInt()
            }
        }

        val quadrantViews = mutableMapOf<String, LinearLayout>()

        val createQuadrant = { id: String, label: String, color: Int ->
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
                setPadding((8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
                layoutParams = LinearLayout.LayoutParams(0, (38 * dp).toInt(), 1f).apply {
                    marginEnd = (4 * dp).toInt()
                    marginStart = (4 * dp).toInt()
                }
                isClickable = true
                isFocusable = true

                val dot = View(context).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(color)
                    }
                    layoutParams = LinearLayout.LayoutParams((8 * dp).toInt(), (8 * dp).toInt()).apply {
                        marginEnd = (6 * dp).toInt()
                    }
                }
                val text = TextView(context).apply {
                    this.text = label
                    textSize = 12f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(tokens.textPrimary)
                }
                addView(dot)
                addView(text)

                setOnClickListener {
                    LivingMosaicHaptics.tick(this)
                    onUrgencySelected(id)
                    updateQuadrantSelections(quadrantViews, id, color, tokens, dp)
                }
            }
        }

        quadrantViews[NoteData.URGENCY_DO_FIRST] = createQuadrant(NoteData.URGENCY_DO_FIRST, context.getString(R.string.notes_urgency_do_first), tokens.danger).also { topQuadrantRow.addView(it) }
        quadrantViews[NoteData.URGENCY_SCHEDULE] = createQuadrant(NoteData.URGENCY_SCHEDULE, context.getString(R.string.notes_urgency_schedule), tokens.accent).also { topQuadrantRow.addView(it) }
        quadrantViews[NoteData.URGENCY_QUICK] = createQuadrant(NoteData.URGENCY_QUICK, context.getString(R.string.notes_urgency_quick), 0xFFF59E0B.toInt()).also { bottomQuadrantRow.addView(it) }
        quadrantViews[NoteData.URGENCY_LATER] = createQuadrant(NoteData.URGENCY_LATER, context.getString(R.string.notes_urgency_later), tokens.textSecondary).also { bottomQuadrantRow.addView(it) }

        quadrantGrid.addView(topQuadrantRow)
        quadrantGrid.addView(bottomQuadrantRow)
        updateQuadrantSelections(quadrantViews, initialUrgency, 0, tokens, dp)

        val updateSelection = { newUrgency: String ->
            updateQuadrantSelections(quadrantViews, newUrgency, 0, tokens, dp)
        }

        return Pair(quadrantGrid, updateSelection)
    }

    private fun updateQuadrantSelections(
        views: Map<String, LinearLayout>,
        selectedUrgency: String,
        activeColor: Int,
        tokens: NexusColorTokens,
        dp: Float
    ) {
        views.forEach { (id, view) ->
            val isSelected = id == selectedUrgency
            view.background = GradientDrawable().apply {
                cornerRadius = 10f * dp
                if (isSelected) {
                    setColor(tokens.surfaceRaised)
                    setStroke((1.5f * dp).toInt(), if (activeColor != 0) activeColor else tokens.accent)
                } else {
                    setColor(tokens.surfaceRaised and 0x00FFFFFF or 0x66000000.toInt())
                    setStroke((1 * dp).toInt(), tokens.divider)
                }
            }
        }
    }
}
