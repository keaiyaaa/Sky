package com.example.msdksample

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.*
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.msdksample.data.local.LocalDataStore
import com.example.msdksample.data.local.LocalProfile
import com.example.msdksample.data.remote.*
import com.example.msdksample.ui.device.DeviceManagementActivity
import com.example.msdksample.ui.profile.ProfileActivity
import com.google.gson.Gson
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.endsWith
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class F19F20ScreensInstrumentedTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val userId = "screen-test-${UUID.randomUUID()}"
    private val store by lazy { AccountCloudStore.getInstance(context) }
    private val local by lazy { LocalDataStore.getInstance(context) }
    private val json = Gson()
    @Before fun session() {
        BackendSessions.registry.install(userId, "测试账户", "instrumentation-only", System.currentTimeMillis() + 600_000)
    }
    @After fun cleanup() {
        BackendSessions.registry.clear()
        for (table in listOf("pending", "snapshots")) store.writableDatabase.delete(table, "user_id=?", arrayOf(userId))
        for (table in listOf("profiles", "devices")) local.writableDatabase.delete(table, "owner=?", arrayOf("user:$userId"))
    }

    @Test fun profileKeepsDraftWhenServiceIsUnavailableAndValidatesEmail() {
        local.saveProfile("user:$userId", LocalProfile("账户昵称", "", "巡检组"))
        store.saveSnapshot(userId, "profile", json.toJson(CloudProfile(userId, "测试账户", "账户昵称", "", "巡检组", 1, "2026-11-04T02:00:00Z")))
        ActivityScenario.launch(ProfileActivity::class.java).use {
            onView(withId(R.id.profile_email)).perform(scrollTo(), replaceText("bad"))
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            onView(withText("保存到账户")).perform(scrollTo(), click())
            onView(withId(R.id.profile_email)).check(matches(hasErrorText("邮箱格式不正确或超过254字")))
            onView(withId(R.id.profile_email)).perform(scrollTo(), replaceText("a@example.com"))
            onView(withId(R.id.profile_nickname)).perform(scrollTo(), replaceText("待保存昵称"))
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            onView(withText("保存到账户")).perform(scrollTo(), click())
            onView(withText("云端服务尚未配置，请联系管理员")).perform(scrollTo()).check(matches(isDisplayed()))
            capture("f19-profile-cloud")
            onView(withId(R.id.profile_email)).check(matches(hasErrorText(org.hamcrest.Matchers.nullValue(String::class.java))))
            onView(withId(R.id.profile_nickname)).perform(scrollTo())
            capture("f19-profile-top")
            assertEquals("待保存昵称", local.profile("user:$userId")!!.nickname)
            assertEquals(1L, json.fromJson(store.snapshot(userId, "profile"), CloudProfile::class.java).version)
        }
    }

    @Test fun deviceRenameIsPersistedAsPendingAndRestoredAfterRecreation() {
        val device = CloudDevice("42", "stable-client", "测试设备", "M30", "SN-TEST", "1", "DJI_SDK", 1,
            "2026-11-04T02:00:00Z", "2026-11-04T02:00:00Z", false)
        store.saveSnapshot(userId, "devices:0", json.toJson(DevicePage(listOf(device), 0, 20, 1)))
        ActivityScenario.launch(DeviceManagementActivity::class.java).use { scenario ->
            onView(withText("账户设备记录")).perform(scrollTo())
            capture("f20-device-list")
            onView(withText("修改 测试设备 的名称")).perform(scrollTo(), click())
            onView(withClassName(endsWith("EditText"))).perform(replaceText("新设备名称"))
            onView(withText("保存")).perform(click())
            val pending = store.pending(userId).single()
            assertTrue(pending.body.contains("新设备名称"))
            scenario.recreate()
            onView(withText("重试修改设备")).perform(scrollTo()).check(matches(isDisplayed()))
            assertEquals(listOf(pending), store.pending(userId))
            onView(withText(containsString("修改设备：新设备名称"))).perform(scrollTo()).check(matches(isDisplayed()))
            capture("f20-device-pending")
        }
    }
    private fun capture(name: String) {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /sdcard/Download/$name.png")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }
}
