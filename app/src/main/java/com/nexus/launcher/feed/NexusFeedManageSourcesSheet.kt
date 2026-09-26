package com.nexus.launcher.feed

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.NestedScrollView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nexus.launcher.R
import com.nexus.launcher.data.FeedDao
import com.nexus.launcher.data.FeedSource
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.folder.FolderBlurCoordinator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class NexusFeedManageSourcesSheet : BottomSheetDialogFragment() {

    @Inject lateinit var sourceManager: NexusFeedSourceManager
    @Inject lateinit var feedDao: FeedDao

    private val dp get() = resources.displayMetrics.density
    private lateinit var scrollView: NestedScrollView
    private lateinit var sourcesContainer: LinearLayout
    private lateinit var emptyView: TextView
    private lateinit var curatedSection: NexusFeedCuratedSection
    private lateinit var urlInput: EditText
    private lateinit var addProgress: ProgressBar
    private lateinit var addStatusText: TextView

    private var isDragging = false
    private var lastRenderedKeys = listOf<String>()
    private lateinit var tokens: NexusColorTokens

    override fun onStart() {
        super.onStart()
        context?.let { FolderBlurCoordinator.setWorkspaceBlur(it, true) }
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        context?.let { FolderBlurCoordinator.setWorkspaceBlur(it, false) }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return BottomSheetDialog(requireContext()).apply {
            window?.let { win ->
                win.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                WindowCompat.setDecorFitsSystemWindows(win, false)
                win.statusBarColor = Color.TRANSPARENT
                win.navigationBarColor = Color.TRANSPARENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    win.isNavigationBarContrastEnforced = false
                    win.isStatusBarContrastEnforced = false
                }
                win.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                win.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                win.setDimAmount(0.72f)
                // Blurs only in Frosted Glass; the dim above carries the separation otherwise.
                com.nexus.launcher.ui.glass.FloatingSurfaces.applyBlurBehind(win)
            }
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
            behavior.isDraggable = false
            setOnShowListener { d ->
                val sheet = (d as BottomSheetDialog).findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                sheet?.setBackgroundColor(Color.TRANSPARENT)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        tokens = try {
            ThemeObserver.currentTokens(requireContext())
        } catch (_: Exception) {
            NexusColorTokens.Dark
        }

        val outerRoot = FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            // Was an opaque `surface` slab even under Frosted Glass, over a blur it could not show.
            background = com.nexus.launcher.ui.glass.FloatingSurfaces.sheetCard(tokens, 24 * dp, dp)
            setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }

        root.addView(View(requireContext()).apply {
            background = GradientDrawable().apply { setColor(tokens.divider); cornerRadius = 2 * dp }
            layoutParams = LinearLayout.LayoutParams((42 * dp).toInt(), (4.5f * dp).toInt()).apply {
                gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = (12 * dp).toInt()
            }
        })

        val header = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, (14 * dp).toInt())

            addView(TextView(requireContext()).apply {
                text = getString(R.string.nexus_feed_tab_settings)
                NexusTypeScale.title.bindTo(this, tokens.textPrimary)
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })

            addView(ImageView(requireContext()).apply {
                setImageResource(R.drawable.ic_close)
                imageTintList = ColorStateList.valueOf(tokens.textSecondary)
                val pad = (6 * dp).toInt()
                setPadding(pad, pad, pad, pad)
                layoutParams = LinearLayout.LayoutParams((32 * dp).toInt(), (32 * dp).toInt())
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(tokens.surfaceRaised)
                }
                setOnClickListener {
                    it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                    dismiss()
                }
            })
        }
        root.addView(header)

        // Auto-refresh interval lives in NexusFeedSettingsSheet only — this used to duplicate
        // it here with its own independently hardcoded styling (the reported inconsistency).
        val scrollContent = LinearLayout(requireContext()).apply { orientation = LinearLayout.VERTICAL }
        scrollContent.addView(buildAddSourceCard())
        scrollContent.addView(buildActiveSourcesCard())

        curatedSection = NexusFeedCuratedSection(requireContext(), lifecycleScope, sourceManager)
        scrollContent.addView(curatedSection)

        scrollView = NestedScrollView(requireContext()).apply {
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
            isNestedScrollingEnabled = true
            addView(scrollContent)
        }
        root.addView(scrollView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        outerRoot.addView(root)
        // Card outer margin (not inner padding) clears the gesture nav bar — the proven fix.
        ViewCompat.setOnApplyWindowInsetsListener(outerRoot) { _, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            (root.layoutParams as? FrameLayout.LayoutParams)?.let { lp ->
                lp.bottomMargin = maxOf(navBars.bottom, ime.bottom) + (8 * dp).toInt()
                root.layoutParams = lp
            }
            insets
        }

        observeSources()
        return outerRoot
    }

    private fun buildAddSourceCard(): View {
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(tokens.surface)
                cornerRadius = 20 * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
            }
            setPadding((18 * dp).toInt(), (16 * dp).toInt(), (18 * dp).toInt(), (16 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (14 * dp).toInt()
            }
        }

        val titleRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(ImageView(requireContext()).apply {
                setImageResource(R.drawable.ic_add)
                imageTintList = ColorStateList.valueOf(tokens.textPrimary)
                layoutParams = LinearLayout.LayoutParams((20 * dp).toInt(), (20 * dp).toInt()).apply { marginEnd = (8 * dp).toInt() }
            })
            addView(TextView(requireContext()).apply {
                text = getString(R.string.nexus_feed_add_new_source)
                NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            })
        }
        card.addView(titleRow)

        val inputRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(tokens.surfaceRaised)
                cornerRadius = 14 * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
            }
            setPadding((14 * dp).toInt(), (4 * dp).toInt(), (6 * dp).toInt(), (4 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (52 * dp).toInt()).apply {
                topMargin = (12 * dp).toInt()
            }
        }

        urlInput = EditText(requireContext()).apply {
            hint = getString(R.string.nexus_feed_add_source_hint)
            setHintTextColor(tokens.textSecondary)
            NexusTypeScale.body.bindTo(this, tokens.textPrimary)
            background = null
            isSingleLine = true
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        inputRow.addView(urlInput)

        addProgress = ProgressBar(requireContext()).apply {
            layoutParams = LinearLayout.LayoutParams((24 * dp).toInt(), (24 * dp).toInt()).apply { marginEnd = (6 * dp).toInt() }
            indeterminateDrawable?.setTint(tokens.textPrimary)
            visibility = View.GONE
        }
        inputRow.addView(addProgress)

        val addBtn = TextView(requireContext()).apply {
            text = getString(R.string.action_add)
            NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(tokens.surfaceRaised)
                cornerRadius = 10 * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
            }
            setPadding((18 * dp).toInt(), (9 * dp).toInt(), (18 * dp).toInt(), (9 * dp).toInt())
            setOnClickListener {
                it.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY, HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                handleAddSource()
            }
        }
        inputRow.addView(addBtn)
        card.addView(inputRow)

        addStatusText = TextView(requireContext()).apply {
            NexusTypeScale.body.bindTo(this, tokens.textPrimary)
            visibility = View.GONE
            setPadding(0, (6 * dp).toInt(), 0, 0)
        }
        card.addView(addStatusText)
        return card
    }

    private fun handleAddSource() {
        val query = urlInput.text.toString().trim()
        if (query.isBlank()) return
        addProgress.visibility = View.VISIBLE
        addStatusText.visibility = View.GONE

        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(urlInput.windowToken, 0)

        lifecycleScope.launch {
            val result = sourceManager.addSource(query)
            addProgress.visibility = View.GONE
            addStatusText.visibility = View.VISIBLE
            result.onSuccess { createdSource: FeedSource ->
                urlInput.setText("")
                addStatusText.text = getString(R.string.nexus_feed_source_added, createdSource.title)
                addStatusText.setTextColor(tokens.textPrimary)
            }.onFailure { err ->
                addStatusText.text = err.message ?: getString(R.string.nexus_feed_source_add_failed)
                addStatusText.setTextColor(tokens.danger)
            }
        }
    }

    private fun buildActiveSourcesCard(): View {
        val card = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(tokens.surface)
                cornerRadius = 20 * dp
                setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
            }
            setPadding((18 * dp).toInt(), (16 * dp).toInt(), (18 * dp).toInt(), (16 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = (14 * dp).toInt()
            }
        }

        val titleRow = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(ImageView(requireContext()).apply {
                setImageResource(R.drawable.ic_feed_rss)
                imageTintList = ColorStateList.valueOf(tokens.textPrimary)
                layoutParams = LinearLayout.LayoutParams((20 * dp).toInt(), (20 * dp).toInt()).apply { marginEnd = (8 * dp).toInt() }
            })
            addView(TextView(requireContext()).apply {
                text = getString(R.string.nexus_feed_active_sources)
                NexusTypeScale.bodyStrong.bindTo(this, tokens.textPrimary)
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
            })
        }
        card.addView(titleRow)

        sourcesContainer = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (12 * dp).toInt(), 0, 0)
        }
        emptyView = TextView(requireContext()).apply {
            text = getString(R.string.nexus_feed_no_sources_yet)
            NexusTypeScale.body.bindTo(this, tokens.textSecondary)
            gravity = Gravity.CENTER
            setPadding(0, (16 * dp).toInt(), 0, (16 * dp).toInt())
            visibility = View.GONE
        }
        sourcesContainer.addView(emptyView)
        card.addView(sourcesContainer)
        return card
    }

    private fun observeSources() {
        lifecycleScope.launch {
            feedDao.getAllSourcesFlow().collectLatest { sources ->
                if (!isDragging) renderSources(sources)
                curatedSection.updateExistingUrls(sources.map { it.url }.toSet())
            }
        }
    }

    private fun renderSources(sources: List<FeedSource>) {
        val currentKeys = sources.map { "${it.id}_${it.title}_${it.isEnabled}" }
        if (currentKeys == lastRenderedKeys && sourcesContainer.childCount == (if (sources.isEmpty()) 1 else sources.size)) return
        lastRenderedKeys = currentKeys
        sourcesContainer.removeAllViews()
        if (sources.isEmpty()) {
            emptyView.visibility = View.VISIBLE
            sourcesContainer.addView(emptyView)
            return
        }
        emptyView.visibility = View.GONE
        sources.forEach { source ->
            sourcesContainer.addView(
                NexusFeedSourceRowBinder.buildSourceRow(
                    context = requireContext(),
                    source = source,
                    dp = dp,
                    scope = lifecycleScope,
                    sourceManager = sourceManager,
                    sourcesContainer = sourcesContainer,
                    onDragStateChanged = { isDragging = it },
                    onReorderCommitted = { newIds ->
                        lastRenderedKeys = emptyList()
                        lifecycleScope.launch { sourceManager.reorderSources(newIds) }
                    }
                )
            )
        }
    }

    companion object {
        const val TAG = "NexusFeedManageSourcesSheet"
    }
}
