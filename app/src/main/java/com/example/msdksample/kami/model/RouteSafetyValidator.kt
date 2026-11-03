package com.example.msdksample.kami.model

import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * 统一添加、编辑、拖拽、保存、优化和执行前的示例空域规则。
 * @param {List<FlightZone>} zones 参与校验的圆形区域，默认仅为教学示例数据。
 */
class RouteSafetyValidator(private val zones: List<FlightZone> = DefaultFlightZones.zones) {
    /**
     * 校验整条航线的航点与相邻航段；参考区不会覆盖更严格的禁飞和限高规则。
     * @param {List<Waypoint>} waypoints 按执行顺序排列的航点。
     * @param {Boolean} forExecution 是否要求至少两个航点以启动任务。
     * @return {Unit} 通过时正常返回；违规时抛出包含原因的 IllegalArgumentException。
     */
    fun requireSafe(waypoints: List<Waypoint>, forExecution: Boolean = false) {
        RouteValidation.requireValidWaypoints(waypoints)
        if (forExecution) require(waypoints.size >= 2) { "至少需要 2 个航点才能执行任务" }
        zones.filter { it.type != ZoneType.SAFE }.forEach { zone ->
            require(zone.radiusMeters.isFinite() && zone.radiusMeters > 0) { "区域半径无效" }
            waypoints.forEachIndexed { index, point ->
                if (zone.contains(point.latitude, point.longitude)) {
                    requireAllowed(zone, point.altitude, "航点 ${index + 1}")
                }
            }
            waypoints.zipWithNext().forEachIndexed { index, (a, b) ->
                if (distanceToSegment(zone, a, b) <= zone.radiusMeters + 0.01) {
                    // 对穿越区域的整段取较高端点高度，采用保守规则，不把端点在区外当作安全。
                    requireAllowed(zone, maxOf(a.altitude, b.altitude), "航段 ${index + 1}→${index + 2}")
                }
            }
        }
    }

    /**
     * 根据区域类型拒绝禁飞和超过示例限高的轨迹。
     * @param {FlightZone} zone 命中的区域规则。
     * @param {Float} altitude 待校验的相对高度，单位米。
     * @param {String} location 面向用户显示的航点或航段位置。
     * @return {Unit} 通过时正常返回；违规时抛出 IllegalArgumentException。
     */
    private fun requireAllowed(zone: FlightZone, altitude: Float, location: String) {
        require(zone.type != ZoneType.NO_FLY) { "$location 进入${zone.name}，请调整航线" }
        if (zone.type == ZoneType.RESTRICTED) {
            val limit = zone.maxAltitudeMeters
            require(limit != null && limit.isFinite() && limit >= 0f) { "${zone.name}缺少有效限高规则" }
            require(altitude <= limit) { "$location 超过${zone.name}的 ${limit.toInt()} 米上限" }
        }
    }

    /**
     * 用球面横向距离计算圆心到最短大圆航段的距离，兼容日期变更线和重复坐标。
     * @param {FlightZone} zone 查询距离的区域圆心。
     * @param {Waypoint} a 航段起点。
     * @param {Waypoint} b 航段终点。
     * @return {Double} 圆心到航段的最短距离，单位米。
     */
    private fun distanceToSegment(zone: FlightZone, a: Waypoint, b: Waypoint): Double {
        val length = GeoMath.haversineDistanceMeters(a.latitude, a.longitude, b.latitude, b.longitude)
        val startDistance = GeoMath.haversineDistanceMeters(a.latitude, a.longitude, zone.centerLatitude, zone.centerLongitude)
        val endDistance = GeoMath.haversineDistanceMeters(b.latitude, b.longitude, zone.centerLatitude, zone.centerLongitude)
        if (length < 0.001) return startDistance
        val angle = startDistance / EARTH_RADIUS
        val headingDifference = bearing(a.latitude, a.longitude, zone.centerLatitude, zone.centerLongitude) -
            bearing(a.latitude, a.longitude, b.latitude, b.longitude)
        val alongTrack = atan2(sin(angle) * cos(headingDifference), cos(angle)) * EARTH_RADIUS
        if (alongTrack < 0 || alongTrack > length) return minOf(startDistance, endDistance)
        return abs(asin((sin(angle) * sin(headingDifference)).coerceIn(-1.0, 1.0))) * EARTH_RADIUS
    }

    /**
     * 计算球面初始方位角。
     * @param {Double} lat1 起点纬度，单位度。
     * @param {Double} lon1 起点经度，单位度。
     * @param {Double} lat2 终点纬度，单位度。
     * @param {Double} lon2 终点经度，单位度。
     * @return {Double} 起点指向终点的方位角，单位弧度。
     */
    private fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val start = Math.toRadians(lat1)
        val end = Math.toRadians(lat2)
        val delta = Math.toRadians(lon2 - lon1)
        return atan2(sin(delta) * cos(end), cos(start) * sin(end) - sin(start) * cos(end) * cos(delta))
    }

    companion object { private const val EARTH_RADIUS = 6371000.0 }
}
