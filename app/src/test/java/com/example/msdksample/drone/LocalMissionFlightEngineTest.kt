package com.example.msdksample.drone

import com.example.msdksample.kami.mission.AircraftTelemetry
import org.junit.Assert.*
import org.junit.Test

/** 自动巡检复用模拟引擎时的短航段、重复坐标和最终位置回归测试。 */
class LocalMissionFlightEngineTest {
    /**
     * 验证重复点和不足一步的航段均能精确到达，不发生越点振荡。
     * @return {Unit} 所有断言通过后返回。
     */
    @Test fun reachesEveryShortAndDuplicateWaypointExactly() {
        val points = listOf(NavPoint(31.0, 121.0, 30f), NavPoint(31.0, 121.0, 40f), NavPoint(31.00001, 121.00001, 50f))
        var last: AircraftTelemetry? = null
        val engine = SimulatedFlightEngine(points, ObstacleAvoidanceMonitor(), arrivalThresholdMeters = 0.05,
            telemetryListener = { last = it }, statusListener = {})
        engine.configure("brake", emptyList())
        engine.start()
        repeat(10) { engine.tick() }
        assertTrue(engine.isFinished)
        assertFalse(engine.isRunning)
        assertEquals(3, engine.targetIndex)
        assertEquals(points.last().latitude, last!!.latitude, 1e-9)
        assertEquals(points.last().longitude, last!!.longitude, 1e-9)
        assertEquals(50f, last!!.altitude)
    }
}
