package com.example.msdksample.kami.model

object RouteValidation {
    private const val MIN_LATITUDE = -90.0
    private const val MAX_LATITUDE = 90.0
    private const val MIN_LONGITUDE = -180.0
    private const val MAX_LONGITUDE = 180.0
    private const val MIN_ALTITUDE = 0f
    private const val MAX_ALTITUDE = 500f

    /**
     * 校验航线名称并在为空时补充默认名称。
     * @param {RoutePlan} route 需要保存或导出的航线对象。
     * @returns {RoutePlan} 已补齐合法名称的航线对象。
     */
    fun normalizeRoute(route: RoutePlan): RoutePlan {
        if (route.name.isBlank()) {
            route.name = "未命名航线"
        }
        return route
    }

    /**
     * 校验单个航点是否满足可保存与可导出的基础约束。
     * @param {Waypoint} waypoint 需要校验的航点对象。
     * @returns {Unit} 校验通过无返回；失败时抛出 IllegalArgumentException。
     */
    fun requireValidWaypoint(waypoint: Waypoint) {
        require(waypoint.latitude in MIN_LATITUDE..MAX_LATITUDE) {
            "航点纬度超出范围: ${waypoint.latitude}"
        }
        require(waypoint.longitude in MIN_LONGITUDE..MAX_LONGITUDE) {
            "航点经度超出范围: ${waypoint.longitude}"
        }
        require(waypoint.altitude in MIN_ALTITUDE..MAX_ALTITUDE) {
            "航点高度超出范围: ${waypoint.altitude}"
        }
    }

    /**
     * 校验航点列表的顺序和基础坐标范围。
     * @param {List<Waypoint>} waypoints 需要保存或导出的航点列表。
     * @returns {Unit} 校验通过无返回；失败时抛出 IllegalArgumentException。
     */
    fun requireValidWaypoints(waypoints: List<Waypoint>) {
        waypoints.forEach { waypoint ->
            requireValidWaypoint(waypoint)
        }
    }
}
