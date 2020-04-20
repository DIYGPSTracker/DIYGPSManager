package dev.csaba.diygpsmanager.data

import java.util.Date


data class Asset(
    var id: String = "",
    var title: String = "",
    var lockLat: Double = .0,
    var lockLon: Double = .0,
    var lockRadius: Int = 0,
    var periodInterval: Int = 3600,
    var created: Date = Date(),
    var updated: Date = Date()
)
