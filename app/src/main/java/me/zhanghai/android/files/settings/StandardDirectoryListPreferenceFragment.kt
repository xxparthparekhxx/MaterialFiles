/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import android.os.Bundle
import androidx.preference.Preference
import me.zhanghai.android.files.compat.getDrawableCompat
import me.zhanghai.android.files.compat.setTintCompat
import me.zhanghai.android.files.filelist.toUserFriendlyString
import me.zhanghai.android.files.navigation.EditStandardDirectoryDialogFragment
import me.zhanghai.android.files.navigation.StandardDirectoriesLiveData
import me.zhanghai.android.files.navigation.StandardDirectory
import me.zhanghai.android.files.ui.PreferenceFragmentCompat
import me.zhanghai.android.files.util.getColorByAttr
import me.zhanghai.android.files.util.putArgs
import me.zhanghai.android.files.util.show
import me.zhanghai.android.files.util.valueCompat

class StandardDirectoryListPreferenceFragment : PreferenceFragmentCompat(),
    Preference.OnPreferenceClickListener {
    override fun onCreatePreferencesFix(
        savedInstanceState: Bundle?,
        rootKey: String?
    ) {}

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        StandardDirectoriesLiveData.observe(viewLifecycleOwner) { onStandardDirectoriesChanged(it) }
    }

    private fun onStandardDirectoriesChanged(
        standardDirectories: List<StandardDirectory>
    ) {
        var preferenceScreen = preferenceScreen
        val context = requireContext()
        if (preferenceScreen == null) {
            preferenceScreen = preferenceManager.createPreferenceScreen(context)
            setPreferenceScreen(preferenceScreen)
        } else {
            for (index in preferenceScreen.preferenceCount - 1 downTo 0) {
                preferenceScreen.removePreference(preferenceScreen.getPreference(index))
            }
        }
        val secondaryTextColor = context.getColorByAttr(android.R.attr.textColorSecondary)
        for (standardDirectory in standardDirectories) {
            val preference = Preference(context).apply {
                key = standardDirectory.key
                icon = context.getDrawableCompat(standardDirectory.iconRes).apply {
                    mutate()
                    setTintCompat(secondaryTextColor)
                }
                title = standardDirectory.getTitle(context)
                summary = standardDirectory.targetPath.toUserFriendlyString()
                isPersistent = false
                onPreferenceClickListener = this@StandardDirectoryListPreferenceFragment
            }
            preferenceScreen.addPreference(preference)
        }
    }

    override fun onPreferenceClick(preference: Preference): Boolean {
        val standardDirectory = StandardDirectoriesLiveData.valueCompat
            .find { it.key == preference.key }!!
        EditStandardDirectoryDialogFragment()
            .putArgs(EditStandardDirectoryDialogFragment.Args(standardDirectory))
            .show(this)
        return true
    }
}
