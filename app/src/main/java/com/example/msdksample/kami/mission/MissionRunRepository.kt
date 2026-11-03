package com.example.msdksample.kami.mission

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import com.example.msdksample.kami.data.RouteDatabaseHelper
import com.example.msdksample.kami.data.SqliteRouteDao
import com.example.msdksample.kami.model.RouteSafetyValidator
import com.example.msdksample.kami.model.Waypoint
import java.io.Closeable
import java.util.UUID

/**
 * 使用正式航线数据库持久化模拟执行快照、进度和事件，不创建演示种子数据。
 * @param {Context} context 仅持有 applicationContext，避免泄漏页面。
 */
class MissionRunRepository(context: Context) : Closeable {
    private val helper = RouteDatabaseHelper(context.applicationContext)

    /**
     * 从数据库中的已保存航线创建不可变任务输入，并取得唯一活动任务锁。
     * @param {String} routeId 已保存航线标识。
     * @param {MissionScenario} scenario 明确选择的本地验证场景。
     * @return {MissionRun} 已提交到 SQLite 的准备中任务；非法输入或并发任务会抛出异常。
     */
    fun create(routeId: String, scenario: MissionScenario): MissionRun {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            check(db.rawQuery("SELECT 1 FROM mission_runs WHERE state IN (${MissionRunSchema.ACTIVE_STATES})", null)
                .use { !it.moveToFirst() }) { "已有模拟任务正在执行，请先取消或等待完成" }
            val mission = SqliteRouteDao(helper).getRoute(routeId) ?: error("航线不存在，请重新选择")
            RouteSafetyValidator().requireSafe(mission.waypoints, forExecution = true)
            val now = System.currentTimeMillis()
            val first = mission.waypoints.first()
            val run = MissionRun(UUID.randomUUID().toString(), routeId, mission.route.name, mission.route.taskName,
                MissionRunState.PREPARING, scenario, mission.waypoints.size, 0,
                first.latitude, first.longitude, first.altitude, "本地模拟准备中；未向飞机发送指令", now, now, 0, 0)
            db.insertOrThrow("mission_runs", null, values(run))
            mission.waypoints.forEachIndexed { index, point ->
                db.insertOrThrow("mission_run_waypoints", null, ContentValues().apply {
                    put("run_id", run.id); put("sequence", index + 1); put("waypoint_id", point.id)
                    put("latitude", point.latitude); put("longitude", point.longitude)
                    put("altitude", point.altitude); put("action", point.action)
                })
            }
            appendEvent(run)
            db.setTransactionSuccessful()
            return run
        } finally { db.endTransaction() }
    }

    /**
     * 读取一个执行结果；不会用当前航线替换历史快照。
     * @param {String} runId 执行记录标识。
     * @return {MissionRun?} 不存在时为 null。
     */
    fun get(runId: String): MissionRun? = helper.readableDatabase.query(
        "mission_runs", null, "id = ?", arrayOf(runId), null, null, null
    ).use { if (it.moveToFirst()) readRun(it) else null }

    /**
     * 分页查询执行历史，包含完成、失败、取消和中断状态。
     * @param {Int} limit 本页最多条数，范围 1 至 100。
     * @param {Int} offset 已跳过的历史条数。
     * @return {List<MissionRun>} 按启动时间倒序排列的已落库结果。
     */
    fun history(limit: Int = 50, offset: Int = 0): List<MissionRun> {
        require(limit in 1..100 && offset >= 0) { "历史分页参数无效" }
        return helper.readableDatabase.query("mission_runs", null, null, null, null, null,
            "started_at DESC, id DESC", "$offset,$limit").use { rows ->
            buildList { while (rows.moveToNext()) add(readRun(rows)) }
        }
    }

    /**
     * 获取任务启动时的航点快照，原航线删除后仍可追溯。
     * @param {String} runId 执行记录标识。
     * @return {List<Waypoint>} 按原执行顺序排列的快照。
     */
    fun waypoints(runId: String): List<Waypoint> = helper.readableDatabase.query(
        "mission_run_waypoints", null, "run_id = ?", arrayOf(runId), null, null, "sequence ASC"
    ).use { rows ->
        buildList {
            while (rows.moveToNext()) add(Waypoint(id = rows.getString(rows.getColumnIndexOrThrow("waypoint_id")),
                sequence = rows.getInt(rows.getColumnIndexOrThrow("sequence")),
                latitude = rows.getDouble(rows.getColumnIndexOrThrow("latitude")),
                longitude = rows.getDouble(rows.getColumnIndexOrThrow("longitude")),
                altitude = rows.getFloat(rows.getColumnIndexOrThrow("altitude")),
                action = rows.getString(rows.getColumnIndexOrThrow("action"))))
        }
    }

    /**
     * 获取任务的全部状态和到点事件，供失败排查和历史详情使用。
     * @param {String} runId 执行记录标识。
     * @return {List<MissionRunEvent>} 按事件序号排列的历史。
     */
    fun events(runId: String): List<MissionRunEvent> = helper.readableDatabase.query(
        "mission_run_events", null, "run_id = ?", arrayOf(runId), null, null, "sequence ASC"
    ).use { rows ->
        buildList {
            while (rows.moveToNext()) add(MissionRunEvent(
                MissionRunState.valueOf(rows.getString(rows.getColumnIndexOrThrow("state"))),
                rows.getString(rows.getColumnIndexOrThrow("message")),
                rows.getLong(rows.getColumnIndexOrThrow("occurred_at"))))
        }
    }

    /**
     * 在同一事务中提交状态、进度和事件；版本号阻止取消后的迟到结果覆盖终态。
     * @param {MissionRun} previous 调用方刚读取的任务和版本。
     * @param {MissionRunState} state 目标状态，必须符合状态机。
     * @param {String} message 本次状态或进度的业务说明。
     * @param {Int} reached 已到达航点数，必须单调递增。
     * @param {AircraftTelemetry?} telemetry 本地模拟位置；未传时保留上次位置。
     * @return {MissionRun} 提交成功的最新结果；冲突或非法状态会抛出异常。
     */
    fun update(previous: MissionRun, state: MissionRunState, message: String,
               reached: Int = previous.reachedPoints, telemetry: AircraftTelemetry? = null): MissionRun {
        check(previous.state.isActive) { "任务已结束，不能再次更改" }
        check(isAllowed(previous.state, state)) { "不允许的任务状态变化：${previous.state.label} → ${state.label}" }
        require(reached in previous.reachedPoints..previous.totalPoints) { "任务进度无效" }
        require(state != MissionRunState.COMPLETED || reached == previous.totalPoints) { "尚未到达全部航点" }
        if (telemetry != null) require(telemetry.latitude.isFinite() && telemetry.latitude in -90.0..90.0 &&
            telemetry.longitude.isFinite() && telemetry.longitude in -180.0..180.0 && telemetry.altitude.isFinite() &&
            telemetry.altitude in 0f..500f) { "模拟遥测无效" }
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val current = get(previous.id) ?: error("任务记录不存在")
            check(current.revision == previous.revision && current.state == previous.state) { "任务状态已变化，请刷新后重试" }
            val now = System.currentTimeMillis()
            val updated = current.copy(state = state, message = message, reachedPoints = reached,
                latitude = telemetry?.latitude ?: current.latitude, longitude = telemetry?.longitude ?: current.longitude,
                altitude = telemetry?.altitude ?: current.altitude, updatedAt = now,
                endedAt = if (state.isActive) 0 else now, revision = current.revision + 1)
            check(db.update("mission_runs", values(updated), "id = ? AND revision = ?",
                arrayOf(current.id, current.revision.toString())) == 1) { "任务状态提交冲突" }
            if (state != current.state || reached != current.reachedPoints) appendEvent(updated)
            db.setTransactionSuccessful()
            return updated
        } finally { db.endTransaction() }
    }

    /**
     * 新进程启动时核销旧进程未完成的执行，禁止把进程退出当作成功或自动重飞。
     * @return {Unit} 所有遗留活动任务转换为可查询的中断终态。
     */
    fun recoverInterrupted() {
        val active = helper.readableDatabase.rawQuery("SELECT * FROM mission_runs WHERE state IN (${MissionRunSchema.ACTIVE_STATES})", null)
            .use { rows -> buildList { while (rows.moveToNext()) add(readRun(rows)) } }
        active.forEach { update(it, MissionRunState.INTERRUPTED, "应用曾关闭，模拟任务已中断；请手动重新发起") }
    }

    /**
     * 判断状态机的合法边，失败、取消及进程中断可以从任何活动态进入。
     * @param {MissionRunState} from 当前状态。
     * @param {MissionRunState} to 目标状态。
     * @return {Boolean} 是否允许本次转换。
     */
    private fun isAllowed(from: MissionRunState, to: MissionRunState): Boolean =
        to in setOf(MissionRunState.CANCELLED, MissionRunState.FAILED, MissionRunState.INTERRUPTED) || when (from) {
            MissionRunState.PREPARING -> to == MissionRunState.UPLOADING
            MissionRunState.UPLOADING -> to == MissionRunState.RUNNING
            MissionRunState.RUNNING -> to in setOf(MissionRunState.RUNNING, MissionRunState.PAUSED, MissionRunState.COMPLETED)
            MissionRunState.PAUSED -> to == MissionRunState.RUNNING
            else -> false
        }

    /**
     * 追加事务内的单调事件序号。
     * @param {MissionRun} run 已通过校验的最新记录。
     * @return {Unit} 写入失败时抛出异常并随主记录回滚。
     */
    private fun appendEvent(run: MissionRun) {
        val db = helper.writableDatabase
        val sequence = db.rawQuery("SELECT COALESCE(MAX(sequence),0)+1 FROM mission_run_events WHERE run_id = ?", arrayOf(run.id))
            .use { it.moveToFirst(); it.getInt(0) }
        db.insertOrThrow("mission_run_events", null, ContentValues().apply {
            put("run_id", run.id); put("sequence", sequence); put("state", run.state.name)
            put("message", run.message); put("occurred_at", run.updatedAt)
        })
    }

    /**
     * 将已验证的任务转换为 SQLite 字段。
     * @param {MissionRun} run 任务业务结果。
     * @return {ContentValues} 与 mission_runs 一一对应的存储字段。
     */
    private fun values(run: MissionRun): ContentValues = ContentValues().apply {
        put("id", run.id); put("route_id", run.routeId); put("route_name", run.routeName); put("task_name", run.taskName)
        put("mode", "LOCAL_SIMULATION"); put("state", run.state.name); put("scenario", run.scenario.name)
        put("total_points", run.totalPoints); put("reached_points", run.reachedPoints)
        put("latitude", run.latitude); put("longitude", run.longitude); put("altitude", run.altitude)
        put("message", run.message); put("started_at", run.startedAt); put("updated_at", run.updatedAt)
        put("ended_at", run.endedAt); put("revision", run.revision)
    }

    /**
     * 读取数据库中的任务实体，未知状态明确报错而不伪装成功。
     * @param {Cursor} row 已定位到一行结果的游标。
     * @return {MissionRun} 完整执行实体。
     */
    private fun readRun(row: Cursor): MissionRun = MissionRun(
        row.getString(row.getColumnIndexOrThrow("id")), row.getString(row.getColumnIndexOrThrow("route_id")),
        row.getString(row.getColumnIndexOrThrow("route_name")), row.getString(row.getColumnIndexOrThrow("task_name")),
        MissionRunState.valueOf(row.getString(row.getColumnIndexOrThrow("state"))),
        MissionScenario.valueOf(row.getString(row.getColumnIndexOrThrow("scenario"))),
        row.getInt(row.getColumnIndexOrThrow("total_points")), row.getInt(row.getColumnIndexOrThrow("reached_points")),
        row.getDouble(row.getColumnIndexOrThrow("latitude")), row.getDouble(row.getColumnIndexOrThrow("longitude")),
        row.getFloat(row.getColumnIndexOrThrow("altitude")), row.getString(row.getColumnIndexOrThrow("message")),
        row.getLong(row.getColumnIndexOrThrow("started_at")), row.getLong(row.getColumnIndexOrThrow("updated_at")),
        row.getLong(row.getColumnIndexOrThrow("ended_at")), row.getLong(row.getColumnIndexOrThrow("revision")))

    /**
     * 释放仓库的数据库连接；只在拥有该实例的运行时结束或测试清理时调用。
     * @return {Unit} 无返回值。
     */
    override fun close() = helper.close()
}
