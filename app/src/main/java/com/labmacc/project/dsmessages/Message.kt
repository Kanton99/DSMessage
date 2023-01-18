package com.labmacc.project.dsmessages

import com.google.android.gms.maps.model.LatLng

data class Message(val text: String, val coords: LatLng, var up: Int, var dwn: Int)
