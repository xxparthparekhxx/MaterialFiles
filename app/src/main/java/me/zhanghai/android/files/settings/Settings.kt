/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.settings

import android.os.Environment
import java8.nio.file.Path
import java8.nio.file.Paths
import me.zhanghai.android.files.R
import me.zhanghai.android.files.app.application
import me.zhanghai.android.files.compat.EnvironmentCompat2
import me.zhanghai.android.files.filelist.FileListDensity
import me.zhanghai.android.files.filelist.FileNameEllipsize
import me.zhanghai.android.files.filelist.FileSortOptions
import me.zhanghai.android.files.filelist.FileViewType
import me.zhanghai.android.files.filelist.OpenApkDefaultAction
import me.zhanghai.android.files.filelist.RecentFile
import me.zhanghai.android.files.navigation.BookmarkDirectory
import me.zhanghai.android.files.navigation.StandardDirectorySettings
import me.zhanghai.android.files.provider.root.RootStrategy
import me.zhanghai.android.files.storage.FileSystemRoot
import me.zhanghai.android.files.storage.PrimaryStorageVolume
import me.zhanghai.android.files.storage.SftpSocksProxy
import me.zhanghai.android.files.storage.Storage
import me.zhanghai.android.files.theme.custom.ThemeColor
import me.zhanghai.android.files.theme.night.NightMode
import java.io.File

object Settings {
    val STORAGES: SettingLiveData<List<Storage>> =
        ParcelValueSettingLiveData(
            R.string.pref_key_storages,
            listOf(FileSystemRoot(null, true), PrimaryStorageVolume(null, true))
        )

    val SFTP_SOCKS_PROXIES: SettingLiveData<List<SftpSocksProxy>> =
        ParcelValueSettingLiveData(R.string.pref_key_sftp_socks_proxies, emptyList())

    val FILE_LIST_DEFAULT_DIRECTORY: SettingLiveData<Path> =
        ParcelValueSettingLiveData(
            R.string.pref_key_file_list_default_directory,
            @Suppress("DEPRECATION")
            Paths.get(Environment.getExternalStorageDirectory().absolutePath)
        )

    val FILE_LIST_REMEMBER_LAST_DIRECTORY: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_file_list_remember_last_directory,
            R.bool.pref_default_value_file_list_remember_last_directory
        )

    val FILE_LIST_LAST_DIRECTORY: SettingLiveData<Path> =
        ParcelValueSettingLiveData(
            R.string.pref_key_file_list_last_directory,
            @Suppress("DEPRECATION")
            Paths.get(Environment.getExternalStorageDirectory().absolutePath)
        )

    val FILE_LIST_PERSISTENT_DRAWER_OPEN: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_file_list_persistent_drawer_open,
            R.bool.pref_default_value_file_list_persistent_drawer_open
        )

    val FILE_LIST_SHOW_HIDDEN_FILES: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_file_list_show_hidden_files,
            R.bool.pref_default_value_file_list_show_hidden_files
        )

    val FILE_LIST_HIDDEN_PATHS: SettingLiveData<Set<String>> =
        StringSetSettingLiveData(
            R.string.pref_key_file_list_hidden_paths,
            R.array.pref_default_value_file_list_hidden_paths
        )

    val FILE_LIST_VIEW_TYPE: SettingLiveData<FileViewType> =
        EnumSettingLiveData(
            R.string.pref_key_file_list_view_type, R.string.pref_default_value_file_list_view_type,
            FileViewType::class.java
        )

    val FILE_LIST_DENSITY: SettingLiveData<FileListDensity> =
        EnumSettingLiveData(
            R.string.pref_key_file_list_density, R.string.pref_default_value_file_list_density,
            FileListDensity::class.java
        )

    val FILE_LIST_LOCK_HEADER: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_file_list_lock_header,
            R.bool.pref_default_value_file_list_lock_header
        )

    val FILE_LIST_GRID_SPAN_COUNT: SettingLiveData<String> =
        StringSettingLiveData(
            R.string.pref_key_file_list_grid_span_count,
            R.string.pref_default_value_file_list_grid_span_count
        )

    val FILE_LIST_DOUBLE_BACK_TO_EXIT: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_file_list_double_back_to_exit,
            R.bool.pref_default_value_file_list_double_back_to_exit
        )

    val FILE_LIST_DRAWER_SWIPE: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_file_list_drawer_swipe,
            R.bool.pref_default_value_file_list_drawer_swipe
        )
    val FILE_LIST_PINNED_PATHS: SettingLiveData<List<String>> =
        ParcelValueSettingLiveData(R.string.pref_key_file_list_pinned_paths, emptyList())

    val TEXT_EDITOR_MONOSPACE: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_text_editor_monospace,
            R.bool.pref_default_value_text_editor_monospace
        )

    val TEXT_EDITOR_FONT_SIZE: SettingLiveData<String> =
        StringSettingLiveData(
            R.string.pref_key_text_editor_font_size,
            R.string.pref_default_value_text_editor_font_size
        )

    val FILE_LIST_FOLDER_SORT_BY: SettingLiveData<String> =
        StringSettingLiveData(
            R.string.pref_key_file_list_folder_sort_by,
            R.string.pref_default_value_file_list_folder_sort_by
        )

    val FILE_LIST_SORT_OPTIONS: SettingLiveData<FileSortOptions> =
        ParcelValueSettingLiveData(
            R.string.pref_key_file_list_sort_options,
            FileSortOptions(FileSortOptions.By.NAME, FileSortOptions.Order.ASCENDING, true)
        )

    val DELETE_ARCHIVE_AFTER_EXTRACT: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_delete_archive_after_extract,
            R.bool.pref_default_value_delete_archive_after_extract
        )

    val CREATE_ARCHIVE_TYPE: SettingLiveData<Int> =
        ResourceIdSettingLiveData(R.string.pref_key_create_archive_type, R.id.zipRadio)

    val NAVIGATION_SHOW_FTP_SERVER: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_navigation_show_ftp_server,
            R.bool.pref_default_value_navigation_show_ftp_server
        )

    val NAVIGATION_SHOW_RECENT_FILES: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_navigation_show_recent_files,
            R.bool.pref_default_value_navigation_show_recent_files
        )

    val FTP_SERVER_TILE_CONFIRM_START: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_ftp_server_tile_confirm_start,
            R.bool.pref_default_value_ftp_server_tile_confirm_start
        )

    val STORAGE_REVEAL_SAVED_PASSWORD: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_storage_reveal_saved_password,
            R.bool.pref_default_value_storage_reveal_saved_password
        )

    val FTP_SERVER_ANONYMOUS_LOGIN: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_ftp_server_anonymous_login,
            R.bool.pref_default_value_ftp_server_anonymous_login
        )

    val FTP_SERVER_USERNAME: SettingLiveData<String> =
        StringSettingLiveData(
            R.string.pref_key_ftp_server_username, R.string.pref_default_value_ftp_server_username
        )

    val FTP_SERVER_PASSWORD: SettingLiveData<String> =
        StringSettingLiveData(
            R.string.pref_key_ftp_server_password, R.string.pref_default_value_empty
        )

    val FTP_SERVER_PORT: SettingLiveData<Int> =
        IntegerSettingLiveData(
            R.string.pref_key_ftp_server_port, R.integer.pref_default_value_ftp_server_port
        )

    val FTP_SERVER_HOME_DIRECTORY: SettingLiveData<Path> =
        ParcelValueSettingLiveData(
            R.string.pref_key_ftp_server_home_directory,
            @Suppress("DEPRECATION")
            Paths.get(Environment.getExternalStorageDirectory().absolutePath)
        )

    val FTP_SERVER_ALLOW_EXTERNAL_CONTROL: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_ftp_server_allow_external_control,
            R.bool.pref_default_value_ftp_server_allow_external_control
        )

    val FTP_SERVER_WRITABLE: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_ftp_server_writable, R.bool.pref_default_value_ftp_server_writable
        )

    val FTP_SERVER_EXPOSE_ALL_STORAGES: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_ftp_server_expose_all_storages,
            R.bool.pref_default_value_ftp_server_expose_all_storages
        )

    val THEME_COLOR: SettingLiveData<ThemeColor> =
        EnumSettingLiveData(
            R.string.pref_key_theme_color, R.string.pref_default_value_theme_color,
            ThemeColor::class.java
        )

    val MATERIAL_DESIGN_3: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_material_design_3, R.bool.pref_default_value_material_design_3
        )

    val NIGHT_MODE: SettingLiveData<NightMode> =
        EnumSettingLiveData(
            R.string.pref_key_night_mode, R.string.pref_default_value_night_mode,
            NightMode::class.java
        )

    val BLACK_NIGHT_MODE: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_black_night_mode, R.bool.pref_default_value_black_night_mode
        )

    val FILE_LIST_ANIMATION: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_file_list_animation, R.bool.pref_default_value_file_list_animation
        )

    val TEXT_EDITOR_WORD_WRAP: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_text_editor_word_wrap, R.bool.pref_default_value_text_editor_word_wrap
        )

    val ERRORS_IN_DIALOG: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_errors_in_dialog, R.bool.pref_default_value_errors_in_dialog
        )

    val FILE_LIST_LOADING_INDICATOR: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_file_list_loading_indicator,
            R.bool.pref_default_value_file_list_loading_indicator
        )

    val OPEN_FILES_IN_NEW_TASK: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_open_files_in_new_task, R.bool.pref_default_value_open_files_in_new_task
        )

    val ISO_DATE_FORMAT: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_iso_date_format, R.bool.pref_default_value_iso_date_format
        )

    val FILE_LIST_HIDDEN_FIRST: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_file_list_hidden_first, R.bool.pref_default_value_file_list_hidden_first
        )

    val FILE_LIST_SEARCH_IN_SUBFOLDERS: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_file_list_search_in_subfolders,
            R.bool.pref_default_value_file_list_search_in_subfolders
        )

    val AUTO_CALCULATE_CHECKSUMS: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_auto_calculate_checksums,
            R.bool.pref_default_value_auto_calculate_checksums
        )

    val FILE_LIST_FIT_THUMBNAILS: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_file_list_fit_thumbnails,
            R.bool.pref_default_value_file_list_fit_thumbnails
        )

    val FILE_LIST_BACK_EXITS: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_file_list_back_exits, R.bool.pref_default_value_file_list_back_exits
        )

    val COPY_PATH_SHELL_ESCAPE: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_copy_path_shell_escape, R.bool.pref_default_value_copy_path_shell_escape
        )

    val OPEN_WITH_BUILT_IN_VIEWERS: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_open_with_built_in_viewers,
            R.bool.pref_default_value_open_with_built_in_viewers
        )

    val HIGHLIGHT_DELETE: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_highlight_delete, R.bool.pref_default_value_highlight_delete
        )

    val BLOCK_SCREENSHOTS: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_block_screenshots, R.bool.pref_default_value_block_screenshots
        )

    val COPY_PRESERVE_MODIFICATION_TIME: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_copy_preserve_modification_time,
            R.bool.pref_default_value_copy_preserve_modification_time
        )

    val FILE_LIST_SHOW_PERMISSIONS: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_file_list_show_permissions,
            R.bool.pref_default_value_file_list_show_permissions
        )

    val FULL_DATE_TIME: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_full_date_time, R.bool.pref_default_value_full_date_time
        )

    val FILE_LIST_HIDE_ADD_BUTTON: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_file_list_hide_add_button,
            R.bool.pref_default_value_file_list_hide_add_button
        )

    val FILE_LIST_DIVIDERS: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_file_list_dividers, R.bool.pref_default_value_file_list_dividers
        )

    val FILE_LIST_SHOW_ITEM_COUNT: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_file_list_show_item_count,
            R.bool.pref_default_value_file_list_show_item_count
        )

    val FILE_LIST_SHOW_DIRECTORY_ITEM_COUNT: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_file_list_show_directory_item_count,
            R.bool.pref_default_value_file_list_show_directory_item_count
        )

    val FILE_LIST_STANDARD_DIRECTORY_ICONS: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_file_list_standard_directory_icons,
            R.bool.pref_default_value_file_list_standard_directory_icons
        )

    val FILE_LIST_INDICATE_LAST_OPENED_ITEM: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_file_list_indicate_last_opened_item,
            R.bool.pref_default_value_file_list_indicate_last_opened_item
        )

    val BINARY_FILE_SIZE_UNIT: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_binary_file_size_unit,
            R.bool.pref_default_value_binary_file_size_unit
        )

    val FILE_LIST_FONT_SIZE: SettingLiveData<String> =
        StringSettingLiveData(
            R.string.pref_key_file_list_font_size, R.string.pref_default_value_file_list_font_size
        )

    val FILE_NAME_ELLIPSIZE: SettingLiveData<FileNameEllipsize> =
        EnumSettingLiveData(
            R.string.pref_key_file_name_ellipsize, R.string.pref_default_value_file_name_ellipsize,
            FileNameEllipsize::class.java
        )

    val STANDARD_DIRECTORY_SETTINGS: SettingLiveData<List<StandardDirectorySettings>> =
        ParcelValueSettingLiveData(R.string.pref_key_standard_directory_settings, emptyList())

    val RECENT_FILES: SettingLiveData<List<RecentFile>> =
        ParcelValueSettingLiveData(R.string.pref_key_recent_files, emptyList())

    val BOOKMARK_DIRECTORIES: SettingLiveData<List<BookmarkDirectory>> =
        ParcelValueSettingLiveData(
            R.string.pref_key_bookmark_directories, listOf(
                BookmarkDirectory(
                    application.getString(R.string.settings_bookmark_directory_screenshots),
                    Paths.get(
                        File(
                            @Suppress("DEPRECATION")
                            Environment.getExternalStoragePublicDirectory(
                                Environment.DIRECTORY_PICTURES
                            ), EnvironmentCompat2.DIRECTORY_SCREENSHOTS
                        ).absolutePath
                    )
                )
            )
        )

    val ROOT_STRATEGY: SettingLiveData<RootStrategy> =
        EnumSettingLiveData(
            R.string.pref_key_root_strategy, R.string.pref_default_value_root_strategy,
            RootStrategy::class.java
        )

    val ARCHIVE_FILE_NAME_ENCODING: SettingLiveData<String> =
        StringSettingLiveData(
            R.string.pref_key_archive_file_name_encoding,
            R.string.pref_default_value_archive_file_name_encoding
        )

    val OPEN_APK_DEFAULT_ACTION: SettingLiveData<OpenApkDefaultAction> =
        EnumSettingLiveData(
            R.string.pref_key_open_apk_default_action,
            R.string.pref_default_value_open_apk_default_action,
            OpenApkDefaultAction::class.java
        )

    val SHOW_THUMBNAILS: SettingLiveData<Boolean> = BooleanSettingLiveData(
        R.string.pref_key_show_thumbnails, R.bool.pref_default_value_show_thumbnails
    )
    val FILE_OPEN_DEFAULTS: SettingLiveData<Set<String>> =
        StringSetSettingLiveData(
            R.string.pref_key_file_open_defaults,
            R.array.pref_default_value_file_open_defaults
        )

    val SHOW_PDF_THUMBNAIL: SettingLiveData<Boolean> = BooleanSettingLiveData(
        R.string.pref_key_show_pdf_thumbnail,
        R.bool.pref_default_value_show_pdf_thumbnail
    )

    val SHOW_PDF_THUMBNAIL_PRE_28: SettingLiveData<Boolean> = BooleanSettingLiveData(
        R.string.pref_key_show_pdf_thumbnail_pre_28,
        R.bool.pref_default_value_show_pdf_thumbnail_pre_28
    )

    val READ_REMOTE_FILES_FOR_THUMBNAIL: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_read_remote_files_for_thumbnail,
            R.bool.pref_default_value_read_remote_files_for_thumbnail
        )

    val NOTIFICATION_PERMISSION_DISMISSED: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_notification_permission_dismissed,
            R.bool.pref_default_value_notification_permission_dismissed
        )

    val READ_ONLY_MODE: SettingLiveData<Boolean> =
        BooleanSettingLiveData(
            R.string.pref_key_read_only_mode, R.bool.pref_default_value_read_only_mode
        )
}
