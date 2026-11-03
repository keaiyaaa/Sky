package com.example.msdksample.kami.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.example.msdksample.kami.model.RouteMission
import com.example.msdksample.kami.model.RoutePlan
import com.example.msdksample.kami.model.RoutePriority
import com.example.msdksample.kami.model.RouteSummary
import com.example.msdksample.kami.model.Waypoint
import com.example.msdksample.kami.model.RouteSafetyValidator
import com.example.msdksample.kami.mission.MissionRunSchema

class SqliteRouteDao(
    private val dbHelper: RouteDatabaseHelper
) : RouteDao {

    override fun getRouteSummaries(): List<RouteSummary> {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT r.id, r.name, r.task_name, r.task_time, r.priority,
                   r.created_at, r.updated_at, COUNT(w.id) AS waypoint_count
            FROM ${RouteDatabaseHelper.TABLE_ROUTES} r
            LEFT JOIN ${RouteDatabaseHelper.TABLE_WAYPOINTS} w ON r.id = w.route_id
            GROUP BY r.id
            ORDER BY r.created_at ASC
            """.trimIndent(),
            emptyArray<String>()
        )
        return cursor.use { rows ->
            buildList {
                while (rows.moveToNext()) {
                    add(
                        RouteSummary(
                            route = rows.readRoute(),
                            waypointCount = rows.getInt(rows.getColumnIndexOrThrow("waypoint_count"))
                        )
                    )
                }
            }
        }
    }

    /**
     * 在同一读事务中获取航线元数据与航点，避免并发保存造成半旧半新的快照。
     * @param {String} routeId 航线稳定标识。
     * @return {RouteMission?} 完整快照；不存在时返回 null。
     */
    override fun getRoute(routeId: String): RouteMission? {
        val db = dbHelper.readableDatabase
        db.beginTransaction()
        try {
        val route = db.query(
            RouteDatabaseHelper.TABLE_ROUTES,
            null,
            "id = ?",
            arrayOf(routeId),
            null,
            null,
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.readRoute() else null
        } ?: run { db.setTransactionSuccessful(); return null }

        val waypoints = db.query(
            RouteDatabaseHelper.TABLE_WAYPOINTS,
            null,
            "route_id = ?",
            arrayOf(routeId),
            null,
            null,
            "sequence ASC"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(cursor.readWaypoint())
                }
            }
        }

            db.setTransactionSuccessful()
            return RouteMission(route, waypoints)
        } finally {
            db.endTransaction()
        }
    }

    /**
     * 原子保存已通过示例安全规则的航线，任一航点写入失败时整体回滚。
     * @param {RoutePlan} route 航线元数据；时间戳在持久化副本中更新，不修改调用方对象。
     * @param {List<Waypoint>} waypoints 按执行顺序排列的航点。
     * @return {Unit} 超额、活动任务锁定、安全违规或写入失败时抛出异常。
     */
    override fun saveRoute(route: RoutePlan, waypoints: List<Waypoint>) {
        require(route.id.isNotBlank()) { "航线标识不能为空" }
        require(route.name.isNotBlank() && route.name.length <= 100) { "航线名称须为 1 至 100 个字符" }
        require(route.taskName.length <= 200 && route.taskTime.length <= 100) { "任务名称或时间过长" }
        require(waypoints.all { it.routeId.isBlank() || it.routeId == route.id }) { "不能保存其他航线的航点" }
        RouteSafetyValidator().requireSafe(waypoints)
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            MissionRunSchema.requireEditable(db, route.id)
            val exists = routeExists(db, route.id)
            if (!exists && getRouteCount(db) >= MAX_ROUTES) {
                throw RouteLimitExceededException()
            }

            val now = System.currentTimeMillis()
            val savedRoute = route.copy(createdAt = route.createdAt.takeIf { it > 0 } ?: now, updatedAt = now)
            // UPDATE 不触发 REPLACE 的级联删除语义；所有插入均使用抛错版本。
            if (exists) {
                check(db.update(RouteDatabaseHelper.TABLE_ROUTES, savedRoute.toContentValues(), "id = ?", arrayOf(route.id)) == 1)
            } else {
                db.insertOrThrow(RouteDatabaseHelper.TABLE_ROUTES, null, savedRoute.toContentValues())
            }

            db.delete(
                RouteDatabaseHelper.TABLE_WAYPOINTS,
                "route_id = ?",
                arrayOf(route.id)
            )

            waypoints.forEachIndexed { index, waypoint ->
                val savedWaypoint = waypoint.copy(routeId = route.id, sequence = index + 1)
                db.insertOrThrow(
                    RouteDatabaseHelper.TABLE_WAYPOINTS,
                    null,
                    savedWaypoint.toContentValues()
                )
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * 删除航线和所属航点，执行中的航线拒绝删除，历史任务快照保留。
     * @param {String} routeId 待删除的航线稳定标识。
     * @return {Unit} 锁定或数据库错误时抛出异常。
     */
    override fun deleteRoute(routeId: String) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            MissionRunSchema.requireEditable(db, routeId)
            db.delete(
                RouteDatabaseHelper.TABLE_WAYPOINTS,
                "route_id = ?",
                arrayOf(routeId)
            )
            db.delete(
                RouteDatabaseHelper.TABLE_ROUTES,
                "id = ?",
                arrayOf(routeId)
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun getRouteCount(): Int {
        return getRouteCount(dbHelper.readableDatabase)
    }

    private fun getRouteCount(db: SQLiteDatabase): Int {
        return db.rawQuery(
            "SELECT COUNT(*) FROM ${RouteDatabaseHelper.TABLE_ROUTES}",
            emptyArray<String>()
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    private fun routeExists(db: SQLiteDatabase, routeId: String): Boolean {
        return db.query(
            RouteDatabaseHelper.TABLE_ROUTES,
            arrayOf("id"),
            "id = ?",
            arrayOf(routeId),
            null,
            null,
            null
        ).use { cursor ->
            cursor.moveToFirst()
        }
    }

    private fun RoutePlan.toContentValues(): ContentValues {
        return ContentValues().apply {
            put("id", id)
            put("name", name)
            put("task_name", taskName)
            put("task_time", taskTime)
            put("priority", priority.name)
            put("created_at", createdAt)
            put("updated_at", updatedAt)
        }
    }

    private fun Waypoint.toContentValues(): ContentValues {
        return ContentValues().apply {
            put("id", id)
            put("route_id", routeId)
            put("sequence", sequence)
            put("latitude", latitude)
            put("longitude", longitude)
            put("altitude", altitude)
            put("action", action)
        }
    }

    private fun Cursor.readRoute(): RoutePlan {
        return RoutePlan(
            id = getString(getColumnIndexOrThrow("id")),
            name = getString(getColumnIndexOrThrow("name")),
            taskName = getString(getColumnIndexOrThrow("task_name")),
            taskTime = getString(getColumnIndexOrThrow("task_time")),
            priority = RoutePriority.fromStorage(getString(getColumnIndexOrThrow("priority"))),
            createdAt = getLong(getColumnIndexOrThrow("created_at")),
            updatedAt = getLong(getColumnIndexOrThrow("updated_at"))
        )
    }

    private fun Cursor.readWaypoint(): Waypoint {
        return Waypoint(
            id = getString(getColumnIndexOrThrow("id")),
            routeId = getString(getColumnIndexOrThrow("route_id")),
            sequence = getInt(getColumnIndexOrThrow("sequence")),
            latitude = getDouble(getColumnIndexOrThrow("latitude")),
            longitude = getDouble(getColumnIndexOrThrow("longitude")),
            altitude = getFloat(getColumnIndexOrThrow("altitude")),
            action = getString(getColumnIndexOrThrow("action"))
        )
    }

    companion object {
        const val MAX_ROUTES = 3
    }
}
