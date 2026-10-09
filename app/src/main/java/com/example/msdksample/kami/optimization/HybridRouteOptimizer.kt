package com.example.msdksample.kami.optimization

import com.example.msdksample.kami.model.RoutePlan
import com.example.msdksample.kami.model.Waypoint

class HybridRouteOptimizer(
    private val aiClient: AiRouteOptimizationClient? = null,
    private val fallbackOptimizer: RouteOptimizer = LocalNearestNeighborRouteOptimizer()
) : RouteOptimizer {
    override fun optimizeRoute(
        route: RoutePlan,
        waypoints: List<Waypoint>
    ): RouteOptimizationResult {
        val aiResult = aiClient?.optimizeRoute(route, waypoints)
        if (aiResult != null) {
            return aiResult
        }
        return fallbackOptimizer.optimizeRoute(route, waypoints)
    }
}
