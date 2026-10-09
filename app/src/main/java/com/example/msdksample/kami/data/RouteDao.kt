package com.example.msdksample.kami.data

import com.example.msdksample.kami.model.RouteMission
import com.example.msdksample.kami.model.RoutePlan
import com.example.msdksample.kami.model.RouteSummary
import com.example.msdksample.kami.model.Waypoint

interface RouteDao {
    fun getRouteSummaries(): List<RouteSummary>
    fun getRoute(routeId: String): RouteMission?
    fun saveRoute(route: RoutePlan, waypoints: List<Waypoint>)
    fun deleteRoute(routeId: String)
    fun getRouteCount(): Int
}

class RouteLimitExceededException(
    message: String = "最多只能保存 3 条航线"
) : IllegalStateException(message)
