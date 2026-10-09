package com.example.msdksample.kami.optimization

import com.example.msdksample.kami.model.RoutePlan
import com.example.msdksample.kami.model.Waypoint

interface RouteOptimizer {
    fun optimizeRoute(route: RoutePlan, waypoints: List<Waypoint>): RouteOptimizationResult
}

data class RouteOptimizationResult(
    val waypoints: List<Waypoint>,
    val provider: String,
    val message: String
)

interface AiRouteOptimizationClient {
    fun optimizeRoute(route: RoutePlan, waypoints: List<Waypoint>): RouteOptimizationResult?
}
