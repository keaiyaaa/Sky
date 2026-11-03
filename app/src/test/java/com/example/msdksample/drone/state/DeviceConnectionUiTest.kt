package com.example.msdksample.drone.state

import org.junit.Assert.*
import org.junit.Test

class DeviceConnectionUiTest {
    private val identity = DeviceIdentity(7, "fixture model", "1.0", "fixture-sn")
    private fun state(mode: DeviceMode = DeviceMode.DEVICE) = DeviceStateSnapshot(
        mode = mode, linkState = LinkState.CONNECTED, readiness = Readiness.DEGRADED, device = identity)

    @Test fun fourSourcesRemainDistinctEvenWhenLinkIsConnected() {
        val labels = DeviceMode.values().map { DeviceStatePresentation.from(state(it)).status }
        assertEquals(4, labels.distinct().size)
        assertTrue(labels.any { it.contains("DJI SDK 模拟器") })
        assertTrue(DeviceStatePresentation.from(state(DeviceMode.LOCAL_DEMO)).details.contains("不会下发飞机指令"))
    }

    @Test fun connectedIdentityDoesNotInventSuccessfulRegistrationOrReadiness() {
        val text = DeviceStatePresentation.from(state().copy(sdkRegistration = SdkRegistrationState.REGISTER_FAILED))
        assertTrue(text.details.contains("SDK：注册失败"))
        assertTrue(text.status.contains("待确认"))
        assertFalse(text.details.contains("SDK 正常"))
    }

    @Test fun databaseIdentityMappingKeepsExactColumnsAndExcludesNonDeviceSources() {
        assertEquals(DroneIdentityFields("fixture model", "fixture-sn", "1.0"), DroneIdentityFields.from(state()))
        DeviceMode.values().filter { it != DeviceMode.DEVICE }.forEach { assertNull(DroneIdentityFields.from(state(it))) }
        assertNull(DroneIdentityFields.from(state().copy(linkState = LinkState.DISCONNECTED)))
        val missing = DroneIdentityFields.from(state().copy(device = identity.copy(serialNumber = null, productType = "未知")))!!
        assertNull(missing.serial_no)
        assertNull(missing.model)
        assertNull(DroneIdentityFields.from(state().copy(device = identity.copy(productType = "UNKNOWN")))!!.model)
        assertEquals(1, missing.validationErrors().size)
    }

    @Test fun databaseLengthsAreValidatedWithoutTruncatingObservedValues() {
        assertTrue(DroneIdentityFields("m".repeat(64), "s".repeat(128), "f".repeat(64)).validationErrors().isEmpty())
        assertTrue(DroneIdentityFields("\uD83D\uDE80".repeat(64), "serial", null).validationErrors().isEmpty())
        val oversized = DroneIdentityFields("m".repeat(65), "s".repeat(129), "f".repeat(65))
        assertEquals(3, oversized.validationErrors().size)
        assertEquals(129, oversized.serial_no!!.length)
    }

    @Test fun disconnectedDemoKeepsSourceButIsUnavailableAndWaitingOfflineIsConnecting() {
        val store = SessionDeviceStateStore()
        store.connection(DeviceMode.LOCAL_DEMO, SdkRegistrationState.NOT_INITIALIZED, LinkState.CONNECTING, null)
        val old = store.currentSnapshot().generation
        store.connection(DeviceMode.LOCAL_DEMO, SdkRegistrationState.NOT_INITIALIZED, LinkState.DISCONNECTED, null)
        assertEquals(DeviceMode.LOCAL_DEMO, store.currentSnapshot().mode)
        assertEquals(Readiness.UNAVAILABLE, store.currentSnapshot().readiness)
        assertTrue(store.currentSnapshot().generation > old)
        store.connection(DeviceMode.OFFLINE, SdkRegistrationState.REGISTERED, LinkState.CONNECTING, null)
        assertEquals(Readiness.CONNECTING, store.currentSnapshot().readiness)
    }
}
