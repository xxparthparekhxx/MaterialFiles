/*
 * Copyright (c) 2021 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.sftp.client

import java.security.Security
import org.bouncycastle.jce.provider.BouncyCastleProvider

// @see https://android-developers.googleblog.com/2018/03/cryptography-changes-in-android-p.html
// @see net.schmizz.sshj.common.SecurityUtils
// @see net.schmizz.sshj.DefaultConfig.DefaultConfig
// SSHJ requires BouncyCastle to be registered before enabling most functionality by default, so we
// better keep BouncyCastle registered.
object SecurityProviderHelper {
    @Volatile
    private var initialized = false

    fun init() {
        if (initialized) {
            return
        }
        synchronized(this) {
            if (initialized) {
                return
            }
            // Constructing the provider runs a huge static initializer. Doing that during app
            // startup puts it in the ART profile, and Android 8 then aborts on the next launch
            // ("Could not find an inlined method" inside BouncyCastleProvider.loadAlgorithms).
            // Callers must invoke this before the first SSHJ use, not from a provider <init>.
            val bouncyCastleProvider = BouncyCastleProvider()
            Security.removeProvider(bouncyCastleProvider.name)
            Security.addProvider(bouncyCastleProvider)
            initialized = true
        }
    }
}
