package com.example.msdksample.kami.map

import android.os.Bundle
import com.example.msdksample.kami.model.FlightZone
import com.example.msdksample.kami.model.Waypoint

interface RouteMapController {
    fun setCallbacks(callbacks: RouteMapCallbacks)
    fun onCreate(savedInstanceState: Bundle?)
    fun onResume()
    fun onPause()
    fun onDestroy()
    fun enableMyLocation()
    fun moveTo(latitude: Double, longitude: Double, zoom: Float = 15f)
    fun renderFlightZones(zones: List<FlightZone>)
    fun renderWaypoints(waypoints: List<Waypoint>)
    fun renderRoute(waypoints: List<Waypoint>, flownWaypointIndex: Int)
    fun showAircraftPosition(latitude: Double, longitude: Double)
    fun clear()
}
