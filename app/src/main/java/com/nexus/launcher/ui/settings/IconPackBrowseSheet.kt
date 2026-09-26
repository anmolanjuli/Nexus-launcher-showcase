package com.nexus.launcher.ui.settings

import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
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
import android.widget.ScrollView
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
import com.nexus.launcher.ui.icons.IconPackBrowser
import com.nexus.launcher.ui.icons.PackIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

class IconPackBrowseSheet(
    private val packPackageName: String,
    private val onIconSelected: (String) -> Unit
) : BottomSheetDialogFragment() {

    private lateinit var currentTokens: NexusColorTokens
    private lateinit var gridLayout: GridLayout
    private lateinit var scrollView: ScrollView
    private lateinit var loadingSpinner: ProgressBar
    private lateinit var searchInput: EditText

    private var allIcons: List<PackIcon> = emptyList()
    private var filteredIcons: List<PackIcon> = emptyList()
    private var loadJob: Job? = null
    private var searchJob: Job? = null

    private var loadedCount = 0
    private val chunkSize = 60

    override fun onStart() {
        super.onStart()
        context?.let { com.nexus.launcher.ui.folder.FolderBlurCoordinator.setWorkspaceBlur(it, true) }
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        context?.let { com.nexus.launcher.ui.folder.FolderBlurCoordinator.setWorkspaceBlur(it, false) }
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
                // Was its own hand-rolled FLAG_DIM_BEHIND(0.72) + FLAG_BLUR_BEHIND(25dp), never
                // converted to the shared frosted-glass component.
                com.nexus.launcher.ui.glass.FrostedGlassEngine.applyDialogWindowChrome(win)
                win.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                win.setDimAmount(0f)
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
                    val behavior = BottomSheetBehavior.from(bottomSheet)
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
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((20 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt(), (8 * dp).toInt())

            val textLayout = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

                val titleView = TextView(requireContext()).apply {
                    text = getString(com.nexus.launcher.R.string.icon_select_title)
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

        val searchContainer = FrameLayout(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins((16 * dp).toInt(), 0, (16 * dp).toInt(), (12 * dp).toInt())
            }
        }

        searchInput = EditText(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (44 * dp).toInt()
            )
            setPadding((40 * dp).toInt(), 0, (16 * dp).toInt(), 0)
            hint = getString(com.nexus.launcher.R.string.icon_search_hint)
            setHintTextColor(currentTokens.textSecondary)
            NexusTypeScale.body.bindTo(this, currentTokens.textPrimary)
            background = GradientDrawable().apply {
                setColor(currentTokens.surface)
                cornerRadius = 14 * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), currentTokens.divider)
            }
            gravity = Gravity.CENTER_VERTICAL
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    searchJob?.cancel()
                    searchJob = lifecycleScope.launch {
                        delay(250)
                        filterIcons(s?.toString().orEmpty())
                    }
                }
            })
        }
        searchContainer.addView(searchInput)

        val searchIcon = ImageView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                (20 * dp).toInt(), (20 * dp).toInt()
            ).apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                marginStart = (12 * dp).toInt()
            }
            setImageResource(R.drawable.ic_search)
            imageTintList = ColorStateList.valueOf(currentTokens.textSecondary)
        }
        searchContainer.addView(searchIcon)
        card.addView(searchContainer)

        val contentContainer = FrameLayout(requireContext()).apply {
            val screenHeight = resources.displayMetrics.heightPixels
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (screenHeight * 0.50f).toInt()
            )
        }

        scrollView = ScrollView(requireContext()).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            if (Build.VERSION.SDK_INT >= 23) {
                setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
                    val child = getChildAt(0)
                    if (child != null) {
                        val diff = child.bottom - (height + scrollY)
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
            ).apply {
                gravity = Gravity.CENTER
            }
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(currentTokens.textPrimary)
        }
        contentContainer.addView(loadingSpinner)
        card.addView(contentContainer)

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
        loadPackMetadata()
        return root
    }

    private fun loadPackMetadata() {
        loadingSpinner.visibility = View.VISIBLE
        gridLayout.removeAllViews()

        lifecycleScope.launch {
            val browser = IconPackBrowser(requireContext())
            allIcons = browser.loadIcons(packPackageName)
            filteredIcons = allIcons

            withContext(Dispatchers.Main) {
                loadingSpinner.visibility = View.GONE
                loadedCount = 0
                loadNextChunk()
            }
        }
    }

    private fun filterIcons(query: String) {
        val q = query.lowercase(Locale.getDefault())
        filteredIcons = if (q.isEmpty()) {
            allIcons
        } else {
            allIcons.filter { IconPackBrowseNameHelper.cleanName(it.drawableName).lowercase(Locale.getDefault()).contains(q) }
        }

        loadJob?.cancel()
        gridLayout.removeAllViews()
        loadedCount = 0
        scrollView.scrollTo(0, 0)
        loadNextChunk()
    }

    private fun loadNextChunk() {
        if (loadedCount >= filteredIcons.size) return

        val dp = resources.displayMetrics.density
        val end = minOf(loadedCount + chunkSize, filteredIcons.size)
        val chunk = filteredIcons.subList(loadedCount, end)
        loadedCount = end

        val context = requireContext()

        loadJob = lifecycleScope.launch {
            val pm = context.packageManager
            val res = try {
                pm.getResourcesForApplication(packPackageName)
            } catch (_: Exception) {
                null
            } ?: return@launch

            for (icon in chunk) {
                val container = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    val cellWidth = (resources.displayMetrics.widthPixels - (80 * dp)) / 5
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = cellWidth.toInt()
                        height = ViewGroup.LayoutParams.WRAP_CONTENT
                        setMargins((2 * dp).toInt(), (8 * dp).toInt(), (2 * dp).toInt(), (8 * dp).toInt())
                    }

                    val imageView = ImageView(context).apply {
                        layoutParams = LinearLayout.LayoutParams((48 * dp).toInt(), (48 * dp).toInt()).apply {
                            bottomMargin = (4 * dp).toInt()
                        }
                    }
                    addView(imageView)

                    val textView = TextView(context).apply {
                        layoutParams = LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        text = IconPackBrowseNameHelper.cleanName(icon.drawableName)
                        NexusTypeScale.caption.bindTo(this, currentTokens.textPrimary)
                        gravity = Gravity.CENTER
                        maxLines = 2
                        ellipsize = android.text.TextUtils.TruncateAt.END
                    }
                    addView(textView)

                    setOnClickListener {
                        it.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
                        val overrideData = "$packPackageName::${icon.drawableName}"
                        onIconSelected(overrideData)
                        dismiss()
                    }

                    withContext(Dispatchers.Main) {
                        gridLayout.addView(this@apply)
                    }

                    val targetSize = (48 * dp).toInt()
                    val drawable = IconPackBrowseBitmapLoader.loadDrawable(context, res, icon.resId, targetSize)

                    withContext(Dispatchers.Main) {
                        imageView.setImageDrawable(drawable)
                    }
                }
            }
        }
    }
}
