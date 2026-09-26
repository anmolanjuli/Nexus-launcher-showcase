package com.nexus.launcher.ui.settings

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nexus.launcher.R
import com.nexus.launcher.domain.model.AppModel
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.MainViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class GestureTargetAppPickerSheet(
    private val onTargetSelected: (String) -> Unit
) : BottomSheetDialogFragment() {

    private lateinit var searchInput: EditText
    private lateinit var scrollView: androidx.core.widget.NestedScrollView
    private lateinit var gridLayout: GridLayout
    private lateinit var loadingSpinner: ProgressBar
    private lateinit var currentTokens: NexusColorTokens

    private var allApps: List<AppModel> = emptyList()
    private var filteredApps: List<AppModel> = emptyList()

    private var searchJob: Job? = null

    // Chunking to avoid massive layout stalls
    private var loadedCount = 0
    private val chunkSize = 50

    override fun onStart() {
        super.onStart()
        // The window's own blur-behind needs something live to actually blur — every other
        // converted sheet activates the shared workspace blur for this same reason; this one
        // never did, so its FLAG_BLUR_BEHIND had nothing behind it worth blurring.
        context?.let { com.nexus.launcher.ui.folder.FolderBlurCoordinator.setWorkspaceBlur(it, true) }
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        context?.let { com.nexus.launcher.ui.folder.FolderBlurCoordinator.setWorkspaceBlur(it, false) }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dp = resources.displayMetrics.density
        return BottomSheetDialog(requireContext()).also { com.nexus.launcher.ui.LandscapeSheets.apply(it) }.apply {
            window?.let { win ->
                WindowCompat.setDecorFitsSystemWindows(win, false)
                win.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
                win.statusBarColor = Color.TRANSPARENT
                win.navigationBarColor = Color.TRANSPARENT
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    win.isNavigationBarContrastEnforced = false
                    win.isStatusBarContrastEnforced = false
                }
                win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                // Was its own hand-rolled FLAG_DIM_BEHIND(0.72) + FLAG_BLUR_BEHIND(25dp), never
                // converted to the shared frosted-glass component — the card itself was also
                // fully opaque so this never read as frosted, just an unconverted, toggle-unaware
                // dim+blur. Matches every other converted sheet now.
                com.nexus.launcher.ui.glass.FrostedGlassEngine.applyDialogWindowChrome(win)
                win.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                win.setDimAmount(0f)
            }
            setOnShowListener { dialog ->
                val d = dialog as BottomSheetDialog
                d.findViewById<View>(com.google.android.material.R.id.touch_outside)?.setBackgroundColor(Color.TRANSPARENT)
                d.findViewById<View>(com.google.android.material.R.id.coordinator)?.setBackgroundColor(Color.TRANSPARENT)
                val bottomSheet = d.findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
                if (bottomSheet != null) {
                    bottomSheet.backgroundTintList = null
                    bottomSheet.setBackgroundResource(android.R.color.transparent)
                    val behavior = BottomSheetBehavior.from(bottomSheet)
                    val targetHeight = (resources.displayMetrics.heightPixels * 0.85f).toInt()
                    bottomSheet.layoutParams?.height = targetHeight
                    behavior.peekHeight = targetHeight
                    behavior.isFitToContents = true
                    behavior.skipCollapsed = true
                    behavior.state = BottomSheetBehavior.STATE_EXPANDED
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
        currentTokens = try {
            ThemeObserver.currentTokens(requireContext())
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }

        val root = FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val marginH = (10 * dp).toInt()
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            ).apply {
                setMargins(marginH, (8 * dp).toInt(), marginH, 0)
            }
            background = GradientDrawable().apply {
                val frostedTokens = com.nexus.launcher.ui.glass.FrostedGlassEngine.resolveFrostedTokens(currentTokens)
                val fillAlpha = (com.nexus.launcher.ui.glass.FrostedGlassEngine.sheetFillAlpha(1f, 0.70f) * 255f).toInt()
                setColor((frostedTokens.surface and 0x00FFFFFF) or (fillAlpha shl 24))
                cornerRadius = 24f * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), frostedTokens.border)
            }
            clipToOutline = true
        }

        val header = LinearLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((20 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())

            val textLayout = LinearLayout(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                orientation = LinearLayout.VERTICAL

                val titleView = TextView(requireContext()).apply {
                    text = "Select App"
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
                }
                setOnClickListener { dismiss() }
            }
            addView(closeBtn)
        }
        card.addView(header)

        searchInput = EditText(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins((16 * dp).toInt(), 0, (16 * dp).toInt(), (12 * dp).toInt())
            }
            hint = "Search apps..."
            setHintTextColor(currentTokens.textSecondary)
            NexusTypeScale.body.bindTo(this, currentTokens.textPrimary)
            background = GradientDrawable().apply {
                setColor(currentTokens.surfaceRaised)
                cornerRadius = 12 * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), currentTokens.divider)
            }
            setPadding((16 * dp).toInt(), (10 * dp).toInt(), (16 * dp).toInt(), (10 * dp).toInt())
            maxLines = 1
            inputType = android.text.InputType.TYPE_CLASS_TEXT

            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    searchJob?.cancel()
                    searchJob = lifecycleScope.launch {
                        delay(300)
                        filterApps(s.toString())
                    }
                }
            })
        }
        card.addView(searchInput)

        val contentContainer = FrameLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0
            ).apply { weight = 1f }
        }

        scrollView = object : androidx.core.widget.NestedScrollView(requireContext()) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            }
        }.apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            clipToPadding = false
            if (android.os.Build.VERSION.SDK_INT >= 23) {
                setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                    val child = getChildAt(0)
                    if (child != null) {
                        val diff = (child.bottom - (height + scrollY))
                        if (diff <= 200 * dp && scrollY > oldScrollY) {
                            loadNextChunk()
                        }
                    }
                }
            }
        }

        gridLayout = GridLayout(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            columnCount = 5
            setPadding((8 * dp).toInt(), 0, (8 * dp).toInt(), (16 * dp).toInt())
        }

        scrollView.addView(gridLayout)
        contentContainer.addView(scrollView)

        loadingSpinner = ProgressBar(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                (48 * dp).toInt(), (48 * dp).toInt()
            ).apply { gravity = Gravity.CENTER }
            isIndeterminate = true
        }
        contentContainer.addView(loadingSpinner)

        card.addView(contentContainer)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val imeBars = insets.getInsets(WindowInsetsCompat.Type.ime())
            val bottomMargin = maxOf(navBars.bottom, imeBars.bottom) + (4 * dp).toInt()
            (card.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                lp.bottomMargin = bottomMargin
                card.layoutParams = lp
            }
            insets
        }

        root.addView(card)

        val viewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        lifecycleScope.launch {
            viewModel.apps.collect { apps ->
                if (allApps.isEmpty()) {
                    val collator = java.text.Collator.getInstance(Locale.getDefault()).apply { strength = java.text.Collator.SECONDARY }
                    allApps = apps.sortedWith(compareBy(collator) { it.label })
                    filteredApps = allApps
                    loadingSpinner.visibility = View.GONE
                    loadedCount = 0
                    gridLayout.removeAllViews()
                    loadNextChunk()
                }
            }
        }

        return root
    }

    private fun filterApps(query: String) {
        val q = query.lowercase(Locale.getDefault())
        filteredApps = if (q.isEmpty()) {
            allApps
        } else {
            allApps.filter { it.label.lowercase(Locale.getDefault()).contains(q) }
        }
        gridLayout.removeAllViews()
        loadedCount = 0
        loadNextChunk()
        scrollView.scrollTo(0, 0)
    }

    private fun loadNextChunk() {
        if (loadedCount >= filteredApps.size) return

        val end = (loadedCount + chunkSize).coerceAtMost(filteredApps.size)
        val chunk = filteredApps.subList(loadedCount, end)
        loadedCount = end

        val dp = resources.displayMetrics.density
        val screenWidth = resources.displayMetrics.widthPixels
        val colWidth = (screenWidth - (48 * dp).toInt()) / 5

        for (app in chunk) {
            val cell = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = GridLayout.LayoutParams().apply {
                    width = colWidth
                    height = ViewGroup.LayoutParams.WRAP_CONTENT
                    setMargins(0, (8 * dp).toInt(), 0, (16 * dp).toInt())
                }
                setOnClickListener {
                    it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                    onTargetSelected(app.packageName)
                    dismiss()
                }
            }

            val iconView = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams((48 * dp).toInt(), (48 * dp).toInt()).apply {
                    bottomMargin = (8 * dp).toInt()
                }
                setImageDrawable(app.icon)
            }
            cell.addView(iconView)

            val labelView = TextView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                text = app.label
                NexusTypeScale.caption.bindTo(this, currentTokens.textPrimary)
                gravity = Gravity.CENTER_HORIZONTAL
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
                isSingleLine = true
                setPadding((4 * dp).toInt(), 0, (4 * dp).toInt(), 0)
            }
            cell.addView(labelView)

            gridLayout.addView(cell)
        }
    }
}
