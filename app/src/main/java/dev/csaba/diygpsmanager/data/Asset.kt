package dev.csaba.diygpsmanager.data

import java.util.Date


data class Asset(
    var id: String = "",
    var title: String = "",
    var lock: Boolean = false,  // Manager sets it
    var lockLat: Double = .0,  // Tracker sets it as a response to lock
    var lockLon: Double = .0,  // Tracker sets it as a response to lock
    var lockRadius: Int = 0,
    var lockAlert: Boolean = false,  // Tracker geo fence exit trips it
    var lockManualAlert: Boolean = false,  // Tracker manual geo fence exit trips it
    var periodInterval: Int = 3600,
    var thresholdTemperature: Float = 150.0f,
    var created: Date = Date(),
    var updated: Date = Date()
)
