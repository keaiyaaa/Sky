package com.example.msdksample.drone

import com.example.msdksample.drone.state.*
import org.junit.Assert.*
import org.junit.Test

class TelemetryDisplayTest {
    private val now = 20_000L
    private val state = DeviceStateSnapshot(generation = 3, version = 8, mode = DeviceMode.LOCAL_DEMO,
        linkState = LinkState.CONNECTED, readiness = Readiness.READY)
    private fun value(number: Int, time: Long = now, source: TelemetrySource = TelemetrySource.LOCAL_DEMO) =
        TelemetryValue(number, source, 1000L, time, true, null)

    @Test fun zeroBatteryAndSignalAreRealValuesAndAgeUsesEachField() {
        val frame = TelemetrySnapshot(3, 1000, now, batteryPercent = value(0, now - 5000), signalPercent = value(0))
        val visible = TelemetryDisplay.from(state.copy(telemetry = frame), now)
        assertEquals(0, visible.batteryPercent)
        assertEquals(0, visible.signalPercent)
        assertEquals(now + 1, visible.nextRefreshAtMonotonicMs)
        val later = TelemetryDisplay.from(state.copy(telemetry = frame), now + 1)
        assertNull(later.batteryPercent)
        assertEquals(0, later.signalPercent)
        assertEquals("DISPLAY_STALE", later.unavailableReasons[TelemetryField.BATTERY])
    }

    @Test fun freshPositionCannotRefreshOldBatteryAndEpochChangesHaveNoEffect() {
        val oldBattery = value(80, now - 6000)
        val frame = TelemetrySnapshot(3, Long.MIN_VALUE, now, batteryPercent = oldBattery,
            latitudeDeg = TelemetryValue(31.0, TelemetrySource.LOCAL_DEMO, Long.MAX_VALUE, now, true))
        val display = TelemetryDisplay.from(state.copy(telemetry = frame), now)
        assertNull(display.batteryPercent)
        assertFalse(display.unavailableReasons.containsKey(TelemetryField.LATITUDE))
        assertEquals(display, TelemetryDisplay.from(state.copy(telemetry = frame.copy(receivedAtEpochMs = Long.MAX_VALUE)), now))
    }

    @Test fun missingInvalidWrongSourceAndFutureDataNeverBecomeValidZeroes() {
        val badFrames = listOf(
            TelemetrySnapshot(3, 1000, now, batteryPercent = value(101)),
            TelemetrySnapshot(3, 1000, now, batteryPercent = value(10, source = TelemetrySource.DEVICE)),
            TelemetrySnapshot(3, 1000, now, batteryPercent = value(10, now + 1)),
            TelemetrySnapshot(2, 1000, now, batteryPercent = value(10)),
            TelemetrySnapshot(3, 1000, now, batteryPercent = value(10).copy(valid = false)),
            TelemetrySnapshot(3, 1000, now))
        badFrames.forEach { assertNull(TelemetryDisplay.from(state.copy(telemetry = it), now).batteryPercent) }
    }

    @Test fun realAltitudeAndDistanceRequireReferencesButNegativeAltitudeIsNotInventedAsZero() {
        val real = state.copy(mode = DeviceMode.DEVICE)
        val altitude = TelemetryValue(-5.0, TelemetrySource.DEVICE, 1000L, now, true)
        val frame = TelemetrySnapshot(3, 1000, now, altitudeMeters = altitude,
            distanceMeters = altitude.copy(value = 0.0))
        assertNull(TelemetryDisplay.from(real.copy(telemetry = frame), now).altitudeMeters)
        val referenced = frame.copy(altitudeReference = "fixture reference", distanceReference = "fixture origin")
        val visible = TelemetryDisplay.from(real.copy(telemetry = referenced), now)
        assertEquals(-5f, visible.altitudeMeters)
        assertEquals(0f, visible.distanceMeters)
    }

    @Test fun connectionFailureClearsValuesAndKeepsLocalDemoProvenance() {
        val frame = TelemetrySnapshot(3, 1000, now, batteryPercent = value(80))
        val display = TelemetryDisplay.from(state.copy(linkState = LinkState.ERROR, telemetry = frame), now)
        assertNull(display.batteryPercent)
        assertNull(display.nextRefreshAtMonotonicMs)
        assertTrue(display.sourceLabel.contains("本地演示"))
        assertTrue(display.sourceLabel.contains("连接失败"))
    }

    @Test fun fourSourcesRemainDistinctWithoutAnyRealObservations() {
        val labels = DeviceMode.values().map { TelemetryDisplay.from(state.copy(mode = it), now).sourceLabel }
        assertEquals(4, labels.distinct().size)
        assertTrue(labels.any { it.contains("DJI SDK 模拟器") })
        assertTrue(labels.any { it.contains("遥测待接入") })
    }

    @Test fun aLateInvalidObservationCannotEraseNewerTelemetry() {
        val store = SessionDeviceStateStore(monotonicMs = { now })
        store.connection(DeviceMode.LOCAL_DEMO, SdkRegistrationState.NOT_INITIALIZED, LinkState.CONNECTED, null)
        val generation = store.currentSnapshot().generation
        store.telemetry(TelemetrySnapshot(generation, 1000, now, batteryPercent = value(80)))
        val good = store.currentSnapshot()
        store.telemetry(TelemetrySnapshot(generation, 1000, now,
            batteryPercent = TelemetryValue(null, TelemetrySource.LOCAL_DEMO, 1000, now - 1, false, "MISSING")))
        assertEquals(good, store.currentSnapshot())
    }
}
