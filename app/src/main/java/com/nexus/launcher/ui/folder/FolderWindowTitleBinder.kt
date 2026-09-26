package com.nexus.launcher.ui.folder

import android.content.Context
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import com.nexus.launcher.data.HomeScreenItem
import com.nexus.launcher.di.DaoEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Inline folder title editing inside the open folder window. */
object FolderWindowTitleBinder {

    fun bind(
        titleEdit: EditText,
        folderItem: HomeScreenItem,
        onTitleChanged: (HomeScreenItem) -> Unit
    ) {
        val tokens = try {
            com.nexus.launcher.theme.ThemeObserver.currentTokens(titleEdit.context)
        } catch (_: Exception) {
            com.nexus.launcher.theme.NexusColorTokens.Dark
        }
        com.nexus.launcher.typography.NexusTypeScale.title.bindTo(titleEdit, tokens.textPrimary)
        titleEdit.setHintTextColor(tokens.textSecondary)
        titleEdit.setText(FolderContextMenuLauncher.folderDisplayName(titleEdit.context, folderItem))
        titleEdit.isFocusable = false
        titleEdit.isFocusableInTouchMode = false
        titleEdit.setCursorVisible(false)

        titleEdit.setOnClickListener {
            titleEdit.isFocusableInTouchMode = true
            titleEdit.isFocusable = true
            titleEdit.setCursorVisible(true)
            titleEdit.requestFocus()
            titleEdit.setSelection(titleEdit.text.length)
            val imm = titleEdit.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(titleEdit, InputMethodManager.SHOW_IMPLICIT)
        }
        titleEdit.setOnEditorActionListener { v, _, _ ->
            v.clearFocus()
            hideKeyboard(v as EditText)
            true
        }
        titleEdit.tag = folderItem
        titleEdit.addOnAttachStateChangeListener(object : android.view.View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: android.view.View) = Unit
            override fun onViewDetachedFromWindow(v: android.view.View) {
                saveIfNeeded(v as EditText, onTitleChanged)
            }
        })
    }

    fun saveIfNeeded(titleEdit: EditText, onTitleChanged: (HomeScreenItem) -> Unit) {
        val folderItem = titleEdit.tag as? HomeScreenItem ?: return
        val newTitle = titleEdit.text.toString().trim().ifBlank { "Folder" }
        if (newTitle == FolderContextMenuLauncher.folderDisplayName(folderItem) ||
            newTitle == FolderContextMenuLauncher.folderDisplayName(titleEdit.context, folderItem)
        ) return
        val updated = folderItem.copy(folderTitle = newTitle)
        titleEdit.tag = updated
        onTitleChanged(updated)
        val dao = EntryPointAccessors.fromApplication(
            titleEdit.context.applicationContext,
            DaoEntryPoint::class.java
        ).homeScreenDao()
        val config = FolderConfigCodec.parse(folderItem.folderConfigJson)
        CoroutineScope(Dispatchers.IO).launch {
            FolderActionEngine.saveFolderConfig(folderItem.id.toLong(), newTitle, config, dao)
        }
    }

    private fun hideKeyboard(edit: EditText) {
        val imm = edit.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(edit.windowToken, 0)
        edit.isFocusable = false
        edit.isFocusableInTouchMode = false
        edit.setCursorVisible(false)
    }
}
