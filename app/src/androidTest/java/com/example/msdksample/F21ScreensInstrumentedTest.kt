package com.example.msdksample

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.onData
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.*
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.msdksample.data.local.LocalDataStore
import com.example.msdksample.data.logs.FlightLogPolicy
import com.example.msdksample.data.remote.BackendSessions
import com.example.msdksample.ui.logs.FlightLogActivity
import org.hamcrest.Matchers.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class F21ScreensInstrumentedTest {
    private val context=InstrumentationRegistry.getInstrumentation().targetContext
    private val id="f21-screen-${UUID.randomUUID()}"
    private val owner="user:$id"
    private val other="user:$id-other"
    private val store by lazy { LocalDataStore.getInstance(context) }
    @Before fun login() { BackendSessions.registry.install(id,"日志测试账户","fixture-only",System.currentTimeMillis()+600_000) }
    @After fun cleanup() {
        BackendSessions.registry.clear()
        store.writableDatabase.delete("flight_logs","owner IN (?,?)",arrayOf(owner,other))
        store.writableDatabase.delete("flight_logs","owner=? AND task_id=?",arrayOf(FlightLogPolicy.LEGACY_OWNER,id))
    }
    private fun record(action: String,eventOwner: String=owner,source: String="DEVICE",time: Long=1_793_750_400_000) {
        store.record("连接",action,"失败","设备连接","测试原因",id,time,eventOwner,source)
    }
    @Test fun sourceAndLegacyFiltersKeepAccountEventsSeparate() {
        record("Alice-only"); record("Bob-only",other); record("Legacy-only",FlightLogPolicy.LEGACY_OWNER)
        record("Demo-only",source="LOCAL_SIMULATION")
        ActivityScenario.launch(FlightLogActivity::class.java).use {
            awaitText("Alice-only")
            onView(withText(containsString("Bob-only"))).check(doesNotExist())
            onView(withId(R.id.log_source)).perform(scrollTo(),click())
            onData(equalTo("本地演示")).perform(click())
            awaitText("Demo-only")
            onView(withText(containsString("Alice-only"))).check(doesNotExist())
            onView(withId(R.id.log_source)).perform(scrollTo(),click())
            onData(equalTo("全部来源")).perform(click())
            onView(withId(R.id.log_scope)).perform(scrollTo(),click())
            onData(equalTo("本机未归属历史（只读）")).perform(click())
            awaitText("Legacy-only")
            onView(withText("上报／重试此事件")).check(doesNotExist())
        }
    }
    @Test fun pagingWorksAndInvalidDatesHaveClearErrors() {
        repeat(21) { record("page-event-$it",time=1000L+it) }
        ActivityScenario.launch(FlightLogActivity::class.java).use {
            awaitSummary("第1页，20条")
            onView(withText("下一页")).perform(scrollTo(),click())
            awaitSummary("第2页，1条")
            onView(withId(R.id.log_from)).perform(scrollTo(),replaceText("2026-02-30"))
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            onView(withText("查询／刷新")).perform(scrollTo(),click())
            awaitSummary("日期无效")
        }
    }
    @Test fun anUnavailableServiceLeavesTheExactUploadPending() {
        record("Pending-upload")
        ActivityScenario.launch(FlightLogActivity::class.java).use { scenario ->
            awaitText("Pending-upload")
            capture("f21-local-list")
            onView(withText("上报／重试此事件")).perform(scrollTo(),click())
            onView(withText("云端服务尚未配置，请联系管理员")).perform(scrollTo()).check(matches(isDisplayed()))
            val pending=store.logs(owner=owner).single()
            assertFalse(pending.uploaded); assertNotNull(pending.uploadBody)
            scenario.recreate(); awaitText("Pending-upload")
            assertEquals(pending.uploadBody,store.logs(owner=owner).single().uploadBody)
            onView(withId(R.id.log_scope)).perform(scrollTo(),click())
            onData(equalTo("账户日志")).perform(click())
            onView(withText("云端服务尚未配置，请联系管理员")).perform(scrollTo()).check(matches(isDisplayed()))
            capture("f21-account-status")
        }
    }
    private fun awaitText(value: String) = await {
        onView(withText(containsString(value))).perform(scrollTo()).check(matches(isDisplayed()))
    }
    private fun awaitSummary(value: String) = await { onView(withId(R.id.log_summary)).check(matches(withText(containsString(value)))) }
    private fun await(assertion: ()->Unit) {
        val end=System.currentTimeMillis()+6000
        while(true) try { assertion(); return } catch(e:Exception) { if(System.currentTimeMillis()>=end) throw e; Thread.sleep(50) }
            catch(e:AssertionError) { if(System.currentTimeMillis()>=end) throw e; Thread.sleep(50) }
    }
    private fun capture(name: String) {
        val file=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /sdcard/Download/$name.png")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(file).use { it.readBytes() }
    }
}
