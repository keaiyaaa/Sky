package com.example.msdksample.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteDatabase

data class LocalProfile(val nickname: String = "", val email: String = "", val organization: String = "")
data class SavedDevice(val key: String, val name: String, val serial: String, val firmware: String, val demo: Boolean)
data class FlightLogEntry(
    val id: Long, val time: Long, val category: String, val action: String,
    val result: String, val source: String, val detail: String, val taskId: String
)

/** Local data only. Device records do not establish account ownership or an SDK connection. */
class LocalDataStore(context: Context, databaseName: String = "peng_local_features.db") :
    SQLiteOpenHelper(context.applicationContext, databaseName, null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE profiles (owner TEXT PRIMARY KEY, nickname TEXT NOT NULL, email TEXT NOT NULL, organization TEXT NOT NULL)")
        db.execSQL("CREATE TABLE devices (owner TEXT NOT NULL, device_key TEXT NOT NULL, name TEXT NOT NULL, serial TEXT NOT NULL, firmware TEXT NOT NULL, demo INTEGER NOT NULL, PRIMARY KEY(owner, device_key))")
        db.execSQL("CREATE TABLE flight_logs (id INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER NOT NULL, category TEXT NOT NULL, action TEXT NOT NULL, result TEXT NOT NULL, source TEXT NOT NULL, detail TEXT NOT NULL, task_id TEXT NOT NULL)")
        db.execSQL("CREATE INDEX logs_time ON flight_logs(time DESC, id DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun profile(owner: String): LocalProfile? = readableDatabase.query(
        "profiles", null, "owner=?", arrayOf(owner), null, null, null
    ).use { if (it.moveToFirst()) LocalProfile(it.getString(1), it.getString(2), it.getString(3)) else null }

    fun saveProfile(owner: String, profile: LocalProfile): Boolean {
        require(profile.nickname.isNotBlank() && profile.nickname.length <= 20)
        require(profile.email.length <= 254 && profile.organization.length <= 60)
        return writableDatabase.insertWithOnConflict("profiles", null, ContentValues().apply {
            put("owner", owner); put("nickname", profile.nickname)
            put("email", profile.email); put("organization", profile.organization)
        }, SQLiteDatabase.CONFLICT_REPLACE) != -1L
    }

    fun devices(owner: String): List<SavedDevice> = readableDatabase.query(
        "devices", null, "owner=?", arrayOf(owner), null, null, "name, device_key"
    ).use { cursor -> buildList {
        while (cursor.moveToNext()) add(SavedDevice(cursor.getString(1), cursor.getString(2),
            cursor.getString(3), cursor.getString(4), cursor.getInt(5) != 0))
    } }

    fun bindDevice(owner: String, device: SavedDevice): Boolean {
        require(device.serial.isNotBlank())
        return writableDatabase.insertWithOnConflict("devices", null, ContentValues().apply {
            put("owner", owner); put("device_key", device.key); put("name", device.name)
            put("serial", device.serial); put("firmware", device.firmware); put("demo", if (device.demo) 1 else 0)
        }, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    fun unbindDevice(owner: String, key: String): Boolean =
        writableDatabase.delete("devices", "owner=? AND device_key=?", arrayOf(owner, key)) > 0

    /** Shared event sink for connection, settings, and future F11/F12 task/command adapters. */
    fun record(category: String, action: String, result: String, source: String,
               detail: String = "", taskId: String = "", time: Long = System.currentTimeMillis()): Long =
        writableDatabase.insertOrThrow("flight_logs", null, ContentValues().apply {
            put("time", time); put("category", category); put("action", action)
            put("result", result); put("source", source); put("detail", detail); put("task_id", taskId)
        })

    fun logs(keyword: String = "", source: String? = null, limit: Int = 200): List<FlightLogEntry> {
        val escaped = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (keyword.isNotBlank()) {
            clauses += "(action LIKE ? ESCAPE '\\' OR detail LIKE ? ESCAPE '\\' OR task_id LIKE ? ESCAPE '\\')"
            repeat(3) { args += "%$escaped%" }
        }
        if (source != null) { clauses += "source=?"; args += source }
        return readableDatabase.query("flight_logs", null, clauses.takeIf { it.isNotEmpty() }?.joinToString(" AND "),
            args.toTypedArray(), null, null, "time DESC, id DESC", limit.coerceIn(1, 1000).toString()).use { cursor -> buildList {
            while (cursor.moveToNext()) add(FlightLogEntry(cursor.getLong(0), cursor.getLong(1),
                cursor.getString(2), cursor.getString(3), cursor.getString(4), cursor.getString(5),
                cursor.getString(6), cursor.getString(7)))
        } }
    }

    companion object {
        @Volatile private var instance: LocalDataStore? = null
        fun getInstance(context: Context): LocalDataStore = instance ?: synchronized(this) {
            instance ?: LocalDataStore(context.applicationContext).also { instance = it }
        }
    }
}
