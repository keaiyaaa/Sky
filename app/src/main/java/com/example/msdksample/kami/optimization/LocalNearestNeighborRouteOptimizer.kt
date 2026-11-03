package com.example.msdksample.kami.optimization

import com.example.msdksample.kami.model.GeoMath
import com.example.msdksample.kami.model.RoutePlan
import com.example.msdksample.kami.model.RouteValidation
import com.example.msdksample.kami.model.Waypoint

class LocalNearestNeighborRouteOptimizer : RouteOptimizer {
    /**
     * 固定起点使用本地最近邻算法优化开放航线，候选更长时保留原顺序。
     * @param {RoutePlan} route 结果所属航线。
     * @param {List<Waypoint>} waypoints 原始航点；不会修改输入对象。
     * @return {RouteOptimizationResult} 包含稳定标识和连续顺序的优化结果。
     */
    override fun optimizeRoute(
        route: RoutePlan,
        waypoints: List<Waypoint>
    ): RouteOptimizationResult {
        RouteValidation.requireValidWaypoints(waypoints)
        if (waypoints.size < 3) {
            return RouteOptimizationResult(
                waypoints = waypoints.mapIndexed { index, waypoint -> waypoint.copy(routeId = route.id, sequence = index + 1) },
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

        // 最近邻并不保证全局最优，拒绝把比原路线更长的结果当作优化结果保存。
        val chosen = if (GeoMath.routeDistanceMeters(optimized) <= GeoMath.routeDistanceMeters(waypoints)) optimized else waypoints
        val resequenced = chosen.mapIndexed { index, waypoint ->
            waypoint.copy(routeId = route.id, sequence = index + 1)
        }

        return RouteOptimizationResult(
            waypoints = resequenced,
            provider = PROVIDER,
            message = "本地最近邻优化：保留起点，按相邻航段总距离比较结果"
        )
    }

    companion object {
        private const val PROVIDER = "local-nearest-neighbor"
    }
}
