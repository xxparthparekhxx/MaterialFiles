package me.zhanghai.android.files.filelist

import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.show

class CreateLinkDialogFragment : FileNameDialogFragment() {
    private val args by args<Args>()

    override val listener: Listener
        get() = super.listener as Listener

    @StringRes
    override val titleRes: Int = R.string.file_create_link_title

    override val initialName: String?
        get() = getString(R.string.file_create_link_name_format, args.target.name)

    override fun onOk(name: String) {
        listener.createLink(args.target, name)
    }

    companion object {
        fun show(target: FileItem, fragment: Fragment) {
            CreateLinkDialogFragment().putArgs(Args(target)).show(fragment)
        }
    }

    @Parcelize
    class Args(val target: FileItem) : ParcelableArgs

    interface Listener : FileNameDialogFragment.Listener {
        fun createLink(target: FileItem, name: String)
    }
}
