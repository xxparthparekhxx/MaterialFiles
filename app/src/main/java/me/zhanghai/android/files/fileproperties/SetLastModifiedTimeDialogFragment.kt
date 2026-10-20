/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileproperties

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.SetLastModifiedTimeDialogBinding
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.show
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Calendar

class SetLastModifiedTimeDialogFragment : AppCompatDialogFragment() {
    private val args by args<Args>()
    private lateinit var binding: SetLastModifiedTimeDialogBinding

    private val listener: Listener
        get() = requireParentFragment() as Listener

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = SetLastModifiedTimeDialogBinding.inflate(layoutInflater)
        val datePicker = binding.datePicker
        val timePicker = binding.timePicker
        val calendar = Calendar.getInstance().apply {
            timeInMillis = args.initialEpochMillis
        }
        datePicker.init(
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH),
            null
        )
        timePicker.setHour(calendar.get(Calendar.HOUR_OF_DAY))
        timePicker.setMinute(calendar.get(Calendar.MINUTE))
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.file_properties_basic_set_last_modified_time_title)
            .setView(binding.root)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
            .apply {
                setOnShowListener {
                    getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                        val zone = ZoneId.systemDefault()
                        val instant = LocalDateTime.of(
                            datePicker.year,
                            datePicker.month + 1,
                            datePicker.dayOfMonth,
                            timePicker.hour,
                            timePicker.minute
                        ).atZone(zone).toInstant()
                        listener.onTimeSet(instant)
                        dismiss()
                    }
                }
            }
        return dialog
    }

    companion object {
        fun show(fragment: Fragment, initial: Instant) {
            SetLastModifiedTimeDialogFragment()
                .putArgs(Args(initial.toEpochMilli()))
                .show(fragment)
        }
    }

    @Parcelize
    class Args(val initialEpochMillis: Long) : ParcelableArgs

    interface Listener {
        fun onTimeSet(instant: Instant)
    }
}
