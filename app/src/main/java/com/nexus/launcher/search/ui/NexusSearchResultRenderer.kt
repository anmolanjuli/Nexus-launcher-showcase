package com.nexus.launcher.search.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.nexus.launcher.R
import com.nexus.launcher.search.SearchResult
import com.nexus.launcher.theme.NexusColorTokens
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.model.DisplayItem

object NexusSearchResultRenderer {

    fun renderAll(
        context: Context,
        tokens: NexusColorTokens,
        results: List<SearchResult>,
        dp: Float,
        container: LinearLayout,
        onResultClicked: () -> Unit,
        onLongPress: ((DisplayItem, View) -> Unit)? = null
    ) {
        container.removeAllViews()
        if (results.isEmpty()) return

        val appResults = results.filterIsInstance<SearchResult.AppResult>()
        val otherResults = results.filter { it !is SearchResult.AppResult }

        if (appResults.isNotEmpty()) {
            val header = TextView(context).apply {
                text = context.getString(R.string.search_section_header_apps)
                setPadding((8 * dp).toInt(), (16 * dp).toInt(), 0, (8 * dp).toInt())
            }
            NexusTypeScale.caption.bindTo(header, tokens.textSecondary)
            container.addView(header)
            container.addView(buildAppGrid(context, tokens, appResults, dp, onResultClicked, onLongPress))
        }

        var currentType: Class<*>? = null
        for (result in otherResults) {
            if (result::class.java != currentType) {
                currentType = result::class.java
                val headerRes = when (result) {
                    is SearchResult.ContactResult -> R.string.search_section_header_contacts
                    is SearchResult.CalculatorResult -> R.string.search_section_header_calculator
                    is SearchResult.UnitConverterResult -> R.string.search_section_header_conversion
                    is SearchResult.WebResult -> R.string.search_section_header_web
                    is SearchResult.MapsResult -> R.string.search_section_header_maps
                    is SearchResult.AppResult -> R.string.search_section_header_apps
                }
                val header = TextView(context).apply {
                    text = context.getString(headerRes)
                    setPadding((8 * dp).toInt(), (16 * dp).toInt(), 0, (8 * dp).toInt())
                }
                NexusTypeScale.caption.bindTo(header, tokens.textSecondary)
                container.addView(header)
            }
            container.addView(render(context, tokens, result, dp, onResultClicked))
        }
    }

    private fun buildAppGrid(
        context: Context,
        tokens: NexusColorTokens,
        apps: List<SearchResult.AppResult>,
        dp: Float,
        onResultClicked: () -> Unit,
        onLongPress: ((DisplayItem, View) -> Unit)?
    ): LinearLayout {
        val grid = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val cols = 5
        var row: LinearLayout? = null
        apps.forEachIndexed { index, result ->
            if (index % cols == 0) {
                row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                }
                grid.addView(row)
            }
            val cell = buildAppCell(context, tokens, result, dp, onResultClicked, onLongPress)
            cell.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            row?.addView(cell)
        }
        val remainder = apps.size % cols
        if (remainder != 0) {
            repeat(cols - remainder) {
                row?.addView(View(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, 1, 1f)
                })
            }
        }
        return grid
    }

    private fun buildAppCell(
        context: Context,
        tokens: NexusColorTokens,
        result: SearchResult.AppResult,
        dp: Float,
        onResultClicked: () -> Unit,
        onLongPress: ((DisplayItem, View) -> Unit)?
    ): LinearLayout {
        val live = try {
            dagger.hilt.android.EntryPointAccessors.fromApplication(
                context.applicationContext,
                com.nexus.launcher.ui.icons.IconResolverEntryPoint::class.java
            ).iconResolver().getIcon(result.packageName)
        } catch (_: Exception) { null }

        val cell = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding((4 * dp).toInt(), (8 * dp).toInt(), (4 * dp).toInt(), (8 * dp).toInt())
        }
        val iconSize = (48 * dp).toInt()
        val icon = ImageView(context).apply {
            setImageDrawable(live ?: result.icon)
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
        }
        cell.addView(icon)
        val label = TextView(context).apply {
            text = result.label
            setSingleLine(true)
            gravity = Gravity.CENTER
        }
        NexusTypeScale.iconLabel.bindTo(label, tokens.textPrimary)
        cell.addView(label)

        cell.setOnClickListener {
            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            val intent = context.packageManager.getLaunchIntentForPackage(result.packageName)
            if (intent != null) { context.startActivity(intent); onResultClicked() }
        }
        if (onLongPress != null) {
            cell.setOnLongClickListener { v ->
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                val intent = context.packageManager.getLaunchIntentForPackage(result.packageName)
                onLongPress(DisplayItem(result.label, live ?: result.icon, intent), v)
                true
            }
        }
        return cell
    }

    fun render(
        context: Context,
        tokens: NexusColorTokens,
        result: SearchResult,
        dp: Float,
        onResultClicked: () -> Unit
    ): View {
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt())
            background = com.nexus.launcher.ui.glass.NeumorphicSurfaces.raisedOr(this, tokens, 16 * dp) {
                GradientDrawable().apply {
                    setColor(tokens.surfaceRaised)
                    cornerRadius = 16 * dp
                }
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = (8 * dp).toInt()
            }
        }

        when (result) {
            is SearchResult.AppResult -> buildAppRow(context, tokens, result, dp, row, onResultClicked)
            is SearchResult.ContactResult -> buildContactRow(context, tokens, result, dp, row)
            is SearchResult.WebResult -> buildWebRow(context, tokens, result, dp, row, onResultClicked)
            is SearchResult.CalculatorResult -> buildCalculatorRow(context, tokens, result, row)
            is SearchResult.UnitConverterResult -> buildUnitRow(context, tokens, result, row)
            is SearchResult.MapsResult -> buildMapsRow(context, tokens, result, dp, row, onResultClicked)
        }

        return row
    }

    private fun buildAppRow(
        context: Context,
        tokens: NexusColorTokens,
        result: SearchResult.AppResult,
        dp: Float,
        row: LinearLayout,
        onResultClicked: () -> Unit
    ) {
        val icon = ImageView(context).apply {
            val live = try {
                dagger.hilt.android.EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    com.nexus.launcher.ui.icons.IconResolverEntryPoint::class.java
                ).iconResolver().getIcon(result.packageName)
            } catch (_: Exception) {
                null
            }
            setImageDrawable(live ?: result.icon)
            layoutParams = LinearLayout.LayoutParams((40 * dp).toInt(), (40 * dp).toInt()).apply {
                marginEnd = (16 * dp).toInt()
            }
        }
        row.addView(icon)
        val title = TextView(context).apply { text = result.label }
        NexusTypeScale.body.bindTo(title, tokens.textPrimary)
        row.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.setOnClickListener {
            val intent = context.packageManager.getLaunchIntentForPackage(result.packageName)
            if (intent != null) {
                context.startActivity(intent)
                onResultClicked()
            }
        }
    }

    private fun buildContactRow(
        context: Context,
        tokens: NexusColorTokens,
        result: SearchResult.ContactResult,
        dp: Float,
        row: LinearLayout
    ) {
        val icon = ImageView(context).apply {
            if (result.photoUri != null) {
                setImageURI(result.photoUri)
                clipToOutline = true
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL }
            } else {
                setImageResource(R.drawable.ic_info)
                imageTintList = ColorStateList.valueOf(tokens.textSecondary)
                background = GradientDrawable().apply {
                    setColor(tokens.surfaceRaised)
                    shape = GradientDrawable.OVAL
                    setStroke((1 * dp).toInt().coerceAtLeast(1), tokens.divider)
                }
                setPadding((8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
            }
            layoutParams = LinearLayout.LayoutParams((40 * dp).toInt(), (40 * dp).toInt()).apply {
                marginEnd = (16 * dp).toInt()
            }
        }
        row.addView(icon)
        val textContainer = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        val title = TextView(context).apply { text = result.name }
        NexusTypeScale.body.bindTo(title, tokens.textPrimary)
        textContainer.addView(title)
        if (result.phone != null) {
            val phone = TextView(context).apply { text = result.phone }
            NexusTypeScale.caption.bindTo(phone, tokens.textSecondary)
            textContainer.addView(phone)
        }
        row.addView(textContainer, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun buildWebRow(
        context: Context,
        tokens: NexusColorTokens,
        result: SearchResult.WebResult,
        dp: Float,
        row: LinearLayout,
        onResultClicked: () -> Unit
    ) {
        val icon = ImageView(context).apply {
            setImageResource(R.drawable.ic_search)
            imageTintList = ColorStateList.valueOf(tokens.textSecondary)
            layoutParams = LinearLayout.LayoutParams((24 * dp).toInt(), (24 * dp).toInt()).apply {
                marginEnd = (16 * dp).toInt()
                marginStart = (8 * dp).toInt()
            }
        }
        row.addView(icon)
        val title = TextView(context).apply {
            text = context.getString(R.string.search_web_for_query, result.query)
        }
        NexusTypeScale.body.bindTo(title, tokens.textPrimary)
        row.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.setOnClickListener {
            val intent = android.content.Intent(android.content.Intent.ACTION_WEB_SEARCH)
            intent.putExtra(android.app.SearchManager.QUERY, result.query)
            try {
                context.startActivity(intent)
                onResultClicked()
            } catch (_: Exception) {
                android.widget.Toast.makeText(context, context.getString(R.string.toast_no_web_browser_found), android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun buildCalculatorRow(
        context: Context,
        tokens: NexusColorTokens,
        result: SearchResult.CalculatorResult,
        row: LinearLayout
    ) {
        val expr = TextView(context).apply { text = result.expression }
        NexusTypeScale.body.bindTo(expr, tokens.textSecondary)
        row.addView(expr)
        val equal = TextView(context).apply { text = " = " }
        NexusTypeScale.body.bindTo(equal, tokens.textSecondary)
        row.addView(equal)
        val ans = TextView(context).apply { text = result.answer }
        NexusTypeScale.bodyStrong.bindTo(ans, tokens.textPrimary)
        row.addView(ans)
    }

    private fun buildUnitRow(
        context: Context,
        tokens: NexusColorTokens,
        result: SearchResult.UnitConverterResult,
        row: LinearLayout
    ) {
        val input = TextView(context).apply { text = result.input }
        NexusTypeScale.body.bindTo(input, tokens.textSecondary)
        row.addView(input)
        val arrow = TextView(context).apply { text = " → " }
        NexusTypeScale.body.bindTo(arrow, tokens.textSecondary)
        row.addView(arrow)
        if (result.locked) {
            // The conversion is the offer: show that one exists, and open the paywall on a tap.
            row.addView(com.nexus.launcher.ui.premium.PremiumBadges.pill(context, tokens).apply { visibility = android.view.View.VISIBLE })
            row.setOnClickListener { com.nexus.launcher.premium.PremiumGate.allow(context, com.nexus.launcher.premium.PremiumFeature.SMART_SEARCH) }
            return
        }
        val out = TextView(context).apply { text = result.output }
        NexusTypeScale.bodyStrong.bindTo(out, tokens.textPrimary)
        row.addView(out)
    }

    private fun buildMapsRow(
        context: Context,
        tokens: NexusColorTokens,
        result: SearchResult.MapsResult,
        dp: Float,
        row: LinearLayout,
        onResultClicked: () -> Unit
    ) {
        val icon = ImageView(context).apply {
            setImageResource(R.drawable.ic_explore)
            imageTintList = ColorStateList.valueOf(tokens.textSecondary)
            layoutParams = LinearLayout.LayoutParams((24 * dp).toInt(), (24 * dp).toInt()).apply {
                marginEnd = (16 * dp).toInt()
                marginStart = (8 * dp).toInt()
            }
        }
        row.addView(icon)
        val title = TextView(context).apply {
            text = context.getString(R.string.search_open_in_maps, result.query)
        }
        NexusTypeScale.body.bindTo(title, tokens.textPrimary)
        row.addView(title, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        row.setOnClickListener {
            val uri = android.net.Uri.parse("geo:0,0?q=${android.net.Uri.encode(result.query)}")
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri)
            try {
                context.startActivity(intent)
                onResultClicked()
            } catch (_: Exception) {
                android.widget.Toast.makeText(context, context.getString(R.string.toast_no_maps_app_found), android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }
}
