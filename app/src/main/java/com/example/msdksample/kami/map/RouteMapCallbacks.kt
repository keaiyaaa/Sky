package com.example.msdksample.kami.map

import com.example.msdksample.kami.model.FlightZone

interface RouteMapCallbacks {
    /**
     * 通知底图加载、失败和离线降级结果。
     * @param {String} message 用户可读的加载结果。
     * @return {Unit} 默认不处理，兼容既有调用方。
     */
    fun onMapStatusChanged(message: String) {}
    fun onMapReady()
    fun onMapPointSelected(latitude: Double, longitude: Double)
    fun onWaypointDragged(waypointId: String, latitude: Double, longitude: Double)
    fun onFlightZoneSelected(zone: FlightZone)
}
