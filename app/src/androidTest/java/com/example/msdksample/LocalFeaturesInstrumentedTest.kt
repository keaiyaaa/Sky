package com.example.msdksample

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.msdksample.data.local.LocalDataStore
import com.example.msdksample.data.local.LocalProfile
import com.example.msdksample.data.local.SavedDevice
import com.example.msdksample.drone.DroneManager
import com.example.msdksample.drone.FlightTelemetry
import com.example.msdksample.drone.state.DeviceMode
import com.example.msdksample.drone.state.LinkState
import com.example.msdksample.drone.state.DeviceAction
import com.example.msdksample.kami.mission.DjiSdkManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class LocalFeaturesInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var context: Context
    private lateinit var store: LocalDataStore
    private val databaseName = "local-features-test-${UUID.randomUUID()}.db"

    @Before fun setup() {
        context = instrumentation.targetContext
        store = LocalDataStore(context, databaseName)
    }
    @After fun teardown() {
        store.close()
        context.deleteDatabase(databaseName)
        instrumentation.runOnMainSync { DroneManager.getInstance().disableSimulator() }
    }

    @Test fun profilesAndDevicesSurviveReopenWithoutMixingOwners() {
        val profile = LocalProfile("彭诗情", "peng@example.com", "巡检组")
        assertTrue(store.saveProfile("guest", profile))
        val device = SavedDevice("demo:SIM-1", "演示飞机", "SIM-1", "local", true)
        assertTrue(store.bindDevice("guest", device))
        assertFalse(store.bindDevice("guest", device.copy(name = "重复绑定不应覆盖")))
        assertTrue(store.bindDevice("other", device))
        store.close()
        store = LocalDataStore(context, databaseName)
        assertEquals(profile, store.profile("guest"))
        assertNull(store.profile("other"))
        assertEquals(listOf(device), store.devices("guest"))
        assertFalse(store.unbindDevice("missing", device.key))
        assertTrue(store.unbindDevice("guest", device.key))
        assertTrue(store.devices("guest").isEmpty())
        assertEquals(listOf(device), store.devices("other"))
    }

    @Test fun logSearchEscapesWildcardsAndOrdersSameTimeEventsById() {
        store.record("任务", "巡检100%", "失败", "本地演示", "错误_A", "route-1", 1000)
        val lastId = store.record("指令", "返航", "成功", "设备连接", "错误BA", "route-2", 1000)
        assertEquals(lastId, store.logs().first().id)
        assertEquals(1, store.logs("%").size)
        assertEquals(1, store.logs("_").size)
        assertTrue(store.logs("' OR 1=1 --").isEmpty())
        assertEquals("返航", store.logs("route-2", "设备连接").single().action)
        assertTrue(store.logs("route-2", "本地演示").isEmpty())
    }

    @Test fun demoConnectionFailureClearsTelemetryAndReconnectRestoresIt() {
        val manager = DroneManager.getInstance()
        instrumentation.runOnMainSync {
            manager.initSDK(context)
            manager.enableSimulator()
        }
        awaitValidDemoTelemetry(manager)
        assertEquals(DjiSdkManager.isRegistered(), manager.isRegistered())
        instrumentation.runOnMainSync {
            manager.setDemoScenario(DroneManager.DemoScenario.CONNECTION_FAILED) { success, _ -> assertTrue(success) }
        }
        assertFalse(manager.isConnected())
        assertEquals(DroneManager.ConnectionStatus.FAILED, manager.getConnectionStatus())
        assertEquals(DeviceMode.LOCAL_DEMO, manager.deviceStateProvider.currentSnapshot().mode)
        assertEquals(LinkState.ERROR, manager.deviceStateProvider.currentSnapshot().linkState)
        assertEquals(FlightTelemetry(), manager.getTelemetry())
        instrumentation.runOnMainSync { manager.connectDevice { success, _ -> assertTrue(success) } }
        awaitValidDemoTelemetry(manager)
        assertTrue(manager.isConnected())
    }

    @Test fun bothScreensCanSubscribeAndInvalidDemoReadingsAreHidden() {
        val manager = DroneManager.getInstance()
        val first = CountDownLatch(1)
        val second = CountDownLatch(1)
        val left = DroneManager.TelemetryListener { if (it.batteryPercent != null) first.countDown() }
        val right = DroneManager.TelemetryListener { if (it.batteryPercent != null) second.countDown() }
        instrumentation.runOnMainSync {
            manager.initSDK(context); manager.enableSimulator()
            manager.addTelemetryListener(left); manager.addTelemetryListener(right)
        }
        try {
            assertTrue(first.await(5, TimeUnit.SECONDS)); assertTrue(second.await(5, TimeUnit.SECONDS))
            instrumentation.runOnMainSync {
                manager.setDemoScenario(DroneManager.DemoScenario.INVALID_DATA) { _, _ -> }
            }
            val invalid = CountDownLatch(1)
            val observer = DroneManager.TelemetryListener {
                if (it.source == FlightTelemetry.Source.LOCAL_DEMO && it.updatedAtMillis != null && it.batteryPercent == null) invalid.countDown()
            }
            instrumentation.runOnMainSync { manager.addTelemetryListener(observer) }
            try { assertTrue(invalid.await(5, TimeUnit.SECONDS)) }
            finally { instrumentation.runOnMainSync { manager.removeTelemetryListener(observer) } }
            assertNull(manager.getTelemetry().altitudeMeters)
            assertNull(manager.getTelemetry().speedMetersPerSecond)
        } finally { instrumentation.runOnMainSync { manager.removeTelemetryListener(left); manager.removeTelemetryListener(right) } }
    }

    @Test fun settingsReceiveRealCallbacksForDemoSuccessAndFailure() {
        val manager = DroneManager.getInstance()
        instrumentation.runOnMainSync { manager.initSDK(context); manager.enableSimulator() }
        fun attempt(scenario: DroneManager.DemoScenario): Boolean? {
            val finished = CountDownLatch(1)
            var result: Boolean? = null
            instrumentation.runOnMainSync {
                manager.setDemoScenario(scenario) { _, _ -> }
                manager.setReturnAltitude(120) { success, _ -> result = success; finished.countDown() }
            }
            assertTrue(finished.await(5, TimeUnit.SECONDS))
            return result
        }
        assertEquals(true, attempt(DroneManager.DemoScenario.NORMAL))
        assertEquals(false, attempt(DroneManager.DemoScenario.SYNC_FAILED))
        val invalid = CountDownLatch(1)
        instrumentation.runOnMainSync {
            manager.setReturnAltitude(999) { success, _ -> assertFalse(success); invalid.countDown() }
        }
        assertTrue(invalid.await(2, TimeUnit.SECONDS))
    }

    private fun awaitValidDemoTelemetry(manager: DroneManager) {
        val available = CountDownLatch(1)
        val observer = DroneManager.TelemetryListener {
            if (it.source == FlightTelemetry.Source.LOCAL_DEMO && it.batteryPercent != null) available.countDown()
        }
        instrumentation.runOnMainSync { manager.addTelemetryListener(observer) }
        try { assertTrue(available.await(5, TimeUnit.SECONDS)) }
        finally { instrumentation.runOnMainSync { manager.removeTelemetryListener(observer) } }
    }

    @Test fun contractBridgeReplaysFirstFrameAndRejectsActionsWithUnknownCapabilities() {
        val manager = DroneManager.getInstance()
        instrumentation.runOnMainSync { manager.initSDK(context); manager.enableSimulator() }
        val provider = manager.deviceStateProvider
        val seen = mutableListOf<Long>()
        lateinit var subscription: com.example.msdksample.drone.state.DeviceStateSubscription
        var firstVersion = -1L
        instrumentation.runOnMainSync {
            firstVersion = provider.currentSnapshot().version
            subscription = provider.subscribe { seen += it.version }
        }
        assertEquals(firstVersion, seen.first())
        val before = provider.currentSnapshot().generation
        assertFalse(manager.checkDeviceAction(DeviceAction.PHOTO, before).allowed)
        instrumentation.runOnMainSync { manager.setDemoScenario(DroneManager.DemoScenario.CONNECTION_FAILED) { _, _ -> } }
        assertTrue(provider.currentSnapshot().generation > before)
        assertEquals(DeviceMode.LOCAL_DEMO, provider.currentSnapshot().mode)
        assertNull(provider.currentSnapshot().telemetry)
        subscription.cancel()
        val count = seen.size
        instrumentation.runOnMainSync { manager.connectDevice { _, _ -> } }
        assertEquals(count, seen.size)
        assertFalse(manager.checkDeviceAction(DeviceAction.PHOTO, before).allowed)
    }
}
