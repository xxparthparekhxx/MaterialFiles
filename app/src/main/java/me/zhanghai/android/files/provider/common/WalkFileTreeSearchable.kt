/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.provider.common

import java8.nio.file.DirectoryIteratorException
import java8.nio.file.FileVisitOption
import java8.nio.file.FileVisitResult
import java8.nio.file.FileVisitor
import java8.nio.file.Files
import java8.nio.file.LinkOption
import java8.nio.file.Path
import java8.nio.file.attribute.BasicFileAttributes
import me.zhanghai.android.files.R
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.settings.getBooleanSetting
import me.zhanghai.android.files.util.isBinary
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.regex.PatternSyntaxException

object WalkFileTreeSearchable {
    private val showHiddenFiles: Boolean
        get() = getBooleanSetting(
            R.string.pref_key_file_list_show_hidden_files,
            R.bool.pref_default_value_file_list_show_hidden_files, false,
            Settings.FILE_LIST_SHOW_HIDDEN_FILES
        )

    // Searching a folder with a lot of subfolders is slow, so this can be turned off to only
    // search the folder itself.
    private val searchInSubfolders: Boolean
        get() = getBooleanSetting(
            R.string.pref_key_file_list_search_in_subfolders,
            R.bool.pref_default_value_file_list_search_in_subfolders, true,
            Settings.FILE_LIST_SEARCH_IN_SUBFOLDERS
        )

    private val searchFileContents: Boolean
        get() = getBooleanSetting(
            R.string.pref_key_file_list_search_file_contents,
            R.bool.pref_default_value_file_list_search_file_contents, false,
            Settings.FILE_LIST_SEARCH_FILE_CONTENTS
        )

    @Throws(IOException::class)
    fun search(
        directory: Path,
        query: String,
        intervalMillis: Long,
        listener: (List<Path>) -> Unit
    ) {
        val paths = mutableListOf<Path>()
        val matches = createNameMatcher(query)
        val searchContents = searchFileContents
        val matchesContents = if (searchContents) createContentMatcher(query) else null
        // We cannot use Files.find() or Files.walk() because it cannot ignore exceptions.
        walkFileTreeForSearch(directory, object : FileVisitor<Path> {
            private var lastProgressMillis = System.currentTimeMillis()

            @Throws(InterruptedIOException::class)
            override fun preVisitDirectory(
                directory: Path,
                attributes: BasicFileAttributes
            ): FileVisitResult {
                visit(directory, attributes)
                throwIfInterrupted()
                return FileVisitResult.CONTINUE
            }

            @Throws(InterruptedIOException::class)
            override fun visitFile(file: Path, attributes: BasicFileAttributes): FileVisitResult {
                visit(file, attributes)
                throwIfInterrupted()
                return FileVisitResult.CONTINUE
            }

            @Throws(InterruptedIOException::class)
            override fun visitFileFailed(file: Path, exception: IOException): FileVisitResult {
                if (exception is InterruptedIOException) {
                    throw exception
                }
                exception.printStackTrace()
                visit(file, null)
                throwIfInterrupted()
                return FileVisitResult.CONTINUE
            }

            @Throws(InterruptedIOException::class)
            override fun postVisitDirectory(
                directory: Path,
                exception: IOException?
            ): FileVisitResult {
                if (exception is InterruptedIOException) {
                    throw exception
                }
                exception?.printStackTrace()
                throwIfInterrupted()
                return FileVisitResult.CONTINUE
            }

            private fun visit(path: Path, attributes: BasicFileAttributes?) {
                // Exclude the directory being searched.
                if (path == directory) {
                    return
                }
                val fileName = path.fileName
                val matchesName = fileName != null && matches(fileName.toString())
                val matchesContent = !matchesName && matchesContents != null &&
                    attributes != null && attributes.isRegularFile &&
                    attributes.size() <= MAX_CONTENT_SEARCH_FILE_SIZE.toLong() &&
                    matchesContents(path, matchesContents)
                if (matchesName || matchesContent) {
                    paths.add(path)
                }
                if (paths.isNotEmpty()) {
                    val currentTimeMillis = System.currentTimeMillis()
                    if (currentTimeMillis >= lastProgressMillis + intervalMillis) {
                        listener(paths)
                        lastProgressMillis = currentTimeMillis
                        paths.clear()
                    }
                }
            }
        })
        if (paths.isNotEmpty()) {
            listener(paths)
        }
    }

    /**
     * Plain text matches names containing it, ignoring case. A query containing `*` or `?` is a
     * wildcard pattern matched against the whole name, and a query starting with `re:` is a
     * case-insensitive regular expression searched within the name.
     */
    private fun createNameMatcher(query: String): (String) -> Boolean {
        if (query.startsWith(REGEX_PREFIX) && query.length > REGEX_PREFIX.length) {
            try {
                val regex = Regex(query.substring(REGEX_PREFIX.length), RegexOption.IGNORE_CASE)
                return { regex.containsMatchIn(it) }
            } catch (e: PatternSyntaxException) {
                // Fall through to a plain text search for an incomplete expression.
            }
        } else if (query.any { it == '*' || it == '?' }) {
            val pattern = StringBuilder()
            for (char in query) {
                when (char) {
                    '*' -> pattern.append(".*")
                    '?' -> pattern.append('.')
                    else -> pattern.append(Regex.escape(char.toString()))
                }
            }
            val regex = Regex(pattern.toString(), RegexOption.IGNORE_CASE)
            return { regex.matches(it) }
        }
        return { it.contains(query, true) }
    }

    private fun createContentMatcher(query: String): ((String) -> Boolean)? {
        if (query.startsWith(REGEX_PREFIX) && query.length > REGEX_PREFIX.length) {
            return try {
                val regex = Regex(query.substring(REGEX_PREFIX.length), RegexOption.IGNORE_CASE)
                val matcher = { text: String -> regex.containsMatchIn(text) }
                matcher
            } catch (e: PatternSyntaxException) {
                null
            }
        }
        if (query.any { it == '*' || it == '?' }) {
            return null
        }
        return { it.contains(query, true) }
    }

    private fun matchesContents(path: Path, matcher: (String) -> Boolean): Boolean =
        try {
            val bytes = path.readAtMost(MAX_CONTENT_SEARCH_FILE_SIZE)
            if (bytes.isBinary()) {
                false
            } else {
                matcher(bytes.decodeText())
            }
        } catch (e: InterruptedIOException) {
            throw e
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }

    private fun Path.readAtMost(maxSize: Int): ByteArray =
        newInputStream().use { inputStream ->
            val outputStream = ByteArrayOutputStream(minOf(maxSize, DEFAULT_BUFFER_SIZE))
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var totalSize = 0
            while (totalSize < maxSize) {
                if (Thread.interrupted()) {
                    throw InterruptedIOException()
                }
                val size = inputStream.read(buffer, 0, minOf(buffer.size, maxSize - totalSize))
                if (size == -1) {
                    break
                }
                outputStream.write(buffer, 0, size)
                totalSize += size
            }
            outputStream.toByteArray()
        }

    private fun ByteArray.decodeText(): String =
        try {
            StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(this))
                .toString()
        } catch (e: CharacterCodingException) {
            String(this, StandardCharsets.ISO_8859_1)
        }

    private const val REGEX_PREFIX = "re:"

    private const val MAX_CONTENT_SEARCH_FILE_SIZE = 1024 * 1024


    // This method traverses the first level first, before diving into child directories.
    // FileVisitResult returned from visitor may be ignored and always considered CONTINUE.
    @Throws(IOException::class)
    private fun walkFileTreeForSearch(start: Path, visitor: FileVisitor<in Path>): Path {
        val showHiddenFiles = showHiddenFiles
        val searchInSubfolders = searchInSubfolders
        val attributes = try {
            start.readAttributes(BasicFileAttributes::class.java)
        } catch (ignored: IOException) {
            try {
                start.readAttributes(BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
            } catch (e: IOException) {
                visitor.visitFileFailed(start, e)
                return start
            }
        }
        if (!attributes.isDirectory) {
            visitor.visitFile(start, attributes)
            return start
        }
        val directoryStream = try {
            start.newDirectoryStream()
        } catch (e: IOException) {
            visitor.visitFileFailed(start, e)
            return start
        }
        val directories = mutableListOf<Path>()
        directoryStream.use {
            visitor.preVisitDirectory(start, attributes)
            try {
                for (path in directoryStream) {
                    if (!showHiddenFiles && path.isHiddenSafely) {
                        continue
                    }
                    val attributes = try {
                        path.readAttributes(BasicFileAttributes::class.java)
                    } catch (ignored: IOException) {
                        try {
                            path.readAttributes(
                                BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS
                            )
                        } catch (e: IOException) {
                            visitor.visitFileFailed(path, e)
                            continue
                        }
                    }
                    visitor.visitFile(path, attributes)
                    if (attributes.isDirectory) {
                        directories.add(path)
                    }
                }
            } catch (e: DirectoryIteratorException) {
                visitor.postVisitDirectory(start, e.cause)
                return start
            }
        }
        if (!searchInSubfolders) {
            // The items in this folder have all been visited already.
            return start
        }
        for (path in directories) {
            Files.walkFileTree(
                path, setOf(FileVisitOption.FOLLOW_LINKS), Int.MAX_VALUE,
                object : FileVisitor<Path> {
                    @Throws(InterruptedIOException::class)
                    override fun preVisitDirectory(
                        directory: Path,
                        attributes: BasicFileAttributes
                    ): FileVisitResult {
                        if (directory == path) {
                            return FileVisitResult.CONTINUE
                        }
                        if (!showHiddenFiles && directory.isHiddenSafely) {
                            return FileVisitResult.SKIP_SUBTREE
                        }
                        return visitor.preVisitDirectory(directory, attributes)
                    }

                    @Throws(InterruptedIOException::class)
                    override fun visitFile(
                        file: Path,
                        attributes: BasicFileAttributes
                    ): FileVisitResult {
                        if (file == path) {
                            return FileVisitResult.CONTINUE
                        }
                        if (!showHiddenFiles && file.isHiddenSafely) {
                            return FileVisitResult.CONTINUE
                        }
                        return visitor.visitFile(file, attributes)
                    }

                    @Throws(InterruptedIOException::class)
                    override fun visitFileFailed(
                        file: Path,
                        exception: IOException
                    ): FileVisitResult {
                        if (file == path) {
                            // We are searching and ignoring errors, so just print it.
                            exception.printStackTrace()
                            return FileVisitResult.CONTINUE
                        }
                        return visitor.visitFileFailed(file, exception)
                    }

                    @Throws(InterruptedIOException::class)
                    override fun postVisitDirectory(
                        directory: Path,
                        exception: IOException?
                    ): FileVisitResult {
                        if (directory == path) {
                            // We are searching and ignoring errors, so just print it.
                            exception?.printStackTrace()
                            return FileVisitResult.CONTINUE
                        }
                        return visitor.postVisitDirectory(path, exception)
                    }
                }
            )
        }
        visitor.postVisitDirectory(start, null)
        return start
    }

    private val Path.isHiddenSafely: Boolean
        get() = try {
            isHidden
        } catch (e: IOException) {
            false
        }


    @Throws(InterruptedIOException::class)
    private fun throwIfInterrupted() {
        if (Thread.interrupted()) {
            throw InterruptedIOException()
        }
    }
}
