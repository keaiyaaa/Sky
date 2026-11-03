package com.example.msdksample.kami.model

import org.junit.Assert.assertThrows
import org.junit.Test

/** 示例区域规则的点、航段、重叠和限高边界测试。 */
class RouteSafetyValidatorTest {
    private val noFly = FlightZone("no-fly", "测试禁飞区", 0.0, 0.0, 100.0, ZoneType.NO_FLY)

    /**
     * 验证端点在区外但航段穿越区域时仍拦截。
     * @return {Unit} 测试断言结果。
     */
    @Test fun rejectsSegmentCrossingWithBothEndpointsOutside() {
        assertThrows(IllegalArgumentException::class.java) {
            RouteSafetyValidator(listOf(noFly)).requireSafe(listOf(
                Waypoint(latitude = 0.0, longitude = -0.002), Waypoint(latitude = 0.0, longitude = 0.002)))
        }
    }

    /**
     * 验证参考区不会覆盖禁飞规则。
     * @return {Unit} 测试断言结果。
     */
    @Test fun safeReferenceDoesNotOverrideNoFlyZone() {
        val reference = noFly.copy(id = "reference", type = ZoneType.SAFE)
        assertThrows(IllegalArgumentException::class.java) {
            RouteSafetyValidator(listOf(reference, noFly)).requireSafe(listOf(Waypoint(latitude = 0.0, longitude = 0.0)))
        }
    }

    /**
     * 验证限高规则真实拦截超高航点，边界高度可以通过。
     * @return {Unit} 测试断言结果。
     */
    @Test fun enforcesAltitudeIncludingBoundary() {
        val restricted = noFly.copy(type = ZoneType.RESTRICTED, maxAltitudeMeters = 60f)
        val validator = RouteSafetyValidator(listOf(restricted))
        validator.requireSafe(listOf(Waypoint(latitude = 0.0, longitude = 0.0, altitude = 60f)))
        assertThrows(IllegalArgumentException::class.java) {
            validator.requireSafe(listOf(Waypoint(latitude = 0.0, longitude = 0.0, altitude = 60.01f)))
        }
    }

    /**
     * 验证重复坐标和邻近但未相交的航段不会产生数值异常或误判。
     * @return {Unit} 测试断言结果。
     */
    @Test fun acceptsOutsideAndDuplicateCoordinates() {
        val points = listOf(Waypoint(latitude = 0.002, longitude = -0.002),
            Waypoint(latitude = 0.002, longitude = -0.002), Waypoint(latitude = 0.002, longitude = 0.002))
        RouteSafetyValidator(listOf(noFly)).requireSafe(points, true)
    }

    /**
     * 验证跨日期变更线的最短航段仍能命中圆形禁飞区。
     * @return {Unit} 测试断言结果。
     */
    @Test fun checksDatelineCrossing() {
        val zone = noFly.copy(centerLongitude = 180.0)
        assertThrows(IllegalArgumentException::class.java) {
            RouteSafetyValidator(listOf(zone)).requireSafe(listOf(
                Waypoint(latitude = 0.0, longitude = 179.998), Waypoint(latitude = 0.0, longitude = -179.998)))
        }
    }

    /**
     * 验证空航线允许编辑保存，但不允许执行。
     * @return {Unit} 测试断言结果。
     */
    @Test fun emptyRouteCannotExecute() {
        RouteSafetyValidator().requireSafe(emptyList())
        assertThrows(IllegalArgumentException::class.java) { RouteSafetyValidator().requireSafe(emptyList(), true) }
    }
}
