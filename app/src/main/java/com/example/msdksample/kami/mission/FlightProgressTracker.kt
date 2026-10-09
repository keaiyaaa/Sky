package com.example.msdksample.kami.mission

import com.example.msdksample.kami.model.GeoMath
import com.example.msdksample.kami.model.Waypoint

class FlightProgressTracker(
    private val waypointReachedThresholdMeters: Double = 20.0
) {
    /**
     * 根据飞机当前位置推算已经飞过的最后一个航点序号。
     * @param {List<Waypoint>} waypoints 当前航线航点列表，按 sequence 正序排列。
     * @param {AircraftTelemetry} telemetry DJI V5 遥测回传的飞机位置。
     * @param {Int} currentFlownIndex 当前已飞航点索引，用于避免进度回退。
     * @returns {Int} 已到达的最后一个航点索引；未到达任何航点时返回 currentFlownIndex。
     */
    fun resolveFlownWaypointIndex(
        waypoints: List<Waypoint>,
        telemetry: AircraftTelemetry,
        currentFlownIndex: Int
    ): Int {
        if (waypoints.isEmpty()) {
            return -1
        }

        var resolvedIndex = currentFlownIndex
        waypoints.forEachIndexed { index, waypoint ->
            val distance = GeoMath.haversineDistanceMeters(
                telemetry.latitude,
                telemetry.longitude,
                waypoint.latitude,
                waypoint.longitude
            )
            if (distance <= waypointReachedThresholdMeters && index > resolvedIndex) {
                resolvedIndex = index
            }
        }
        return resolvedIndex
    }
}
