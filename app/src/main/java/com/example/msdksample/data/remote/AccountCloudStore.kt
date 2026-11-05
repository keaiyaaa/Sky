package com.example.msdksample.data.remote

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class PendingDeviceWrite(val userId: String, val key: String, val method: String, val path: String, val body: String)

/** Account partitioning is independent of legacy demo/phone data. Pending writes survive process death. */
class AccountCloudStore(context: Context, databaseName: String = "account_cloud.db") :
    SQLiteOpenHelper(context.applicationContext, databaseName, null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE snapshots(user_id TEXT NOT NULL,kind TEXT NOT NULL,json TEXT NOT NULL,PRIMARY KEY(user_id,kind))")
        db.execSQL("CREATE TABLE pending(user_id TEXT NOT NULL,operation_key TEXT NOT NULL,method TEXT NOT NULL,path TEXT NOT NULL,body TEXT NOT NULL,PRIMARY KEY(user_id,operation_key))")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    fun snapshot(userId: String, kind: String): String? = readableDatabase.query("snapshots", arrayOf("json"),
        "user_id=? AND kind=?", arrayOf(userId, kind), null, null, null).use { if (it.moveToFirst()) it.getString(0) else null }
    fun saveSnapshot(userId: String, kind: String, json: String) {
        check(writableDatabase.insertWithOnConflict("snapshots", null, ContentValues().apply {
            put("user_id", userId); put("kind", kind); put("json", json)
        }, SQLiteDatabase.CONFLICT_REPLACE) != -1L)
    }
    fun deleteSnapshot(userId: String, kind: String) {
        writableDatabase.delete("snapshots", "user_id=? AND kind=?", arrayOf(userId, kind))
    }
    @Synchronized fun pending(userId: String, key: String, method: String, path: String,
                              createBody: () -> String): PendingDeviceWrite {
        val existing = pending(userId).firstOrNull { it.key == key }
        if (existing != null) {
            check(existing.method == method && existing.path == path) { "存在未确认操作，请先重试或放弃" }
            return existing
        }
        val write = PendingDeviceWrite(userId, key, method, path, createBody())
        writableDatabase.insertOrThrow("pending", null, ContentValues().apply {
            put("user_id", userId); put("operation_key", key); put("method", method); put("path", path); put("body", write.body)
        })
        return write
    }
    fun pending(userId: String): List<PendingDeviceWrite> = readableDatabase.query("pending", null,
        "user_id=?", arrayOf(userId), null, null, "operation_key").use { cursor -> buildList {
        while (cursor.moveToNext()) add(PendingDeviceWrite(cursor.getString(0), cursor.getString(1),
            cursor.getString(2), cursor.getString(3), cursor.getString(4)))
    } }
    fun confirm(write: PendingDeviceWrite) {
        writableDatabase.delete("pending", "user_id=? AND operation_key=? AND body=?", arrayOf(write.userId, write.key, write.body))
    }
    companion object {
        @Volatile private var instance: AccountCloudStore? = null
        fun getInstance(context: Context): AccountCloudStore = instance ?: synchronized(this) {
            instance ?: AccountCloudStore(context).also { instance = it }
        }
    }
}
