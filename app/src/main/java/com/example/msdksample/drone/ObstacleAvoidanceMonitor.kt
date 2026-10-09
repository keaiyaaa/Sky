package com.example.msdksample.drone

import kotlin.math.abs

/**
 * 避障行为语义（与设置页 brake/bypass 字符串对应）。
 */
enum class ObstacleActionMode {
    /** 刹车悬停：遇障悬停，障碍消失才恢复 */
    BRAKE,

    /** 绕行：生成横向偏移航向绕飞障碍 */
    BYPASS
}

/** 避障状态机状态 */
enum class AvoidanceState {
    /** 无障碍威胁，正常飞行 */
    NORMAL,

    /** 避障中（刹车悬停） */
    BRAKING,

    /** 避障中（绕行） */
    BYPASSING
}

/** 绕行计划（Engine 执行侧向绕行所需） */
data class BypassPlan(
    val detourHeadingDegrees: Float,
    val obstacleLatitude: Double,
    val obstacleLongitude: Double
)

/** 单次探测-决策结果 */
data class AvoidanceDecision(
    val state: AvoidanceState,
    /** 前方最近障碍距离（米）；无威胁时 null */
    val nearestObstacleDistanceMeters: Double?,
    /** 绕行计划（state==BYPASSING 时非空） */
    val bypassPlan: BypassPlan? = null,
    /** 面向 UI 的提示文案 */
    val message: String? = null
)

/**
 * 统一“探测 → 决策”层：实时或模拟两阶段可复用。
 *
 * 几何约定：
 *  - 探测区域 = 机头前方 [lookAheadMeters] 米、与机头夹角 ≤ [detectionConeHalfAngleDegrees] 的扇形；
 *  - 防抖：连续 [debounceTicks] 次满足威胁 / 清除才切换状态，避免机头抖动造成的误触发。
 *
 * 刹车语义（修订版）：一旦进入 [AvoidanceState.BRAKING]，
 * 只有在“前方威胁持续消失 [debounceTicks] 次”后才恢复 NORMAL——
 * 障碍仍在期间持续悬停，避免“悬停-恢复”振荡。
 *
 * 本类为纯 Kotlin，可在 JVM 单测中直接使用。
 */
class ObstacleAvoidanceMonitor(
    private val lookAheadMeters: Double = 50.0,
    private val detectionConeHalfAngleDegrees: Double = 60.0,
    private val debounceTicks: Int = 2
) {

    var mode: ObstacleActionMode = ObstacleActionMode.BRAKE
        private set

    var state: AvoidanceState = AvoidanceState.NORMAL
        private set

    private var obstacles: List<ObstacleCylinder> = emptyList()
    private var threatStreak = 0
    private var clearStreak = 0

    fun setMode(actionMode: ObstacleActionMode) {
        mode = actionMode
    }

    fun setObstacles(list: List<ObstacleCylinder>) {
        obstacles = list
        resetStreaks()
    }

    fun reset() {
        state = AvoidanceState.NORMAL
        resetStreaks()
    }

    private fun resetStreaks() {
        threatStreak = 0
        clearStreak = 0
    }

    /** 每收到一次遥测调用一次，返回最新决策 */
    fun onTelemetry(latitude: Double, longitude: Double, headingDegrees: Float): AvoidanceDecision {
        val front = nearestFrontObstacle(latitude, longitude, headingDegrees)
        val hasThreat = front != null

        if (hasThreat) {
            threatStreak++
            clearStreak = 0
        } else {
            threatStreak = 0
            clearStreak++
        }

        when {
            // NORMAL → 进入避障（需防抖）
            state == AvoidanceState.NORMAL && hasThreat && threatStreak >= debounceTicks -> {
                state = if (mode == ObstacleActionMode.BRAKE) {
                    AvoidanceState.BRAKING
                } else {
                    AvoidanceState.BYPASSING
                }
            }
            // BRAKING：障碍仍在 → 保持悬停；障碍消失（防抖）→ 恢复
            state == AvoidanceState.BRAKING && !hasThreat && clearStreak >= debounceTicks -> {
                state = AvoidanceState.NORMAL
            }
            // BYPASSING：绕行成功后前方不再有威胁 → 恢复
            state == AvoidanceState.BYPASSING && !hasThreat && clearStreak >= debounceTicks -> {
                state = AvoidanceState.NORMAL
            }
        }

        val nearestDistance = front?.distanceMeters
        return AvoidanceDecision(
            state = state,
            nearestObstacleDistanceMeters = nearestDistance,
            bypassPlan = if (state == AvoidanceState.BYPASSING && front != null) {
                computeBypassPlan(latitude, longitude, headingDegrees, front.obstacle)
            } else {
                null
            },
            message = buildMessage(nearestDistance)
        )
    }

    /**
     * 前方最近障碍（圆形探测锥内的障碍，按圆心距离取最近）。
     * 返回障碍与其圆心距离。
     */
    private fun nearestFrontObstacle(
        latitude: Double,
        longitude: Double,
        headingDegrees: Float
    ): FrontObstacle? {
        var best: FrontObstacle? = null
        for (obstacle in obstacles) {
            val centerDistance = obstacle.distanceToMeters(latitude, longitude)
            if (centerDistance > lookAheadMeters) continue
            val obstacleBearing = DroneGeo.bearingDeg(latitude, longitude, obstacle.centerLatitude, obstacle.centerLongitude)
            val angleDiff = abs(DroneGeo.angleDiffDeg(obstacleBearing, headingDegrees.toDouble()))
            if (angleDiff <= detectionConeHalfAngleDegrees) {
                if (best == null || centerDistance < best.distanceMeters) {
                    best = FrontObstacle(obstacle, centerDistance)
                }
            }
        }
        return best
    }

    /**
     * 计算绕行航向：转向障碍相对机头方位的“对侧”90°，
     * 即障碍偏左则向右绕，障碍偏右则向左绕，保证绕行远离障碍。
     */
    private fun computeBypassPlan(
        latitude: Double,
        longitude: Double,
        headingDegrees: Float,
        obstacle: ObstacleCylinder
    ): BypassPlan {
        val obstacleBearing = DroneGeo.bearingDeg(latitude, longitude, obstacle.centerLatitude, obstacle.centerLongitude)
        val rel = DroneGeo.angleDiffDeg(obstacleBearing, headingDegrees.toDouble())
        val detourHeading = if (rel >= 0) headingDegrees - 90f else headingDegrees + 90f
        return BypassPlan(
            detourHeadingDegrees = (detourHeading + 360f) % 360f,
            obstacleLatitude = obstacle.centerLatitude,
            obstacleLongitude = obstacle.centerLongitude
        )
    }

    private fun buildMessage(nearestDistance: Double?): String? {
        return when (state) {
            AvoidanceState.BRAKING -> "检测到前方障碍（${nearestDistance?.toInt() ?: "?"}m），悬停避让中…"
            AvoidanceState.BYPASSING -> "检测到前方障碍，正在绕行…"
            AvoidanceState.NORMAL -> null
        }
    }

    private data class FrontObstacle(
        val obstacle: ObstacleCylinder,
        val distanceMeters: Double
    )
}
