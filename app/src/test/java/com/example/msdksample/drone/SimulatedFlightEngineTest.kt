package com.example.msdksample.drone

import com.example.msdksample.kami.mission.AircraftTelemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SimulatedFlightEngine 纯 JVM 单测。
 *
 * 约定：航点 A(0,0)→B(0, 0.001)，赤道 0.001° ≈ 111.3m；机头朝东。
 * Engine 每 tick 前进 speed(18m/s) * 1s = 18m。
 */
class SimulatedFlightEngineTest {

    private val waypointA = NavPoint(0.0, 0.0)
    private val waypointB = NavPoint(0.0, 0.001)

    private fun runTicks(engine: SimulatedFlightEngine, times: Int) {
        repeat(times) { engine.tick() }
    }

    // ── Engine：brake 模式下遇障持续悬停，障碍清除后恢复并到达终点 ─
    @Test
    fun `brake engine hovers while obstacle present and resumes after clear`() {
        val monitor = ObstacleAvoidanceMonitor()
        var lastTelemetry: AircraftTelemetry? = null
        val engine = SimulatedFlightEngine(
            waypoints = listOf(waypointA, waypointB),
            monitor = monitor,
            telemetryListener = { lastTelemetry = it },
            statusListener = { }
        )
        // 障碍位于航段中点（约 55.6m），进入 50m 探测区后触发
        val obstacle = SimulatedObstacleFactory.createOnSegment(waypointA, waypointB, radiusMeters = 15.0)
        engine.configure("brake", listOf(obstacle))
        engine.start()

        // 推进直至触发 BRAKING（上限 30 拍）
        var sawBraking = false
        for (i in 0 until 30) {
            engine.tick()
            if (monitor.state == AvoidanceState.BRAKING) {
                sawBraking = true
                break
            }
        }
        assertTrue("前方障碍应触发 BRAKING", sawBraking)

        // 记录进入刹车时的位置，继续推 5 拍：障碍仍在 → 位置必须不变（持续悬停）
        val hoverLon = lastTelemetry?.longitude ?: error("缺少遥测")
        repeat(5) { engine.tick() }
        assertEquals("障碍仍在期间应持续悬停", hoverLon, lastTelemetry!!.longitude, 1e-9)

        // 障碍消失 → 恢复前进，最终到达终点
        monitor.setObstacles(emptyList())
        runTicks(engine, 120)
        assertTrue("清除障碍后应恢复并最终完成航线", engine.isFinished)
    }

    // ── Engine：bypass 模式下会产生横向（纬度）偏移，即肉眼可见的绕行 ─
    @Test
    fun `bypass engine produces lateral latitude offset when avoiding`() {
        val monitor = ObstacleAvoidanceMonitor()
        val latitudes = mutableListOf<Double>()
        val engine = SimulatedFlightEngine(
            waypoints = listOf(waypointA, waypointB),
            monitor = monitor,
            telemetryListener = { latitudes.add(it.latitude) },
            statusListener = { }
        )
        val obstacle = SimulatedObstacleFactory.createOnSegment(waypointA, waypointB, radiusMeters = 15.0)
        engine.configure("bypass", listOf(obstacle))
        engine.start()

        var sawBypassing = false
        for (i in 0 until 30) {
            engine.tick()
            if (monitor.state == AvoidanceState.BYPASSING) {
                sawBypassing = true
                break
            }
        }
        assertTrue("bypass 模式应进入 BYPASSING", sawBypassing)

        // 继续推进，绕行会让纬度偏离 0（正北向偏移）
        runTicks(engine, 15)
        assertTrue(
            "绕行应产生纬度偏移（实际 ${latitudes.maxOrNull()}）",
            latitudes.any { Math.abs(it) > 1e-6 }
        )
    }
}
