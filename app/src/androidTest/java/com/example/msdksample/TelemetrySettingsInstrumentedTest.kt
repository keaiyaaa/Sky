package com.example.msdksample

import android.content.Context
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.action.ViewActions.*
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.msdksample.aerial.Aerial_photography
import com.example.msdksample.drone.*
import com.example.msdksample.drone.state.*
import com.example.msdksample.settings.*
import com.example.msdksample.ui.settings.*
import org.hamcrest.Matchers.containsString
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class TelemetrySettingsInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun migrationKeepsOriginalSettingsAndDoesNotBackUpCredentials() {
        val oldName = "legacy-settings-${UUID.randomUUID()}"
        val newName = "new-settings-${UUID.randomUUID()}"
        val old = context.getSharedPreferences(oldName, Context.MODE_PRIVATE)
        val fresh = context.getSharedPreferences(newName, Context.MODE_PRIVATE)
        try {
            old.edit().putInt("control_sensitivity", 7).putInt("return_altitude", 120)
                .putString("registered_password", "fixture-do-not-copy").putBoolean("auto_sync", false).commit()
            val store = UserSettingsPreferences(context, fresh, old)
            val first = store.load()
            assertEquals(first, UserSettingsPreferences(context, fresh, old).load())
            assertTrue(first.sensitivityNeedsReview)
            assertEquals(5, first.settings.control_sensitivity)
            assertEquals(7, old.getInt("control_sensitivity", -1))
            assertTrue(fresh.getString("legacy_backup", "")!!.contains("control_sensitivity"))
            assertFalse(fresh.getString("legacy_backup", "")!!.contains("registered_password"))
            val updated = first.settings.copy(return_altitude_m = BigDecimal("100.25"), control_sensitivity = 6,
                failsafe_action = "2", obstacle_action = "1", auto_sync_records = "0")
            store.save(updated, sensitivityReviewed = true)
            val reopened = UserSettingsPreferences(context, fresh, old)
            assertEquals(updated, reopened.load().settings)
            assertFalse(reopened.load().sensitivityNeedsReview)
            reopened.save(updated.copy(return_altitude_m = null))
            assertNull(UserSettingsPreferences(context, fresh, old).load().settings.return_altitude_m)
            assertEquals("0", fresh.getString("auto_sync_records", null))
            fresh.edit().putInt("schema_version", 99).commit()
            val unknownVersion = runCatching { reopened.load() }
            assertTrue(unknownVersion.isFailure)
            assertEquals(99, fresh.getInt("schema_version", 0))
            assertEquals("0", fresh.getString("auto_sync_records", null))
        } finally { context.deleteSharedPreferences(oldName); context.deleteSharedPreferences(newName) }
    }

    @Test fun observerExpiresOneFieldWithoutRewritingObservationsAndCancelsQueuedUpdates() {
        val store = SessionDeviceStateStore(monotonicMs = SystemClock::elapsedRealtime)
        store.connection(DeviceMode.LOCAL_DEMO, SdkRegistrationState.NOT_INITIALIZED, LinkState.CONNECTED, null)
        val now = SystemClock.elapsedRealtime()
        val epoch = System.currentTimeMillis()
        store.telemetry(TelemetrySnapshot(store.currentSnapshot().generation, epoch, now,
            batteryPercent = TelemetryValue(80, TelemetrySource.LOCAL_DEMO, epoch, now - 4000, true),
            signalPercent = TelemetryValue(60, TelemetrySource.LOCAL_DEMO, epoch, now, true)))
        val original = store.currentSnapshot()
        val initial = CountDownLatch(1); val expired = CountDownLatch(1); val calls = AtomicInteger()
        lateinit var observer: TelemetryUiObserver
        instrumentation.runOnMainSync {
            observer = TelemetryUiObserver(store) { _, display ->
                calls.incrementAndGet()
                if (display.batteryPercent == 80) initial.countDown()
                if (display.batteryPercent == null && display.signalPercent == 60) expired.countDown()
            }
        }
        try {
            assertTrue(initial.await(2, TimeUnit.SECONDS))
            assertTrue(expired.await(3, TimeUnit.SECONDS))
            assertEquals(original, store.currentSnapshot())
            instrumentation.runOnMainSync { observer.cancel(); observer.cancel() }
            val previousCalls = calls.get()
            store.connection(DeviceMode.OFFLINE, SdkRegistrationState.NOT_INITIALIZED, LinkState.DISCONNECTED, null)
            instrumentation.waitForIdleSync()
            assertEquals(previousCalls, calls.get())
        } finally { instrumentation.runOnMainSync { observer.cancel() } }
    }

    @Test fun settingsNeverDispatchToRealOrSdkSimulatorOrForCloudOnlyFields() {
        val applyCalls = AtomicInteger()
        val store = SessionDeviceStateStore()
        DeviceMode.values().filter { it != DeviceMode.LOCAL_DEMO }.forEach { mode ->
            store.connection(mode, SdkRegistrationState.REGISTERED, LinkState.CONNECTED, DeviceIdentity(1, "fixture", "1", "fixture"))
            instrumentation.runOnMainSync {
                val sync = SettingsSyncController(context, onChanged = { text, busy ->
                    if (!busy) assertTrue(text.contains("未同步"))
                }, provider = store, apply = { _, _, reply -> applyCalls.incrementAndGet(); reply(true, null) })
                sync.start(listOf(SettingValue(SettingField.VIDEO, "1080p")))
                sync.detach()
            }
        }
        store.connection(DeviceMode.LOCAL_DEMO, SdkRegistrationState.NOT_INITIALIZED, LinkState.CONNECTED, null)
        instrumentation.runOnMainSync {
            val sync = SettingsSyncController(context, onChanged = { text, busy ->
                if (!busy) assertTrue(text.contains("账户保存请单独操作"))
            }, provider = store, apply = { _, _, _ -> applyCalls.incrementAndGet() })
            sync.start(listOf(SettingValue(SettingField.AUTO_SYNC_RECORDS, "1")))
            sync.detach()
        }
        assertEquals(0, applyCalls.get())
    }

    @Test fun generationIsCheckedImmediatelyBeforeEachDispatchAndLateRepliesCannotRestoreSuccess() {
        val store = SessionDeviceStateStore()
        store.connection(DeviceMode.LOCAL_DEMO, SdkRegistrationState.NOT_INITIALIZED, LinkState.CONNECTED, null)
        var calls = 0; var completions = 0; var finalText = ""
        val finished = CountDownLatch(1)
        lateinit var lateReply: (Boolean, String?) -> Unit
        lateinit var sync: SettingsSyncController
        instrumentation.runOnMainSync {
            sync = SettingsSyncController(context, onChanged = { text, busy ->
                if (!busy) { finalText = text; completions++; finished.countDown() }
            }, provider = store, apply = { _, _, reply ->
                calls++; lateReply = reply
                store.connection(DeviceMode.OFFLINE, SdkRegistrationState.NOT_INITIALIZED, LinkState.DISCONNECTED, null)
            })
            sync.start(listOf(SettingValue(SettingField.VIDEO, "1080p"), SettingValue(SettingField.IMAGE, "high")))
        }
        try {
            assertTrue(finished.await(3, TimeUnit.SECONDS))
            assertEquals(1, calls)
            assertFalse(finalText.contains("同步成功"))
            assertTrue(finalText.contains("会话已变化"))
            instrumentation.runOnMainSync { lateReply(true, null) }
            instrumentation.waitForIdleSync()
            assertEquals(1, completions)
        } finally { instrumentation.runOnMainSync { sync.detach() } }
    }

    @Test fun timeoutAndDetachIgnoreDuplicateOrLateCallbacks() {
        val store = SessionDeviceStateStore()
        store.connection(DeviceMode.LOCAL_DEMO, SdkRegistrationState.NOT_INITIALIZED, LinkState.CONNECTED, null)
        var completions = 0; var text = ""; var applies = 0
        val done = CountDownLatch(1)
        lateinit var sync: SettingsSyncController
        lateinit var lateReply: (Boolean, String?) -> Unit
        instrumentation.runOnMainSync {
            sync = SettingsSyncController(context, onChanged = { result, busy ->
                if (!busy) { completions++; text = result; done.countDown() }
            }, provider = store, timeoutMillis = 200, apply = { value, _, reply ->
                applies++
                if (value.field == SettingField.VIDEO) { reply(true, null); reply(false, "duplicate") }
                else lateReply = reply
            })
            val batch = listOf(SettingValue(SettingField.VIDEO, "1080p"), SettingValue(SettingField.IMAGE, "high"))
            sync.start(batch); sync.start(batch)
        }
        assertTrue(done.await(3, TimeUnit.SECONDS))
        assertEquals(2, applies)
        assertTrue(text.contains("等待设备确认超时"))
        assertTrue(text.contains("演示确认=1080p"))
        instrumentation.runOnMainSync { lateReply(true, null); sync.detach() }
        instrumentation.waitForIdleSync()
        assertEquals(1, completions)
        instrumentation.runOnMainSync {
            val detached = SettingsSyncController(context, onChanged = { _, busy -> if (!busy) completions++ },
                provider = store, apply = { _, _, reply -> lateReply = reply })
            detached.start(listOf(SettingValue(SettingField.VIDEO, "1080p")))
            detached.detach(); lateReply(true, null)
        }
        instrumentation.waitForIdleSync()
        assertEquals(1, completions)
    }

    @Test fun bothScreensUseSameSnapshotAndMissingValuesStayMissing() {
        val manager = DroneManager.getInstance()
        instrumentation.runOnMainSync { manager.initSDK(context); manager.enableSimulator() }
        try {
            ActivityScenario.launch(HomeActivity::class.java).use {
                onView(withId(R.id.tv_telemetry_source)).check(matches(withText(containsString("本地演示"))))
                onView(withId(R.id.card_start_flight)).perform(click())
                onView(withId(R.id.tv_telemetry_source)).check(matches(withText(containsString("本地演示"))))
                instrumentation.runOnMainSync { manager.setDemoScenario(DroneManager.DemoScenario.MISSING_DATA) { _, _ -> } }
                onView(withId(R.id.tvHeight)).check(matches(withText("--m")))
                onView(withId(R.id.tv_telemetry_source)).check(matches(withText(containsString("信号--%"))))
                pressBack()
                onView(withId(R.id.tv_osd_height)).check(matches(withText("H:--m")))
                onView(withId(R.id.tv_telemetry_source)).check(matches(withText(containsString("信号--%"))))
                onView(withId(R.id.tv_telemetry_source)).check(matches(withText(containsString("本地演示"))))
            }
        } finally { instrumentation.runOnMainSync { manager.disableSimulator() } }
    }
}
