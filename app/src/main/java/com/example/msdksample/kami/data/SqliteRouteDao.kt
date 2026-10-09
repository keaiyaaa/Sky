package com.example.msdksample.kami.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.example.msdksample.kami.model.RouteMission
import com.example.msdksample.kami.model.RoutePlan
import com.example.msdksample.kami.model.RoutePriority
import com.example.msdksample.kami.model.RouteSummary
import com.example.msdksample.kami.model.Waypoint

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

    override fun getRoute(routeId: String): RouteMission? {
        val db = dbHelper.readableDatabase
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
        } ?: return null

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

        return RouteMission(route, waypoints)
    }

    override fun saveRoute(route: RoutePlan, waypoints: List<Waypoint>) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            val exists = routeExists(db, route.id)
            if (!exists && getRouteCount(db) >= MAX_ROUTES) {
                throw RouteLimitExceededException()
            }

            val now = System.currentTimeMillis()
            if (route.createdAt <= 0L) {
                route.createdAt = now
            }
            route.updatedAt = now

            db.insertWithOnConflict(
                RouteDatabaseHelper.TABLE_ROUTES,
                null,
                route.toContentValues(),
                SQLiteDatabase.CONFLICT_REPLACE
            )

            db.delete(
                RouteDatabaseHelper.TABLE_WAYPOINTS,
                "route_id = ?",
                arrayOf(route.id)
            )

            waypoints.forEachIndexed { index, waypoint ->
                waypoint.routeId = route.id
                waypoint.sequence = index + 1
                db.insertWithOnConflict(
                    RouteDatabaseHelper.TABLE_WAYPOINTS,
                    null,
                    waypoint.toContentValues(),
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    override fun deleteRoute(routeId: String) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
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
