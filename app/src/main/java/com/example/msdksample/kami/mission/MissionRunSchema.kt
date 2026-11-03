package com.example.msdksample.kami.mission

import android.database.sqlite.SQLiteDatabase

/** 本地巡检执行的正式持久化结构；快照不随原航线编辑或删除而丢失。 */
object MissionRunSchema {
    const val ACTIVE_STATES = "'PREPARING','UPLOADING','RUNNING','PAUSED'"

    /**
     * 创建执行记录、不可变航点快照及状态事件，迁移和首次安装共用。
     * @param {SQLiteDatabase} db 正在建表的数据库事务。
     * @return {Unit} SQL 失败时由调用方回滚。
     */
    fun create(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE mission_runs (
            id TEXT PRIMARY KEY, route_id TEXT NOT NULL, route_name TEXT NOT NULL, task_name TEXT NOT NULL,
            mode TEXT NOT NULL CHECK(mode = 'LOCAL_SIMULATION'),
            state TEXT NOT NULL CHECK(state IN ($ACTIVE_STATES,'COMPLETED','CANCELLED','FAILED','INTERRUPTED')),
            scenario TEXT NOT NULL, total_points INTEGER NOT NULL CHECK(total_points >= 2),
            reached_points INTEGER NOT NULL DEFAULT 0 CHECK(reached_points >= 0 AND reached_points <= total_points),
            latitude REAL NOT NULL, longitude REAL NOT NULL, altitude REAL NOT NULL,
            message TEXT NOT NULL, started_at INTEGER NOT NULL, updated_at INTEGER NOT NULL,
            ended_at INTEGER NOT NULL DEFAULT 0, revision INTEGER NOT NULL DEFAULT 0
        )""")
        db.execSQL("CREATE UNIQUE INDEX mission_single_active ON mission_runs((1)) WHERE state IN ($ACTIVE_STATES)")
        db.execSQL("CREATE INDEX mission_run_history ON mission_runs(started_at DESC)")
        db.execSQL("""CREATE TABLE mission_run_waypoints (
            run_id TEXT NOT NULL REFERENCES mission_runs(id) ON DELETE CASCADE,
            sequence INTEGER NOT NULL, waypoint_id TEXT NOT NULL,
            latitude REAL NOT NULL, longitude REAL NOT NULL, altitude REAL NOT NULL, action TEXT NOT NULL,
            PRIMARY KEY(run_id, sequence)
        )""")
        db.execSQL("""CREATE TABLE mission_run_events (
            run_id TEXT NOT NULL REFERENCES mission_runs(id) ON DELETE CASCADE,
            sequence INTEGER NOT NULL, state TEXT NOT NULL, message TEXT NOT NULL, occurred_at INTEGER NOT NULL,
            PRIMARY KEY(run_id, sequence)
        )""")
    }

    /**
     * 在航线写事务内检查执行锁，避免任务上传或飞行中修改任务输入。
     * @param {SQLiteDatabase} db 调用方持有的写事务连接。
     * @param {String} routeId 准备修改的航线标识。
     * @return {Unit} 无活动任务时返回；否则抛出 IllegalStateException。
     */
    fun requireEditable(db: SQLiteDatabase, routeId: String) {
        val active = db.rawQuery("SELECT 1 FROM mission_runs WHERE route_id = ? AND state IN ($ACTIVE_STATES) LIMIT 1", arrayOf(routeId))
            .use { it.moveToFirst() }
        check(!active) { "该航线正在执行模拟任务，请先取消任务再编辑" }
    }
}
