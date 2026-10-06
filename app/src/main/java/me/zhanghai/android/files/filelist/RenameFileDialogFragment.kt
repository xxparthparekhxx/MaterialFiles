/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.app.Dialog
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.setOnEditorConfirmActionListener
import me.zhanghai.android.files.util.show

class RenameFileDialogFragment : FileNameDialogFragment() {
    private val args by args<Args>()

    override val listener: Listener
        get() = super.listener as Listener

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        val extension = separatedExtension
        if (extension != null) {
            binding.extensionDot.isVisible = true
            binding.extensionLayout.isVisible = true
            binding.extensionEdit.setOnEditorConfirmActionListener { onOk() }
            if (savedInstanceState == null) {
                binding.extensionEdit.setText(extension)
            }
        }
        return dialog
    }

    private val separatedExtension: String?
        get() {
            if (args.file.attributes.isDirectory) {
                return null
            }
            val extension = args.file.extension
            if (extension.isEmpty() || args.file.baseName.isEmpty()) {
                return null
            }
            return extension
        }

    @StringRes
    override val titleRes: Int = R.string.rename

    override val initialName: String?
        get() = if (separatedExtension == null) args.file.name else args.file.baseName

    override val originalName: String
        get() = args.file.name

    override val name: String
        get() {
            val baseName = binding.nameEdit.text.toString().trim()
            val extension = separatedExtension ?: return baseName
            val editedExtension = binding.extensionEdit.text.toString().trim().trimStart('.')
            return if (editedExtension.isEmpty()) baseName else "$baseName.$editedExtension"
        }

    override fun onOk(name: String) {
        listener.renameFile(args.file, name)
    }

    companion object {
        fun show(file: FileItem, fragment: Fragment) {
            RenameFileDialogFragment().putArgs(Args(file)).show(fragment)
        }
    }

    @Parcelize
    class Args(val file: FileItem) : ParcelableArgs

    interface Listener : FileNameDialogFragment.Listener {
        fun renameFile(file: FileItem, newName: String)
    }
}
