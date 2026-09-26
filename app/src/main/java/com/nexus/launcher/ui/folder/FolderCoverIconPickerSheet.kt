package com.nexus.launcher.ui.folder

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.icons.IconPackDetector
import com.nexus.launcher.ui.settings.IconGallerySource
import com.nexus.launcher.ui.settings.IconPackBrowseSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class FolderCoverIconPickerSheet(
    private val folderId: Long,
    private val onIconSelected: (String) -> Unit
) : BottomSheetDialogFragment() {

    private lateinit var galleryLauncher: androidx.activity.result.ActivityResultLauncher<android.content.Intent>
    private lateinit var currentTokens: NexusColorTokens

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        galleryLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.data
            if (result.resultCode == android.app.Activity.RESULT_OK && uri != null) {
                lifecycleScope.launch(Dispatchers.IO) {
                    val path = IconGallerySource.copyToInternal(requireContext(), "folder_$folderId", uri)
                    withContext(Dispatchers.Main) {
                        if (path != null) {
                            onIconSelected(path)
                            dismiss()
                        }
                    }
                }
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dp = resources.displayMetrics.density
        return BottomSheetDialog(requireContext(), theme).also { com.nexus.launcher.ui.LandscapeSheets.apply(it) }.apply {
            window?.let { win ->
                WindowCompat.setDecorFitsSystemWindows(win, false)
                win.statusBarColor = Color.TRANSPARENT
                win.navigationBarColor = Color.TRANSPARENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    win.isNavigationBarContrastEnforced = false
                    win.isStatusBarContrastEnforced = false
                }
                win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                win.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                win.setDimAmount(0.72f)
                // Blurs only in Frosted Glass; the dim above carries the separation otherwise.
                com.nexus.launcher.ui.glass.FloatingSurfaces.applyBlurBehind(win)
            }
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
            setOnShowListener { dialog ->
                val d = dialog as BottomSheetDialog
                d.findViewById<View>(com.google.android.material.R.id.touch_outside)?.setBackgroundColor(Color.TRANSPARENT)
                d.findViewById<View>(com.google.android.material.R.id.coordinator)?.setBackgroundColor(Color.TRANSPARENT)
                val bottomSheet = d.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
                if (bottomSheet != null) {
                    bottomSheet.backgroundTintList = null
                    bottomSheet.setBackgroundResource(android.R.color.transparent)
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val dp = resources.displayMetrics.density
        currentTokens = try {
            ThemeObserver.currentTokens(requireContext())
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }

        val root = FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val marginH = (10 * dp).toInt()
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(marginH, (8 * dp).toInt(), marginH, 0)
            }
            background = com.nexus.launcher.ui.glass.FloatingSurfaces.sheetCard(currentTokens, 24f * dp, dp)
            clipToOutline = true
        }

        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((20 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())

            val textLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

                val titleView = TextView(requireContext()).apply {
                    text = requireContext().getString(com.nexus.launcher.R.string.folder_cover_icon_title)
                    gravity = Gravity.START or Gravity.CENTER_VERTICAL
                    textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                }
                NexusTypeScale.title.bindTo(titleView, currentTokens.textPrimary)
                addView(titleView)
            }
            addView(textLayout)

            val closeBtn = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams((32 * dp).toInt(), (32 * dp).toInt())
                setImageResource(R.drawable.ic_close)
                imageTintList = ColorStateList.valueOf(currentTokens.textSecondary)
                setPadding((6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt())
                background = GradientDrawable().apply {
                    setColor(currentTokens.surfaceRaised)
                    cornerRadius = 16 * dp
                    setStroke((1 * dp).toInt().coerceAtLeast(1), currentTokens.divider)
                }
                setOnClickListener { dismiss() }
            }
            addView(closeBtn)
        }
        card.addView(header)

        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (8 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt())
        }

        lifecycleScope.launch {
            val packs = IconPackDetector(requireContext()).getAvailableIconPacks()
            withContext(Dispatchers.Main) {
                val grid = GridLayout(requireContext()).apply {
                    columnCount = 4
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = (8 * dp).toInt() }
                }

                val margin = (4 * dp).toInt()

                for (pack in packs) {
                    val packBtn = buildGridTile(pack.label, dp, pack.icon) {
                        it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        IconPackBrowseSheet(pack.packageName) { overrideData ->
                            onIconSelected(overrideData)
                            dismiss()
                        }.show(parentFragmentManager, "IconPackBrowseSheet")
                    }
                    val params = GridLayout.LayoutParams().apply {
                        width = 0
                        height = GridLayout.LayoutParams.WRAP_CONTENT
                        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                        setMargins(margin, margin, margin, margin)
                    }
                    packBtn.layoutParams = params
                    grid.addView(packBtn)
                }

                val galleryBtn = buildGridTile(requireContext().getString(com.nexus.launcher.R.string.edit_sheet_gallery), dp, null, R.drawable.ic_gallery) {
                    it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    val intent = if (Build.VERSION.SDK_INT >= 33) {
                        android.content.Intent(android.provider.MediaStore.ACTION_PICK_IMAGES)
                    } else {
                        android.content.Intent(android.content.Intent.ACTION_OPEN_DOCUMENT).apply {
                            addCategory(android.content.Intent.CATEGORY_OPENABLE)
                            type = "image/*"
                        }
                    }
                    galleryLauncher.launch(intent)
                }
                val gParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = GridLayout.LayoutParams.WRAP_CONTENT
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(margin, margin, margin, margin)
                }
                galleryBtn.layoutParams = gParams
                grid.addView(galleryBtn)

                content.addView(grid)
            }
        }

        card.addView(content)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val imeBars = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomMargin = maxOf(navBars.bottom, imeBars.bottom) + (8 * dp).toInt()
            (card.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                lp.bottomMargin = bottomMargin
                card.layoutParams = lp
            }
            insets
        }

        root.addView(card)
        return root
    }

    private fun buildGridTile(
        text: String,
        dp: Float,
        icon: android.graphics.drawable.Drawable?,
        fallbackResId: Int = R.drawable.ic_apps,
        onClick: (View) -> Unit
    ): View {
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt(), (12 * dp).toInt())
            background = GradientDrawable().apply {
                setColor(currentTokens.surfaceRaised)
                cornerRadius = 14 * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), currentTokens.divider)
            }
            isClickable = true
            isFocusable = true
            setOnClickListener {
                it.animate().scaleX(0.95f).scaleY(0.95f).setDuration(50).withEndAction {
                    it.animate().scaleX(1f).scaleY(1f).setDuration(50).start()
                }.start()
                onClick(it)
            }

            addView(ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams((36 * dp).toInt(), (36 * dp).toInt()).apply {
                    bottomMargin = (6 * dp).toInt()
                }
                if (icon != null) {
                    setImageDrawable(icon)
                } else {
                    setImageResource(fallbackResId)
                    imageTintList = ColorStateList.valueOf(currentTokens.textPrimary)
                }
            })

            addView(TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                this.text = text
                NexusTypeScale.caption.bindTo(this, currentTokens.textPrimary)
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            })
        }
    }
}
