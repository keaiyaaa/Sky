package com.example.msdksample.drone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * ObstacleAvoidanceMonitor 纯 JVM 单测。
 *
 * 约定场景：
 *  - 坐标原点 (0,0)，机头朝东（heading = 90°）；
 *  - 赤道处 1° 经度 ≈ 111_320m，30m ≈ 0.0002695°。
 */
class ObstacleAvoidanceMonitorTest {

    private fun obstacleEastOf(distanceMeters: Double, id: String = "obs"): ObstacleCylinder =
        ObstacleCylinder(id = id, centerLatitude = 0.0, centerLongitude = distanceMeters / 111_320.0, radiusMeters = 15.0)

    // ── 场景 1：正前方存在障碍 → brake 刹车悬停 ─────────────────
    @Test
    fun `front obstacle in brake mode leads to BRAKING and stays while obstacle remains`() {
        val monitor = ObstacleAvoidanceMonitor()
        monitor.setMode(ObstacleActionMode.BRAKE)
        monitor.setObstacles(listOf(obstacleEastOf(30.0)))

        // 防抖：第一拍有威胁但尚未进入 BRAKING
        val first = monitor.onTelemetry(0.0, 0.0, 90f)
        assertEquals(AvoidanceState.NORMAL, first.state)
        assertNotNull(first.nearestObstacleDistanceMeters)

        // 第二拍：连续威胁达到 debounce → BRAKING
        val second = monitor.onTelemetry(0.0, 0.0, 90f)
        assertEquals(AvoidanceState.BRAKING, second.state)
        assertNotNull(second.message)

        // 障碍仍在 → 持续 BRAKING，不振荡回 NORMAL
        val third = monitor.onTelemetry(0.0, 0.0, 90f)
        val fourth = monitor.onTelemetry(0.0, 0.0, 90f)
        assertEquals(AvoidanceState.BRAKING, third.state)
        assertEquals(AvoidanceState.BRAKING, fourth.state)
    }

    // ── 场景 2：正前方存在障碍且可绕 → bypass 绕行 ─────────────
    @Test
    fun `front obstacle in bypass mode leads to BYPASSING with detour plan`() {
        val monitor = ObstacleAvoidanceMonitor()
        monitor.setMode(ObstacleActionMode.BYPASS)
        monitor.setObstacles(listOf(obstacleEastOf(30.0)))

        monitor.onTelemetry(0.0, 0.0, 90f) // 威胁 1
        val decision = monitor.onTelemetry(0.0, 0.0, 90f) // 威胁 2 → BYPASSING
        assertEquals(AvoidanceState.BYPASSING, decision.state)
        assertNotNull(decision.message)

        val plan = decision.bypassPlan
        assertNotNull("BYPASSING 决策必须携带绕行计划", plan)
        // 障碍正东（相对方位角 0°，>=0）→ 选择向右绕：heading 90 - 90 = 0°（北）
        assertEquals(0f, plan!!.detourHeadingDegrees, 1f)
    }

    // ── 场景 3：障碍清除后恢复 NORMAL ───────────────────────────
    @Test
    fun `braking aircraft recovers to NORMAL after obstacle is cleared`() {
        val monitor = ObstacleAvoidanceMonitor()
        monitor.setMode(ObstacleActionMode.BRAKE)
        monitor.setObstacles(listOf(obstacleEastOf(30.0)))

        monitor.onTelemetry(0.0, 0.0, 90f)
        val braking = monitor.onTelemetry(0.0, 0.0, 90f)
        assertEquals(AvoidanceState.BRAKING, braking.state)

        // 障碍消失
        monitor.setObstacles(emptyList())

        val clear1 = monitor.onTelemetry(0.0, 0.0, 90f)
        assertEquals("清除防抖第一拍仍应保持 BRAKING", AvoidanceState.BRAKING, clear1.state)
        assertNull(clear1.nearestObstacleDistanceMeters)

        val clear2 = monitor.onTelemetry(0.0, 0.0, 90f)
        assertEquals(AvoidanceState.NORMAL, clear2.state)
        assertNull(clear2.message)
    }
}
