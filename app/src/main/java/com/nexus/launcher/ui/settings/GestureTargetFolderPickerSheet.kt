package com.nexus.launcher.ui.settings

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nexus.launcher.R
import com.nexus.launcher.di.DaoEntryPoint
import com.nexus.launcher.ui.MainViewModel
import com.nexus.launcher.ui.WallpaperSheetComponents
import com.nexus.launcher.ui.folder.FolderEditSheetDecor
import com.nexus.launcher.ui.folder.FolderPremiumGlassBuilder
import kotlinx.coroutines.launch

class GestureTargetFolderPickerSheet(
    private val onTargetSelected: (String) -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val theme = com.google.android.material.R.style.Theme_Design_BottomSheetDialog
        return BottomSheetDialog(requireContext(), theme).also { com.nexus.launcher.ui.LandscapeSheets.apply(it) }.apply {
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
            setOnShowListener { dialog ->
                val d = dialog as BottomSheetDialog
                // Apply the heavy dark blur backdrop matching Edit App sheet
                FolderEditSheetDecor.apply(d)
                
                // Add margins so it floats (not edge-to-edge)
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
        
        val root = LinearLayout(requireContext()).apply {
            // This ID is needed by FolderEditSheetDecor to apply the corner radius and background
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
                    text = requireContext().getString(com.nexus.launcher.R.string.gesture_pick_folder_title)
                    com.nexus.launcher.typography.NexusTypeScale.title.bindTo(this, Color.WHITE)
                })
            }
            addView(textLayout)
            addView(WallpaperSheetComponents.buildCloseButton(requireContext(), dp) { dismiss() })
        }
        root.addView(header)

        val scrollView = ScrollView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        
        val contentLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), (24 * dp).toInt())
        }
        
        contentLayout.addView(TextView(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins((8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt())
            }
            text = "FOLDERS"
            com.nexus.launcher.typography.NexusTypeScale.labelSmall.bindTo(
                this,
                Color.parseColor("#99FFFFFF")
            )
            isAllCaps = true
        })

        val gridLayout = android.widget.GridLayout(requireContext()).apply {
            columnCount = 2
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        
        contentLayout.addView(gridLayout)
        scrollView.addView(contentLayout)
        root.addView(scrollView)

        val dao = dagger.hilt.EntryPoints.get(
            requireContext().applicationContext, DaoEntryPoint::class.java
        ).homeScreenDao()
        
        lifecycleScope.launch {
            dao.getAllItems().collect { items ->
                val folders = items.filter { it.itemType == 1 }
                gridLayout.removeAllViews()
                
                if (folders.isEmpty()) {
                    gridLayout.addView(TextView(requireContext()).apply {
                        layoutParams = android.widget.GridLayout.LayoutParams().apply {
                            width = 0
                            columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 2, 1f)
                            setMargins((8 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt(), 0)
                        }
                        text = requireContext().getString(com.nexus.launcher.R.string.gesture_no_folders)
                        com.nexus.launcher.typography.NexusTypeScale.body.bindTo(
                            this,
                            Color.parseColor("#99FFFFFF")
                        )
                        gravity = Gravity.CENTER
                    })
                    return@collect
                }
                
                for (folder in folders) {
                    val tile = LinearLayout(requireContext()).apply {
                        layoutParams = android.widget.GridLayout.LayoutParams().apply {
                            width = 0
                            height = ViewGroup.LayoutParams.WRAP_CONTENT
                            columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                            setMargins((8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
                        }
                        orientation = LinearLayout.VERTICAL
                        gravity = Gravity.CENTER
                        setPadding((16 * dp).toInt(), (24 * dp).toInt(), (16 * dp).toInt(), (24 * dp).toInt())
                        
                        background = GradientDrawable().apply {
                            setColor(Color.parseColor("#1AFFFFFF"))
                            cornerRadius = 20 * dp
                        }
                        
                        isClickable = true
                        isFocusable = true
                        setOnClickListener {
                            it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                            onTargetSelected(folder.id.toString())
                            dismiss()
                        }
                    }
                    
                    tile.addView(android.widget.ImageView(requireContext()).apply {
                        layoutParams = LinearLayout.LayoutParams((32 * dp).toInt(), (32 * dp).toInt()).apply {
                            bottomMargin = (12 * dp).toInt()
                        }
                        setImageResource(com.nexus.launcher.R.drawable.ic_folder_solid)
                        setColorFilter(Color.parseColor("#7EB8D4"))
                    })
                    
                    tile.addView(TextView(requireContext()).apply {
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                        text = folder.folderTitle.ifEmpty { requireContext().getString(com.nexus.launcher.R.string.edit_sheet_unnamed_folder) }
                        com.nexus.launcher.typography.NexusTypeScale.bodyStrong.bindTo(this, Color.WHITE)
                        gravity = Gravity.CENTER
                        maxLines = 1
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    })
                    
                    gridLayout.addView(tile)
                }
            }
        }

        return root
    }
}
