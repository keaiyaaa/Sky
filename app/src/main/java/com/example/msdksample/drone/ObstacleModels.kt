package com.example.msdksample.drone

import com.example.msdksample.kami.model.GeoMath
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * 避障模拟所需的数据模型与几何工具。
 *
 * 仅服务于“模拟兜底（演示）”链路与单元测试，真实 DJI 避障不依赖本文件。
 */

/** 模拟障碍物：地面圆柱（半径即水平范围，忽略高度维度） */
data class ObstacleCylinder(
    val id: String,
    val centerLatitude: Double,
    val centerLongitude: Double,
    val radiusMeters: Double
) {
    /** 圆心到 (lat,lon) 的地面距离（米） */
    fun distanceToMeters(latitude: Double, longitude: Double): Double =
        GeoMath.haversineDistanceMeters(latitude, longitude, centerLatitude, centerLongitude)
}

/** 引擎使用的轻量航点（仅经纬度/高度，不依赖 kami 的 Waypoint 模型） */
data class NavPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Float = 30f
)

/**
 * 确定性模拟障碍工厂：
 * 在相邻航段中点上（默认法向偏移 0m）放一个圆柱障碍，保证飞机沿航线飞行必会被探测到，
 * 使演示每次都能触发“检测 → 刹车/绕行”闭环。
 */
object SimulatedObstacleFactory {

    /**
     * 在航段 [a]→[b] 上放置障碍。
     * @param offsetMeters  垂直于航段方向的偏移（正=右侧）。0 表示正好位于航线上。
     */
    fun createOnSegment(
        a: NavPoint,
        b: NavPoint,
        offsetMeters: Double = 0.0,
        radiusMeters: Double = 15.0,
        id: String = "obs"
    ): ObstacleCylinder {
        val mid = DroneGeo.midpoint(a.latitude, a.longitude, b.latitude, b.longitude)
        val segmentBearing = DroneGeo.bearingDeg(a.latitude, a.longitude, b.latitude, b.longitude)
        val placement = DroneGeo.destination(mid.first, mid.second, segmentBearing + 90.0, offsetMeters)
        return ObstacleCylinder(
            id = id,
            centerLatitude = placement.first,
            centerLongitude = placement.second,
            radiusMeters = radiusMeters
        )
    }

    /** 沿整条航线每个航段放一个障碍（默认正好压在航线上） */
    fun createDemoObstacles(
        waypoints: List<NavPoint>,
        radiusMeters: Double = 15.0
    ): List<ObstacleCylinder> {
        if (waypoints.size < 2) return emptyList()
        return waypoints.zipWithNext().mapIndexed { index, (a, b) ->
            createOnSegment(a, b, radiusMeters = radiusMeters, id = "obs-$index")
        }
    }
}

/** drone 包内部几何工具（避免修改 kami.model.GeoMath） */
internal object DroneGeo {
    private const val METERS_PER_DEGREE_LAT = 111320.0

    private fun metersPerDegreeLon(latitude: Double): Double =
        METERS_PER_DEGREE_LAT * cos(Math.toRadians(latitude))

    /** 两点方位角（0~360，0=北，顺时针） */
    fun bearingDeg(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(Math.toRadians(lat2))
        val x = cos(Math.toRadians(lat1)) * sin(Math.toRadians(lat2)) -
                sin(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * cos(dLon)
        val deg = Math.toDegrees(atan2(y, x))
        return (deg + 360.0) % 360.0
    }

    /** 从 (lat,lon) 沿 bearing（度）前进 dist（米）后的坐标 */
    fun destination(lat: Double, lon: Double, bearingDeg: Double, distMeters: Double): Pair<Double, Double> {
        val dLat = distMeters / METERS_PER_DEGREE_LAT
        val dLon = distMeters / metersPerDegreeLon(lat)
        val rad = Math.toRadians(bearingDeg)
        return Pair(lat + dLat * cos(rad), lon + dLon * sin(rad))
    }

    /** 两点中点 */
    fun midpoint(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Pair<Double, Double> =
        Pair((lat1 + lat2) / 2.0, (lon1 + lon2) / 2.0)

    /** 角度差归一到 [-180,180] */
    fun angleDiffDeg(a: Double, b: Double): Double {
        var diff = (a - b) % 360.0
        if (diff > 180.0) diff -= 360.0
        if (diff < -180.0) diff += 360.0
        return diff
    }

    /** 两坐标点距离 */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double =
        GeoMath.haversineDistanceMeters(lat1, lon1, lat2, lon2)

    /** 点到圆边界距离近似（圆心距离 - 半径），用于“擦过”判定 */
    fun distanceToObstacleEdge(obstacle: ObstacleCylinder, lat: Double, lon: Double): Double =
        obstacle.distanceToMeters(lat, lon) - obstacle.radiusMeters
}
