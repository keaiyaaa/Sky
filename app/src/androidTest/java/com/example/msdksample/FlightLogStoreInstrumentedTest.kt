package com.example.msdksample

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.msdksample.data.local.LocalDataStore
import com.example.msdksample.data.logs.FlightLogPolicy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class FlightLogStoreInstrumentedTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun versionOneUpgradePreservesAllTablesAndQuarantinesOldEvents() {
        val name="f21-migration-${UUID.randomUUID()}.db"
        context.openOrCreateDatabase(name,Context.MODE_PRIVATE,null).use { db ->
            db.execSQL("CREATE TABLE profiles(owner TEXT PRIMARY KEY,nickname TEXT NOT NULL,email TEXT NOT NULL,organization TEXT NOT NULL)")
            db.execSQL("CREATE TABLE devices(owner TEXT NOT NULL,device_key TEXT NOT NULL,name TEXT NOT NULL,serial TEXT NOT NULL,firmware TEXT NOT NULL,demo INTEGER NOT NULL,PRIMARY KEY(owner,device_key))")
            db.execSQL("CREATE TABLE flight_logs(id INTEGER PRIMARY KEY AUTOINCREMENT,time INTEGER NOT NULL,category TEXT NOT NULL,action TEXT NOT NULL,result TEXT NOT NULL,source TEXT NOT NULL,detail TEXT NOT NULL,task_id TEXT NOT NULL)")
            db.execSQL("INSERT INTO profiles VALUES('guest','旧资料','','')")
            db.execSQL("INSERT INTO devices VALUES('guest','dji:SN','旧设备','SN','1',0)")
            db.execSQL("INSERT INTO flight_logs(time,category,action,result,source,detail,task_id) VALUES(1000,'连接','旧事件','失败','设备连接','原始原因','run-1')")
            db.version=1
        }
        var store=LocalDataStore(context,name)
        try {
            assertEquals("旧资料",store.profile("guest")!!.nickname)
            assertEquals("旧设备",store.devices("guest").single().name)
            assertTrue(store.logs(owner="user:alice").isEmpty())
            val legacy=store.logs(owner=FlightLogPolicy.LEGACY_OWNER).single()
            assertEquals(1000L,legacy.time); assertEquals("UNKNOWN",legacy.sourceCode)
            store.close(); store=LocalDataStore(context,name)
            assertEquals(legacy.clientId,store.logs(owner=FlightLogPolicy.LEGACY_OWNER).single().clientId)
        } finally { store.close(); context.deleteDatabase(name) }
    }
    @Test fun uploadBodySurvivesReopenAndCannotBeConfirmedForAnotherOwner() {
        val name="f21-owner-${UUID.randomUUID()}.db"
        var store=LocalDataStore(context,name)
        try {
            store.record("连接","操作","失败","设备连接",owner="user:alice",sourceCode="DEVICE")
            store.record("连接","Bob","失败","设备连接",owner="user:bob",sourceCode="DEVICE")
            val event=store.logs(owner="user:alice").single()
            val body=store.prepareLogUpload(event.owner,event.clientId) { "stable-body-and-key" }
            store.close(); store=LocalDataStore(context,name)
            assertEquals(body,store.prepareLogUpload(event.owner,event.clientId) { error("Do not regenerate") })
            try { store.confirmLogUpload("user:bob",event.clientId,body); fail() } catch(_:IllegalStateException) { }
            assertFalse(store.logs(owner="user:alice").single().uploaded)
            store.confirmLogUpload(event.owner,event.clientId,body)
            assertTrue(store.logs(owner="user:alice").single().uploaded)
        } finally { store.close(); context.deleteDatabase(name) }
    }
    @Test fun literalFiltersAndSnapshotPagingDoNotRepeatNewlyInsertedRows() {
        val name="f21-page-${UUID.randomUUID()}.db"
        val store=LocalDataStore(context,name)
        try {
            repeat(25) { store.record("连接","事件$it","失败","设备连接","错误100%_",time=1000L+it,owner="user:alice") }
            val bound=store.maxLogId()
            val first=store.logs("%",limit=20,owner="user:alice",maxId=bound)
            store.record("连接","新事件","成功","设备连接",time=5000,owner="user:alice")
            val second=store.logs("%",limit=20,owner="user:alice",offset=20,maxId=bound)
            assertEquals(20,first.size); assertEquals(5,second.size)
            assertTrue(first.map{it.clientId}.intersect(second.map{it.clientId}.toSet()).isEmpty())
            assertTrue(store.logs("' OR 1=1 --",owner="user:alice").isEmpty())
            assertEquals(1,store.logs(owner="user:alice",from=1000,to=1001).size)
        } finally { store.close(); context.deleteDatabase(name) }
    }
}
