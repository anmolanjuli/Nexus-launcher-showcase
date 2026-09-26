package com.nexus.launcher.ui.settings

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.MainActivity
import com.nexus.launcher.ui.ManagePagesCardFactory
import com.nexus.launcher.ui.ManagePagesThumbSpec
import com.nexus.launcher.ui.WallpaperSheetComponents
import com.nexus.launcher.ui.canvas.PageThumbnailRenderer
import com.nexus.launcher.ui.folder.FolderEditSheetDecor
import kotlinx.coroutines.launch

class GestureTargetPagePickerSheet(
    private val onTargetSelected: (String) -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val theme = com.google.android.material.R.style.Theme_Design_BottomSheetDialog
        return BottomSheetDialog(requireContext(), theme).also { com.nexus.launcher.ui.LandscapeSheets.apply(it) }.apply {
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
            setOnShowListener { dialog ->
                val d = dialog as BottomSheetDialog
                FolderEditSheetDecor.apply(d)

                val sheet = d.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
                sheet?.layoutParams = (sheet?.layoutParams as? ViewGroup.MarginLayoutParams)?.apply {
                    val dp = context.resources.displayMetrics.density
                    setMargins((8 * dp).toInt(), (24 * dp).toInt(), (8 * dp).toInt(), (24 * dp).toInt())
                }
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val dp = resources.displayMetrics.density
        val tokens = try {
            ThemeObserver.currentTokens(requireContext())
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }

        val root = LinearLayout(requireContext()).apply {
            id = R.id.folder_edit_sheet_root
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
        }

        val header = LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((24 * dp).toInt(), (24 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt())

            val textLayout = LinearLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                orientation = LinearLayout.VERTICAL

                addView(TextView(requireContext()).apply {
                    text = requireContext().getString(com.nexus.launcher.R.string.gesture_pick_page_title)
                    NexusTypeScale.title.bindTo(this, tokens.textPrimary)
                })
            }
            addView(textLayout)
            addView(WallpaperSheetComponents.buildCloseButton(requireContext(), dp, tokens) { dismiss() })
        }
        root.addView(header)

        val scrollView = ScrollView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val gridLayout = GridLayout(requireContext()).apply {
            columnCount = 2
            setPadding((12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt(), (24 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        scrollView.addView(gridLayout)
        root.addView(scrollView)

        lifecycleScope.launch {
            val mainActivity = activity as? MainActivity
            val count = mainActivity?.canvasView?.totalPages ?: 1

            gridLayout.removeAllViews()

            gridLayout.post {
                val availableWidth = gridLayout.width - gridLayout.paddingStart - gridLayout.paddingEnd
                val columnWidth = availableWidth / 2

                for (i in 0 until count) {
                    val pageCard = ManagePagesCardFactory.createPageCard(requireContext(), dp, tokens, columnWidth)

                    val thumbHeight = (columnWidth * ManagePagesThumbSpec.HEIGHT_OVER_WIDTH).toInt()
                    val thumb = if (mainActivity != null) PageThumbnailRenderer.renderPage(
                        mainActivity.canvasView, i, columnWidth, thumbHeight
                    ) else null

                    ManagePagesCardFactory.applyCardState(
                        pageCard, i, thumb, isCurrent = false, isDefault = (i == 0), density = dp, tokens = tokens
                    )

                    pageCard.setOnClickListener {
                        it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        onTargetSelected(i.toString())
                        dismiss()
                    }

                    gridLayout.addView(pageCard)
                }
            }
        }

        return root
    }
}
