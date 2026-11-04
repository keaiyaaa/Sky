package com.example.msdksample

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.action.ViewActions.swipeUp
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.example.msdksample.drone.DroneManager
import com.example.msdksample.drone.state.DeviceMode
import com.example.msdksample.drone.state.LinkState
import org.hamcrest.Matchers.containsString
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeviceConnectionNavigationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val manager get() = DroneManager.getInstance()

    @Test fun signingOutClearsBothLocalSessionsAndKeepsHomeAvailable() {
        val prefs = com.example.msdksample.utils.PreferenceManager.getInstance(instrumentation.targetContext)
        instrumentation.runOnMainSync {
            prefs.setUsername("navigation fixture")
            prefs.setLoggedIn(true)
            com.example.msdksample.auth.DemoSession.signIn("navigation fixture")
        }
        try {
            ActivityScenario.launch(HomeActivity::class.java).use {
                onView(withId(R.id.btn_navigation)).perform(click())
                onView(withId(R.id.nav_view)).perform(swipeUp())
                onView(withText("退出登录")).perform(click())
                onView(withText("确定")).perform(click())
                onView(withId(R.id.btn_navigation)).check(matches(isDisplayed()))
                assertFalse(prefs.isLoggedIn())
                assertNull(com.example.msdksample.auth.DemoSession.signedInUser)
                onView(withId(R.id.btn_navigation)).perform(click())
                onView(withId(R.id.nav_view)).perform(swipeUp())
                onView(withText("登录")).check(matches(isDisplayed()))
            }
        } finally { instrumentation.runOnMainSync { prefs.logout(); com.example.msdksample.auth.DemoSession.signOut() } }
    }

    @Test @SdkSuppress(minSdkVersion = 28)
    fun primaryEntriesOpenActualModulesAndBackReturnsHome() {
        // Navigation coverage uses granted location permission; F07 owns its permission UX.
        val packageName = instrumentation.targetContext.packageName
        instrumentation.uiAutomation.grantRuntimePermission(packageName, android.Manifest.permission.ACCESS_COARSE_LOCATION)
        instrumentation.uiAutomation.grantRuntimePermission(packageName, android.Manifest.permission.ACCESS_FINE_LOCATION)
        val destinations = listOf(
            R.id.card_start_flight to com.example.msdksample.aerial.Aerial_photography::class.java,
            R.id.btn_fullscreen to com.example.msdksample.aerial.Aerial_photography::class.java,
            R.id.card_route_plan to com.example.msdksample.kami.RoutePlanningActivity::class.java,
            R.id.ll_mission_entry to com.example.msdksample.asuka.MainActivity::class.java,
            R.id.btn_vip to com.example.msdksample.asuka.VipActivity::class.java,
            R.id.btn_menu to com.example.msdksample.ui.settings.SettingsActivity::class.java
        )
        ActivityScenario.launch(HomeActivity::class.java).use {
            destinations.forEach { (viewId, destination) ->
                val monitor = instrumentation.addMonitor(destination.name, null, false)
                try {
                    onView(withId(viewId)).perform(click())
                    assertNotNull("入口应打开 ${destination.simpleName}", instrumentation.waitForMonitorWithTimeout(monitor, 5000))
                    pressBack()
                    onView(withId(R.id.btn_navigation)).check(matches(isDisplayed()))
                } finally { instrumentation.removeMonitor(monitor) }
            }
        }
    }

    @Test fun panelCoversFailureCancelAndReconnectWithoutChangingDemoSource() {
        instrumentation.runOnMainSync { manager.initSDK(instrumentation.targetContext); manager.enableSimulator() }
        try {
            ActivityScenario.launch(HomeActivity::class.java).use {
                onView(withId(R.id.tv_device_card_title)).perform(click(), click(), click(), click(), click())
                onView(withId(R.id.btn_test_scenario)).perform(scrollTo(), click())
                onView(withText("连接失败")).perform(click())
                onView(withId(R.id.tv_test_status)).check(matches(withText(containsString("本地演示 · 连接失败"))))
                assertEquals("LOCAL_DEMO_CONNECTION_FAILED", manager.deviceStateProvider.currentSnapshot().errorCode)
                onView(withId(R.id.btn_test_scenario)).perform(scrollTo(), click())
                onView(withText("连接中")).perform(click())
                val pending = manager.deviceStateProvider.currentSnapshot().generation
                onView(withId(R.id.btn_test_cancel_wait)).perform(scrollTo(), click())
                val cancelled = manager.deviceStateProvider.currentSnapshot()
                assertEquals(DeviceMode.LOCAL_DEMO, cancelled.mode)
                assertEquals(LinkState.DISCONNECTED, cancelled.linkState)
                assertTrue(cancelled.generation > pending)
                onView(withId(R.id.btn_test_reconnect)).perform(scrollTo(), click())
                assertEquals(LinkState.CONNECTED, manager.deviceStateProvider.currentSnapshot().linkState)
                onView(withId(R.id.btn_test_close)).perform(scrollTo(), click())
                onView(withId(R.id.tv_device_status)).check(matches(withText(containsString("本地演示 · 已连接"))))
            }
        } finally { instrumentation.runOnMainSync { manager.disableSimulator() } }
    }

    @Test fun homeRestoresLatestSnapshotAfterStopAndDrawerBackKeepsHomeOpen() {
        instrumentation.runOnMainSync { manager.initSDK(instrumentation.targetContext); manager.enableSimulator() }
        try {
            ActivityScenario.launch(HomeActivity::class.java).use { activity ->
                onView(withId(R.id.tv_device_card_title)).check(matches(withText("设备状态")))
                onView(withId(R.id.progress_storage)).check(matches(withEffectiveVisibility(Visibility.GONE)))
                activity.moveToState(Lifecycle.State.CREATED)
                instrumentation.runOnMainSync { manager.setDemoScenario(DroneManager.DemoScenario.CONNECTION_FAILED) { _, _ -> } }
                activity.moveToState(Lifecycle.State.RESUMED)
                onView(withId(R.id.tv_device_status)).check(matches(withText(containsString("本地演示 · 连接失败"))))
                onView(withId(R.id.btn_navigation)).perform(click())
                pressBack()
                onView(withId(R.id.tv_switch_device)).check(matches(isDisplayed()))
                activity.recreate()
                onView(withId(R.id.tv_device_status)).check(matches(withText(containsString("本地演示 · 连接失败"))))
            }
        } finally { instrumentation.runOnMainSync { manager.disableSimulator() } }
    }
}
