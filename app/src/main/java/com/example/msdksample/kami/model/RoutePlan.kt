package com.example.msdksample.kami.model

import java.io.Serializable
import java.util.UUID

data class RoutePlan(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "航线",
    var taskName: String = "",
    var taskTime: String = "",
    var priority: RoutePriority = RoutePriority.NORMAL,
    var createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis()
) : Serializable

data class RouteMission(
    val route: RoutePlan,
    val waypoints: List<Waypoint>
) : Serializable

data class RouteSummary(
    val route: RoutePlan,
    val waypointCount: Int
)
