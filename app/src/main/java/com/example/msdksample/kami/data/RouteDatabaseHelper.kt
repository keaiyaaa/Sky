package com.example.msdksample.kami.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.msdksample.kami.mission.MissionRunSchema

class RouteDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
    /**
     * 创建正式航线、航点和本地巡检执行记录表。
     * @param {SQLiteDatabase} db 首次打开的数据库。
     * @return {Unit} 建表失败由 SQLite 抛出异常并回滚。
     */
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_ROUTES (
                id TEXT PRIMARY KEY,
                name TEXT NOT NULL,
                task_name TEXT NOT NULL,
                task_time TEXT NOT NULL,
                priority TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_WAYPOINTS (
                id TEXT PRIMARY KEY,
                route_id TEXT NOT NULL,
                sequence INTEGER NOT NULL,
                latitude REAL NOT NULL,
                longitude REAL NOT NULL,
                altitude REAL NOT NULL,
                action TEXT NOT NULL,
                FOREIGN KEY(route_id) REFERENCES $TABLE_ROUTES(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            "CREATE INDEX idx_waypoints_route_sequence ON $TABLE_WAYPOINTS(route_id, sequence)"
        )
        MissionRunSchema.create(db)
    }

    /**
     * 为每个连接启用外键约束，防止孤立航点和孤立执行事件。
     * @param {SQLiteDatabase} db 正在配置的连接。
     * @return {Unit} 无返回值。
     */
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    /**
     * 保留既有航线，仅增量创建执行记录结构。
     * @param {SQLiteDatabase} db 由 SQLiteOpenHelper 包裹在迁移事务中的连接。
     * @param {Int} oldVersion 已安装版本。
     * @param {Int} newVersion 目标版本。
     * @return {Unit} 不支持的迁移明确失败，禁止通过删表掩盖数据丢失。
     */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        check(oldVersion == 1 && newVersion == 2) { "不支持的航线数据库迁移：$oldVersion → $newVersion" }
        MissionRunSchema.create(db)
    }

    companion object {
        private const val DATABASE_NAME = "route_planning.db"
        private const val DATABASE_VERSION = 2

        const val TABLE_ROUTES = "routes"
        const val TABLE_WAYPOINTS = "waypoints"
    }
}
