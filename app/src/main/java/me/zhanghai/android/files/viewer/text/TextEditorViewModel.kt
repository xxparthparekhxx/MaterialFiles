/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.text

import android.content.Context
import android.os.Parcelable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java8.nio.file.Path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import me.zhanghai.android.files.filejob.FileJobService
import me.zhanghai.android.files.provider.common.readAllBytes
import me.zhanghai.android.files.provider.common.size
import me.zhanghai.android.files.util.ActionState
import me.zhanghai.android.files.util.DataState
import me.zhanghai.android.files.util.isFinished
import me.zhanghai.android.files.util.isReady
import me.zhanghai.android.files.util.toError
import me.zhanghai.android.files.util.toLoading
import java.io.IOException
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

class TextEditorViewModel(file: Path) : ViewModel() {
    private val _file = MutableStateFlow(file)
    val file = _file.asStateFlow()

    private val _bytesState = MutableStateFlow<DataState<ByteArray>>(DataState.Loading())

    private var loadJob: Job? = null
    private var reloadJob: Job? = null

    init {
        viewModelScope.launch {
            _file.collectLatest {
                loadJob?.cancel()?.also { loadJob = null }
                reloadJob?.cancel()?.also { reloadJob = null }
                loadJob = launch {
                    mapFileToBytesState(it)
                    if (isActive) {
                        loadJob = null
                    }
                }
            }
        }
    }

    fun reload() {
        viewModelScope.launch {
            loadJob?.cancel()?.also { loadJob = null }
            reloadJob?.cancel()?.also { reloadJob = null }
            reloadJob = launch {
                mapFileToBytesState(_file.value)
                if (isActive) {
                    reloadJob = null
                }
            }
        }
    }

    private suspend fun mapFileToBytesState(file: Path) {
        _bytesState.value = _bytesState.value.toLoading()
        try {
            val bytes = runInterruptible(Dispatchers.IO) {
                val size = file.size()
                if (size > MAX_FILE_SIZE) {
                    throw FileTooLargeException(size, MAX_FILE_SIZE)
                }
                file.readAllBytes()
            }
            currentCoroutineContext().ensureActive()
            _bytesState.value = DataState.Success(bytes)
        } catch (e: CancellationException) {
            e.printStackTrace()
        } catch (e: Exception) {
            _bytesState.value = _bytesState.value.toError(e)
        }
    }

    val encoding = MutableStateFlow<Charset>(StandardCharsets.UTF_8)

    private var isEncodingChosen = false
    private var detectedBytes: ByteArray? = null

    /** Set the encoding explicitly, which turns off automatic detection. */
    fun chooseEncoding(charset: Charset) {
        isEncodingChosen = true
        encoding.value = charset
    }

    private val _textState = MutableStateFlow<DataState<String>>(DataState.Loading())
    val textState = _textState.asStateFlow()

    init {
        viewModelScope.launch {
            _bytesState.combine(encoding) { bytesState, encoding -> bytesState to encoding }
                .collectLatest { (bytesState, encoding) ->
                    when (bytesState) {
                        is DataState.Loading -> _textState.value = _textState.value.toLoading()
                        is DataState.Success -> {
                            if (bytesState.data.isBinary) {
                                _textState.value = _textState.value.toError(BinaryFileException())
                                return@collectLatest
                            }
                            if (!isEncodingChosen && detectedBytes !== bytesState.data) {
                                detectedBytes = bytesState.data
                                val detected = withContext(Dispatchers.Default) {
                                    detectTextEncoding(bytesState.data)
                                }
                                if (detected != encoding) {
                                    // Setting this re-runs the collector with the detected value.
                                    this@TextEditorViewModel.encoding.value = detected
                                    return@collectLatest
                                }
                            }
                            try {
                                val text = withContext(Dispatchers.Default) {
                                    String(bytesState.data, encoding)
                                }
                                currentCoroutineContext().ensureActive()
                                _textState.value = DataState.Success(text)
                            } catch (e: CancellationException) {
                                e.printStackTrace()
                            } catch (e: Exception) {
                                _textState.value = _textState.value.toError(e)
                            }
                        }
                        is DataState.Error ->
                            _textState.value = _textState.value.toError(bytesState.throwable)
                    }
                }
        }
    }

    val isTextChanged = MutableStateFlow(false)

    private val _writeFileState =
        MutableStateFlow<ActionState<Path, Unit>>(ActionState.Ready())
    val writeFileState = _writeFileState.asStateFlow()

    fun writeFile(path: Path, text: CharSequence, context: Context) {
        viewModelScope.launch {
            check(_writeFileState.value.isReady)
            _writeFileState.value = ActionState.Running(path)
            val bytes = withContext(Dispatchers.Default) {
                text.toString().toByteArray(encoding.value)
            }
            FileJobService.write(path, bytes, context) { successful ->
                if (successful) {
                    loadJob?.cancel()?.also { loadJob = null }
                    reloadJob?.cancel()?.also { reloadJob = null }
                    _bytesState.value = DataState.Success(bytes)
                }
                _writeFileState.value = if (successful) {
                    ActionState.Success(path, Unit)
                } else {
                    // The error will be toasted by service so we should never show it in UI, but we
                    // need a non-null value here.
                    ActionState.Error(path, Throwable())
                }
            }
        }
    }

    fun finishWritingFile() {
        viewModelScope.launch {
            check(_writeFileState.value.isFinished)
            _writeFileState.value = ActionState.Ready()
        }
    }

    private var editTextSavedState: Parcelable? = null

    fun setEditTextSavedState(editTextSavedState: Parcelable?) {
        this.editTextSavedState = editTextSavedState
    }

    fun removeEditTextSavedState(): Parcelable? {
        val savedState = editTextSavedState
        editTextSavedState = null
        return savedState
    }

    companion object {
        private const val MAX_FILE_SIZE = 4 * 1024 * 1024.toLong()
    }
}

class BinaryFileException : IOException("Binary file cannot be opened as text")

class FileTooLargeException(val size: Long, val maxSize: Long) :
    IOException("File size $size is too large (maximum $maxSize)")

private val ByteArray.isBinary: Boolean
    get() {
        val checkLength = minOf(size, 8192)
        for (i in 0 until checkLength) {
            if (this[i] == 0.toByte()) {
                return true
            }
        }
        return false
    }
