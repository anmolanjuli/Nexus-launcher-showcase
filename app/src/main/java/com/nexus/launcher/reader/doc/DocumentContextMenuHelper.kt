package com.nexus.launcher.reader.doc

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.format.Formatter
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.typography.NexusTypeScale

/**
 * Handles long-press context menu and sub-dialogs for the Document Library.
 */
object DocumentContextMenuHelper {

    fun showDocumentMenu(
        context: Context,
        document: DocumentRecord,
        collections: List<CollectionRecord>,
        onOpen: () -> Unit,
        onMove: (Long?) -> Unit,
        onCreateCollection: (String, String) -> Unit,
        onRename: (String?) -> Unit,
        onRemove: () -> Unit
    ) {
        val dp = context.resources.displayMetrics.density
        val tokens = NexusDocDialogFactory.resolveTokens(context)
        val (dialog, _, card) = NexusDocDialogFactory.createDialogCard(context, document.effectiveTitle, tokens, dp)

        val subMeta = TextView(context).apply {
            val size = Formatter.formatFileSize(context, document.fileSize)
            text = "${document.fileType.uppercase()} • $size"
            NexusTypeScale.caption.bindTo(this, tokens.textSecondary)
            setPadding(0, 0, 0, (10 * dp).toInt())
        }
        card.addView(subMeta)

        val options = listOf(
            context.getString(R.string.nexus_doc_action_open) to false,
            context.getString(R.string.nexus_doc_move_to_collection) to false,
            context.getString(R.string.nexus_doc_rename) to false,
            context.getString(R.string.nexus_doc_file_info) to false,
            context.getString(R.string.nexus_doc_remove_title) to true
        )

        options.forEachIndexed { index, (label, isDestructive) ->
            val row = NexusDocDialogFactory.buildMenuItemRow(context, label, tokens, dp, isDestructive) {
                dialog.dismiss()
                when (index) {
                    0 -> onOpen()
                    1 -> showMoveToCollectionDialog(context, document, collections, onMove, onCreateCollection)
                    2 -> showRenameDialog(context, document, onRename)
                    3 -> {
                        val colName = collections.find { it.id == document.collectionId }?.name
                            ?: context.getString(R.string.nexus_doc_uncategorized)
                        showFileInfoDialog(context, document, colName)
                    }
                    4 -> showRemoveConfirmDialog(context, document, onRemove)
                }
            }
            card.addView(row)
        }

        dialog.show()
    }

    fun showRenameDialog(
        context: Context,
        document: DocumentRecord,
        onSave: (String?) -> Unit
    ) {
        val dp = context.resources.displayMetrics.density
        val tokens = NexusDocDialogFactory.resolveTokens(context)
        val (dialog, _, card) = NexusDocDialogFactory.createDialogCard(
            context,
            context.getString(R.string.nexus_doc_rename_title),
            tokens,
            dp
        )

        val isEInk = com.nexus.launcher.feed.NexusFeedEInkCoordinator.isEInkMode(context)
        val input = EditText(context).apply {
            setText(document.effectiveTitle)
            setSelection(text.length)
            setTextColor(tokens.textPrimary)
            setHintTextColor(tokens.textSecondary)
            textSize = 14f
            typeface = if (isEInk) android.graphics.Typeface.MONOSPACE else null
            setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
            background = GradientDrawable().apply {
                setColor(tokens.surfaceRaised)
                cornerRadius = if (isEInk) 0f else 10 * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (16 * dp).toInt()
            }
        }
        card.addView(input)

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            addView(NexusDocDialogFactory.buildActionButton(context, context.getString(R.string.nexus_doc_rename_reset), tokens, dp) {
                dialog.dismiss()
                onSave(null)
            })
            addView(NexusDocDialogFactory.buildActionButton(context, context.getString(R.string.nexus_doc_cancel), tokens, dp) {
                dialog.dismiss()
            })
            addView(NexusDocDialogFactory.buildActionButton(context, context.getString(R.string.nexus_doc_action_save), tokens, dp, isPrimary = true) {
                val newName = input.text.toString().trim()
                dialog.dismiss()
                onSave(if (newName.isBlank() || newName == document.fileName) null else newName)
            })
        }
        card.addView(btnRow)
        dialog.show()
    }

    fun showMoveToCollectionDialog(
        context: Context,
        document: DocumentRecord,
        collections: List<CollectionRecord>,
        onMove: (Long?) -> Unit,
        onCreateCollection: (String, String) -> Unit
    ) {
        val dp = context.resources.displayMetrics.density
        val tokens = NexusDocDialogFactory.resolveTokens(context)
        val (dialog, _, card) = NexusDocDialogFactory.createDialogCard(
            context,
            context.getString(R.string.nexus_doc_move_to_collection),
            tokens,
            dp
        )

        val scroll = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                (240 * dp).toInt()
            ).apply {
                bottomMargin = (12 * dp).toInt()
            }
        }
        val list = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        // Uncategorized
        list.addView(NexusDocDialogFactory.buildMenuItemRow(context, context.getString(R.string.nexus_doc_uncategorized), tokens, dp) {
            dialog.dismiss()
            onMove(null)
        })

        // Existing Collections
        collections.forEach { col ->
            list.addView(NexusDocDialogFactory.buildMenuItemRow(context, "● ${col.name}", tokens, dp) {
                dialog.dismiss()
                onMove(col.id)
            })
        }

        // Create New
        list.addView(NexusDocDialogFactory.buildMenuItemRow(context, "＋ " + context.getString(R.string.nexus_doc_create_collection), tokens, dp) {
            dialog.dismiss()
            showCreateCollectionDialog(context, onCreateCollection)
        })

        scroll.addView(list)
        card.addView(scroll)
        dialog.show()
    }

    fun showCreateCollectionDialog(
        context: Context,
        onCreate: (String, String) -> Unit
    ) {
        val dp = context.resources.displayMetrics.density
        val isEInk = com.nexus.launcher.feed.NexusFeedEInkCoordinator.isEInkMode(context)
        val tokens = NexusDocDialogFactory.resolveTokens(context)
        val (dialog, _, card) = NexusDocDialogFactory.createDialogCard(
            context,
            context.getString(R.string.nexus_doc_create_collection),
            tokens,
            dp
        )

        val input = EditText(context).apply {
            hint = context.getString(R.string.nexus_doc_collection_name)
            setTextColor(tokens.textPrimary)
            setHintTextColor(tokens.textSecondary)
            textSize = 14f
            typeface = if (isEInk) android.graphics.Typeface.MONOSPACE else null
            setPadding((12 * dp).toInt(), (10 * dp).toInt(), (12 * dp).toInt(), (10 * dp).toInt())
            background = GradientDrawable().apply {
                setColor(tokens.surfaceRaised)
                cornerRadius = if (isEInk) 0f else 10 * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (14 * dp).toInt()
            }
        }
        card.addView(input)

        val colors = arrayOf("#B0BEC5", "#90A4AE", "#78909C", "#8D6E63", "#7E57C2", "#4DB6AC")
        var selectedColor = colors[0]

        val dotRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (16 * dp).toInt())
        }

        colors.forEach { hex ->
            val dot = View(context).apply {
                val size = (22 * dp).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = (10 * dp).toInt() }
                background = GradientDrawable().apply {
                    shape = if (isEInk) GradientDrawable.RECTANGLE else GradientDrawable.OVAL
                    setColor(if (isEInk) tokens.textSecondary else Color.parseColor(hex))
                    if (isEInk) {
                        cornerRadius = 0f
                        setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
                    }
                }
                setOnClickListener {
                    selectedColor = hex
                    dotRow.invalidate()
                }
            }
            dotRow.addView(dot)
        }
        card.addView(dotRow)

        val btnRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            addView(NexusDocDialogFactory.buildActionButton(context, context.getString(R.string.nexus_doc_cancel), tokens, dp) {
                dialog.dismiss()
            })
            addView(NexusDocDialogFactory.buildActionButton(context, context.getString(R.string.nexus_doc_action_save), tokens, dp, isPrimary = true) {
                val name = input.text.toString().trim()
                if (name.isNotBlank()) {
                    dialog.dismiss()
                    onCreate(name, selectedColor)
                }
            })
        }
        card.addView(btnRow)
        dialog.show()
    }

    fun showFileInfoDialog(
        context: Context,
        document: DocumentRecord,
        collectionName: String
    ) {
        NexusDocDialogFactory.showFileInfoDialog(context, document, collectionName)
    }

    fun showRemoveConfirmDialog(
        context: Context,
        document: DocumentRecord,
        onConfirm: () -> Unit
    ) {
        NexusDocDialogFactory.showRemoveConfirmDialog(context, document, onConfirm)
    }
}
