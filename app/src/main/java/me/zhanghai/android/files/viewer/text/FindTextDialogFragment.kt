package me.zhanghai.android.files.viewer.text

import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.show

class FindTextDialogFragment : AppCompatDialogFragment() {
    private val args by args<Args>()

    private val listener: Listener
        get() = requireParentFragment() as Listener

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val edit = EditText(context).apply {
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setHint(R.string.text_editor_find_hint)
            if (savedInstanceState == null) {
                setText(args.query)
                selectAll()
            }
        }
        val padding = context.resources.getDimensionPixelSize(R.dimen.screen_edge_margin)
        val container = FrameLayout(context).apply {
            setPaddingRelative(padding, padding / 2, padding, 0)
            addView(edit)
        }
        val dialog = MaterialAlertDialogBuilder(context, theme)
            .setTitle(R.string.text_editor_find)
            .setView(container)
            .setPositiveButton(R.string.text_editor_find_next_short) { _, _ ->
                listener.findText(edit.text.toString())
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        edit.setOnEditorActionListener { _, _, _ ->
            listener.findText(edit.text.toString())
            dialog.dismiss()
            true
        }
        return dialog
    }

    companion object {
        fun show(query: String, fragment: Fragment) {
            FindTextDialogFragment().putArgs(Args(query)).show(fragment)
        }
    }

    @Parcelize
    class Args(val query: String) : ParcelableArgs

    interface Listener {
        fun findText(query: String)
    }
}
