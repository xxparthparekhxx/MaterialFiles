/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileaction

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.os.Parcelable
import android.util.SparseArray
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.widget.NestedScrollView
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.compat.requireViewByIdCompat
import me.zhanghai.android.files.databinding.PasswordDialogBinding
import me.zhanghai.android.files.filelist.FileItemSet
import me.zhanghai.android.files.ui.AllowSoftInputHackAlertDialogCustomView
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.ParcelableState
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.finish
import me.zhanghai.android.files.util.getState
import me.zhanghai.android.files.util.hideTextInputLayoutErrorOnTextChange
import me.zhanghai.android.files.util.layoutInflater
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.putState
import me.zhanghai.android.files.util.setOnEditorConfirmActionListener
import me.zhanghai.android.files.util.show

class FilePasswordDialogFragment : AppCompatDialogFragment() {
    private val args by args<Args>()

    private val listener: Listener
        get() = requireParentFragment() as Listener

    private lateinit var binding: PasswordDialogBinding

    private var isListenerNotified = false

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        val hierarchyState = SparseArray<Parcelable>()
            .apply { binding.root.saveHierarchyState(this) }
        outState.putState(State(hierarchyState))
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        return MaterialAlertDialogBuilder(context, theme)
            .setTitle(
                if (args.encrypt) {
                    R.string.file_action_encrypt
                } else {
                    R.string.file_action_decrypt
                }
            )
            .apply {
                binding = PasswordDialogBinding.inflate(context.layoutInflater)
                binding.passwordEdit.hideTextInputLayoutErrorOnTextChange(binding.passwordLayout)
                binding.passwordEdit.setOnEditorConfirmActionListener { onOk() }
                if (savedInstanceState != null) {
                    val state = savedInstanceState.getState<State>()
                    binding.root.restoreHierarchyState(state.hierarchyState)
                }
                setView(AllowSoftInputHackAlertDialogCustomView(context))
            }
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
            .create()
            .apply {
                setCanceledOnTouchOutside(false)
                // Override the listener here so that we have control over when to close the dialog.
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { onOk() }
                }
                window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
            }
    }

    override fun onStart() {
        super.onStart()

        val dialog = requireDialog() as AlertDialog
        if (binding.root.parent == null) {
            val scrollView = dialog.requireViewByIdCompat<NestedScrollView>(R.id.scrollView)
            val linearLayout = scrollView.getChildAt(0) as LinearLayout
            linearLayout.addView(binding.root)
            binding.passwordEdit.requestFocus()
        }
    }

    private fun onOk() {
        val password = binding.passwordEdit.text!!.toString()
        if (password.isEmpty()) {
            binding.passwordLayout.error = getString(R.string.file_action_password_error_empty)
            return
        }
        notifyListenerOnce(password)
        finish()
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)

        notifyListenerOnce(null)
        finish()
    }

    fun onFinish() {
        notifyListenerOnce(null)
    }

    private fun notifyListenerOnce(password: String?) {
        if (isListenerNotified) {
            return
        }
        if (password != null) {
            listener.providePassword(args.files, args.encrypt, password)
        }
        isListenerNotified = true
    }

    companion object {
        fun show(files: FileItemSet, encrypt: Boolean, fragment: Fragment) {
            FilePasswordDialogFragment().putArgs(Args(files, encrypt)).show(fragment)
        }
    }

    @Parcelize
    class Args(val files: FileItemSet, val encrypt: Boolean) : ParcelableArgs

    interface Listener {
        fun providePassword(files: FileItemSet, encrypt: Boolean, password: String)
    }

    @Parcelize
    private class State(
        val hierarchyState: SparseArray<Parcelable>
    ) : ParcelableState
}
