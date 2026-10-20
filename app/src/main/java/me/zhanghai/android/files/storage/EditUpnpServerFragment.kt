/*
 * Copyright (c) 2026 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.storage

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.parcelize.Parcelize
import me.zhanghai.android.files.R
import me.zhanghai.android.files.databinding.EditUpnpServerFragmentBinding
import me.zhanghai.android.files.provider.upnp.UpnpClient
import me.zhanghai.android.files.provider.upnp.UpnpDevice
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.fadeToVisibilityUnsafe
import me.zhanghai.android.files.util.finish
import me.zhanghai.android.files.util.setResult
import me.zhanghai.android.files.util.showToast
import me.zhanghai.android.files.util.takeIfNotEmpty

class EditUpnpServerFragment : Fragment() {
    private val args by args<Args>()

    private lateinit var binding: EditUpnpServerFragmentBinding

    private var devices: List<UpnpDevice> = emptyList()

    private var selectedDevice: UpnpDevice? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        EditUpnpServerFragmentBinding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity() as AppCompatActivity
        activity.lifecycleScope.launchWhenCreated {
            activity.setSupportActionBar(binding.toolbar)
            activity.supportActionBar!!.setDisplayHomeAsUpEnabled(true)
            activity.setTitle(
                if (args.server != null) {
                    R.string.storage_edit_upnp_server_title_edit
                } else {
                    R.string.storage_edit_upnp_server_title_add
                }
            )
        }

        binding.discoverButton.setOnClickListener { discoverDevices() }
        binding.saveOrAddButton.setOnClickListener { saveOrAdd() }
        binding.cancelButton.setOnClickListener { finish() }
        binding.removeOrAddButton.setOnClickListener {
            if (args.server != null) {
                remove()
            } else {
                saveOrAdd()
            }
        }
        binding.removeOrAddButton.setText(
            if (args.server != null) R.string.remove else R.string.storage_edit_upnp_server_add
        )

        if (savedInstanceState == null) {
            val server = args.server
            if (server != null) {
                binding.nameEdit.setText(server.customName)
                // The device is not discovered again, but it is known to exist.
                selectedDevice = UpnpDevice(
                    server.location, server.friendlyName, null, null, "", "0"
                )
            }
        }
    }

    private fun discoverDevices() {
        binding.progress.fadeToVisibilityUnsafe(true)
        lifecycleScope.launch {
            val foundDevices = withContext(Dispatchers.IO) {
                try {
                    UpnpClient.discoverDevices()
                } catch (e: Exception) {
                    e.printStackTrace()
                    emptyList()
                }
            }
            binding.progress.fadeToVisibilityUnsafe(false)
            devices = foundDevices
            updateDevices()
        }
    }

    private fun updateDevices() {
        binding.devicesGroup.removeAllViews()
        for (device in devices) {
            val radioButton = RadioButton(requireContext())
            radioButton.text = device.friendlyName
            radioButton.setOnClickListener {
                selectedDevice = device
                binding.nameLayout.placeholderText = device.friendlyName
            }
            binding.devicesGroup.addView(radioButton)
        }
        if (devices.isEmpty()) {
            binding.devicesText.setText(R.string.storage_edit_upnp_server_no_device_found)
        } else {
            binding.devicesText.setText(R.string.storage_edit_upnp_server_devices_found)
        }
    }

    private fun saveOrAdd() {
        val server = getServerOrSetError() ?: return
        Storages.addOrReplace(server)
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun remove() {
        Storages.remove(args.server!!)
        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun getServerOrSetError(): UpnpServer? {
        val device = selectedDevice
        if (device == null) {
            showToast(R.string.storage_edit_upnp_server_error_no_device)
            return null
        }
        val name = binding.nameEdit.text.toString().takeIfNotEmpty()
        return UpnpServer(args.server?.id, name, device.location, device.friendlyName)
    }

    @Parcelize
    class Args(val server: UpnpServer? = null) : ParcelableArgs
}
