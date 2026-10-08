/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileproperties.image

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.ExifAttributesDialogBinding
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.layoutInflater
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.show

class ExifAttributesDialogFragment : AppCompatDialogFragment() {
    private val args by args<Args>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = ExifAttributesDialogBinding.inflate(layoutInflater)
        binding.recyclerView.adapter = ExifAttributesAdapter(args.attributes)
        return MaterialAlertDialogBuilder(requireContext(), theme)
            .setTitle(R.string.file_properties_image_all_exif_dialog_title)
            .setView(binding.root)
            .setPositiveButton(android.R.string.ok, null)
            .create()
    }

    companion object {
        fun show(attributes: Map<String, String>, fragment: Fragment) {
            ExifAttributesDialogFragment().putArgs(Args(attributes)).show(fragment)
        }
    }

    @Parcelize
    class Args(val attributes: Map<String, String>) : ParcelableArgs
}
