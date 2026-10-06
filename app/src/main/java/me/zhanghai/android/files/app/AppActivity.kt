/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.app

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.theme.custom.CustomThemeHelper
import me.zhanghai.android.files.theme.night.NightModeHelper

abstract class AppActivity : AppCompatActivity() {
    private var isDelegateCreated = false

    override fun getDelegate(): AppCompatDelegate {
        val delegate = super.getDelegate()

        if (!isDelegateCreated) {
            isDelegateCreated = true
            NightModeHelper.apply(this)
        }
        return delegate
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        CustomThemeHelper.apply(this)

        super.onCreate(savedInstanceState)

        Settings.BLOCK_SCREENSHOTS.observe(this) {
            if (it) {
                window.setFlags(
                    WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE
                )
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        if (!super.onSupportNavigateUp()) {
            finish()
        }
        return true
    }
}
