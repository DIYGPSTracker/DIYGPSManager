package dev.csaba.diygpsmanager.data

import java.util.Date


data class Report(
    var id: String = "",
    var lat: Double = .0,
    var lon: Double = .0,
    var speed: Float = .0f,
    var battery: Int = 0,
    var temperature: Float = .0f,
    var created: Date = Date()
)
