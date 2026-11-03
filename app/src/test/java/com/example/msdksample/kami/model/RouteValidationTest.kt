package com.example.msdksample.kami.model

import org.junit.Assert.assertThrows
import org.junit.Test

/** 航线持久化入口的参数边界回归测试。 */
class RouteValidationTest {
    /**
     * 验证从非页面入口提交超量航点时仍被业务层拒绝。
     * @return {Unit} 断言失败时抛出测试异常。
     */
    @Test fun rejectsMoreThanNinetyNineWaypoints() {
        assertThrows(IllegalArgumentException::class.java) {
            RouteValidation.requireValidWaypoints(List(100) { Waypoint(latitude = 31.0, longitude = 121.0) })
        }
    }

    /**
     * 验证重复主键不会在 SQLite 写入时静默覆盖其他航点。
     * @return {Unit} 断言失败时抛出测试异常。
     */
    @Test fun rejectsDuplicateWaypointIds() {
        val point = Waypoint(latitude = 31.0, longitude = 121.0)
        assertThrows(IllegalArgumentException::class.java) {
            RouteValidation.requireValidWaypoints(listOf(point, point.copy()))
        }
    }

    /**
     * 验证非法经纬度和高度不会被保存。
     * @return {Unit} 断言失败时抛出测试异常。
     */
    @Test fun rejectsNonFiniteValues() {
        listOf(
            Waypoint(latitude = Double.NaN, longitude = 121.0),
            Waypoint(latitude = 31.0, longitude = Double.POSITIVE_INFINITY),
            Waypoint(latitude = 31.0, longitude = 121.0, altitude = Float.NaN)
        ).forEach { point ->
            assertThrows(IllegalArgumentException::class.java) { RouteValidation.requireValidWaypoint(point) }
        }
    }
}
