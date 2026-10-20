/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

/**
 * The top bar of the file list, which shows the current location.
 *
 * [PATH_BAR] shows the path bar with the trail of the current directory, and the navigation bar
 * shows the name of the app. [DIRECTORY_NAME] hides the path bar and shows the name of the current
 * directory in the navigation bar instead.
 */
enum class FileListTopBar {
    PATH_BAR,
    DIRECTORY_NAME
}
