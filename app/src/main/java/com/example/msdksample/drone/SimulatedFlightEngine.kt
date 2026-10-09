package com.example.msdksample.drone

import com.example.msdksample.kami.mission.AircraftTelemetry

/**
 * 模拟飞行引擎（演示用）：
 *
 * 沿注入的航点序列匀速推进，每 [tick] 把飞机位置喂给 [ObstacleAvoidanceMonitor]，
 * 依据决策执行：
 *  - NORMAL    → 朝向当前目标航点前进；
 *  - BRAKING   → 悬停（障碍仍在期间持续悬停并上报提示，障碍消失才恢复，避免振荡）；
 *  - BYPASSING → 按绕行计划横向偏移飞行指定距离（轨迹肉眼可见），随后回切原航点序列。
 *
 * 本类为“纯推进器”，不自带 Handler/协程：定时循环由调用方驱动（RoutePlanningActivity
 * 用 lifecycleScope 每 1s 调 [tick]），因此可在 JVM 单测中直接调用；防重入由 [running]
 * 保证，停止通过 [stop]。
 */
class SimulatedFlightEngine(
    private val waypoints: List<NavPoint>,
    private val monitor: ObstacleAvoidanceMonitor,
    private val speedMetersPerSecond: Double = 18.0,
    private val bypassTravelDistanceMeters: Double = 70.0,
    private val arrivalThresholdMeters: Double = 15.0,
    private val telemetryListener: (AircraftTelemetry) -> Unit,
    private val statusListener: (String) -> Unit
) {

    @Volatile
    private var running = false

    private var finished = false
    private var currentIndex = 0
    private var positionLat = 0.0
    private var positionLon = 0.0
    private var altitude = 30.0f
    private var headingDeg = 0.0f
    private var bypassRemaining = 0.0
    private var lastDecisionState: AvoidanceState = AvoidanceState.NORMAL

    val isRunning: Boolean get() = running
    val isFinished: Boolean get() = finished

    /** 当前目标航点下标（测试/UI 可读） */
    val targetIndex: Int get() = currentIndex

    /** 应用避障模式与障碍列表（start 前调用） */
    fun configure(action: String, obstacles: List<ObstacleCylinder>) {
        monitor.setMode(
            if (action == "bypass") ObstacleActionMode.BYPASS else ObstacleActionMode.BRAKE
        )
        monitor.setObstacles(obstacles)
    }

    /** 启动（幂等防重入）。位置重置到首个航点。 */
    fun start() {
        if (running) {
            statusListener("模拟避障演示已在运行")
            return
        }
        if (waypoints.size < 2) {
            statusListener("航点不足（至少 2 个），无法开始模拟避障演示")
            return
        }
        finished = false
        currentIndex = 0
        bypassRemaining = 0.0
        positionLat = waypoints[0].latitude
        positionLon = waypoints[0].longitude
        altitude = waypoints[0].altitude
        headingDeg = bearingToTarget()
        lastDecisionState = AvoidanceState.NORMAL
        monitor.reset()
        running = true
        statusListener("模拟避障演示已启动")
    }

    /** 停止（幂等）。由调用方在 onDestroy / 任务取消时调用。 */
    fun stop() {
        if (!running) return
        running = false
        monitor.reset()
        statusListener("模拟避障演示已停止")
    }

    /** 单步推进（时间步固定 = 1s）。调用方应保证串行调用（协程单 Job / 主线程）。 */
    fun tick() {
        if (!running || finished) return

        val decision = monitor.onTelemetry(positionLat, positionLon, headingDeg)
        val previousState = lastDecisionState
        lastDecisionState = decision.state
        val stepMeters = speedMetersPerSecond * 1.0

        when (decision.state) {
            AvoidanceState.NORMAL -> {
                // 从避障状态恢复时提示一次（避免与旧状态文本残留混淆）
                if (previousState == AvoidanceState.BRAKING || previousState == AvoidanceState.BYPASSING) {
                    statusListener("避障已解除，恢复正常飞行")
                }
                moveTowardsTarget(stepMeters)
                if (reachedTarget()) {
                    currentIndex++
                    if (currentIndex > waypoints.lastIndex) {
                        finished = true
                        running = false
                        statusListener("模拟避障演示完成（已到达终点）")
                        return
                    }
                    headingDeg = bearingToTarget()
                }
            }
            AvoidanceState.BRAKING -> {
                // 悬停：位置不动，持续上报；障碍消失后 monitor 切回 NORMAL 才恢复前进
                decision.message?.let(statusListener)
            }
            AvoidanceState.BYPASSING -> {
                val plan = decision.bypassPlan
                if (plan != null) {
                    headingDeg = plan.detourHeadingDegrees
                    bypassRemaining = bypassTravelDistanceMeters
                }
                if (bypassRemaining > 0.0) {
                    moveByHeading(stepMeters)
                    bypassRemaining -= stepMeters
                } else {
                    // 绕行距离走完但仍在威胁区：沿当前绕行方向小步推进，等待 monitor 判定清除
                    moveByHeading(stepMeters * 0.5)
                }
                decision.message?.let(statusListener)
            }
        }
    }

    private fun moveTowardsTarget(stepMeters: Double) {
        headingDeg = bearingToTarget()
        moveByHeading(stepMeters)
    }

    private fun moveByHeading(stepMeters: Double) {
        val next = DroneGeo.destination(positionLat, positionLon, headingDeg.toDouble(), stepMeters)
        positionLat = next.first
        positionLon = next.second
        telemetryListener(
            AircraftTelemetry(
                latitude = positionLat,
                longitude = positionLon,
                altitude = altitude,
                headingDegrees = headingDeg
            )
        )
    }

    private fun bearingToTarget(): Float {
        val target = waypoints[currentIndex.coerceAtMost(waypoints.lastIndex)]
        return DroneGeo.bearingDeg(positionLat, positionLon, target.latitude, target.longitude).toFloat()
    }

    private fun reachedTarget(): Boolean {
        val target = waypoints[currentIndex]
        return DroneGeo.distanceMeters(positionLat, positionLon, target.latitude, target.longitude) <= arrivalThresholdMeters
    }
}
