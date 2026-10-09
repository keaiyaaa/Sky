package com.example.msdksample.kami.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class RouteDatabaseHelper(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {
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
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_WAYPOINTS")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_ROUTES")
        onCreate(db)
    }

    companion object {
        private const val DATABASE_NAME = "route_planning.db"
        private const val DATABASE_VERSION = 1

        const val TABLE_ROUTES = "routes"
        const val TABLE_WAYPOINTS = "waypoints"
    }
}
