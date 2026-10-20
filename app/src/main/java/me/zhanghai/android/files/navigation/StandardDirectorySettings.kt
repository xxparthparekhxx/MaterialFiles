/*
 * Copyright (c) 2019 Hai Zhang <dreaming.in.code.zh@gmail.com>
 * All Rights Reserved.
 */

package me.zhanghai.android.files.navigation

import android.os.Parcel
import android.os.Parcelable

/**
 * The settings of a standard directory.
 *
 * The custom path was added after the first release, so the parcel of the older versions doesn't
 * contain it. The parcel is written by hand instead of with `@Parcelize` for that reason, so that
 * the settings of the existing installations are still read.
 */
data class StandardDirectorySettings(
    val id: String,
    val customTitle: String?,
    val isEnabled: Boolean,
    val customPath: String? = null
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString()!!,
        parcel.readString(),
        parcel.readInt() != 0,
        // The custom path is the last field, so it is missing from the older data.
        if (parcel.dataAvail() > 0) parcel.readString() else null
    )

    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeString(id)
        dest.writeString(customTitle)
        dest.writeInt(if (isEnabled) 1 else 0)
        dest.writeString(customPath)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<StandardDirectorySettings> =
            object : Parcelable.Creator<StandardDirectorySettings> {
                override fun createFromParcel(source: Parcel): StandardDirectorySettings =
                    StandardDirectorySettings(source)

                override fun newArray(size: Int): Array<StandardDirectorySettings?> =
                    arrayOfNulls(size)
            }
    }
}
