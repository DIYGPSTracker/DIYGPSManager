package dev.csaba.diygpsmanager.data.remote

import com.google.firebase.Timestamp


data class RemoteReport(
    var id: String = "",
    var lat: Double = .0,
    var lon: Double = .0,
    var speed: Float = .0f,
    var battery: Int = 0,
    var temperature: Float = .0f,
    var created: Timestamp = Timestamp(0, 0)  // TODO use @ServerTimestamp
)
