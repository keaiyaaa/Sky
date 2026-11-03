package com.example.msdksample.drone

import org.junit.Assert.*
import org.junit.Test

class FlightTelemetryTest {
    @Test fun offlineClearsAllCachedValues() {
        val disconnected = FlightTelemetry(FlightTelemetry.Source.OFFLINE, 25f, 100f, 8f, 85, 90, 1000).validated()
        assertEquals(FlightTelemetry(), disconnected)
    }

    @Test fun invalidFieldsDoNotEraseOtherValidFields() {
        val sample = FlightTelemetry(FlightTelemetry.Source.LOCAL_DEMO, Float.NaN, -1f,
            Float.POSITIVE_INFINITY, 101, 85, 1000).validated()
        assertNull(sample.altitudeMeters)
        assertNull(sample.distanceMeters)
        assertNull(sample.speedMetersPerSecond)
        assertNull(sample.batteryPercent)
        assertEquals(85, sample.signalPercent)
        assertEquals(FlightTelemetry.Source.LOCAL_DEMO, sample.source)
    }

    @Test fun negativeRelativeAltitudeAndZeroValuesAreValid() {
        val sample = FlightTelemetry(FlightTelemetry.Source.DJI, -5f, 0f, 0f, 0, 100, 1000)
        assertEquals(sample, sample.validated())
    }

    @Test fun staleSampleHidesValuesAndPreservesItsProvenance() {
        val sample = FlightTelemetry(FlightTelemetry.Source.LOCAL_DEMO, 25f, 100f, 8f, 85, 90, 1000)
        assertEquals(sample, sample.displayAt(6000))
        assertEquals(FlightTelemetry(FlightTelemetry.Source.LOCAL_DEMO), sample.displayAt(6001))
    }

    @Test fun physicalConnectionWithoutTelemetryDoesNotInventReadings() {
        val sample = FlightTelemetry(FlightTelemetry.Source.DJI)
        assertNull(sample.batteryPercent)
        assertNull(sample.altitudeMeters)
        assertTrue(sample.sourceLabel.contains("待接入"))
    }
}
