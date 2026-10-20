/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileproperties.image

import android.content.Intent
import android.location.Geocoder
import androidx.lifecycle.lifecycleScope
import java8.nio.file.Path
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.WriteWith
import me.zhanghai.android.files.R
import me.zhanghai.android.files.file.FileItem
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.file.formatLong
import me.zhanghai.android.files.file.isImage
import me.zhanghai.android.files.filelist.name
import me.zhanghai.android.files.fileproperties.FilePropertiesTabFragment
import me.zhanghai.android.files.util.ParcelableArgs
import me.zhanghai.android.files.util.ParcelableParceler
import me.zhanghai.android.files.util.Stateful
import me.zhanghai.android.files.util.args
import me.zhanghai.android.files.util.awaitGetFromLocation
import me.zhanghai.android.files.util.createViewLocation
import me.zhanghai.android.files.util.isGeocoderPresent
import me.zhanghai.android.files.util.startActivitySafe
import me.zhanghai.android.files.util.userFriendlyString
import me.zhanghai.android.files.util.viewModels
import me.zhanghai.android.files.databinding.FilePropertiesImageRemoveExifItemBinding
import me.zhanghai.android.files.util.layoutInflater
import me.zhanghai.android.files.util.showToast
import kotlin.math.pow
import kotlin.math.roundToInt

class FilePropertiesImageTabFragment : FilePropertiesTabFragment(),
    ConfirmRemoveExifDialogFragment.Listener {
    private val args by args<Args>()

    private val viewModel by viewModels {
        { FilePropertiesImageTabViewModel(args.path, args.mimeType) }
    }

    private var addressJob: Job? = null

    override fun onResume() {
        super.onResume()

        viewModel.imageInfoLiveData.observe(viewLifecycleOwner) { onImageInfoChanged(it) }
    }

    override fun refresh() {
        viewModel.reload()
    }

    private fun onImageInfoChanged(stateful: Stateful<ImageInfo>) {
        addressJob?.cancel()
        addressJob = null
        bindView(stateful) { imageInfo ->
            addItemView(
                R.string.file_properties_media_dimensions, if (imageInfo.dimensions != null) {
                    getString(
                        R.string.file_properties_media_dimensions_format,
                        imageInfo.dimensions.width, imageInfo.dimensions.height
                    )
                } else {
                    getString(R.string.unknown)
                }
            )
            val exifInfo = imageInfo.exifInfo
            if (exifInfo != null && exifInfo.hasExifData) {
                if (exifInfo.dateTimeOriginal != null) {
                    addItemView(
                        R.string.file_properties_media_date_time,
                        exifInfo.dateTimeOriginal.formatLong()
                    )
                }
                if (exifInfo.gpsCoordinates != null) {
                    addItemView(
                        R.string.file_properties_media_coordinates, getString(
                            R.string.file_properties_media_coordinates_format,
                            exifInfo.gpsCoordinates.first, exifInfo.gpsCoordinates.second
                        )
                    ) {
                        startActivitySafe(
                            Intent::class.createViewLocation(
                                exifInfo.gpsCoordinates.first.toFloat(),
                                exifInfo.gpsCoordinates.second.toFloat(), args.path.name
                            )
                        )
                    }
                    if (isGeocoderPresent) {
                        val textView = addItemView(
                            R.string.file_properties_media_address, getString(R.string.loading)
                        )
                        val geocoder = Geocoder(requireContext())
                        addressJob = viewLifecycleOwner.lifecycleScope.launch {
                            val address = try {
                                geocoder.awaitGetFromLocation(
                                    exifInfo.gpsCoordinates.first, exifInfo.gpsCoordinates.second, 1
                                ).first()
                            } catch (e: Exception) {
                                null
                            }
                            if (isActive) {
                                textView.text = address?.userFriendlyString
                                    ?: getString(R.string.unknown)
                            }
                        }
                    }
                }
                if (exifInfo.gpsAltitude != null) {
                    addItemView(
                        R.string.file_properties_image_gps_altitude, getString(
                            R.string.file_properties_image_gps_altitude_format, exifInfo.gpsAltitude
                        )
                    )
                }
                val equipment = getEquipment(exifInfo.make, exifInfo.model)
                if (equipment != null) {
                    addItemView(R.string.file_properties_image_equipment, equipment)
                }
                if (exifInfo.lensModel != null) {
                    addItemView(R.string.file_properties_image_lens_model, exifInfo.lensModel)
                }
                if (exifInfo.fNumber != null) {
                    addItemView(
                        R.string.file_properties_image_f_number, getString(
                            R.string.file_properties_image_f_number_format, exifInfo.fNumber
                        )
                    )
                }
                if (exifInfo.exposureTime != null) {
                    addItemView(
                        R.string.file_properties_image_exposure_time,
                        formatExposureTime(exifInfo.exposureTime)
                    )
                } else if (exifInfo.shutterSpeedValue != null) {
                    addItemView(
                        R.string.file_properties_image_shutter_speed,
                        getShutterSpeedText(exifInfo.shutterSpeedValue)
                    )
                }
                if (exifInfo.exposureBiasValue != null) {
                    addItemView(
                        R.string.file_properties_image_exposure_bias, getString(
                            R.string.file_properties_image_exposure_bias_format, exifInfo.exposureBiasValue
                        )
                    )
                }
                if (exifInfo.focalLength != null) {
                    addItemView(
                        R.string.file_properties_image_focal_length, getString(
                            R.string.file_properties_image_focal_length_format, exifInfo.focalLength
                        )
                    )
                }
                if (exifInfo.focalLengthIn35mm != null) {
                    addItemView(
                        R.string.file_properties_image_focal_length_in_35mm_film, getString(
                            R.string.file_properties_image_focal_length_in_35mm_film_format,
                            exifInfo.focalLengthIn35mm
                        )
                    )
                }
                if (exifInfo.photographicSensitivity != null) {
                    addItemView(
                        R.string.file_properties_image_photographic_sensitivity, getString(
                            R.string.file_properties_image_photographic_sensitivity_format,
                            exifInfo.photographicSensitivity
                        )
                    )
                }
                if (exifInfo.flash != null) {
                    val flashText = if ((exifInfo.flash and 1) != 0) {
                        getString(R.string.file_properties_image_flash_fired)
                    } else {
                        getString(R.string.file_properties_image_flash_did_not_fire)
                    }
                    addItemView(R.string.file_properties_image_flash, flashText)
                }
                if (exifInfo.whiteBalance != null) {
                    val wbText = if (exifInfo.whiteBalance == 1) {
                        getString(R.string.file_properties_image_white_balance_manual)
                    } else {
                        getString(R.string.file_properties_image_white_balance_auto)
                    }
                    addItemView(R.string.file_properties_image_white_balance, wbText)
                }
                if (exifInfo.software != null) {
                    addItemView(R.string.file_properties_image_software, exifInfo.software)
                }
                if (exifInfo.description != null) {
                    addItemView(R.string.file_properties_image_description, exifInfo.description)
                }
                if (exifInfo.artist != null) {
                    addItemView(R.string.file_properties_image_artist, exifInfo.artist)
                }
                if (exifInfo.copyright != null) {
                    addItemView(R.string.file_properties_image_copyright, exifInfo.copyright)
                }
                if (exifInfo.allAttributes.isNotEmpty()) {
                    addItemView(
                        R.string.file_properties_image_all_exif_attributes,
                        getString(
                            R.string.file_properties_image_all_exif_count_format,
                            exifInfo.allAttributes.size
                        )
                    ) {
                        ExifAttributesDialogFragment.show(
                            exifInfo.allAttributes,
                            this@FilePropertiesImageTabFragment
                        )
                    }
                }
                addRemoveExifButton()
            } else {
                addItemView(
                    R.string.file_properties_image,
                    getString(R.string.file_properties_image_no_exif)
                )
            }
        }
    }

    private fun getEquipment(make: String?, model: String?): String? =
        when {
            make != null && model != null -> {
                if (model.startsWith(make, true)) {
                    model
                } else {
                    getString(R.string.file_properties_image_equipment_format, make, model)
                }
            }
            make != null -> make
            model != null -> model
            else -> null
        }

    // @see com.android.documentsui.inspector.MediaView.formatShutterSpeed
    private fun getShutterSpeedText(value: Double): String =
        if (value <= 0) {
            val shutterSpeed = 2.0.pow(-1 * value)
            ((shutterSpeed * 10.0).roundToInt() / 10.0).toString()
        } else {
            val approximateDenominator = 2.0.pow(value).toInt() + 1
            getString(
                R.string.file_properties_image_shutter_speed_with_denominator_format,
                approximateDenominator
            )
        }

    private fun formatExposureTime(seconds: Double): String =
        if (seconds < 1.0 && seconds > 0.0) {
            val denominator = (1.0 / seconds).roundToInt()
            "1/$denominator s"
        } else {
            getString(R.string.file_properties_image_exposure_time_format, seconds)
        }

    private fun ViewBuilder.addRemoveExifButton() {
        val binding = getScrapItemBinding(FilePropertiesImageRemoveExifItemBinding::class.java)
            ?.also { addView(it) }
            ?: FilePropertiesImageRemoveExifItemBinding.inflate(
                linearLayout.context.layoutInflater, linearLayout, true
            ).also { it.root.tag = it }
        binding.removeExifButton.setOnClickListener {
            ConfirmRemoveExifDialogFragment.show(args.path, this@FilePropertiesImageTabFragment)
        }
    }

    override fun removeExif(path: Path) {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = viewModel.removeExif()
            if (!isAdded) return@launch
            if (result.isSuccess) {
                showToast(R.string.file_properties_image_remove_exif_success)
            } else {
                showToast(
                    getString(
                        R.string.file_properties_image_remove_exif_failed,
                        result.exceptionOrNull()?.message
                    )
                )
            }
        }
    }

    companion object {
        fun isAvailable(file: FileItem): Boolean = file.mimeType.isImage
    }

    @Parcelize
    class Args(
        val path: @WriteWith<ParcelableParceler> Path,
        val mimeType: MimeType
    ) : ParcelableArgs
}
