package com.labmacc.project.dsmessages

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class Message(
    val text: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    var up: Int = 0,
    var dwn: Int = 0,
    var uID: String? = "",
    var msgID: Int = 0
    ) : Parcelable
