package com.example.msdksample.drone.state

import org.junit.Assert.*
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class DeviceStateContractTest {
    private var mono = 1000L
    private var epoch = 50_000L
    private fun store() = SessionDeviceStateStore({ epoch }, { mono })
    private val identity = DeviceIdentity(1, "test fixture", "test", "fixture-SN")

    private fun connected(store: SessionDeviceStateStore, mode: DeviceMode = DeviceMode.DEVICE): Long {
        store.connection(mode, SdkRegistrationState.REGISTERED, LinkState.CONNECTED, identity)
        return store.currentSnapshot().generation
    }
    private fun frame(generation: Long, source: TelemetrySource = TelemetrySource.DEVICE,
                      batteryTime: Long = mono) = TelemetrySnapshot(generation, epoch, mono,
        batteryPercent = TelemetryValue(80, source, epoch, batteryTime, true, null))

    @Test fun firstFrameIsCurrentAndReentrantUpdatesAreOrderedForEverySubscriber() {
        val store = store()
        val first = mutableListOf<Long>(); val second = mutableListOf<Long>()
        store.subscribe { value ->
            first += value.version
            if (value.version == 1L) store.connection(DeviceMode.LOCAL_DEMO, SdkRegistrationState.REGISTERED,
                LinkState.ERROR, null)
        }
        store.subscribe { second += it.version }
        connected(store, DeviceMode.LOCAL_DEMO)
        assertEquals(listOf(0L, 1L, 2L), first)
        assertEquals(first, second)
        assertEquals(DeviceMode.LOCAL_DEMO, store.currentSnapshot().mode)
        assertEquals(LinkState.ERROR, store.currentSnapshot().linkState)
    }

    @Test fun cancelDropsQueuedEventsAndIsIdempotent() {
        val store = store()
        var calls = 0
        lateinit var subscription: DeviceStateSubscription
        subscription = store.subscribe {
            calls++
            if (it.version == 1L) {
                store.connection(DeviceMode.OFFLINE, SdkRegistrationState.REGISTERED, LinkState.DISCONNECTED, null)
                subscription.cancel()
            }
        }
        connected(store)
        subscription.cancel()
        assertEquals(2, calls)
    }

    @Test fun sameProductIdReconnectCannotAcceptOldIdentityTelemetryOrCapabilities() {
        val store = store()
        val old = connected(store)
        store.connection(DeviceMode.OFFLINE, SdkRegistrationState.REGISTERED, LinkState.DISCONNECTED, null)
        val current = connected(store)
        assertTrue(current > old)
        assertFalse(store.connection(DeviceMode.DEVICE, SdkRegistrationState.REGISTERED, LinkState.CONNECTED,
            identity.copy(serialNumber = "old callback"), expectedGeneration = old))
        assertFalse(store.telemetry(frame(old)))
        assertFalse(store.capabilities(DeviceCapabilities(old, mono, photo = true)))
        assertEquals(identity, store.currentSnapshot().device)
        assertNull(store.currentSnapshot().telemetry)
        assertNull(store.currentSnapshot().capabilities)
    }

    @Test fun connectionChangesClearObservationsButRegistrationUpdatesDoNotCreateSessions() {
        val store = store(); val generation = connected(store)
        store.telemetry(frame(generation)); store.capabilities(DeviceCapabilities(generation, mono, photo = true))
        store.connection(DeviceMode.DEVICE, SdkRegistrationState.REGISTER_FAILED, LinkState.CONNECTED, identity)
        assertEquals(generation, store.currentSnapshot().generation)
        assertEquals(Readiness.DEGRADED, store.currentSnapshot().readiness)
        store.connection(DeviceMode.DJI_SIMULATOR, SdkRegistrationState.REGISTERED, LinkState.CONNECTED, identity)
        assertTrue(store.currentSnapshot().generation > generation)
        assertNull(store.currentSnapshot().telemetry); assertNull(store.currentSnapshot().capabilities)
    }

    @Test fun unknownSdkSimulatorStateIsNeverReadyAndDoesNotErasePhysicalIdentity() {
        val store = store()
        store.connection(DeviceMode.DEVICE, SdkRegistrationState.REGISTERED, LinkState.CONNECTED,
            identity, simulatorStateKnown = false)
        assertEquals(identity, store.currentSnapshot().device)
        assertEquals(Readiness.DEGRADED, store.currentSnapshot().readiness)
        assertEquals("SDK_SIMULATOR_STATE_UNKNOWN", store.currentSnapshot().errorCode)
    }

    @Test fun hiddenPhysicalDisconnectCanInvalidateSessionWithoutChangingDemoSource() {
        val store = store(); val old = connected(store, DeviceMode.LOCAL_DEMO)
        store.connection(DeviceMode.LOCAL_DEMO, SdkRegistrationState.REGISTERED, LinkState.CONNECTED,
            identity, forceNewSession = true)
        assertEquals(DeviceMode.LOCAL_DEMO, store.currentSnapshot().mode)
        assertTrue(store.currentSnapshot().generation > old)
        assertFalse(store.telemetry(frame(old, TelemetrySource.LOCAL_DEMO)))
    }

    @Test fun wrongSourceInvalidPercentAndOutOfOrderObservationsNeverReplaceGoodData() {
        val store = store(); val generation = connected(store)
        store.telemetry(frame(generation))
        val good = store.currentSnapshot()
        store.telemetry(frame(generation, TelemetrySource.LOCAL_DEMO))
        store.telemetry(frame(generation).copy(batteryPercent = TelemetryValue(101, TelemetrySource.DEVICE, epoch, mono, true)))
        store.telemetry(frame(generation, batteryTime = mono - 1))
        store.telemetry(frame(generation).copy(altitudeMeters = TelemetryValue(Double.NaN, TelemetrySource.DEVICE, epoch, mono, true)))
        assertEquals(good, store.currentSnapshot())
    }

    @Test fun perFieldAgesUseMonotonicTimeAndIgnoreWallClockChanges() {
        val store = store(); val generation = connected(store)
        store.capabilities(DeviceCapabilities(generation, mono, photo = true))
        store.telemetry(frame(generation, batteryTime = mono - 500))
        epoch -= 1_000_000
        val policy = mapOf(TelemetryField.BATTERY to 100L)
        assertEquals("TELEMETRY_STALE", DeviceActionGate.check(store.currentSnapshot(), generation,
            DeviceAction.PHOTO, policy, mono).errorCode)
        store.telemetry(frame(generation))
        assertTrue(DeviceActionGate.check(store.currentSnapshot(), generation, DeviceAction.PHOTO, policy, mono).allowed)
    }

    @Test fun consumerCallsDeviceOnlyWhenEveryIndependentPreconditionPasses() {
        val store = store(); val generation = connected(store)
        store.capabilities(DeviceCapabilities(generation, mono, photo = true))
        store.telemetry(frame(generation))
        val ready = store.currentSnapshot()
        val policy = mapOf(TelemetryField.BATTERY to 100L)
        var calls = 0
        fun dispatch(state: DeviceStateSnapshot, expected: Long = generation,
                     required: Map<TelemetryField, Long>? = policy) {
            if (DeviceActionGate.check(state, expected, DeviceAction.PHOTO, required, mono).allowed) calls++
        }
        DeviceMode.values().filter { it != DeviceMode.DEVICE }.forEach { dispatch(ready.copy(mode = it)) }
        dispatch(ready.copy(sdkRegistration = SdkRegistrationState.REGISTERING))
        dispatch(ready.copy(linkState = LinkState.DISCONNECTED))
        dispatch(ready.copy(readiness = Readiness.DEGRADED))
        dispatch(ready.copy(capabilities = null))
        dispatch(ready.copy(capabilities = DeviceCapabilities(generation, mono, photo = false)))
        dispatch(ready.copy(capabilities = DeviceCapabilities(generation, mono)))
        dispatch(ready.copy(telemetry = null))
        dispatch(ready.copy(telemetry = frame(generation - 1)))
        dispatch(ready, generation - 1)
        dispatch(ready, required = null)
        assertEquals(0, calls)
        dispatch(ready)
        assertEquals(1, calls)
        store.connection(DeviceMode.OFFLINE, SdkRegistrationState.REGISTERED, LinkState.DISCONNECTED, null)
        dispatch(store.currentSnapshot())
        assertEquals(1, calls)
    }

    @Test fun concurrentPublishersNeverDeliverParallelOrOutOfOrderCallbacks() {
        val store = store()
        val versions = Collections.synchronizedList(mutableListOf<Long>())
        store.subscribe { versions += it.version }
        val executor = Executors.newFixedThreadPool(4)
        val finished = CountDownLatch(80)
        repeat(80) { number -> executor.execute {
            try { store.connection(DeviceMode.LOCAL_DEMO, SdkRegistrationState.REGISTERED,
                LinkState.CONNECTED, identity.copy(serialNumber = "$number")) } finally { finished.countDown() }
        } }
        assertTrue(finished.await(5, TimeUnit.SECONDS))
        executor.shutdown(); assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS))
        assertEquals((0L..80L).toList(), versions.toList())
    }

    @Test fun subscriberFailureCannotStopOtherSubscribersOrHideProviderError() {
        val store = store(); val values = mutableListOf<DeviceStateSnapshot>()
        store.subscribe { error("consumer failed") }; store.subscribe { values += it }
        val generation = connected(store)
        store.failure(generation, "STATE_PROVIDER_ERROR", "测试错误")
        assertEquals(Readiness.ERROR, values.last().readiness)
        assertEquals("STATE_PROVIDER_ERROR", values.last().errorCode)
    }
}
