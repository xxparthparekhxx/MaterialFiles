/*
 * Copyright (c) 2018 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.filelist

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.compat.reversedCompat
import me.zhanghai.android.files.file.FileItem

@Parcelize
data class FileSortOptions(
    val by: By,
    val order: Order,
    val isDirectoriesFirst: Boolean
) : Parcelable {
    /**
     * @param folderBy if not null, folders are sorted by this instead of [by] when folders are
     *   listed first
     */
    fun createComparator(isHiddenFirst: Boolean = false, folderBy: By? = null): Comparator<FileItem> {
        val fileComparator = createComparatorBy(by, isHiddenFirst)
        if (!isDirectoriesFirst) {
            return fileComparator
        }
        val folderComparator = if (folderBy != null) createComparatorBy(folderBy, isHiddenFirst) else fileComparator
        return Comparator { first, second ->
            val isFirstDirectory = first.attributes.isDirectory
            val isSecondDirectory = second.attributes.isDirectory
            when {
                isFirstDirectory && !isSecondDirectory -> -1
                !isFirstDirectory && isSecondDirectory -> 1
                isFirstDirectory -> folderComparator.compare(first, second)
                else -> fileComparator.compare(first, second)
            }
        }
    }

    private fun createComparatorBy(by: By, isHiddenFirst: Boolean): Comparator<FileItem> {
        var comparator = compareBy<FileItem> {
            NAME_UNIMPORTANT_PREFIXES.any { prefix -> it.name.startsWith(prefix) }
        }.thenBy { it.nameCollationKey }
        when (by) {
            // Nothing to do.
            By.NAME -> {}
            By.TYPE ->
                comparator = compareBy<FileItem, String>(String.CASE_INSENSITIVE_ORDER) {
                    it.extension
                }.then(comparator)
            By.SIZE -> comparator = compareBy<FileItem> { it.attributes.size() }.then(comparator)
            By.LAST_MODIFIED ->
                comparator = compareBy<FileItem> { it.attributes.lastModifiedTime() }
                    .then(comparator)
        }
        when (order) {
            Order.ASCENDING -> {}
            Order.DESCENDING -> comparator = comparator.reversedCompat()
        }
        if (isHiddenFirst) {
            comparator = compareBy<FileItem> { !it.isHidden }.then(comparator)
        }
        return comparator
    }

    companion object {
        // Same behavior as Nautilus.
        private val NAME_UNIMPORTANT_PREFIXES = listOf(".", "#")
    }

    enum class By {
        NAME,
        TYPE,
        SIZE,
        LAST_MODIFIED
    }

    enum class Order {
        ASCENDING,
        DESCENDING
    }
}
