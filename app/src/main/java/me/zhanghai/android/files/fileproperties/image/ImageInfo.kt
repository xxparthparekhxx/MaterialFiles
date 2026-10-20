/*
 * Copyright (c) 2020 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.fileproperties.image

import android.util.Size
import java.time.Instant

class ImageInfo(
    val dimensions: Size?,
    val exifInfo: ExifInfo?
)

// @see com.android.documentsui.inspector.MediaView
// @see https://github.com/GNOME/nautilus/blob/c73ad94a72f8e9a989b01858018de74182d17f0e/extensions/image-properties/nautilus-image-properties-page.c#L198
class ExifInfo(
    val dateTimeOriginal: Instant?,
    val gpsCoordinates: Pair<Double, Double>?,
    val gpsAltitude: Double?,
    val make: String?,
    val model: String?,
    val fNumber: Double?,
    val shutterSpeedValue: Double?,
    val focalLength: Double?,
    val photographicSensitivity: Int?,
    val software: String?,
    val description: String?,
    val artist: String?,
    val copyright: String?,
    val exposureTime: Double? = null,
    val exposureBiasValue: Double? = null,
    val flash: Int? = null,
    val whiteBalance: Int? = null,
    val focalLengthIn35mm: Int? = null,
    val lensModel: String? = null,
    val allAttributes: Map<String, String> = emptyMap()
) {
    val hasExifData: Boolean
        get() = allAttributes.isNotEmpty() || dateTimeOriginal != null || gpsCoordinates != null ||
            gpsAltitude != null || make != null || model != null || fNumber != null ||
            shutterSpeedValue != null || focalLength != null || photographicSensitivity != null ||
            software != null || description != null || artist != null || copyright != null ||
            exposureTime != null || exposureBiasValue != null || flash != null ||
            whiteBalance != null || focalLengthIn35mm != null || lensModel != null
}

