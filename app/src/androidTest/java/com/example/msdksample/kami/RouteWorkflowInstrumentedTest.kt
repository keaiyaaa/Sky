package com.example.msdksample.kami

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.msdksample.R
import com.example.msdksample.kami.data.RouteDatabaseHelper
import com.example.msdksample.kami.data.RouteRepository
import com.example.msdksample.kami.mission.*
import com.example.msdksample.kami.model.GeoMath
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.geometry.LatLng
import java.util.concurrent.atomic.AtomicReference
import com.example.msdksample.ui.logs.FlightLogActivity
import org.hamcrest.Matchers.allOf
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking

/** 通过实际页面和应用数据库验证离线编辑、优化、模拟、失败和记录查询。 */
@RunWith(AndroidJUnit4::class)
class RouteWorkflowInstrumentedTest {
    /**
     * 运行完整界面业务链，并清理本用例创建的数据，不删除既有航线。
     * @return {Unit} 所有界面与数据库断言通过后返回。
     */
    @Test fun editOptimizeExecuteAndRestoreThroughRealPage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val routes = RouteRepository(context)
        val runs = MissionRunRepository(context)
        val runtime = LocalMissionRuntime.get(context)
        val route = routes.createRoute().route
        val preferences = context.getSharedPreferences("kami.RoutePlanningActivity", Context.MODE_PRIVATE)
        val previousSelection = preferences.getString("selected_route_id", null)
        preferences.edit().putString("selected_route_id", route.id).commit()
        try {
            ActivityScenario.launch(RoutePlanningActivity::class.java).use { scenario ->
                eventually { onView(withId(R.id.btnCoordinatePoint)).check(matches(isEnabled())) }
                addPoint("31.0", "121.0")
                eventually { assertEquals(1, routes.getRoute(route.id)!!.waypoints.size) }
                addPoint("31.002", "121.002")
                eventually { assertEquals(2, routes.getRoute(route.id)!!.waypoints.size) }
                addPoint("31.0001", "121.0001")
                eventually { assertEquals(3, routes.getRoute(route.id)!!.waypoints.size) }
                val original = routes.getRoute(route.id)!!.waypoints
                onView(withId(R.id.btnOptimize)).perform(click())
                eventually { onView(withText("保存优化")).perform(click()) }
                eventually { onView(withId(R.id.btnUndoOptimization)).check(matches(isEnabled())) }
                assertTrue(GeoMath.routeDistanceMeters(routes.getRoute(route.id)!!.waypoints) <= GeoMath.routeDistanceMeters(original))
                onView(withId(R.id.btnUndoOptimization)).perform(click())
                eventually { assertEquals(original.map { it.id }, routes.getRoute(route.id)!!.waypoints.map { it.id }) }
                scenario.recreate()
                eventually { onView(withId(R.id.tvCurrentPosition)).check(matches(withText(route.name + " · 3/99 点"))) }
                assertRestoredCamera(scenario, original.first().latitude, original.first().longitude)
                startScenario("正常巡检")
                eventually { assertEquals(MissionRunState.RUNNING, runtime.state.value?.state) }
                onView(withId(R.id.btnPauseMission)).perform(scrollTo(), click())
                eventually { assertEquals(MissionRunState.PAUSED, runtime.state.value?.state) }
                val paused = runs.get(runtime.state.value!!.id)!!
                Thread.sleep(1300)
                assertEquals(paused.reachedPoints, runs.get(paused.id)!!.reachedPoints)
                assertEquals(paused.latitude, runs.get(paused.id)!!.latitude, 0.0)
                onView(withId(R.id.btnPauseMission)).perform(scrollTo(), click())
                eventually { assertEquals(MissionRunState.RUNNING, runtime.state.value?.state) }
                onView(withId(R.id.btnCancel)).perform(scrollTo(), click())
                onView(withText("取消任务")).perform(click())
                eventually { assertEquals(MissionRunState.CANCELLED, runtime.state.value?.state) }
                startScenario("模拟上传失败")
                eventually { assertEquals(MissionRunState.FAILED, runtime.state.value?.state) }
                assertTrue(runs.get(runtime.state.value!!.id)!!.message.contains("上传失败"))
                startScenario("模拟执行中断连")
                eventually { assertEquals(MissionRunState.FAILED, runtime.state.value?.state) }
                assertTrue(runs.get(runtime.state.value!!.id)!!.message.contains("断连"))
                // 缩短航段，从真实坐标编辑入口删除中间点后运行完成场景。
                eventually { onView(withId(R.id.btnErasePoint)).check(matches(isEnabled())) }
                onView(withId(R.id.btnErasePoint)).perform(click())
                eventually { assertEquals(2, routes.getRoute(route.id)!!.waypoints.size) }
                startScenario("正常巡检")
                eventually(30000) { assertEquals(MissionRunState.COMPLETED, runtime.state.value?.state) }
                val completed = runs.get(runtime.state.value!!.id)!!
                assertEquals(completed.totalPoints, completed.reachedPoints)
                assertTrue(completed.endedAt > 0)
                onView(withId(R.id.btnMissionHistory)).perform(scrollTo(), click())
                eventually { onView(withText("本地模拟执行记录")).check(matches(isDisplayed())) }
                onView(withText("关闭")).perform(click())
                scenario.recreate()
                eventually { onView(withId(R.id.tvObstacleStatus)).check(matches(withSubstring("模拟完成"))) }
                ActivityScenario.launch(FlightLogActivity::class.java).use {
                    onView(withId(R.id.log_keyword)).perform(scrollTo(), replaceText(completed.id), closeSoftKeyboard())
                    onView(withText("查询／刷新")).perform(scrollTo(), click())
                    eventually {
                        onView(allOf(withSubstring(completed.id), withSubstring("模拟完成")))
                            .perform(scrollTo()).check(matches(isDisplayed()))
                    }
                }
            }
        } finally {
            if (runtime.state.value?.routeId == route.id && runtime.state.value?.state?.isActive == true) runBlocking { runtime.cancel() }
            routes.deleteRoute(route.id)
            RouteDatabaseHelper(context).use { it.writableDatabase.delete("mission_runs", "route_id = ?", arrayOf(route.id)) }
            preferences.edit().putString("selected_route_id", previousSelection).commit()
            routes.close(); runs.close()
        }
    }

    /**
     * 使用实际坐标表单创建并保存一个航点。
     * @param {String} latitude 输入纬度文本。
     * @param {String} longitude 输入经度文本。
     * @return {Unit} 保存请求已通过真实按钮发出。
     */
    private fun addPoint(latitude: String, longitude: String) {
        eventually { onView(withId(R.id.btnCoordinatePoint)).check(matches(isEnabled())) }
        onView(withId(R.id.btnCoordinatePoint)).perform(scrollTo(), click())
        onView(withId(R.id.etWaypointLatitude)).perform(replaceText(latitude))
        onView(withId(R.id.etWaypointLongitude)).perform(replaceText(longitude), closeSoftKeyboard())
        onView(withText("保存")).perform(click())
    }

    /**
     * 从页面选择场景并确认启动。
     * @param {String} scenario 显示在场景列表中的名称。
     * @return {Unit} 启动请求已发出。
     */
    private fun startScenario(scenario: String) {
        eventually { onView(withId(R.id.btnAction)).check(matches(isEnabled())) }
        onView(withId(R.id.btnAction)).perform(scrollTo(), click())
        onView(withText(scenario)).perform(click())
        onView(withText("开始模拟")).perform(click())
    }

    /**
     * 验证地图与数据库任意加载顺序下，重建页面会恢复到已保存航点附近。
     * @param {ActivityScenario<RoutePlanningActivity>} scenario 当前真实页面场景。
     * @param {Double} latitude 已保存首点的纬度。
     * @param {Double} longitude 已保存首点的经度。
     * @return {Unit} 镜头目标与首点一致后返回。
     */
    private fun assertRestoredCamera(scenario: ActivityScenario<RoutePlanningActivity>, latitude: Double, longitude: Double) {
        val target = AtomicReference<LatLng>()
        eventually {
            scenario.onActivity { activity ->
                activity.findViewById<MapView>(R.id.mapView).getMapAsync { target.set(it.cameraPosition.target) }
            }
            val center = target.get()
            assertNotNull(center)
            assertEquals(latitude, center.latitude, 0.0001)
            assertEquals(longitude, center.longitude, 0.0001)
        }
    }

    /**
     * 等待真实异步保存或执行结果，超时保留最后一个断言失败原因。
     * @param {Long} timeoutMillis 最大等待时间，毫秒。
     * @param {Function0<Unit>} assertion 必须最终通过的真实界面或数据库断言。
     * @return {Unit} 断言通过后返回，超时则失败。
     */
    private fun eventually(timeoutMillis: Long = 10000, assertion: () -> Unit) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        var last: Throwable? = null
        while (System.currentTimeMillis() < deadline) {
            try { assertion(); return }
            catch (error: AssertionError) { last = error }
            catch (error: NoMatchingViewException) { last = error }
            Thread.sleep(100)
        }
        throw AssertionError("等待异步业务结果超时", last)
    }
}
