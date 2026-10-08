/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.text

enum class LineEnding(val separator: String, val displayName: String) {
    LF("\n", "LF"),
    CRLF("\r\n", "CRLF"),
    CR("\r", "CR");

    companion object {
        fun detect(text: String): LineEnding {
            val crlfCount = countOccurrences(text, "\r\n")
            val lfCount = countOccurrences(text, "\n") - crlfCount
            val crCount = countOccurrences(text, "\r") - crlfCount
            return when {
                crlfCount >= lfCount && crlfCount >= crCount && crlfCount > 0 -> CRLF
                crCount > lfCount -> CR
                else -> LF
            }
        }

        private fun countOccurrences(text: String, substring: String): Int {
            var count = 0
            var index = text.indexOf(substring)
            while (index >= 0) {
                count++
                index = text.indexOf(substring, index + substring.length)
            }
            return count
        }
    }
}
