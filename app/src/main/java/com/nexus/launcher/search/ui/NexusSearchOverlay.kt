package com.nexus.launcher.search.ui

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.nexus.launcher.R
import com.nexus.launcher.search.NexusSearchEngine
import com.nexus.launcher.search.NexusSearchSettings
import com.nexus.launcher.search.SearchContext
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.MainActivity
import com.nexus.launcher.ui.folder.FolderBlurCoordinator
import com.nexus.launcher.ui.glass.FrostedGlassEngine
import com.nexus.launcher.ui.model.DisplayItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NexusSearchOverlay(
    context: Context,
    private val searchContext: SearchContext
) : FrameLayout(context) {

    private val dp = resources.displayMetrics.density
    private val activity = context as MainActivity
    private val engine = NexusSearchEngine(context)
    private val settings = NexusSearchSettings(context)
    private var isDismissing = false
    private var isDraggingToHome = false
    private val tokens: NexusColorTokens = try {
        ThemeObserver.currentTokens(context)
    } catch (_: Exception) {
        NexusColorTokens.Dark
    }

    // Views that need to be accessible outside init (e.g., backCallback, showSettingsPanel)
    private lateinit var scroll: ScrollView
    private lateinit var contentLayout: LinearLayout
    private var settingsPanelView: NexusSearchSettingsPanel? = null
    private var chipsRow: LinearLayout? = null

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (settingsPanelView?.visibility == View.VISIBLE) hideSettingsPanel()
            else dismiss()
        }
    }

    private val searchBar: EditText
    private val resultsContainer: LinearLayout
    private val recentSection: NexusSearchRecentSection
    private var searchJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        isClickable = true
        isFocusable = true
        setOnClickListener { dismiss() }

        // Default/Neumorphism: fully opaque theme bg. Frosted Glass: translucent fill over the
        // REAL workspace blur FolderBlurCoordinator.setWorkspaceBlur() already activates in
        // onAttachedToWindow below — same three-way relationship as every other surface.
        //
        // This used to ALSO attach WallpaperSheetFrost's own ~50% black scrim on top of that
        // translucent root — a second darkening layer stacked over the first, which compounds
        // toward flat/near-black instead of reading as more frosted (0.50 black + 0.55 root
        // scrim compounds to ~77.5% opacity before any content fill is even drawn on top). Same
        // "two stacked scrims crush toward black" trap this session already found and fixed the
        // same way in WallpaperSheet.kt: WallpaperSheetFrost is only the right tool when a
        // surface has NO other blur source of its own (unlike here, where FolderBlurCoordinator
        // already supplies a real one) — a single translucent root layer is the complete fix.
        applyRootBackground(tokens)

        contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (48 * dp).toInt(), (16 * dp).toInt(), (16 * dp).toInt())
        }

        ViewCompat.setOnApplyWindowInsetsListener(contentLayout) { view, insets ->
            // Status bar OR camera cutout: in immersive mode the status bar inset is 0 (and the
            // keyboard opening re-applies insets), but the pill must stay below the camera.
            val bars = insets.getInsets(WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout())
            val pad = (16 * dp).toInt()
            view.setPadding(pad + bars.left, bars.top + pad, pad + bars.right, pad)
            insets
        }
        contentLayout.addOnAttachStateChangeListener(object : OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = ViewCompat.requestApplyInsets(v)
            override fun onViewDetachedFromWindow(v: View) {}
        })

        searchBar = EditText(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            background = null
            hint = context.getString(R.string.home_search_hint)
            setHintTextColor(tokens.textSecondary)
            setTextColor(tokens.textPrimary)
            setSingleLine()
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setOnTouchListener { v, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    v.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                }
                false
            }
        }
        NexusTypeScale.body.bindTo(searchBar, tokens.textPrimary)

        val clearBtn = ImageView(context).apply {
            setImageResource(R.drawable.ic_close)
            imageTintList = ColorStateList.valueOf(tokens.textSecondary)
            layoutParams = LinearLayout.LayoutParams((24 * dp).toInt(), (24 * dp).toInt()).apply {
                marginStart = (8 * dp).toInt()
                marginEnd = (8 * dp).toInt()
            }
            visibility = View.GONE
            setOnClickListener {
                it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                searchBar.setText("")
            }
        }

        val searchPill = NexusSearchOverlayPillBuilder.buildPill(
            context, tokens, dp, searchBar, clearBtn,
            onBackClicked = {
                if (settingsPanelView?.visibility == View.VISIBLE) hideSettingsPanel()
                else dismiss()
            },
            onSettingsClicked = { showSettingsPanel() }
        )
        chipsRow = NexusSearchOverlayPillBuilder.buildChipsRow(context, tokens, dp)

        // Recent / New apps section
        recentSection = NexusSearchRecentSection(context, activity, dp, tokens) { dismiss() }
        recentSection.bind(scope)
        ThemeObserver.observe(activity, scope) { liveTokens ->
            applyRootBackground(liveTokens); recentSection.refreshTheme(liveTokens) }
        // Results Container
        resultsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, (16 * dp).toInt(), 0, 0)
        }
        scroll = ScrollView(context).apply {
            clipToPadding = false
            overScrollMode = View.OVER_SCROLL_NEVER
        }
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { view, insets ->
            view.setPadding(0, 0, 0, insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom)
            insets
        }
        val scrollContent = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        scrollContent.addView(recentSection.newTitle)
        scrollContent.addView(recentSection.newScroll)
        scrollContent.addView(recentSection.recentTitle)
        scrollContent.addView(recentSection.recentScroll)
        scrollContent.addView(resultsContainer)
        scroll.addView(scrollContent)

        contentLayout.addView(searchPill, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        chipsRow?.let { contentLayout.addView(it) }
        contentLayout.addView(scroll, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        // Raised result cards need their shadow's room inside the scroll, not cut at its edge.
        if (com.nexus.launcher.ui.glass.NeumorphicSurfaces.isActive) com.nexus.launcher.ui.glass.NeumorphicSurfaces.makeShadowRoom(scroll)

        addView(contentLayout, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        searchBar.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString() ?: ""
                clearBtn.visibility = if (query.isNotEmpty()) View.VISIBLE else View.GONE
                chipsRow?.visibility = if (query.isBlank()) View.VISIBLE else View.GONE
                recentSection.updateVisibility(query.isBlank())
                searchJob?.cancel()
                searchJob = scope.launch {
                    delay(150)
                    performSearch(query)
                }
            }
        })

        searchBar.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                val query = searchBar.text.toString()
                if (query.isNotBlank()) {
                    val intent = Intent(Intent.ACTION_WEB_SEARCH).apply { putExtra("query", query) }
                    try { context.startActivity(intent) } catch (_: Exception) {}
                    dismiss()
                }
                true
            } else false
        }
    }

    private fun showSettingsPanel() {
        if (settingsPanelView == null) {
            val panel = NexusSearchSettingsPanel(context)
            settingsPanelView = panel
            contentLayout.addView(panel, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        scroll.visibility = View.GONE
        chipsRow?.visibility = View.GONE
        settingsPanelView?.visibility = View.VISIBLE
    }

    private fun hideSettingsPanel() {
        settingsPanelView?.visibility = View.GONE
        chipsRow?.visibility = if (searchBar.text.isNullOrBlank()) View.VISIBLE else View.GONE
        scroll.visibility = View.VISIBLE
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?) = isDraggingToHome
    override fun onTouchEvent(event: MotionEvent?): Boolean {
        if (!isDraggingToHome || event == null) return super.onTouchEvent(event)
        val loc = IntArray(2).also { activity.canvasView.getLocationOnScreen(it) }
        val lx = event.rawX - loc[0]; val ly = event.rawY - loc[1]
        when (event.action) {
            MotionEvent.ACTION_MOVE -> { activity.canvasView.dragHandler.directMove(lx, ly); activity.canvasView.onDragMoved?.invoke(lx, ly) }
            MotionEvent.ACTION_UP -> { isDraggingToHome = false; activity.canvasView.onDragDropped?.invoke(lx, ly); dismiss() }
            MotionEvent.ACTION_CANCEL -> { isDraggingToHome = false; activity.canvasView.onDragCancelled?.invoke(); dismiss() }
        }
        return true
    }

    private suspend fun performSearch(query: String) {
        val currentTokens = try { ThemeObserver.currentTokens(context) } catch (_: Exception) { tokens }
        val results = engine.query(query, searchContext, settings, activity.viewModel.apps.value)
        withContext(Dispatchers.Main) {
            NexusSearchResultRenderer.renderAll(
                context, currentTokens, results, dp, resultsContainer,
                onResultClicked = { dismiss() },
                onLongPress = { item, startView -> startInternalDrag(item, startView) }
            )
        }
    }

    private fun startInternalDrag(item: DisplayItem, startView: View) {
        val loc = IntArray(2).also { startView.getLocationOnScreen(it) }
        isDraggingToHome = true; startView.alpha = 0f
        FolderBlurCoordinator.setWorkspaceBlur(activity, false); clearWindowBlur()
        animate().cancel(); alpha = 0f
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager)?.hideSoftInputFromWindow(searchBar.windowToken, 0)
        activity.canvasView.onDragStarted?.invoke(item, loc[0] + startView.width / 2f, loc[1] + startView.height / 2f)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        FolderBlurCoordinator.setWorkspaceBlur(activity, true)

        alpha = 0f
        animate().alpha(1f).setDuration(250).withEndAction {
            searchBar.requestFocus()
            searchBar.postDelayed({
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(searchBar, InputMethodManager.SHOW_IMPLICIT)
            }, 50)
        }.start()

        if (settings.searchContactsEnabled
            && androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            activity.requestPermissions(arrayOf(android.Manifest.permission.READ_CONTACTS), 4001)
        }

        activity.onBackPressedDispatcher.addCallback(activity, backCallback)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        FolderBlurCoordinator.setWorkspaceBlur(activity, false)
        clearWindowBlur()
        scope.cancel()
        backCallback.remove()
    }

    fun dismiss() {
        if (isDismissing) return
        isDismissing = true
        isClickable = false
        isFocusable = false
        backCallback.remove()
        FolderBlurCoordinator.setWorkspaceBlur(activity, false)
        clearWindowBlur()

        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(searchBar.windowToken, 0)

        animate().alpha(0f).setDuration(200).withEndAction {
            visibility = View.GONE
            val p = parent as? ViewGroup
            try { p?.removeView(this) } catch (_: Exception) {}
            com.nexus.launcher.ui.island.IslandController.notifyChrome()
        }.start()
    }

    private fun clearWindowBlur() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        runCatching {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
            activity.window.attributes = activity.window.attributes.apply { blurBehindRadius = 0 }
        }
    }

    /** Root scrim: fully opaque theme bg in Default/Neumorphism, translucent over the live
     *  blurred-wallpaper backdrop in Frosted Glass.
     *  In Drawer context, 88% opacity diffuses high-density icons into soft ambient color blobs,
     *  while a soft frosted top sheen emits ambient light for true frosted glass depth. */
    private fun applyRootBackground(liveTokens: NexusColorTokens) {
        if (!FrostedGlassEngine.isGlobalFrostedGlassEnabled) {
            background = ColorDrawable(liveTokens.bg or 0xFF000000.toInt())
            return
        }
        val isDrawer = searchContext == SearchContext.APP_DRAWER
        val targetOpacity = if (isDrawer) 0.88f else 0.55f
        val alpha = (targetOpacity * 255f).toInt().coerceIn(0, 255)
        val baseColor = (alpha shl 24) or (liveTokens.bg and 0x00FFFFFF)

        if (isDrawer) {
            val base = ColorDrawable(baseColor)
            val ambientSheen = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.argb(0x18, 255, 255, 255), Color.TRANSPARENT)
            )
            background = LayerDrawable(arrayOf(base, ambientSheen))
        } else {
            background = ColorDrawable(baseColor)
        }
    }

    companion object {
        fun show(activity: MainActivity, context: SearchContext) {
            val container = activity.findViewById<FrameLayout>(R.id.main_container) ?: return
            container.findViewWithTag<NexusSearchOverlay>("SearchOverlay")
                ?.let { try { container.removeView(it) } catch (_: Exception) {} }
            val overlay = NexusSearchOverlay(activity, context).apply { tag = "SearchOverlay" }
            container.addView(overlay, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            com.nexus.launcher.ui.island.IslandController.notifyChrome()
        }

        fun dismiss(activity: MainActivity) {
            activity.findViewById<FrameLayout>(R.id.main_container)
                ?.findViewWithTag<NexusSearchOverlay>("SearchOverlay")?.dismiss()
        }
    }
}
