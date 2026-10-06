/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.preference.Preference
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.defaultSharedPreferences
import me.zhanghai.android.files.theme.custom.CustomThemeHelper
import me.zhanghai.android.files.util.showToast
import java.io.IOException
import me.zhanghai.android.files.theme.custom.ThemeColor
import me.zhanghai.android.files.theme.night.NightMode
import me.zhanghai.android.files.theme.night.NightModeHelper
import me.zhanghai.android.files.ui.PreferenceFragmentCompat

class SettingsPreferenceFragment : PreferenceFragmentCompat() {
    private lateinit var localePreference: LocalePreference

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            exportSettings(uri)
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            importSettings(uri)
        }
    }

    override fun onCreatePreferencesFix(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.settings)

        localePreference = preferenceScreen.findPreference(getString(R.string.pref_key_locale))!!
        findPreference<Preference>(getString(R.string.pref_key_settings_backup_restore))!!
            .setOnPreferenceClickListener {
                showBackupRestoreDialog()
                true
            }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            localePreference.setApplicationLocalesPre33 = { locales ->
                val activity = requireActivity() as SettingsActivity
                activity.setApplicationLocalesPre33(locales)
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val viewLifecycleOwner = viewLifecycleOwner
        // The following may end up passing the same lambda instance to the observer because it has
        // no capture, and result in an IllegalArgumentException "Cannot add the same observer with
        // different lifecycles" if activity is finished and instantly started again. To work around
        // this, always use an instance method reference.
        // https://stackoverflow.com/a/27524543
        //Settings.THEME_COLOR.observe(viewLifecycleOwner) { CustomThemeHelper.sync() }
        //Settings.MATERIAL_DESIGN_3.observe(viewLifecycleOwner) { CustomThemeHelper.sync() }
        //Settings.NIGHT_MODE.observe(viewLifecycleOwner) { NightModeHelper.sync() }
        //Settings.BLACK_NIGHT_MODE.observe(viewLifecycleOwner) { CustomThemeHelper.sync() }
        Settings.THEME_COLOR.observe(viewLifecycleOwner, this::onThemeColorChanged)
        Settings.MATERIAL_DESIGN_3.observe(viewLifecycleOwner, this::onMaterialDesign3Changed)
        Settings.NIGHT_MODE.observe(viewLifecycleOwner, this::onNightModeChanged)
        Settings.BLACK_NIGHT_MODE.observe(viewLifecycleOwner, this::onBlackNightModeChanged)
    }

    private fun showBackupRestoreDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_backup_restore_title)
            .setItems(
                arrayOf(
                    getString(R.string.settings_backup),
                    getString(R.string.settings_restore)
                )
            ) { _, which ->
                if (which == 0) {
                    exportLauncher.launch(EXPORT_FILE_NAME)
                } else {
                    confirmRestore()
                }
            }
            .show()
    }

    private fun confirmRestore() {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.settings_restore_confirm)
            .setPositiveButton(R.string.settings_restore) { _, _ ->
                importLauncher.launch(arrayOf("application/json"))
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun exportSettings(uri: android.net.Uri) {
        val json = SettingsBackup.export(defaultSharedPreferences)
        viewLifecycleOwner.lifecycleScope.launch {
            val error = withContext(Dispatchers.IO) {
                try {
                    requireContext().contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(json.toByteArray())
                    } ?: throw IOException("Cannot open $uri for writing")
                    null
                } catch (e: Exception) {
                    e.printStackTrace()
                    e
                }
            }
            if (!isAdded) {
                return@launch
            }
            showToast(error?.toString() ?: getString(R.string.settings_backup_success))
        }
    }

    private fun importSettings(uri: android.net.Uri) {
        viewLifecycleOwner.lifecycleScope.launch {
            val error = withContext(Dispatchers.IO) {
                try {
                    val json = requireContext().contentResolver.openInputStream(uri)?.use { input ->
                        input.readBytes().toString(Charsets.UTF_8)
                    } ?: throw IOException("Cannot open $uri for reading")
                    withContext(Dispatchers.Main) {
                        SettingsBackup.import(json, defaultSharedPreferences)
                    }
                    null
                } catch (e: Exception) {
                    e.printStackTrace()
                    e
                }
            }
            if (!isAdded) {
                return@launch
            }
            if (error != null) {
                showToast(error.toString())
            } else {
                showToast(R.string.settings_restore_success)
            }
        }
    }

    private fun onThemeColorChanged(themeColor: ThemeColor) {
        CustomThemeHelper.sync()
    }

    private fun onMaterialDesign3Changed(isMaterialDesign3: Boolean) {
        CustomThemeHelper.sync()
    }

    private fun onNightModeChanged(nightMode: NightMode) {
        NightModeHelper.sync()
    }

    private fun onBlackNightModeChanged(blackNightMode: Boolean) {
        CustomThemeHelper.sync()
    }

    override fun onResume() {
        super.onResume()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Refresh locale preference summary because we aren't notified for an external change
            // between system default and the locale that's the current system default.
            localePreference.notifyChanged()
        }
    }

    companion object {
        private const val EXPORT_FILE_NAME = "material-files-settings.json"
    }
}
