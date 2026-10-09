package com.example.msdksample.kami.mission

data class AircraftTelemetry(
    val latitude: Double,
    val longitude: Double,
    val altitude: Float,
    val headingDegrees: Float,
    val timestampMillis: Long = System.currentTimeMillis()
)

fun interface AircraftTelemetryListener {
    fun onTelemetryChanged(telemetry: AircraftTelemetry)
}
