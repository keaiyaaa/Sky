package com.example.msdksample.kami

import android.content.Context
import android.content.ContextWrapper
import android.database.DatabaseErrorHandler
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.msdksample.kami.data.RouteDatabaseHelper
import com.example.msdksample.kami.data.RouteLimitExceededException
import com.example.msdksample.kami.data.RouteRepository
import com.example.msdksample.kami.mission.*
import com.example.msdksample.kami.model.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/** 在 Android 真实 SQLite 上验证事务、迁移、状态锁和执行历史，不使用内存数据库。 */
@RunWith(AndroidJUnit4::class)
class RoutePersistenceInstrumentedTest {
    private lateinit var context: Context
    private lateinit var routes: RouteRepository
    private lateinit var runs: MissionRunRepository

    /**
     * 为每个用例使用独立数据库文件，避免清理或覆盖用户航线。
     * @return {Unit} 无返回值。
     */
    @Before fun setup() {
        context = PrefixDatabaseContext(InstrumentationRegistry.getInstrumentation().targetContext)
        routes = RouteRepository(context)
        runs = MissionRunRepository(context)
    }

    /**
     * 关闭测试连接并删除本用例产生的数据库。
     * @return {Unit} 无返回值。
     */
    @After fun cleanup() { routes.close(); runs.close(); context.deleteDatabase("route_planning.db") }

    /**
     * 验证创建、编辑、删除和关闭重开后数据一致。
     * @return {Unit} 测试断言结果。
     */
    @Test fun savesAndRestoresRealDatabase() {
        val route = routes.createRoute().route
        val points = points(route.id)
        routes.saveRoute(route.copy(name = "持久化航线"), points)
        routes.close()
        routes = RouteRepository(context)
        val restored = routes.getRoute(route.id)!!
        assertEquals("持久化航线", restored.route.name)
        assertEquals(points.map { it.id }, restored.waypoints.map { it.id })
        assertEquals(listOf(1, 2), restored.waypoints.map { it.sequence })
        routes.saveRoute(restored.route, restored.waypoints.drop(1))
        assertEquals(1, routes.getRoute(route.id)!!.waypoints.single().sequence)
        routes.deleteRoute(route.id)
        assertNull(routes.getRoute(route.id))
    }

    /**
     * 验证安全和数量错误不会改变数据库，三条航线上限在业务层强制执行。
     * @return {Unit} 测试断言结果。
     */
    @Test fun rejectsInvalidEditsWithoutPartialWrites() {
        val route = routes.createRoute().route
        routes.saveRoute(route, points(route.id))
        assertThrows(IllegalArgumentException::class.java) {
            routes.saveRoute(route.copy(name = "不应写入"), listOf(Waypoint(latitude = 39.908823, longitude = 116.397470)))
        }
        assertEquals(route.name, routes.getRoute(route.id)!!.route.name)
        assertEquals(2, routes.getRoute(route.id)!!.waypoints.size)
        assertThrows(IllegalArgumentException::class.java) {
            routes.saveRoute(route, List(100) { Waypoint(latitude = 31.0, longitude = 121.0) })
        }
        routes.createRoute(); routes.createRoute()
        assertThrows(RouteLimitExceededException::class.java) { routes.createRoute() }
        assertEquals(3, routes.getRouteSummaries().size)
    }

    /**
     * 制造写入中途的主键冲突，验证元数据和先前航点整体回滚。
     * @return {Unit} 测试断言结果。
     */
    @Test fun insertFailureRollsBackEntireRoute() {
        val a = routes.createRoute().route
        val b = routes.createRoute().route
        val aPoints = points(a.id)
        val bPoints = points(b.id)
        routes.saveRoute(a, aPoints); routes.saveRoute(b, bPoints)
        assertThrows(android.database.sqlite.SQLiteConstraintException::class.java) {
            routes.saveRoute(a.copy(name = "失败的修改"), listOf(aPoints.first(), bPoints.first().copy(routeId = a.id)))
        }
        assertEquals(a.name, routes.getRoute(a.id)!!.route.name)
        assertEquals(aPoints.map { it.id }, routes.getRoute(a.id)!!.waypoints.map { it.id })
        assertEquals(bPoints.map { it.id }, routes.getRoute(b.id)!!.waypoints.map { it.id })
    }

    /**
     * 验证活动任务锁、取消幂等保护及删除原航线后的历史可追溯性。
     * @return {Unit} 测试断言结果。
     */
    @Test fun executionLockAndHistoryArePersistent() {
        val route = routes.createRoute().route
        routes.saveRoute(route, points(route.id))
        val created = runs.create(route.id, MissionScenario.NORMAL)
        assertNotNull(runs.get(created.id))
        assertThrows(IllegalStateException::class.java) { routes.saveRoute(route, points(route.id)) }
        assertThrows(IllegalStateException::class.java) { routes.deleteRoute(route.id) }
        assertThrows(IllegalStateException::class.java) { runs.create(route.id, MissionScenario.NORMAL) }
        val cancelled = runs.update(created, MissionRunState.CANCELLED, "用户取消")
        assertThrows(IllegalStateException::class.java) { runs.update(created, MissionRunState.UPLOADING, "迟到上传回调") }
        assertThrows(IllegalStateException::class.java) { runs.update(cancelled, MissionRunState.COMPLETED, "重复终态", 2) }
        routes.deleteRoute(route.id)
        runs.close(); runs = MissionRunRepository(context)
        assertEquals(MissionRunState.CANCELLED, runs.get(created.id)!!.state)
        assertEquals(2, runs.waypoints(created.id).size)
        assertEquals(2, runs.events(created.id).size)
        assertTrue(runs.get(created.id)!!.endedAt > 0)
    }

    /**
     * 验证合法状态流转、暂停期间的约束及完成进度门禁。
     * @return {Unit} 测试断言结果。
     */
    @Test fun stateMachineRejectsIllegalCompletionAndProgressRegression() {
        val route = routes.createRoute().route
        routes.saveRoute(route, points(route.id))
        var run = runs.create(route.id, MissionScenario.NORMAL)
        assertThrows(IllegalStateException::class.java) { runs.update(run, MissionRunState.RUNNING, "跳过上传") }
        run = runs.update(run, MissionRunState.UPLOADING, "上传")
        run = runs.update(run, MissionRunState.RUNNING, "执行")
        assertThrows(IllegalArgumentException::class.java) { runs.update(run, MissionRunState.COMPLETED, "提前完成") }
        run = runs.update(run, MissionRunState.RUNNING, "到点", 1)
        assertThrows(IllegalArgumentException::class.java) { runs.update(run, MissionRunState.RUNNING, "进度回退", 0) }
        run = runs.update(run, MissionRunState.PAUSED, "暂停")
        assertThrows(IllegalStateException::class.java) { runs.update(run, MissionRunState.COMPLETED, "暂停中完成", 2) }
        run = runs.update(run, MissionRunState.RUNNING, "继续")
        run = runs.update(run, MissionRunState.COMPLETED, "完成", 2)
        assertEquals(MissionRunState.COMPLETED, runs.get(run.id)!!.state)
    }

    /**
     * 验证新进程恢复只标记中断，不自动成功或再次执行。
     * @return {Unit} 测试断言结果。
     */
    @Test fun recoversUnfinishedRunWithoutReplayingIt() {
        val route = routes.createRoute().route
        routes.saveRoute(route, points(route.id))
        val run = runs.create(route.id, MissionScenario.NORMAL)
        runs.close(); runs = MissionRunRepository(context)
        runs.recoverInterrupted(); runs.recoverInterrupted()
        assertEquals(MissionRunState.INTERRUPTED, runs.get(run.id)!!.state)
        assertEquals(2, runs.events(run.id).size)
        routes.saveRoute(route, points(route.id))
    }

    /**
     * 先创建真实 v1 文件，再由 v2 helper 增量迁移并核对原航线。
     * @return {Unit} 测试断言结果。
     */
    @Test fun migratesVersionOneWithoutDroppingRoutes() {
        routes.close(); runs.close()
        context.openOrCreateDatabase("route_planning.db", Context.MODE_PRIVATE, null).use { db ->
            db.execSQL("CREATE TABLE routes (id TEXT PRIMARY KEY,name TEXT NOT NULL,task_name TEXT NOT NULL,task_time TEXT NOT NULL,priority TEXT NOT NULL,created_at INTEGER NOT NULL,updated_at INTEGER NOT NULL)")
            db.execSQL("CREATE TABLE waypoints (id TEXT PRIMARY KEY,route_id TEXT NOT NULL,sequence INTEGER NOT NULL,latitude REAL NOT NULL,longitude REAL NOT NULL,altitude REAL NOT NULL,action TEXT NOT NULL,FOREIGN KEY(route_id) REFERENCES routes(id) ON DELETE CASCADE)")
            db.execSQL("INSERT INTO routes VALUES ('legacy','旧航线','','','NORMAL',1,1)")
            db.execSQL("INSERT INTO waypoints VALUES ('legacy-point','legacy',1,31.0,121.0,30,'拍照')")
            db.version = 1
        }
        routes = RouteRepository(context); runs = MissionRunRepository(context)
        assertEquals("旧航线", routes.getRoute("legacy")!!.route.name)
        assertEquals("legacy-point", routes.getRoute("legacy")!!.waypoints.single().id)
        assertTrue(runs.history().isEmpty())
        RouteDatabaseHelper(context).use { assertEquals(2, it.readableDatabase.version) }
    }

    /**
     * 创建不会触及示例限制区域的短航段。
     * @param {String} routeId 测试航线标识。
     * @return {List<Waypoint>} 两个可实际持久化的航点。
     */
    private fun points(routeId: String) = listOf(Waypoint(routeId = routeId, latitude = 31.0, longitude = 121.0),
        Waypoint(routeId = routeId, latitude = 31.0001, longitude = 121.0001))
}

/**
 * 只隔离测试数据库文件名，仍使用设备提供的原生 SQLite。
 * @param {Context} base 测试目标应用的真实上下文。
 */
private class PrefixDatabaseContext(base: Context) : ContextWrapper(base) {
    private val prefix = "route_validation_" + UUID.randomUUID().toString() + "_"

    /**
     * 保留数据库隔离包装。
     * @return {Context} 当前测试上下文。
     */
    override fun getApplicationContext(): Context = this

    /**
     * 解析独立测试数据库文件。
     * @param {String} name 业务数据库名。
     * @return {java.io.File} 独立测试数据库路径。
     */
    override fun getDatabasePath(name: String) = baseContext.getDatabasePath(prefix + name)

    /**
     * 用真实 SQLite 打开隔离测试文件。
     * @param {String} name 业务数据库名。
     * @param {Int} mode Android 文件访问模式。
     * @param {SQLiteDatabase.CursorFactory?} factory 游标工厂。
     * @return {SQLiteDatabase} 设备数据库连接。
     */
    override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?) =
        baseContext.openOrCreateDatabase(prefix + name, mode, factory)

    /**
     * 用真实 SQLite 和错误处理器打开隔离文件。
     * @param {String} name 业务数据库名。
     * @param {Int} mode Android 文件访问模式。
     * @param {SQLiteDatabase.CursorFactory?} factory 游标工厂。
     * @param {DatabaseErrorHandler?} errorHandler SQLite 错误处理器。
     * @return {SQLiteDatabase} 设备数据库连接。
     */
    override fun openOrCreateDatabase(name: String, mode: Int, factory: SQLiteDatabase.CursorFactory?, errorHandler: DatabaseErrorHandler?) =
        baseContext.openOrCreateDatabase(prefix + name, mode, factory, errorHandler)

    /**
     * 只删除当前测试实例的数据库。
     * @param {String} name 业务数据库名。
     * @return {Boolean} Android 删除结果。
     */
    override fun deleteDatabase(name: String) = baseContext.deleteDatabase(prefix + name)
}
