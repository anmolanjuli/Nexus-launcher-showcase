package com.nexus.launcher.reader.doc

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.typography.NexusTypeScale

/**
 * Shown when a free user adds a PDF or EPUB past [DocumentLibraryRepository.FREE_DOCUMENT_LIMIT].
 *
 * Says why the file was not added and both ways forward before any paywall: jumping straight to
 * the subscription page read as an error, not an explanation.
 */
object DocumentLimitDialog {

    /** [onGetPremium] runs once the dialog has closed, and only if Get Premium was tapped. */
    fun show(context: Context, onGetPremium: () -> Unit) {
        val dp = context.resources.displayMetrics.density
        val isEInk = com.nexus.launcher.feed.NexusFeedEInkCoordinator.isEInkMode(context)
        val tokens = NexusDocDialogFactory.resolveTokens(context)
        val (dialog, _, card) = NexusDocDialogFactory.createDialogCard(
            context, context.getString(R.string.nexus_doc_limit_title), tokens, dp,
        )
        var premiumChosen = false
        // After dismissal, so the paywall never opens underneath this dialog.
        dialog.setOnDismissListener { if (premiumChosen) onGetPremium() }

        card.addView(TextView(context).apply {
            text = context.getString(R.string.nexus_doc_limit_desc, DocumentLibraryRepository.FREE_DOCUMENT_LIMIT)
            typeface = if (isEInk) Typeface.MONOSPACE else null
            NexusTypeScale.body.bindTo(this, tokens.textSecondary)
            setPadding(0, 0, 0, (18 * dp).toInt())
        })
        card.addView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
            addView(NexusDocDialogFactory.buildActionButton(
                context, context.getString(R.string.nexus_doc_limit_not_now), tokens, dp,
            ) { dialog.dismiss() })
            addView(NexusDocDialogFactory.buildActionButton(
                context, context.getString(R.string.nexus_doc_limit_get_premium), tokens, dp, isPrimary = true,
            ) {
                premiumChosen = true
                dialog.dismiss()
            })
        })
        dialog.show()
    }
}
