package com.example.msdksample.kami.model

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object GeoMath {
    private const val EARTH_RADIUS_METERS = 6371000.0

    /**
     * 计算两组经纬度之间的球面距离。
     * @param {Double} lat1 起点纬度，单位度。
     * @param {Double} lon1 起点经度，单位度。
     * @param {Double} lat2 终点纬度，单位度。
     * @param {Double} lon2 终点经度，单位度。
     * @return {Double} 球面距离，单位米。
     */
    fun haversineDistanceMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        // 浮点舍入可能使对跖点的 a 略大于 1，钳制后避免合法坐标产生 NaN。
        val bounded = a.coerceIn(0.0, 1.0)
        val c = 2 * atan2(sqrt(bounded), sqrt(1 - bounded))
        return EARTH_RADIUS_METERS * c
    }

    /**
     * 计算与页面连线和任务执行一致的开放航线长度，不额外添加返航段。
     * @param {List<Waypoint>} waypoints 按执行顺序排列的航点。
     * @return {Double} 相邻航段距离之和，单位米。
     */
    fun routeDistanceMeters(waypoints: List<Waypoint>): Double = waypoints.zipWithNext().sumOf { (a, b) ->
        haversineDistanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)
    }
}
