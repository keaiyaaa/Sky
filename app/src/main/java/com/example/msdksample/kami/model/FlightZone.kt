package com.example.msdksample.kami.model

data class FlightZone(
    val id: String,
    val name: String,
    val centerLatitude: Double,
    val centerLongitude: Double,
    val radiusMeters: Double,
    val type: ZoneType
) {
    fun contains(latitude: Double, longitude: Double): Boolean {
        return GeoMath.haversineDistanceMeters(
            latitude,
            longitude,
            centerLatitude,
            centerLongitude
        ) <= radiusMeters
    }
}

enum class ZoneType {
    NO_FLY,
    RESTRICTED,
    SAFE
}

object DefaultFlightZones {
    val zones = listOf(
        FlightZone(
            id = "beijing-core-no-fly",
            name = "禁飞区",
            centerLatitude = 39.908823,
            centerLongitude = 116.397470,
            radiusMeters = 1000.0,
            type = ZoneType.NO_FLY
        ),
        FlightZone(
            id = "beijing-restricted",
            name = "限高区",
            centerLatitude = 39.923540,
            centerLongitude = 116.408820,
            radiusMeters = 1500.0,
            type = ZoneType.RESTRICTED
        ),
        FlightZone(
            id = "beijing-safe",
            name = "可飞区",
            centerLatitude = 39.895780,
            centerLongitude = 116.418760,
            radiusMeters = 2000.0,
            type = ZoneType.SAFE
        )
    )
}
