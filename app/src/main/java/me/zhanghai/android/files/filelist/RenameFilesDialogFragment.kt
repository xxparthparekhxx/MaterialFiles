package me.zhanghai.android.files.filelist

import android.app.Dialog
import android.os.Bundle
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.asFileName
import me.zhanghai.android.files.util.asFileNameOrNull
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.show

class RenameFilesDialogFragment : NameDialogFragment() {
    private val args by args<Args>()

    override val listener: Listener
        get() = super.listener as Listener

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        binding.nameLayout.helperText = getString(R.string.file_rename_multiple_helper)
        return dialog
    }

    @StringRes
    override val titleRes: Int = R.string.file_rename_multiple_title

    override fun isNameValid(name: String): Boolean {
        if (!super.isNameValid(name)) {
            return false
        }
        if (name.isEmpty()) {
            binding.nameLayout.error = getString(R.string.file_name_error_empty)
            return false
        }
        val newNames = generateNames(args.files, name)
        if (newNames.any { it.second.asFileNameOrNull() == null }) {
            binding.nameLayout.error = getString(R.string.file_name_error_invalid)
            return false
        }
        // The new names must not collide with each other, or with files that are not renamed
        // here.
        if (newNames.map { it.second }.toSet().size != newNames.size ||
            newNames.any { (file, newName) ->
                newName != file.name && listener.hasFileWithName(newName)
            }) {
            binding.nameLayout.error = getString(R.string.file_name_error_already_exists)
            return false
        }
        return true
    }

    override fun onOk(name: String) {
        listener.renameFiles(generateNames(args.files, name))
    }

    companion object {
        fun show(files: FileItemSet, fragment: Fragment) {
            RenameFilesDialogFragment().putArgs(Args(files)).show(fragment)
        }

        // Files are numbered in name order. The "{n}" placeholder marks where the number goes,
        // and a " (n)" suffix is added when it is missing. File extensions are kept.
        fun generateNames(files: FileItemSet, pattern: String): List<Pair<FileItem, String>> {
            val sortedFiles = files.sortedWith(
                compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            )
            return sortedFiles.mapIndexed { index, file ->
                val number = (index + 1).toString()
                val baseName = if (PLACEHOLDER in pattern) {
                    pattern.replace(PLACEHOLDER, number)
                } else {
                    "$pattern ($number)"
                }
                val extension = if (file.attributesNoFollowLinks.isDirectory) {
                    ""
                } else {
                    val fileName = file.name
                    fileName.substring(fileName.asFileName().baseName.length)
                }
                file to baseName + extension
            }
        }

        private const val PLACEHOLDER = "{n}"
    }

    @Parcelize
    class Args(val files: FileItemSet) : ParcelableArgs

    interface Listener : NameDialogFragment.Listener {
        fun hasFileWithName(name: String): Boolean

        fun renameFiles(newNames: List<Pair<FileItem, String>>)
    }
}
