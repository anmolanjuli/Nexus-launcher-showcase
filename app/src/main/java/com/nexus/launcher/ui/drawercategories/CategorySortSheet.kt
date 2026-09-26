package com.nexus.launcher.ui.drawercategories

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.nexus.launcher.R
import com.nexus.launcher.domain.model.AppModel
import com.nexus.launcher.theme.ThemeObserver
import com.nexus.launcher.typography.NexusTypeScale
import com.nexus.launcher.ui.DrawerSearchPillBuilder
import com.nexus.launcher.ui.LandscapeSheets
import com.nexus.launcher.ui.MainViewModel
import com.nexus.launcher.ui.settings.CategoryPickerSheet

/**
 * Sorting apps into categories in bulk: tick as many apps as you like, then pick the category
 * they all belong in. One app at a time through its own menu is fine for a correction, but not
 * for the first pass over a few hundred apps.
 *
 * Each row shows where the app sits now, so what the launcher guessed is visible while sorting.
 */
object CategorySortSheet {

    fun show(activity: Activity, viewModel: MainViewModel) {
        val tokens = ThemeObserver.currentTokens(activity)
        val dp = activity.resources.displayMetrics.density
        val dialog = BottomSheetDialog(activity)
        val selected = linkedSetOf<String>()

        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
            setBackgroundColor(tokens.bg)
        }
        val title = TextView(activity).apply { text = activity.getString(R.string.category_sort_title) }
        NexusTypeScale.bodyStrong.bindTo(title, tokens.textPrimary)
        root.addView(title)

        val search = EditText(activity).apply {
            hint = activity.getString(R.string.drawer_category_search_hint)
            setSingleLine()
            background = null
            setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
        }
        NexusTypeScale.body.bindTo(search, tokens.textPrimary)
        search.setHintTextColor(tokens.textSecondary)
        root.addView(search)

        val list = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        root.addView(
            ScrollView(activity).apply {
                isFillViewport = true
                overScrollMode = View.OVER_SCROLL_NEVER
                addView(list, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f),
        )

        val action = TextView(activity).apply {
            gravity = Gravity.CENTER
            setPadding(0, (14 * dp).toInt(), 0, (10 * dp).toInt())
            isEnabled = false
            alpha = 0.4f
            text = activity.getString(R.string.category_sort_move)
        }
        NexusTypeScale.bodyStrong.bindTo(action, tokens.accent)
        root.addView(action)

        fun refreshAction() {
            action.isEnabled = selected.isNotEmpty()
            action.alpha = if (selected.isEmpty()) 0.4f else 1f
            action.text = if (selected.isEmpty()) {
                activity.getString(R.string.category_sort_move)
            } else {
                activity.getString(R.string.category_sort_move_count, selected.size)
            }
        }

        fun fill(query: String) {
            list.removeAllViews()
            val apps = viewModel.apps.value
                .filter { query.isBlank() || it.label.contains(query, ignoreCase = true) }
                .sortedBy { it.label.lowercase() }
            apps.forEach { app ->
                list.addView(row(activity, tokens, dp, app, viewModel, selected) { refreshAction() })
            }
        }

        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                fill(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        fill("")
        refreshAction()

        action.setOnClickListener {
            if (selected.isEmpty()) return@setOnClickListener
            val manager = (activity as? FragmentActivity)?.supportFragmentManager
                ?: return@setOnClickListener
            CategoryPickerSheet.show(
                fragmentManager = manager,
                categories = viewModel.categories,
                currentCategoryId = 0,
                title = activity.getString(R.string.category_sort_pick),
                context = activity,
            ) { categoryId ->
                selected.forEach { pkg -> viewModel.setAppCategory(pkg, categoryId) }
                dialog.dismiss()
            }
        }

        dialog.setContentView(root)
        LandscapeSheets.apply(dialog)
        dialog.show()
    }

    private fun row(
        activity: Activity,
        tokens: com.nexus.launcher.theme.NexusColorTokens,
        dp: Float,
        app: AppModel,
        viewModel: MainViewModel,
        selected: MutableSet<String>,
        onChanged: () -> Unit,
    ): View {
        val row = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, (8 * dp).toInt(), 0, (8 * dp).toInt())
            isClickable = true
        }
        val icon = android.widget.ImageView(activity).apply {
            setImageDrawable(app.icon)
            layoutParams = LinearLayout.LayoutParams((32 * dp).toInt(), (32 * dp).toInt()).apply {
                marginEnd = (12 * dp).toInt()
            }
        }
        val column = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val label = TextView(activity).apply {
            text = app.label
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        NexusTypeScale.body.bindTo(label, tokens.textPrimary)
        val categoryKey = viewModel.drawerCategories.keyForApp(app.categoryId)
        val category = TextView(activity).apply {
            text = categoryKey?.let { DrawerSearchPillBuilder.categoryLabelText(activity, it) }
                ?: activity.getString(R.string.category_uncategorised)
        }
        NexusTypeScale.caption.bindTo(category, tokens.textSecondary)
        column.addView(label)
        column.addView(category)
        val check = android.widget.CheckBox(activity).apply {
            isChecked = app.packageName in selected
            buttonTintList = android.content.res.ColorStateList.valueOf(tokens.accent)
        }
        fun toggle(checked: Boolean) {
            if (checked) selected.add(app.packageName) else selected.remove(app.packageName)
            onChanged()
        }
        check.setOnCheckedChangeListener { _, checked -> toggle(checked) }
        row.setOnClickListener { check.isChecked = !check.isChecked }
        row.addView(icon)
        row.addView(column)
        row.addView(check)
        return row
    }
}
