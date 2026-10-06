/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.text

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.text.method.KeyListener
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import java8.nio.file.Path
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.TextEditorFragmentBinding
import me.zhanghai.android.files.file.asFileSize
import me.zhanghai.android.files.settings.Settings
import me.zhanghai.android.files.ui.ThemedFastScroller
import me.zhanghai.android.files.util.ActionState
import me.zhanghai.android.files.util.DataState
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.addOnBackPressedCallback
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.extraPath
import me.zhanghai.android.files.util.fadeInUnsafe
import me.zhanghai.android.files.util.fadeOutUnsafe
import me.zhanghai.android.files.util.hideSoftInput
import me.zhanghai.android.files.util.isReady
import me.zhanghai.android.files.util.showCharsetPickerDialog
import me.zhanghai.android.files.util.showToast
import me.zhanghai.android.files.util.valueCompat
import me.zhanghai.android.files.util.viewModels
import java.nio.charset.Charset

class TextEditorFragment : Fragment(), ConfirmReloadDialogFragment.Listener,
    ConfirmCloseDialogFragment.Listener, FindTextDialogFragment.Listener {
    private val args by args<Args>()
    private lateinit var argsFile: Path

    private lateinit var binding: TextEditorFragmentBinding

    private lateinit var menuBinding: MenuBinding

    private val viewModel by viewModels { { TextEditorViewModel(argsFile) } }

    private lateinit var onBackPressedCallback: OnBackPressedCallback

    private var isSettingText = false

    private var lastFindQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setHasOptionsMenu(true)

        val file = args.intent.extraPath
        if (file == null) {
            finish()
            return
        }
        argsFile = file

        lifecycleScope.launchWhenStarted {
            onBackPressedCallback = object : OnBackPressedCallback(false) {
                override fun handleOnBackPressed() {
                    val isKeyboardVisible = ViewCompat.getRootWindowInsets(binding.textEdit)
                        ?.isVisible(WindowInsetsCompat.Type.ime()) == true
                    if (isKeyboardVisible || binding.textEdit.hasFocus()) {
                        binding.textEdit.clearFocus()
                        binding.textEdit.hideSoftInput()
                    } else {
                        ConfirmCloseDialogFragment.show(this@TextEditorFragment)
                    }
                }
            }
            launch {
                viewModel.isTextChanged.collect {
                    onBackPressedCallback.isEnabled = viewModel.isTextChanged.value
                }
            }
            addOnBackPressedCallback(onBackPressedCallback)

            launch { viewModel.encoding.collect { onEncodingChanged(it) } }
            launch { viewModel.textState.collect { onTextStateChanged(it) } }
            launch { viewModel.isTextChanged.collect { onIsTextChangedChanged(it) } }
            launch { viewModel.writeFileState.collect { onWriteFileStateChanged(it) } }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        TextEditorFragmentBinding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!::argsFile.isInitialized) {
            return
        }

        val activity = requireActivity() as AppCompatActivity
        activity.lifecycleScope.launchWhenCreated {
            activity.setSupportActionBar(binding.toolbar)
            activity.supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        }

        Settings.TEXT_EDITOR_WORD_WRAP.observe(viewLifecycleOwner) {
            binding.textEdit.setHorizontallyScrolling(!it)
            requireActivity().invalidateOptionsMenu()
        }

        val defaultTextSize = binding.textEdit.textSize
        Settings.TEXT_EDITOR_FONT_SIZE.observe(viewLifecycleOwner) {
            val sp = it.toFloatOrNull() ?: 0f
            if (sp > 0f) {
                binding.textEdit.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            } else {
                binding.textEdit.setTextSize(TypedValue.COMPLEX_UNIT_PX, defaultTextSize)
            }
        }

        // TODO: Move reload-prevent here so that we can also handle save-as, etc. Or maybe just get
        //  rid of the mPathLiveData in TextEditorViewModel.
        ThemedFastScroller.create(binding.scrollView)
        // Manually save and restore state in view model to avoid TransactionTooLargeException.
        binding.textEdit.isSaveEnabled = false
        val textEditSavedState = viewModel.removeEditTextSavedState()
        if (textEditSavedState != null) {
            binding.textEdit.onRestoreInstanceState(textEditSavedState)
        }
        binding.textEdit.doAfterTextChanged {
            if (isSettingText) {
                return@doAfterTextChanged
            }
            // Might happen if the animation is running and user is quick enough.
            if (viewModel.textState.value !is DataState.Success) {
                return@doAfterTextChanged
            }
            viewModel.updateDraft(it?.toString() ?: "")
            viewModel.isTextChanged.value = true
        }

        Settings.TEXT_EDITOR_MONOSPACE.observe(viewLifecycleOwner) { onMonospaceChanged(it) }

        // TODO: Request storage permission if not granted.
    }

    private fun onMonospaceChanged(monospace: Boolean) {
        binding.textEdit.typeface = if (monospace) Typeface.MONOSPACE else Typeface.DEFAULT
        updateMonospaceMenuItem()
    }

    private fun updateMonospaceMenuItem() {
        if (!this::menuBinding.isInitialized) {
            return
        }
        menuBinding.menu.findItem(R.id.action_monospace).isChecked =
            Settings.TEXT_EDITOR_MONOSPACE.valueCompat
    }

    override fun onPause() {
        super.onPause()

        if (::binding.isInitialized && viewModel.isTextChanged.value) {
            viewModel.persistDraft()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        if (!::argsFile.isInitialized || !::binding.isInitialized) {
            return
        }
        viewModel.setEditTextSavedState(binding.textEdit.onSaveInstanceState())
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)

        // Without a file we are finishing, and the view model cannot be created.
        if (!::argsFile.isInitialized) {
            return
        }
        menuBinding = MenuBinding.inflate(menu, inflater)
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

        updateSaveMenuItem()
        updateMonospaceMenuItem()
        menu.findItem(R.id.action_word_wrap).isChecked = Settings.TEXT_EDITOR_WORD_WRAP.valueCompat
        menu.findItem(R.id.action_read_only).isChecked = isReadOnly
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean =
        if (!::argsFile.isInitialized) {
            super.onOptionsItemSelected(item)
        } else when (item.itemId) {
            R.id.action_save -> {
                save()
                true
            }
            R.id.action_find -> {
                FindTextDialogFragment.show(lastFindQuery, this)
                true
            }
            R.id.action_find_next -> {
                findNext()
                true
            }
            R.id.action_undo -> {
                binding.textEdit.onTextContextMenuItem(android.R.id.undo)
                true
            }
            R.id.action_redo -> {
                binding.textEdit.onTextContextMenuItem(android.R.id.redo)
                true
            }
            R.id.action_read_only -> {
                setReadOnly(!item.isChecked)
                true
            }
            R.id.action_reload -> {
                onReload()
                true
            }
            R.id.action_encoding -> {
                requireContext().showCharsetPickerDialog(viewModel.encoding.value.name()) {
                    viewModel.chooseEncoding(it)
                }
                true
            }
            R.id.action_monospace -> {
                Settings.TEXT_EDITOR_MONOSPACE.putValue(!Settings.TEXT_EDITOR_MONOSPACE.valueCompat)
                true
            }
            R.id.action_word_wrap -> {
                Settings.TEXT_EDITOR_WORD_WRAP.putValue(!item.isChecked)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }

    override fun findText(query: String) {
        lastFindQuery = query
        findNext()
    }

    private fun findNext() {
        if (lastFindQuery.isEmpty()) {
            FindTextDialogFragment.show(lastFindQuery, this)
            return
        }
        val editText = binding.textEdit
        val text = editText.text ?: return
        val start = editText.selectionEnd.coerceAtLeast(0)
        // Wrap around to the beginning when there is no further match.
        val index = text.indexOf(lastFindQuery, start, true).takeIf { it >= 0 }
            ?: text.indexOf(lastFindQuery, 0, true)
        if (index < 0) {
            showToast(R.string.text_editor_find_not_found)
            return
        }
        editText.requestFocus()
        editText.setSelection(index, index + lastFindQuery.length)
    }

    private var isReadOnly = false
    private var editableKeyListener: KeyListener? = null

    private fun setReadOnly(readOnly: Boolean) {
        if (readOnly == isReadOnly) {
            return
        }
        isReadOnly = readOnly
        val textEdit = binding.textEdit
        if (readOnly) {
            editableKeyListener = textEdit.keyListener
            // Removing the key listener stops typing while keeping selection and scrolling.
            textEdit.keyListener = null
            textEdit.hideSoftInput()
        } else {
            textEdit.keyListener = editableKeyListener
        }
        // Making the text selectable re-sets the same text, which must not count as an edit.
        isSettingText = true
        textEdit.setTextIsSelectable(true)
        isSettingText = false
        requireActivity().invalidateOptionsMenu()
    }

    fun onSupportNavigateUp(): Boolean {
        if (::onBackPressedCallback.isInitialized && onBackPressedCallback.isEnabled) {
            onBackPressedCallback.handleOnBackPressed()
            return true
        }
        return false
    }

    override fun finish() {
        if (::argsFile.isInitialized) {
            viewModel.discardDraft()
        }
        requireActivity().finish()
    }

    private fun onEncodingChanged(encoding: Charset) {
        // The encoding dialog reads the current value when opened.
    }

    private fun onTextStateChanged(state: DataState<String>) {
        updateTitle()
        when (state) {
            is DataState.Loading -> {
                binding.progress.fadeInUnsafe()
                binding.errorText.fadeOutUnsafe()
                binding.textEdit.fadeOutUnsafe()
            }
            is DataState.Success -> {
                binding.progress.fadeOutUnsafe()
                binding.errorText.fadeOutUnsafe()
                binding.textEdit.fadeInUnsafe()
                val draft = viewModel.draftText
                if (draft != null && viewModel.isTextChanged.value) {
                    setText(draft, changed = true)
                } else if (!viewModel.isTextChanged.value) {
                    setText(state.data)
                }
            }
            is DataState.Error -> {
                state.throwable.printStackTrace()
                binding.progress.fadeOutUnsafe()
                binding.errorText.fadeInUnsafe()
                val throwable = state.throwable
                binding.errorText.text = when (throwable) {
                    is BinaryFileException -> getString(R.string.text_editor_error_binary_file)
                    is FileTooLargeException -> getString(
                        R.string.text_editor_error_file_too_large_format,
                        throwable.size.asFileSize().formatHumanReadable(requireContext()),
                        throwable.maxSize.asFileSize().formatHumanReadable(requireContext())
                    )
                    else -> throwable.localizedMessage ?: throwable.toString()
                }
                binding.textEdit.fadeOutUnsafe()
            }
        }
    }

    private fun setText(text: String?, changed: Boolean = false) {
        // This is also called after saving because we'll be updating our state of the unchanged
        // text, but we don't want to call TextView.setText() again which resets things like cursor
        // position.
        if (binding.textEdit.text?.contentEquals(text) != true) {
            isSettingText = true
            binding.textEdit.setText(text)
            isSettingText = false
        }
        viewModel.isTextChanged.value = changed
    }

    private fun onIsTextChangedChanged(changed: Boolean) {
        updateTitle()
    }

    private fun updateTitle() {
        val fileName = viewModel.file.value.fileName.toString()
        val changed = viewModel.isTextChanged.value
        requireActivity().title = getString(
            if (changed) {
                R.string.text_editor_title_changed_format
            } else {
                R.string.text_editor_title_format
            }, fileName
        )
    }

    private fun onReload() {
        if (viewModel.isTextChanged.value) {
            ConfirmReloadDialogFragment.show(this)
        } else {
            reload()
        }
    }

    override fun reload() {
        viewModel.discardDraft()
        viewModel.isTextChanged.value = false
        viewModel.reload()
    }

    private fun save() {
        val text = binding.textEdit.text ?: return
        viewModel.writeFile(argsFile, text, requireContext())
    }

    private fun onWriteFileStateChanged(state: ActionState<Path, Unit>) {
        when (state) {
            is ActionState.Ready, is ActionState.Running -> updateSaveMenuItem()
            is ActionState.Success -> {
                showToast(R.string.text_editor_save_success)
                viewModel.finishWritingFile()
                viewModel.isTextChanged.value = false
            }
            // The error will be toasted by service so we should never show it in UI.
            is ActionState.Error -> viewModel.finishWritingFile()
        }
    }

    private fun updateSaveMenuItem() {
        if (!this::menuBinding.isInitialized) {
            return
        }
        menuBinding.saveItem.isEnabled = viewModel.writeFileState.value.isReady
    }

    @Parcelize
    class Args(val intent: Intent) : ParcelableArgs

    private class MenuBinding private constructor(
        val menu: Menu,
        val saveItem: MenuItem
    ) {
        companion object {
            fun inflate(menu: Menu, inflater: MenuInflater): MenuBinding {
                inflater.inflate(R.menu.text_editor, menu)
                return MenuBinding(menu, menu.findItem(R.id.action_save))
            }
        }
    }
}
