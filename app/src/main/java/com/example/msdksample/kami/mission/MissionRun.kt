package com.example.msdksample.kami.mission

/** 持久化任务状态；终态不可被迟到回调覆盖。 */
enum class MissionRunState(val label: String) {
    PREPARING("准备中"), UPLOADING("模拟上传中"), RUNNING("模拟执行中"), PAUSED("已暂停"),
    COMPLETED("模拟完成"), CANCELLED("已取消"), FAILED("模拟失败"), INTERRUPTED("执行中断");

    val isActive: Boolean get() = this in setOf(PREPARING, UPLOADING, RUNNING, PAUSED)
}

/** 用户主动选择的可重复本地场景，不改变任何真实设备状态。 */
enum class MissionScenario(val label: String) {
    NORMAL("正常巡检"), UPLOAD_FAILURE("模拟上传失败"), DISCONNECT("模拟执行中断连")
}

/** 页面和查询共用同一份已落库的执行结果。 */
data class MissionRun(
    val id: String, val routeId: String, val routeName: String, val taskName: String,
    val state: MissionRunState, val scenario: MissionScenario, val totalPoints: Int,
    val reachedPoints: Int, val latitude: Double, val longitude: Double, val altitude: Float,
    val message: String, val startedAt: Long, val updatedAt: Long, val endedAt: Long, val revision: Long
)

/** 执行明细中的状态和到点事件。 */
data class MissionRunEvent(val state: MissionRunState, val message: String, val occurredAt: Long)
