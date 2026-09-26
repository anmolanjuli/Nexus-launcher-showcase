package com.nexus.launcher.ui.picker

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.ColorStateList
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.nexus.launcher.R
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.MainActivity

/**
 * Reusable full-screen themed overlay shell for pickers (Shortcut, Widget, App).
 * Eliminates BottomSheetDialogFragment overhead, window blur/dim compositing stalls,
 * and standardizes header, search, empty state, and back-press handling.
 */
open class PickerOverlayShell(context: Context) : FrameLayout(context) {

    protected val dp = resources.displayMetrics.density
    protected val tokens: NexusColorTokens = try {
        ThemeObserver.currentTokens(context)
    } catch (_: Exception) {
        NexusColorTokens.Dark
    }

    private val headerContainer: LinearLayout
    private val titleView: TextView
    private val closeButton: ImageView
    private val searchBar: EditText
    private val customHeaderSlot: FrameLayout
    private val contentContainer: FrameLayout
    private val emptyStateContainer: LinearLayout
    private val emptyTitleView: TextView
    private val emptySubtitleView: TextView
    private val loadingSpinner: ProgressBar

    private var backCallback: OnBackPressedCallback? = null
    private val systemNavHelper = PickerOverlaySystemNavHelper { dismiss() }
    var onDismissed: (() -> Unit)? = null
    private val mainLayout: LinearLayout
    // Deliberately opaque by default (see class doc — avoids blur/dim compositing stalls), but
    // that's specifically a Default/Neumorphism-mode look: with the global UI Style set to
    // Frosted Glass, this should read as frosted like every other surface, not stay flat opaque
    // regardless of the picker choice. Applied in show()/dismiss() (not init) since the toggle
    // can differ between construction and actual display.
    private var activatedWorkspaceBlur = false

    fun animatePickerChrome(toAlpha: Float, duration: Long = 200) {
        mainLayout.animate().alpha(toAlpha).setDuration(duration).start()
    }

    init {
        tag = TAG
        isClickable = true
        isFocusable = true
        setBackgroundColor(tokens.bg)

        mainLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        // Header Container - query immediate top inset to prevent notch/status bar overlap on first render
        val initialTopInset = computeInitialTopInset()
        headerContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
            setPadding(
                (16 * dp).toInt(),
                initialTopInset + (12 * dp).toInt(),
                (16 * dp).toInt(),
                (12 * dp).toInt()
            )
        }

        // Top Row: Title + Close Button
        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
        }

        titleView = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f)
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            textAlignment = View.TEXT_ALIGNMENT_VIEW_START
        }
        NexusTypeScale.title.bindTo(titleView, tokens.textPrimary)
        topRow.addView(titleView)

        closeButton = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams((32 * dp).toInt(), (32 * dp).toInt())
            setImageResource(R.drawable.ic_close)
            imageTintList = ColorStateList.valueOf(tokens.textSecondary)
            setPadding((6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt(), (6 * dp).toInt())
            PickerOverlayStyle.closeButton(this, tokens, dp)
            setOnClickListener { dismiss() }
        }
        topRow.addView(closeButton)
        headerContainer.addView(topRow)

        // Search Bar (default visible)
        searchBar = EditText(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                (44 * dp).toInt()
            ).apply {
                topMargin = (12 * dp).toInt()
            }
            hint = context.getString(com.nexus.launcher.R.string.search_hint)
            setHintTextColor(tokens.textSecondary)
            NexusTypeScale.body.bindTo(this, tokens.textPrimary)
            PickerOverlayStyle.searchField(this, tokens, dp)
            setPadding((16 * dp).toInt(), 0, (16 * dp).toInt(), 0)
            maxLines = 1
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        headerContainer.addView(searchBar)

        // Slot for custom header rows (e.g. tabs, category pills)
        customHeaderSlot = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.WRAP_CONTENT
            )
            visibility = View.GONE
        }
        headerContainer.addView(customHeaderSlot)
        mainLayout.addView(headerContainer)

        // Content Area
        val bodyWrapper = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        contentContainer = FrameLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            // Generous default bottom padding for navigation bar and physical corner curves
            setPadding(0, 0, 0, (48 * dp).toInt())
        }
        bodyWrapper.addView(contentContainer)

        // Empty State Container
        emptyStateContainer = LinearLayout(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding((32 * dp).toInt(), 0, (32 * dp).toInt(), 0)
        }

        emptyTitleView = TextView(context).apply {
            gravity = Gravity.CENTER
        }
        NexusTypeScale.title.bindTo(emptyTitleView, tokens.textPrimary)
        emptyStateContainer.addView(emptyTitleView)

        emptySubtitleView = TextView(context).apply {
            gravity = Gravity.CENTER
            setPadding(0, (8 * dp).toInt(), 0, 0)
        }
        NexusTypeScale.caption.bindTo(emptySubtitleView, tokens.textSecondary)
        emptyStateContainer.addView(emptySubtitleView)
        bodyWrapper.addView(emptyStateContainer)

        // Loading Spinner
        loadingSpinner = ProgressBar(context).apply {
            layoutParams = LayoutParams(
                (48 * dp).toInt(),
                (48 * dp).toInt()
            ).apply {
                gravity = Gravity.CENTER
            }
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(tokens.textPrimary)
            visibility = View.GONE
        }
        bodyWrapper.addView(loadingSpinner)

        mainLayout.addView(bodyWrapper)
        addView(mainLayout)

        // Window insets handling: status bars / cutouts on header top, navigation bars / corner margin on bottom
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val displayCutout = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val imeBars = insets.getInsets(WindowInsetsCompat.Type.ime())

            val effectiveTop = maxOf(statusBars.top, displayCutout.top, initialTopInset)
            headerContainer.setPadding(
                (16 * dp).toInt(),
                effectiveTop + (12 * dp).toInt(),
                (16 * dp).toInt(),
                (12 * dp).toInt()
            )

            // Extra 32dp beyond navigation bar ensures curved screen corners don't clip the bottom card
            val bottomInset = maxOf(navBars.bottom, imeBars.bottom) + (32 * dp).toInt()
            contentContainer.setPadding(0, 0, 0, bottomInset)
            insets
        }
    }

    private fun computeInitialTopInset(): Int {
        val act = findActivity(context)
        val mainActivity = act as? MainActivity
        val canvasTop = mainActivity?.canvasView?.topInset ?: 0
        if (canvasTop > 0) return canvasTop

        val rootInsets = act?.window?.decorView?.let { ViewCompat.getRootWindowInsets(it) }
        val statusBarsTop = rootInsets?.getInsets(WindowInsetsCompat.Type.statusBars())?.top ?: 0
        val cutoutTop = rootInsets?.getInsets(WindowInsetsCompat.Type.displayCutout())?.top ?: 0
        return maxOf(statusBarsTop, cutoutTop, (28 * dp).toInt())
    }

    fun setTitle(title: CharSequence) {
        titleView.text = title
    }

    fun setSearchHint(hint: CharSequence) {
        searchBar.hint = hint
    }

    fun setSearchBarVisible(visible: Boolean) {
        searchBar.visibility = if (visible) View.VISIBLE else View.GONE
    }

    fun setOnSearchQueryChanged(listener: (String) -> Unit) {
        searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                listener(s?.toString() ?: "")
            }
        })
    }

    fun setCustomHeaderView(view: View) {
        customHeaderSlot.removeAllViews()
        customHeaderSlot.addView(view)
        customHeaderSlot.visibility = View.VISIBLE
    }

    fun setContent(view: View) {
        contentContainer.removeAllViews()
        contentContainer.addView(view)
    }

    fun showSpinner() {
        loadingSpinner.visibility = View.VISIBLE
    }

    fun hideSpinner() {
        loadingSpinner.visibility = View.GONE
    }

    fun showLoading(loading: Boolean) {
        if (loading) showSpinner() else hideSpinner()
    }

    open fun canDismissSafely(): Boolean = true

    open fun onDiscardRequested() {
        dismiss()
    }

    fun showEmptyState(title: String, subtitle: String? = null) {
        emptyTitleView.text = title
        if (subtitle != null) {
            emptySubtitleView.text = subtitle
            emptySubtitleView.visibility = View.VISIBLE
        } else {
            emptySubtitleView.visibility = View.GONE
        }
        emptyStateContainer.visibility = View.VISIBLE
        contentContainer.visibility = View.GONE
    }

    fun hideEmptyState() {
        emptyStateContainer.visibility = View.GONE
        contentContainer.visibility = View.VISIBLE
    }

    open fun show() {
        val targetActivity = findActivity(context) ?: return
        val mainContainer = targetActivity.findViewById<FrameLayout>(R.id.main_container)
            ?: targetActivity.window.decorView.findViewById(android.R.id.content)
            ?: return

        // Register back pressed callback
        if (targetActivity is ComponentActivity) {
            backCallback = object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    dismiss()
                }
            }
            targetActivity.onBackPressedDispatcher.addCallback(targetActivity, backCallback!!)
        }
        systemNavHelper.register(targetActivity)

        applyStyleBackground(targetActivity)

        this.layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        this.alpha = 0f
        mainContainer.addView(this)
        ViewCompat.requestApplyInsets(this)
        this.animate().alpha(1f).setDuration(200).start()
    }

    private fun applyStyleBackground(activity: Activity) {
        activatedWorkspaceBlur = PickerOverlayStyle.applyBackground(this, tokens, activity)
    }

    open fun dismiss() {
        systemNavHelper.unregister()
        val targetActivity = findActivity(context)
        val mainContainer = targetActivity?.findViewById<FrameLayout>(R.id.main_container)
            ?: targetActivity?.window?.decorView?.findViewById(android.R.id.content)
            ?: (parent as? ViewGroup)

        backCallback?.remove()
        backCallback = null

        if (activatedWorkspaceBlur) {
            activatedWorkspaceBlur = false
            PickerOverlayStyle.release(targetActivity)
        }

        this.animate().alpha(0f).setDuration(200)
            .withEndAction {
                mainContainer?.removeView(this)
                onDismissed?.invoke()
            }
            .start()
    }

    protected fun findActivity(ctx: Context): Activity? {
        var current: Context? = ctx
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    companion object {
        const val TAG = "picker_overlay_shell"
    }
}
