/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.compare

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import java.security.MessageDigest
import java8.nio.file.FileVisitResult
import java8.nio.file.Files
import java8.nio.file.LinkOption
import java8.nio.file.Path
import java8.nio.file.SimpleFileVisitor
import java8.nio.file.attribute.BasicFileAttributes
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.zhanghai.android.files.R
import me.zhanghai.android.files.provider.common.newInputStream
import me.zhanghai.android.files.provider.common.readAllBytes
import me.zhanghai.android.files.provider.common.readAttributes
import me.zhanghai.android.files.util.Failure
import me.zhanghai.android.files.util.isBinary
import me.zhanghai.android.files.util.Loading
import me.zhanghai.android.files.util.Stateful
import me.zhanghai.android.files.util.Success
import me.zhanghai.android.files.viewer.text.detectTextEncoding

class FileCompareViewModel(
    private val leftPath: Path,
    private val rightPath: Path
) : ViewModel() {
    private val _state = MutableLiveData<Stateful<FileCompareResult>>(Loading(null))
    val state: LiveData<Stateful<FileCompareResult>> = _state

    private var loadJob: Job? = null

    private val contentCompareJobs = mutableMapOf<String, Job>()

    init {
        reload()
    }

    fun reload() {
        loadJob?.cancel()
        contentCompareJobs.values.forEach { it.cancel() }
        contentCompareJobs.clear()
        loadJob = viewModelScope.launch {
            _state.value = Loading(_state.value?.value)
            try {
                val result = withContext(Dispatchers.IO) { compare() }
                _state.value = Success(result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = Failure(_state.value?.value, e)
            }
        }
    }

    fun compareContents(entry: FolderCompareEntry) {
        val left = entry.left ?: return
        val right = entry.right ?: return
        if (left.isDirectory || right.isDirectory) {
            return
        }
        if (entry.contentState == ContentCompareState.COMPARING ||
            entry.contentState == ContentCompareState.SAME ||
            entry.contentState == ContentCompareState.DIFFERENT
        ) {
            return
        }
        updateEntry(entry.relativePath) {
            it.copy(contentState = ContentCompareState.COMPARING)
        }
        contentCompareJobs[entry.relativePath] = viewModelScope.launch {
            try {
                val contentState = try {
                    val isSame = withContext(Dispatchers.IO) {
                        sha256(left.path).contentEquals(sha256(right.path))
                    }
                    if (isSame) ContentCompareState.SAME else ContentCompareState.DIFFERENT
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e.printStackTrace()
                    ContentCompareState.ERROR
                }
                updateEntry(entry.relativePath) {
                    it.copy(contentState = contentState)
                }
            } finally {
                contentCompareJobs.remove(entry.relativePath)
            }
        }
    }

    private fun updateEntry(
        relativePath: String,
        transform: (FolderCompareEntry) -> FolderCompareEntry
    ) {
        val state = _state.value
        if (state !is Success) {
            return
        }
        val result = state.value
        if (result !is FileCompareResult.Folders) {
            return
        }
        _state.value = Success(
            result.copy(entries = result.entries.map {
                if (it.relativePath == relativePath) transform(it) else it
            })
        )
    }

    private suspend fun compare(): FileCompareResult {
        if (leftPath == rightPath) {
            throw FileCompareException(R.string.file_compare_error_same_path)
        }
        val leftAttributes = leftPath.readAttributes()
        val rightAttributes = rightPath.readAttributes()
        if (leftAttributes.isDirectory != rightAttributes.isDirectory) {
            throw FileCompareException(R.string.file_compare_error_not_same_type)
        }
        return if (leftAttributes.isDirectory) {
            val entries = compareFolders()
            FileCompareResult.Folders(entries)
        } else {
            compareFiles(leftAttributes, rightAttributes)
        }
    }

    private fun Path.readAttributes(): BasicFileAttributes =
        readAttributes(BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)

    private suspend fun compareFolders(): List<FolderCompareEntry> {
        val context = currentCoroutineContext()
        val leftFiles = walkFileTree(leftPath, context)
        val rightFiles = walkFileTree(rightPath, context)
        val keys = (leftFiles.keys + rightFiles.keys).toSortedSet()
        val coveredPaths = mutableListOf<String>()
        val entries = mutableListOf<FolderCompareEntry>()
        for (key in keys) {
            context.ensureActive()
            if (coveredPaths.any { key.startsWith("$it/") }) {
                continue
            }
            val leftFile = leftFiles[key]
            val rightFile = rightFiles[key]
            if (leftFile == null) {
                if (rightFile == null) {
                    continue
                }
                entries += FolderCompareEntry(
                    key, null, rightFile, FolderCompareStatus.ONLY_RIGHT
                )
                if (rightFile.isDirectory) {
                    coveredPaths += key
                }
                continue
            }
            if (rightFile == null) {
                entries += FolderCompareEntry(
                    key, leftFile, null, FolderCompareStatus.ONLY_LEFT
                )
                if (leftFile.isDirectory) {
                    coveredPaths += key
                }
                continue
            }
            if (leftFile.isDirectory != rightFile.isDirectory) {
                entries += FolderCompareEntry(
                    key, leftFile, rightFile, FolderCompareStatus.DIFFERENT
                )
                coveredPaths += key
                continue
            }
            if (leftFile.isDirectory) {
                continue
            }
            if (leftFile.size != rightFile.size ||
                leftFile.lastModifiedMillis != rightFile.lastModifiedMillis
            ) {
                entries += FolderCompareEntry(
                    key, leftFile, rightFile, FolderCompareStatus.DIFFERENT
                )
            }
        }
        return entries
    }

    private fun walkFileTree(
        root: Path,
        context: CoroutineContext
    ): Map<String, FolderCompareFileInfo> {
        val files = mutableMapOf<String, FolderCompareFileInfo>()
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(
                directory: Path,
                attributes: BasicFileAttributes
            ): FileVisitResult {
                context.ensureActive()
                if (directory != root) {
                    files[relativePath(root, directory)] = FolderCompareFileInfo(
                        directory, true, 0L, attributes.lastModifiedTime().toMillis()
                    )
                }
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(
                file: Path,
                attributes: BasicFileAttributes
            ): FileVisitResult {
                context.ensureActive()
                files[relativePath(root, file)] = FolderCompareFileInfo(
                    file, false, attributes.size(), attributes.lastModifiedTime().toMillis()
                )
                return FileVisitResult.CONTINUE
            }

            override fun visitFileFailed(file: Path, exception: IOException): FileVisitResult {
                exception.printStackTrace()
                return FileVisitResult.CONTINUE
            }
        })
        return files
    }

    private fun relativePath(root: Path, path: Path): String {
        val relativePath = root.relativize(path)
        return (0 until relativePath.nameCount).joinToString("/") {
            relativePath.getName(it).toString()
        }
    }

    private fun compareFiles(
        leftAttributes: BasicFileAttributes,
        rightAttributes: BasicFileAttributes
    ): FileCompareResult {
        if (leftAttributes.size() > MAX_FILE_SIZE || rightAttributes.size() > MAX_FILE_SIZE) {
            throw FileCompareException(R.string.file_compare_error_file_too_large)
        }
        val leftBytes = leftPath.readAllBytes()
        val rightBytes = rightPath.readAllBytes()
        if (leftBytes.isBinary() || rightBytes.isBinary()) {
            throw FileCompareException(R.string.file_compare_error_binary_file)
        }
        val leftLines = leftBytes.decodeLines()
        val rightLines = rightBytes.decodeLines()
        if (leftLines.size > MAX_DIFF_LINES || rightLines.size > MAX_DIFF_LINES ||
            leftLines.size.toLong() * rightLines.size > MAX_DIFF_CELLS
        ) {
            throw FileCompareException(R.string.file_compare_error_too_many_lines)
        }
        val diff = createUnifiedDiff(
            leftLines, rightLines, leftPath.fileName?.toString() ?: leftPath.toString(),
            rightPath.fileName?.toString() ?: rightPath.toString()
        )
        return FileCompareResult.Text(diff, diff.isEmpty())
    }

    private fun ByteArray.decodeLines(): List<String> {
        val lines = String(this, detectTextEncoding(this)).split('\n')
        // A trailing newline is a line terminator, not an extra empty line.
        return (if (lines.isNotEmpty() && lines.last().isEmpty()) lines.dropLast(1) else lines)
            .map { it.removeSuffix("\r") }
    }

    private fun createUnifiedDiff(
        leftLines: List<String>,
        rightLines: List<String>,
        leftName: String,
        rightName: String
    ): String {
        val lines = computeDiffLines(leftLines, rightLines)
        val changeIndices = lines.indices.filter { lines[it].type != DiffType.CONTEXT }
        if (changeIndices.isEmpty()) {
            return ""
        }
        val hunks = mutableListOf<IntRange>()
        var start = maxOf(0, changeIndices.first() - CONTEXT_LINES)
        var end = minOf(lines.lastIndex, changeIndices.first() + CONTEXT_LINES)
        for (index in changeIndices.drop(1)) {
            if (index - CONTEXT_LINES <= end + 1) {
                end = minOf(lines.lastIndex, index + CONTEXT_LINES)
            } else {
                hunks += start..end
                start = maxOf(0, index - CONTEXT_LINES)
                end = minOf(lines.lastIndex, index + CONTEXT_LINES)
            }
        }
        hunks += start..end

        val builder = StringBuilder()
        builder.append("--- ").append(leftName).append('\n')
        builder.append("+++ ").append(rightName).append('\n')
        for (hunk in hunks) {
            val hunkLines = lines.subList(hunk.first, hunk.last + 1)
            val oldCount = hunkLines.count { it.type != DiffType.INSERT }
            val newCount = hunkLines.count { it.type != DiffType.DELETE }
            val oldStart = if (oldCount == 0) {
                maxOf(0, hunkLines.first().oldLine - 1)
            } else {
                hunkLines.first { it.type != DiffType.INSERT }.oldLine
            }
            val newStart = if (newCount == 0) {
                maxOf(0, hunkLines.first().newLine - 1)
            } else {
                hunkLines.first { it.type != DiffType.DELETE }.newLine
            }
            builder.append("@@ -").append(oldStart).append(',').append(oldCount)
                .append(" +").append(newStart).append(',').append(newCount).append(" @@\n")
            for (line in hunkLines) {
                builder.append(
                    when (line.type) {
                        DiffType.CONTEXT -> ' '
                        DiffType.DELETE -> '-'
                        DiffType.INSERT -> '+'
                    }
                )
                builder.append(line.text).append('\n')
            }
        }
        return builder.toString()
    }

    private fun computeDiffLines(
        leftLines: List<String>,
        rightLines: List<String>
    ): List<DiffLine> {
        val leftSize = leftLines.size
        val rightSize = rightLines.size
        if (leftSize == 0) {
            return rightLines.mapIndexed { index, line ->
                DiffLine(DiffType.INSERT, line, 1, index + 1)
            }
        }
        if (rightSize == 0) {
            return leftLines.mapIndexed { index, line ->
                DiffLine(DiffType.DELETE, line, index + 1, 1)
            }
        }
        val lengths = IntArray(rightSize + 1)
        val directions = ByteArray(leftSize * rightSize)
        for (leftIndex in 1..leftSize) {
            var previous = 0
            for (rightIndex in 1..rightSize) {
                val oldLength = lengths[rightIndex]
                if (leftLines[leftIndex - 1] == rightLines[rightIndex - 1]) {
                    lengths[rightIndex] = previous + 1
                    directions[(leftIndex - 1) * rightSize + (rightIndex - 1)] = 0
                } else if (lengths[rightIndex] >= lengths[rightIndex - 1]) {
                    directions[(leftIndex - 1) * rightSize + (rightIndex - 1)] = 1
                } else {
                    lengths[rightIndex] = lengths[rightIndex - 1]
                    directions[(leftIndex - 1) * rightSize + (rightIndex - 1)] = 2
                }
                previous = oldLength
            }
        }

        var leftIndex = leftSize
        var rightIndex = rightSize
        val reversedLines = ArrayList<DiffLine>(leftSize + rightSize)
        while (leftIndex > 0 || rightIndex > 0) {
            if (leftIndex > 0 && rightIndex > 0) {
                when (directions[(leftIndex - 1) * rightSize + (rightIndex - 1)].toInt()) {
                    0 -> {
                        reversedLines += DiffLine(
                            DiffType.CONTEXT, leftLines[leftIndex - 1], leftIndex, rightIndex
                        )
                        --leftIndex
                        --rightIndex
                    }
                    1 -> {
                        reversedLines += DiffLine(
                            DiffType.DELETE, leftLines[leftIndex - 1], leftIndex, rightIndex + 1
                        )
                        --leftIndex
                    }
                    else -> {
                        reversedLines += DiffLine(
                            DiffType.INSERT, rightLines[rightIndex - 1], leftIndex + 1, rightIndex
                        )
                        --rightIndex
                    }
                }
            } else if (leftIndex > 0) {
                reversedLines += DiffLine(
                    DiffType.DELETE, leftLines[leftIndex - 1], leftIndex, 1
                )
                --leftIndex
            } else {
                reversedLines += DiffLine(
                    DiffType.INSERT, rightLines[rightIndex - 1], 1, rightIndex
                )
                --rightIndex
            }
        }
        reversedLines.reverse()
        return reversedLines
    }

    private fun sha256(path: Path): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        path.newInputStream().use { inputStream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val size = inputStream.read(buffer)
                if (size == -1) {
                    break
                }
                digest.update(buffer, 0, size)
            }
        }
        return digest.digest()
    }

    private enum class DiffType {
        CONTEXT,
        DELETE,
        INSERT
    }

    private data class DiffLine(
        val type: DiffType,
        val text: String,
        val oldLine: Int,
        val newLine: Int
    )

    companion object {
        private const val MAX_FILE_SIZE = 4 * 1024 * 1024L

        private const val MAX_DIFF_LINES = 2000

        private const val MAX_DIFF_CELLS = 4_000_000L

        private const val CONTEXT_LINES = 3

    }
}
