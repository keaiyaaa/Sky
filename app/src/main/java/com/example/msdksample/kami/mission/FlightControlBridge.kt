package com.example.msdksample.kami.mission

import com.example.msdksample.kami.model.RoutePlan
import com.example.msdksample.kami.model.Waypoint
import java.io.File

interface FlightControlBridge {
    fun prepareMission(route: RoutePlan, waypoints: List<Waypoint>): MissionLaunchResult
    fun startPreparedMission(kmzFile: File): MissionLaunchResult
}

data class MissionLaunchResult(
    val success: Boolean,
    val message: String,
    val kmzFile: File? = null
)
