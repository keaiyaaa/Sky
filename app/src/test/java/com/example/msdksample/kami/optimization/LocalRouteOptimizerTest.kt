package com.example.msdksample.kami.optimization

import com.example.msdksample.kami.model.GeoMath
import com.example.msdksample.kami.model.RoutePlan
import com.example.msdksample.kami.model.Waypoint
import org.junit.Assert.*
import org.junit.Test
import java.util.Random

/** 本地优化的稳定标识、重复点和距离不退化测试。 */
class LocalRouteOptimizerTest {
    /**
     * 使用固定种子覆盖多种路线，验证起点、航点集合、顺序和输入不可变性。
     * @return {Unit} 测试断言结果。
     */
    @Test fun neverWorsensDistanceOrMutatesInput() {
        val random = Random(20261103)
        repeat(60) {
            val route = RoutePlan()
            val points = List(12) { index -> Waypoint(routeId = route.id, sequence = index + 1,
                latitude = 31.0 + random.nextDouble() * 0.01, longitude = 121.0 + random.nextDouble() * 0.01) }
            val original = points.map { it.copy() }
            val result = LocalNearestNeighborRouteOptimizer().optimizeRoute(route, points)
            assertEquals(original, points)
            assertEquals(points.first().id, result.waypoints.first().id)
            assertEquals(points.map { it.id }.toSet(), result.waypoints.map { it.id }.toSet())
            assertEquals((1..points.size).toList(), result.waypoints.map { it.sequence })
            assertTrue(GeoMath.routeDistanceMeters(result.waypoints) <= GeoMath.routeDistanceMeters(points) + 0.001)
            assertEquals("local-nearest-neighbor", result.provider)
        }
    }

    /**
     * 验证少量点和重复坐标均不丢失航点标识。
     * @return {Unit} 测试断言结果。
     */
    @Test fun supportsEmptySingleAndDuplicateCoordinates() {
        val route = RoutePlan()
        for (count in 0..4) {
            val points = List(count) { Waypoint(latitude = 31.0, longitude = 121.0) }
            val result = LocalNearestNeighborRouteOptimizer().optimizeRoute(route, points)
            assertEquals(count, result.waypoints.size)
            assertEquals(points.map { it.id }, result.waypoints.map { it.id })
        }
    }
}
