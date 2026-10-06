package me.zhanghai.android.files.util

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.ListView
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import me.zhanghai.android.files.R
import java.nio.charset.Charset

/**
 * Show a dialog for picking a charset, with a search field to filter the (long) list.
 */
fun Context.showCharsetPickerDialog(
    currentCharsetName: String?,
    onCharsetPicked: (Charset) -> Unit
): AlertDialog {
    val charsets = Charset.availableCharsets().values.toList()
    val view = layoutInflater.inflate(R.layout.charset_picker_dialog, null)
    val searchEdit = view.findViewById<TextInputEditText>(R.id.searchEdit)
    val listView = view.findViewById<ListView>(R.id.charsetList)
    var shownCharsets = charsets
    val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_single_choice, mutableListOf<String>())
    listView.adapter = adapter
    fun updateList() {
        val query = searchEdit.text?.toString()?.trim().orEmpty()
        shownCharsets = if (query.isEmpty()) {
            charsets
        } else {
            charsets.filter {
                it.displayName().contains(query, true) ||
                    it.aliases().any { alias -> alias.contains(query, true) }
            }
        }
        adapter.clear()
        adapter.addAll(shownCharsets.map { it.displayName() })
        val checkedIndex = shownCharsets.indexOfFirst { it.name() == currentCharsetName }
        if (checkedIndex != -1) {
            listView.setItemChecked(checkedIndex, true)
            listView.setSelection(checkedIndex)
        }
    }
    searchEdit.doAfterTextChanged { updateList() }
    updateList()
    val dialog = MaterialAlertDialogBuilder(this)
        .setView(view)
        .setNegativeButton(android.R.string.cancel, null)
        .create()
    listView.setOnItemClickListener { _, _, position, _ ->
        onCharsetPicked(shownCharsets[position])
        dialog.dismiss()
    }
    dialog.show()
    return dialog
}
