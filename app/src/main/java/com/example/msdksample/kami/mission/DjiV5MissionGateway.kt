package com.example.msdksample.kami.mission

import java.io.File

/**
 * Boundary for the future DJI V5 implementation.
 *
 * The current page generates KMZ/WPML and hands the file to this boundary.
 * The concrete DJI V5 implementation should import the KMZ, validate aircraft
 * capability, upload the mission, observe aircraft telemetry, and start flight.
 */
interface DjiV5MissionGateway {
    fun setTelemetryListener(listener: AircraftTelemetryListener?)
    fun importKmz(kmzFile: File): MissionLaunchResult
    fun startMission(): MissionLaunchResult
    fun stopMission(): MissionLaunchResult
}
