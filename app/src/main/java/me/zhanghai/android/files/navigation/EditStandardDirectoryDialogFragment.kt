/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.navigation

import android.app.Dialog
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java8.nio.file.Path
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.EditStandardDirectoryDialogBinding
import me.zhanghai.android.files.filelist.FileListActivity
import me.zhanghai.android.files.filelist.toUserFriendlyString
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.ParcelableParceler
import me.zhanghai.android.files.util.ParcelableState
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.getState
import me.zhanghai.android.files.util.layoutInflater
import me.zhanghai.android.files.util.launchSafe
import me.zhanghai.android.files.util.putState
import me.zhanghai.android.files.util.valueCompat

class EditStandardDirectoryDialogFragment : AppCompatDialogFragment() {
    private val openPathLauncher =
        registerForActivityResult(FileListActivity.OpenDirectoryContract(), ::onOpenPathResult)

    private val args by args<Args>()

    private lateinit var path: Path
    private lateinit var customTitle: String
    private var isEnabled: Boolean = true
    // The custom path is kept apart from the path, because the path of the standard directory is
    // the custom one when there is one.
    private var customPath: String? = null

    private lateinit var binding: EditStandardDirectoryDialogBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val state = savedInstanceState?.getState<State>()
        val settings = args.standardDirectory.toSettings()
        path = state?.path ?: args.standardDirectory.targetPath
        customTitle = state?.customTitle ?: settings.customTitle.orEmpty()
        isEnabled = state?.isEnabled ?: args.standardDirectory.isEnabled
        customPath = state?.customPath ?: settings.customPath
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        MaterialAlertDialogBuilder(requireContext(), theme)
            .setTitle(R.string.navigation_edit_standard_directory_title)
            .apply {
                binding = EditStandardDirectoryDialogBinding.inflate(context.layoutInflater)
                if (savedInstanceState == null) {
                    binding.nameEdit.setText(customTitle)
                }
                binding.enableSwitch.isChecked = isEnabled
                updatePathText()
                binding.pathText.setOnClickListener { onEditPath() }
                setView(binding.root)
            }
            .setPositiveButton(android.R.string.ok) { _, _ -> save() }
            .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.cancel() }
            .setNeutralButton(R.string.reset) { _, _ -> reset() }
            .create()
            .apply {
                window!!.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
            }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putState(State(path, customTitle, isEnabled, customPath))
    }

    private fun onEditPath() {
        openPathLauncher.launchSafe(path, this)
    }

    private fun onOpenPathResult(result: Path?) {
        result ?: return
        path = result
        customPath = result.takeIf { it != args.standardDirectory.defaultPath }?.toString()
        updatePathText()
    }

    private fun updatePathText() {
        binding.pathText.setText(path.toUserFriendlyString())
    }

    private fun save() {
        val title = binding.nameEdit.text.toString()
        val newCustomTitle = title.takeIf { it.isNotEmpty() }
        val newCustomPath = customPath
        val newIsEnabled = binding.enableSwitch.isChecked
        val settingsList = Settings.STANDARD_DIRECTORY_SETTINGS.valueCompat.toMutableList()
        val index = settingsList.indexOfFirst { it.id == args.standardDirectory.key }
        if (index != -1) {
            settingsList[index] = settingsList[index].copy(
                customTitle = newCustomTitle, customPath = newCustomPath,
                isEnabled = newIsEnabled
            )
        } else {
            settingsList += StandardDirectorySettings(
                args.standardDirectory.key, newCustomTitle, newIsEnabled, newCustomPath
            )
        }
        Settings.STANDARD_DIRECTORY_SETTINGS.putValue(settingsList)
    }

    private fun reset() {
        // The reset button closes the dialog, so the settings are written right away.
        val defaultIsEnabled = defaultStandardDirectories
            .find { it.key == args.standardDirectory.key }?.isEnabled ?: false
        val settingsList = Settings.STANDARD_DIRECTORY_SETTINGS.valueCompat.toMutableList()
        val index = settingsList.indexOfFirst { it.id == args.standardDirectory.key }
        if (index != -1) {
            settingsList[index] = settingsList[index].copy(
                customTitle = null, customPath = null, isEnabled = defaultIsEnabled
            )
            Settings.STANDARD_DIRECTORY_SETTINGS.putValue(settingsList)
        }
    }

    @Parcelize
    class Args(val standardDirectory: StandardDirectory) : ParcelableArgs

    @Parcelize
    private class State(
        var path: @WriteWith<ParcelableParceler> Path,
        var customTitle: String,
        var isEnabled: Boolean,
        var customPath: String?
    ) : ParcelableState
}
