package com.example.msdksample.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteDatabase
import com.example.msdksample.data.logs.FlightLogPolicy
import com.example.msdksample.data.remote.AccountIdentity
import java.util.UUID

data class LocalProfile(val nickname: String = "", val email: String = "", val organization: String = "")
data class SavedDevice(val key: String, val name: String, val serial: String, val firmware: String, val demo: Boolean)
data class FlightLogEntry(
    val id: Long, val time: Long, val category: String, val action: String,
    val result: String, val source: String, val detail: String, val taskId: String,
    val clientId: String = "", val owner: String = FlightLogPolicy.LEGACY_OWNER,
    val sourceCode: String = "UNKNOWN", val uploaded: Boolean = false, val uploadBody: String? = null
)

/** Local data only. Device records do not establish account ownership or an SDK connection. */
class LocalDataStore(context: Context, databaseName: String = "peng_local_features.db") :
    SQLiteOpenHelper(context.applicationContext, databaseName, null, 2) {
    private val appContext=context.applicationContext
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE profiles (owner TEXT PRIMARY KEY, nickname TEXT NOT NULL, email TEXT NOT NULL, organization TEXT NOT NULL)")
        db.execSQL("CREATE TABLE devices (owner TEXT NOT NULL, device_key TEXT NOT NULL, name TEXT NOT NULL, serial TEXT NOT NULL, firmware TEXT NOT NULL, demo INTEGER NOT NULL, PRIMARY KEY(owner, device_key))")
        db.execSQL("CREATE TABLE flight_logs (id INTEGER PRIMARY KEY AUTOINCREMENT, time INTEGER NOT NULL, category TEXT NOT NULL, action TEXT NOT NULL, result TEXT NOT NULL, source TEXT NOT NULL, detail TEXT NOT NULL, task_id TEXT NOT NULL)")
        db.execSQL("CREATE INDEX logs_time ON flight_logs(time DESC, id DESC)")
        addLogOwnership(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        check(oldVersion == 1 && newVersion == 2)
        addLogOwnership(db)
    }
    private fun addLogOwnership(db: SQLiteDatabase) {
        db.execSQL("ALTER TABLE flight_logs ADD COLUMN owner TEXT NOT NULL DEFAULT 'legacy-unassigned'")
        db.execSQL("ALTER TABLE flight_logs ADD COLUMN client_id TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE flight_logs ADD COLUMN source_code TEXT NOT NULL DEFAULT 'UNKNOWN'")
        db.execSQL("ALTER TABLE flight_logs ADD COLUMN uploaded INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE flight_logs ADD COLUMN upload_body TEXT NULL")
        db.execSQL("UPDATE flight_logs SET client_id='legacy-' || lower(hex(randomblob(16))), source_code=CASE WHEN source='本地演示' THEN 'LOCAL_SIMULATION' ELSE 'UNKNOWN' END")
        db.query("flight_logs",arrayOf("id","action","result","detail","task_id"),null,null,null,null,null).use { rows ->
            while(rows.moveToNext()) db.update("flight_logs",ContentValues().apply {
                put("action",FlightLogPolicy.clean(rows.getString(1))); put("result",FlightLogPolicy.clean(rows.getString(2)))
                put("detail",FlightLogPolicy.clean(rows.getString(3))); put("task_id",FlightLogPolicy.clean(rows.getString(4)))
            },"id=?",arrayOf(rows.getLong(0).toString()))
        }
        db.execSQL("CREATE UNIQUE INDEX logs_client_id ON flight_logs(client_id)")
        db.execSQL("CREATE INDEX logs_owner_time ON flight_logs(owner,time DESC,id DESC)")
    }

    fun profile(owner: String): LocalProfile? = readableDatabase.query(
        "profiles", null, "owner=?", arrayOf(owner), null, null, null
    ).use { if (it.moveToFirst()) LocalProfile(it.getString(1), it.getString(2), it.getString(3)) else null }

    fun saveProfile(owner: String, profile: LocalProfile): Boolean {
        require(profile.nickname.isNotBlank() && profile.nickname.codePointCount(0, profile.nickname.length) <= 20)
        require(profile.email.codePointCount(0, profile.email.length) <= 254 &&
            profile.organization.codePointCount(0, profile.organization.length) <= 60)
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
               detail: String = "", taskId: String = "", time: Long = System.currentTimeMillis(),
               owner: String = AccountIdentity.owner(appContext),
               sourceCode: String = if(source=="本地演示") "LOCAL_SIMULATION" else "UNKNOWN"): Long =
        writableDatabase.insertOrThrow("flight_logs", null, ContentValues().apply {
            require(sourceCode in FlightLogPolicy.sources && owner.isNotBlank())
            put("time", time); put("category", category); put("action", FlightLogPolicy.clean(action))
            put("result", FlightLogPolicy.clean(result)); put("source", source); put("detail", FlightLogPolicy.clean(detail)); put("task_id", FlightLogPolicy.clean(taskId))
            put("owner",owner); put("client_id",UUID.randomUUID().toString()); put("source_code",sourceCode)
        })

    fun logs(keyword: String = "", source: String? = null, limit: Int = 200,
             owner: String = AccountIdentity.owner(appContext), offset: Int = 0, maxId: Long = Long.MAX_VALUE,
             from: Long? = null, to: Long? = null, category: String? = null, sourceCode: String? = null): List<FlightLogEntry> {
        require(limit in 1..1000 && offset>=0)
        val escaped = keyword.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        val clauses = mutableListOf("owner=?", "id<=?")
        val args = mutableListOf(owner,maxId.toString())
        if (keyword.isNotBlank()) {
            clauses += "(action LIKE ? ESCAPE '\\' OR result LIKE ? ESCAPE '\\' OR detail LIKE ? ESCAPE '\\' OR task_id LIKE ? ESCAPE '\\')"
            repeat(4) { args += "%$escaped%" }
        }
        if (source != null) { clauses += "source=?"; args += source }
        if(sourceCode!=null) { clauses += "source_code=?"; args += sourceCode }
        if(category!=null) { clauses += "category=?"; args += category }
        if(from!=null) { clauses += "time>=?"; args += from.toString() }
        if(to!=null) { clauses += "time<?"; args += to.toString() }
        return readableDatabase.query("flight_logs", null, clauses.takeIf { it.isNotEmpty() }?.joinToString(" AND "),
            args.toTypedArray(), null, null, "time DESC, id DESC", "$offset,$limit").use { cursor -> buildList {
            while (cursor.moveToNext()) add(readLog(cursor))
        } }
    }

    fun maxLogId(): Long = readableDatabase.rawQuery("SELECT COALESCE(MAX(id),0) FROM flight_logs",null).use { it.moveToFirst(); it.getLong(0) }
    @Synchronized fun prepareLogUpload(owner: String, clientId: String, createBody: (FlightLogEntry)->String): String {
        check(owner.startsWith("user:")) { "只有已登录账号的新日志可上报" }
        val db=writableDatabase
        db.beginTransaction()
        try {
            val entry=db.query("flight_logs",null,"owner=? AND client_id=?",arrayOf(owner,clientId),null,null,null).use {
                check(it.moveToFirst()) { "日志不存在" }; readLog(it)
            }
            check(!entry.uploaded && entry.sourceCode !in listOf("LOCAL_SIMULATION","DJI_SIMULATOR") && FlightLogPolicy.category(entry.category) in listOf("CONNECTION","SETTING","COMMAND")) { "此日志不支持账户上报" }
            val body=entry.uploadBody ?: createBody(entry)
            check(db.update("flight_logs",ContentValues().apply { put("upload_body",body) },"owner=? AND client_id=?",arrayOf(owner,clientId))==1)
            db.setTransactionSuccessful(); return body
        } finally { db.endTransaction() }
    }
    fun confirmLogUpload(owner: String,clientId: String,body: String) {
        check(writableDatabase.update("flight_logs",ContentValues().apply { put("uploaded",1); putNull("upload_body") },
            "owner=? AND client_id=? AND upload_body=?",arrayOf(owner,clientId,body))==1)
    }
    private fun readLog(cursor: android.database.Cursor) = FlightLogEntry(cursor.getLong(0),cursor.getLong(1),
        cursor.getString(2),FlightLogPolicy.clean(cursor.getString(3)),FlightLogPolicy.clean(cursor.getString(4)),cursor.getString(5),
        FlightLogPolicy.clean(cursor.getString(6)),FlightLogPolicy.clean(cursor.getString(7)),cursor.getString(9),cursor.getString(8),
        cursor.getString(10),cursor.getInt(11)!=0,if(cursor.isNull(12)) null else cursor.getString(12))

    companion object {
        @Volatile private var instance: LocalDataStore? = null
        fun getInstance(context: Context): LocalDataStore = instance ?: synchronized(this) {
            instance ?: LocalDataStore(context.applicationContext).also { instance = it }
        }
    }
}
