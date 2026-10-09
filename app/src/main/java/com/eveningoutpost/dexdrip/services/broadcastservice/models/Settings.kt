package com.eveningoutpost.dexdrip.services.broadcastservice.models

import android.os.Parcel
import android.os.Parcelable
import androidx.annotation.Keep

/**
 * Parcelable Settings model for xDrip+ BroadcastService handshake.
 * xDrip's BroadcastService.java strictly deserializes this class from extra "SETTINGS".
 */
@Keep
class Settings : Parcelable {

    var graphStart: Long = 0
        private set

    var graphEnd: Long = 0
        private set

    val apkName: String
    val isDisplayGraph: Boolean

    constructor(parcel: Parcel) {
        apkName = parcel.readString() ?: ""
        graphStart = parcel.readLong()
        graphEnd = parcel.readLong()
        isDisplayGraph = parcel.readInt() == 1
    }

    constructor() {
        apkName = ""
        isDisplayGraph = false
        graphStart = 0L
        graphEnd = 0L
    }

    constructor(appName: String) {
        apkName = appName
        isDisplayGraph = false
        graphStart = 0L
        graphEnd = 0L
    }

    constructor(appName: String, graphDurationMs: Long) {
        apkName = appName
        isDisplayGraph = true
        graphStart = graphDurationMs
        graphEnd = 0L
    }

    override fun describeContents(): Int = 0

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(apkName)
        parcel.writeLong(graphStart)
        parcel.writeLong(graphEnd)
        parcel.writeInt(if (isDisplayGraph) 1 else 0)
    }

    companion object {
        @JvmField
        val CREATOR: Parcelable.Creator<Settings> = object : Parcelable.Creator<Settings> {
            override fun createFromParcel(parcel: Parcel): Settings = Settings(parcel)
            override fun newArray(size: Int): Array<Settings?> = arrayOfNulls(size)
        }
    }
}
