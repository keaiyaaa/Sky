package com.example.msdksample.kami.optimization

import com.example.msdksample.kami.model.GeoMath
import com.example.msdksample.kami.model.RoutePlan
import com.example.msdksample.kami.model.Waypoint

class LocalNearestNeighborRouteOptimizer : RouteOptimizer {
    override fun optimizeRoute(
        route: RoutePlan,
        waypoints: List<Waypoint>
    ): RouteOptimizationResult {
        if (waypoints.size < 3) {
            return RouteOptimizationResult(
                waypoints = waypoints.mapIndexed { index, waypoint -> waypoint.copy(sequence = index + 1) },
                provider = PROVIDER,
                message = "航点少于 3 个，无需优化"
            )
        }

        val remaining = waypoints.map { it.copy() }.toMutableList()
        val optimized = mutableListOf<Waypoint>()
        optimized.add(remaining.removeAt(0))

        while (remaining.isNotEmpty()) {
            val current = optimized.last()
            val nearest = remaining.minByOrNull { candidate ->
                GeoMath.haversineDistanceMeters(
                    current.latitude,
                    current.longitude,
                    candidate.latitude,
                    candidate.longitude
                )
            } ?: break
            remaining.remove(nearest)
            optimized.add(nearest)
        }

        val resequenced = optimized.mapIndexed { index, waypoint ->
            waypoint.copy(routeId = route.id, sequence = index + 1)
        }

        return RouteOptimizationResult(
            waypoints = resequenced,
            provider = PROVIDER,
            message = "已按最近邻算法优化航点顺序"
        )
    }

    companion object {
        private const val PROVIDER = "local-nearest-neighbor"
    }
}
