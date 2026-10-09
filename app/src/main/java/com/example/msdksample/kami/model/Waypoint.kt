package com.example.msdksample.kami.model

import java.io.Serializable
import java.util.UUID

/**
 *航点数据模型。
 *
 * Keep this model free of map SDK objects so it can be saved, exported to WPML,
 * and passed to DJI V5 flight modules without leaking UI/runtime state.
 */
data class Waypoint(
    val id: String = UUID.randomUUID().toString(),
    var routeId: String = "",
    var sequence: Int = 0,
    var latitude: Double,
    var longitude: Double,
    var altitude: Float = 30f,
    var action: String = "拍照"
) : Serializable
