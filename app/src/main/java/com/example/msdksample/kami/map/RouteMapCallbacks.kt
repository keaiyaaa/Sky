package com.example.msdksample.kami.map

import com.example.msdksample.kami.model.FlightZone

interface RouteMapCallbacks {
    fun onMapReady()
    fun onMapPointSelected(latitude: Double, longitude: Double)
    fun onWaypointDragged(waypointId: String, latitude: Double, longitude: Double)
    fun onFlightZoneSelected(zone: FlightZone)
}
