package com.example.msdksample

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.clearText
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.action.ViewActions.swipeUp
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import org.junit.Test
import org.junit.runner.RunWith
import com.example.msdksample.drone.DroneManager
import com.example.msdksample.ui.settings.FlightSettingsActivity
import com.example.msdksample.ui.settings.VideoSettingsActivity
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matchers.containsString

@RunWith(AndroidJUnit4::class)
class PengNavigationInstrumentedTest {
    @Test fun settingsShowCallbackFailureThenDemoConfirmation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val manager = DroneManager.getInstance()
        instrumentation.runOnMainSync {
            com.example.msdksample.settings.UserSettingsPreferences(instrumentation.targetContext)
                .save(com.example.msdksample.settings.UserSettings(), sensitivityReviewed = true)
            manager.initSDK(instrumentation.targetContext)
            manager.enableSimulator()
            manager.setDemoScenario(DroneManager.DemoScenario.SYNC_FAILED) { _, _ -> }
        }
        try {
            ActivityScenario.launch(FlightSettingsActivity::class.java).use { scenario ->
                clickSaveByScrolling()
                awaitSyncText("演示同步失败")
                scenario.onActivity { manager.setDemoScenario(DroneManager.DemoScenario.NORMAL) { _, _ -> } }
                clickSaveByScrolling()
                awaitSyncText("演示同步成功")
                onView(withId(R.id.tv_sync_status)).check(matches(withText(containsString("演示确认=100.00m"))))
            }
            ActivityScenario.launch(VideoSettingsActivity::class.java).use {
                clickSaveByScrolling()
                awaitSyncText("演示同步成功")
                onView(withId(R.id.tv_sync_status)).check(matches(withText(containsString("未下发飞机"))))
            }
        } finally { instrumentation.runOnMainSync { manager.disableSimulator() } }
    }

    @Test fun profileDeviceAndLogEntriesOpenAndReturnToHome() {
        ActivityScenario.launch(HomeActivity::class.java).use {
            onView(withId(R.id.btn_navigation)).perform(click())
            onView(withText("个人中心")).perform(click())
            onView(withId(R.id.profile_nickname)).perform(scrollTo(), clearText())
            onView(withText("保存到本机")).perform(scrollTo(), click())
            onView(withId(R.id.profile_nickname)).check(matches(hasErrorText("请输入昵称")))
            closeSoftKeyboard()
            pressBack()
            onView(withId(R.id.tv_switch_device)).perform(click())
            onView(withText("当前连接设备")).perform(scrollTo()).check(matches(isDisplayed()))
            pressBack()
            onView(withId(R.id.btn_navigation)).perform(click())
            onView(withText("飞行日志")).perform(click())
            onView(withId(R.id.log_keyword)).check(matches(isDisplayed()))
            pressBack()
            onView(withId(R.id.btn_menu)).check(matches(isDisplayed()))
        }
    }

    private fun awaitSyncText(text: String) {
        val deadline = System.currentTimeMillis() + 5_000
        while (true) {
            try { onView(withId(R.id.tv_sync_status)).check(matches(withText(containsString(text)))); return }
            catch (error: AssertionError) {
                if (System.currentTimeMillis() >= deadline) throw error
                Thread.sleep(50)
            }
        }
    }

    private fun clickSaveByScrolling() {
        repeat(12) {
            try {
                onView(withId(R.id.btn_save)).check(matches(isCompletelyDisplayed()))
                onView(withId(R.id.btn_save)).perform(click())
                return
            } catch (_: AssertionError) {
                onView(isAssignableFrom(android.widget.ScrollView::class.java)).perform(swipeUp())
            }
        }
        onView(withId(R.id.btn_save)).check(matches(isCompletelyDisplayed()))
    }
}
