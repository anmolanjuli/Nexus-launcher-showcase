package com.nexus.launcher.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.nexus.launcher.R
import com.nexus.launcher.data.HomeScreenItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class GridInjectionBottomSheet(
    private val maxSelectable: Int = -1,
    private val onWorkspaceAppsSelected: ((List<HomeScreenItem>) -> Unit)
) : BottomSheetDialogFragment() {

    private lateinit var adapter: InjectionAdapter
    private var allApps: List<HomeScreenItem> = emptyList()
    private val selectedIds = mutableSetOf<Long>()
    private var searchQuery = ""
    private lateinit var saveBtn: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.bottom_sheet_folder_app_picker, container, false)
    }

    override fun onStart() {
        super.onStart()
        val d = dialog as? BottomSheetDialog ?: return
        com.nexus.launcher.ui.folder.FolderSheetWallpaperDecor.applyWindowBlur(d)
        d.window?.decorView?.post {
            com.nexus.launcher.ui.folder.FolderAppPickerDecor.apply(d)
            val sheetContent = d.findViewById<View>(
                com.google.android.material.R.id.design_bottom_sheet
            )
            sheetContent?.background = null
            sheetContent?.backgroundTintList = null
            val frost = android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadii = floatArrayOf(
                    60f, 60f, 60f, 60f, 0f, 0f, 0f, 0f
                )
                setColor(android.graphics.Color.argb(30, 255, 255, 255))
            }
            sheetContent?.background = frost
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val targetHeight = (resources.displayMetrics.heightPixels * 0.9).toInt()
        view.layoutParams = android.widget.FrameLayout.LayoutParams(
            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
            targetHeight
        )
        applyNavBarInsets(view)

        val switchView = view.findViewById<SwitchCompat>(R.id.show_folder_apps_switch)
        val searchInput = view.findViewById<EditText>(R.id.picker_search_input)
        val recycler = view.findViewById<RecyclerView>(R.id.picker_recycler)
        saveBtn = view.findViewById(R.id.save_button)
        val closeBtn = view.findViewById<ImageView>(R.id.btn_close_picker)

        // Hide "show in other folders" switch
        (switchView?.parent as? View)?.visibility = View.GONE
        
        // Update Title
        view.findViewById<View>(R.id.folder_picker_glass_panel)?.let {
            val titleParent = (it as android.view.ViewGroup).getChildAt(1)
            if (titleParent is android.view.ViewGroup) {
                (titleParent.getChildAt(0) as? TextView)?.text = getString(R.string.action_add_to_home)
            }
        }

        closeBtn?.setOnClickListener { dismiss() }

        com.nexus.launcher.typography.NexusTypeScale.bodyStrong.bindTo(saveBtn)
        com.nexus.launcher.typography.NexusTypeScale.body.bindTo(searchInput)
        com.nexus.launcher.ui.folder.FolderEditTypographyHelper.bindSectionHeaders(view)

        adapter = InjectionAdapter()
        recycler.layoutManager = androidx.recyclerview.widget.GridLayoutManager(requireContext(), 5)
        recycler.adapter = adapter

        saveBtn.visibility = View.VISIBLE
        refreshSaveLabel()

        saveBtn.setOnClickListener {
            val baseTime2 = System.currentTimeMillis()
            val finalApps = allApps.filter { selectedIds.contains(it.id.toLong()) }
                .mapIndexed { index, app -> app.copy(id = (baseTime2 + index).toInt()) }
            onWorkspaceAppsSelected.invoke(finalApps)
            dismiss()
        }

        searchInput?.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString()?.trim()?.lowercase() ?: ""
                updateList()
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })

        loadApps()
    }

    private fun loadApps() {
        lifecycleScope.launch(Dispatchers.IO) {
            val pm = requireContext().packageManager
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN, null)
            intent.addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            val resolveInfos = pm.queryIntentActivities(intent, 0)
            val tempApps = mutableListOf<HomeScreenItem>()
            var idCounter = 1000000L
            resolveInfos.forEach { ri ->
                tempApps.add(
                    HomeScreenItem(
                        id = (idCounter++).toInt(),
                        packageName = ri.activityInfo.packageName,
                        page = 0, column = 0, row = 0, itemType = 0, containerId = -1L
                    )
                )
            }
            allApps = tempApps.sortedBy { labelFor(requireContext(), it).lowercase() }
            withContext(Dispatchers.Main) {
                updateList()
            }
        }
    }

    private fun updateList() {
        val filtered = if (searchQuery.isEmpty()) allApps else allApps.filter {
            labelFor(requireContext(), it).lowercase().contains(searchQuery)
        }
        adapter.submitList(filtered)
    }

    private fun labelFor(context: Context, item: HomeScreenItem): String {
        return try {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(item.packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            item.packageName
        }
    }

    private fun refreshSaveLabel() {
        val count = selectedIds.size
        saveBtn.text = if (count > 0) "Add $count app(s) to Home" else "Select Apps"
        saveBtn.isEnabled = count > 0
        saveBtn.alpha = if (count > 0) 1f else 0.5f
    }

    inner class InjectionAdapter : RecyclerView.Adapter<InjectionAdapter.ViewHolder>() {
        private var items: List<HomeScreenItem> = emptyList()
        fun submitList(newItems: List<HomeScreenItem>) {
            items = newItems
            notifyDataSetChanged()
        }
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_picker, parent, false)
            return ViewHolder(view)
        }
        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            val pm = requireContext().packageManager
            holder.text.text = labelFor(requireContext(), item)
            try {
                // Go through IconResolver so icon packs, per-app overrides and themed icons apply.
                val resolver = dagger.hilt.android.EntryPointAccessors.fromApplication(
                    requireContext().applicationContext,
                    com.nexus.launcher.ui.icons.IconResolverEntryPoint::class.java
                ).iconResolver()
                holder.icon.setImageDrawable(resolver.getIcon(item.packageName))
            } catch (_: Exception) {
                holder.icon.setImageDrawable(try { pm.getApplicationIcon(item.packageName) } catch (_: Exception) { null })
            }
            val isSelected = selectedIds.contains(item.id.toLong())
            holder.checkIndicator.visibility = if (isSelected) View.VISIBLE else View.GONE
            holder.itemView.setOnClickListener {
                if (isSelected) selectedIds.remove(item.id.toLong())
                else {
                    if (maxSelectable == -1 || selectedIds.size < maxSelectable) {
                        selectedIds.add(item.id.toLong())
                    } else return@setOnClickListener
                }
                notifyItemChanged(position)
                refreshSaveLabel()
            }
        }
        override fun getItemCount() = items.size
        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val text: TextView = view.findViewById<TextView>(R.id.picker_app_label).apply {
                com.nexus.launcher.typography.NexusTypeScale.caption.bindTo(this)
            }
            val icon: ImageView = view.findViewById(R.id.picker_app_icon)
            val checkIndicator: ImageView = view.findViewById(R.id.check_indicator)
        }
    }

    private fun applyNavBarInsets(view: View) {
        val baseBottom = (8 * resources.displayMetrics.density).toInt()
        androidx.core.view.ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val nav = insets.getInsets(androidx.core.view.WindowInsetsCompat.Type.navigationBars())
            v.setPadding(v.paddingLeft, v.paddingTop, v.paddingRight, baseBottom + nav.bottom)
            insets
        }
        androidx.core.view.ViewCompat.requestApplyInsets(view)
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        com.nexus.launcher.ui.folder.FolderSheetWallpaperDecor.removeWindowBlur(
            this.dialog as? BottomSheetDialog, requireContext())
        com.nexus.launcher.ui.folder.FolderAppPickerDecor.onDismiss(requireContext())
        super.onDismiss(dialog)
    }

    companion object {
        fun show(
            fragmentManager: androidx.fragment.app.FragmentManager,
            maxSelectable: Int = -1,
            onWorkspaceAppsSelected: ((List<HomeScreenItem>) -> Unit)
        ) {
            GridInjectionBottomSheet(maxSelectable, onWorkspaceAppsSelected).show(fragmentManager, "GridInjectionBottomSheet")
        }
    }
}
