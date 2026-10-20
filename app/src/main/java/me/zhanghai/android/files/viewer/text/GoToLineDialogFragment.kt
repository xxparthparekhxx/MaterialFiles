/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

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
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.show

class GoToLineDialogFragment : AppCompatDialogFragment() {
    private val listener: Listener
        get() = requireParentFragment() as Listener

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val edit = EditText(context).apply {
            setSingleLine()
            inputType = InputType.TYPE_CLASS_NUMBER
            imeOptions = EditorInfo.IME_ACTION_GO
            setHint(R.string.text_editor_go_to_line_hint)
            if (savedInstanceState == null) {
                selectAll()
            }
        }
        val padding = context.resources.getDimensionPixelSize(R.dimen.screen_edge_margin)
        val container = FrameLayout(context).apply {
            setPaddingRelative(padding, padding / 2, padding, 0)
            addView(edit)
        }
        val dialog = MaterialAlertDialogBuilder(context, theme)
            .setTitle(R.string.text_editor_go_to_line)
            .setView(container)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                edit.text.toString().toIntOrNull()?.let { listener.goToLine(it) }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        edit.setOnEditorActionListener { _, _, _ ->
            edit.text.toString().toIntOrNull()?.let { listener.goToLine(it) }
            dialog.dismiss()
            true
        }
        return dialog
    }

    companion object {
        fun show(fragment: Fragment) {
            GoToLineDialogFragment().putArgs(Args).show(fragment)
        }
    }

    @Parcelize
    private object Args : ParcelableArgs

    interface Listener {
        fun goToLine(line: Int)
    }
}
