package com.nexus.launcher.ui.contextmenu

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.ui.NexusDesignSystem

class ContextMenuView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val menuCard: LinearLayout
    private val topNotch: ImageView
    private val bottomNotch: ImageView

    val headerContainer: LinearLayout
    val shortcutContainer: LinearLayout
    val actionContainer: LinearLayout
    private val shortcutDivider: View
    private val headerDivider: View
    
    val iconView: ImageView
    val nameTextView: TextView
    val infoIcon: ImageView

    private var notchXInternal: Float? = null
    var globalIconCenterX: Float = 0f
    var globalIconBounds: android.graphics.Rect? = null

    private var currentTokens: com.nexus.launcher.theme.NexusColorTokens = 
        com.nexus.launcher.ui.NexusContextMenuDesignHelper.resolveTokens(context)

    private val borderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = currentTokens.divider
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.FILL
    }
    
    private val arcPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = currentTokens.textSecondary
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = 2.5f
        strokeCap = android.graphics.Paint.Cap.ROUND
    }

    private val haloLinePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = currentTokens.textSecondary
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
    }

    fun setNotchPosition(x: Float) {
        if (isSideAnchored) return
        notchXInternal = x
        updateSpacers()
    }

    /** Opened beside the item (landscape) rather than above/below it: no notch, no notch gap. */
    var isSideAnchored: Boolean = false
        set(value) {
            field = value
            if (value) notchXInternal = null
            updateSpacers()
            invalidate()
        }
        
    var isAbove: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                updateSpacers()
            }
        }

    init {
        orientation = VERTICAL
        layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        
        // Use the exact same visual structure as the folder menu
        val wrapper = LayoutInflater.from(context).inflate(R.layout.view_folder_context_menu, this, true)
        
        menuCard = wrapper.findViewById(R.id.folder_menu_card)
        topNotch = wrapper.findViewById(R.id.folder_menu_notch_top)
        bottomNotch = wrapper.findViewById(R.id.folder_menu_notch_bottom)

        // Hide standard arrows permanently, as icon menu uses custom sink geometry
        topNotch.visibility = View.GONE
        bottomNotch.visibility = View.GONE
        
        setWillNotDraw(false)
        
        menuCard.removeAllViews() // Clear static XML
        menuCard.background = null // Clear hardcoded dark XML drawable so dynamic theme tokens apply

        val density = resources.displayMetrics.density
        arcPaint.strokeWidth = density * 2.5f
        haloLinePaint.strokeWidth = density * 2f
        borderPaint.strokeWidth = density * 1f
        
        // Build header
        headerContainer = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            setPadding((16 * density).toInt(), (12 * density).toInt(), (16 * density).toInt(), (12 * density).toInt())
            gravity = Gravity.CENTER_VERTICAL
        }
        
        iconView = ImageView(context).apply {
            visibility = View.GONE
        }
        
        nameTextView = TextView(context).apply {
            layoutParams = LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (4 * density).toInt()
            }
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            com.nexus.launcher.typography.NexusTypeScale.bodyStrong.bindTo(this, currentTokens.textPrimary)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        
        infoIcon = ImageView(context).apply {
            layoutParams = LayoutParams((24 * density).toInt(), (24 * density).toInt()).also {
                it.marginStart = (8 * density).toInt()
            }
            setImageResource(R.drawable.ic_info)
            setColorFilter(currentTokens.textSecondary, android.graphics.PorterDuff.Mode.SRC_IN)
        }
        
        headerContainer.addView(nameTextView)
        headerContainer.addView(infoIcon)
        
        headerDivider = NexusDesignSystem.buildContextMenuDivider(context)

        shortcutContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            visibility = View.GONE
        }
        shortcutDivider = NexusDesignSystem.buildContextMenuDivider(context)
        shortcutDivider.visibility = View.GONE

        actionContainer = LinearLayout(context).apply {
            orientation = VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            setPadding(0, 0, 0, (4 * density).toInt()) // tiny padding at bottom of card
        }

        menuCard.addView(headerContainer)
        menuCard.addView(headerDivider)
        menuCard.addView(shortcutContainer)
        menuCard.addView(shortcutDivider)
        menuCard.addView(actionContainer)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        // We set background manually in setAccentColor to control the outline, so no need here.
    }

    private fun updateSpacers() {
        val density = resources.displayMetrics.density
        val notchGap = (36 * density).toInt()

        if (isSideAnchored) {
            menuCard.setPadding(0, 0, 0, 0)
        } else if (isAbove) {
            menuCard.setPadding(0, 0, 0, notchGap)
        } else {
            menuCard.setPadding(0, notchGap, 0, 0)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val density = resources.displayMetrics.density
        val maxWidthPx = (com.nexus.launcher.ui.ContextMenuMetrics.APP_MENU_MAX_WIDTH_DP * density).toInt()
        val desiredWidth = measuredWidth.coerceAtMost(maxWidthPx)
        val exactSpec = MeasureSpec.makeMeasureSpec(desiredWidth, MeasureSpec.EXACTLY)
        super.onMeasure(exactSpec, heightMeasureSpec)
        updateSpacers() // ensure notch is placed after layout
    }

    fun setAppIcon(drawable: android.graphics.drawable.Drawable?) {
        iconView.setImageDrawable(drawable)
    }

    fun setAppName(name: String) {
        nameTextView.text = name
    }

    fun setInfoClickListener(listener: OnClickListener) {
        infoIcon.setOnClickListener(listener)
    }

    fun hideTopActions() {
        infoIcon.visibility = View.GONE
    }

    fun addShortcut(label: String, icon: android.graphics.drawable.Drawable?, onClick: () -> Unit) {
        shortcutContainer.visibility = View.VISIBLE
        shortcutDivider.visibility = View.VISIBLE
        val row = NexusDesignSystem.buildContextMenuRow(context, label, null, false, 0, icon, onClick)
        shortcutContainer.addView(row)
    }

    fun addAction(label: String, iconResId: Int?, isDestructive: Boolean = false, onClick: () -> Unit) {
        val row = NexusDesignSystem.buildContextMenuRow(context, label, iconResId, isDestructive, 0, null, onClick)
        
        if (actionContainer.childCount > 0) {
            actionContainer.addView(NexusDesignSystem.buildContextMenuDivider(context))
        }
        actionContainer.addView(row)
    }

    fun addAction(label: String, iconResId: Int?, color: Int?, onClick: () -> Unit) {
        val isDestructive = color != null && (color == Color.parseColor("#FF6B6B") || color == currentTokens.danger)
        addAction(label, iconResId, isDestructive, onClick)
    }

    fun setAccentColor(color: Int) {
        currentTokens = com.nexus.launcher.ui.NexusContextMenuDesignHelper.resolveTokens(context)
        applyTokens(currentTokens)
    }

    fun applyTokens(tokens: com.nexus.launcher.theme.NexusColorTokens) {
        currentTokens = tokens
        borderPaint.color = com.nexus.launcher.ui.glass.FrostedGlassEngine.resolveFrostedTokens(tokens).border
        arcPaint.color = tokens.textSecondary
        haloLinePaint.color = tokens.textSecondary
        infoIcon.setColorFilter(tokens.textSecondary, android.graphics.PorterDuff.Mode.SRC_IN)
        com.nexus.launcher.typography.NexusTypeScale.bodyStrong.bindTo(nameTextView, tokens.textPrimary)
        
        menuCard.background = null
        menuCard.clipToOutline = true
        menuCard.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                val density = resources.displayMetrics.density
                val cornerRadius = 16f * density
                val boundsRect = android.graphics.RectF(0f, 0f, view.width.toFloat(), view.height.toFloat())
                val backgroundPath = android.graphics.Path().apply {
                    addRoundRect(boundsRect, cornerRadius, cornerRadius, android.graphics.Path.Direction.CW)
                }
                notchXInternal?.let { notchX ->
                    val iconRadius = globalIconBounds?.let { it.width() / 2f } ?: 0f
                    val notchY = if (isAbove) view.height.toFloat() else 0f
                    val notchPath = android.graphics.Path().apply {
                        addCircle(notchX, notchY, iconRadius, android.graphics.Path.Direction.CW)
                    }
                    backgroundPath.op(notchPath, android.graphics.Path.Op.DIFFERENCE)
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    outline.setPath(backgroundPath)
                } else {
                    outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
                }
            }
        }
        invalidate()
    }

    override fun dispatchDraw(canvas: android.graphics.Canvas) {
        val density = resources.displayMetrics.density
        val cornerRadius = 16f * density
        val strokeHalf = borderPaint.strokeWidth / 2f
        
        val fillBoundsRect = android.graphics.RectF(0f, 0f, width.toFloat(), height.toFloat())
        val fillPath = android.graphics.Path().apply {
            addRoundRect(fillBoundsRect, cornerRadius, cornerRadius, android.graphics.Path.Direction.CW)
        }
        
        val boundsRect = android.graphics.RectF(
            strokeHalf, 
            strokeHalf, 
            width.toFloat() - strokeHalf, 
            height.toFloat() - strokeHalf
        )
        val backgroundPath = android.graphics.Path().apply {
            addRoundRect(boundsRect, cornerRadius, cornerRadius, android.graphics.Path.Direction.CW)
        }
        
        notchXInternal?.let { notchX ->
            val iconRadius = globalIconBounds?.let { it.width() / 2f } ?: 0f
            val notchY = if (isAbove) height.toFloat() else 0f
            
            val notchPath = android.graphics.Path()
            notchPath.addCircle(
                notchX, notchY, iconRadius,
                android.graphics.Path.Direction.CW
            )
            
            fillPath.op(notchPath, android.graphics.Path.Op.DIFFERENCE)
            backgroundPath.op(notchPath, android.graphics.Path.Op.DIFFERENCE)
        }
        
        run {
            val frostedTokens = com.nexus.launcher.ui.glass.FrostedGlassEngine.resolveFrostedTokens(currentTokens)
            val fillAlpha = (com.nexus.launcher.ui.glass.FrostedGlassEngine.sheetFillAlpha(1f, 0.70f) * 255f).toInt()
            fillPaint.color = (frostedTokens.surface and 0x00FFFFFF) or (fillAlpha shl 24)
        }
        canvas.drawPath(fillPath, fillPaint)

        super.dispatchDraw(canvas)
        
        notchXInternal?.let { notchX ->
            val iconRadius = globalIconBounds?.let { it.width() / 2f } ?: 0f
            val notchY = if (isAbove) height.toFloat() else 0f
            
            val rect = android.graphics.RectF(
                notchX - iconRadius,
                notchY - iconRadius,
                notchX + iconRadius,
                notchY + iconRadius
            )
            val startAngle = if (isAbove) 180f else 0f
            val arcPath = android.graphics.Path()
            arcPath.addArc(rect, startAngle, 180f)
            
            // Draw continuous border following notch curve
            canvas.drawPath(backgroundPath, borderPaint)
            
            canvas.drawPath(arcPath, arcPaint)

            val adjacentLength = 20 * density
            val leftStart = notchX - iconRadius - adjacentLength
            val leftEnd = notchX - iconRadius
            val rightStart = notchX + iconRadius
            val rightEnd = notchX + iconRadius + adjacentLength
            val lineStrokeHalf = 1.5f * density / 2f
            
            canvas.drawLine(
                leftStart.coerceAtLeast(lineStrokeHalf), notchY,
                leftEnd.coerceAtLeast(lineStrokeHalf), notchY,
                haloLinePaint
            )
            canvas.drawLine(
                rightStart.coerceAtMost(width.toFloat() - lineStrokeHalf), notchY,
                rightEnd.coerceAtMost(width.toFloat() - lineStrokeHalf), notchY,
                haloLinePaint
            )
        } ?: run {
            canvas.drawPath(backgroundPath, borderPaint)
        }
    }
}
