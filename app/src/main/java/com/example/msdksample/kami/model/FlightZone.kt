package com.example.msdksample.kami.model

data class FlightZone(
    val id: String,
    val name: String,
    val centerLatitude: Double,
    val centerLongitude: Double,
    val radiusMeters: Double,
    val type: ZoneType,
    /** 示例限高规则使用的相对高度上限；不是正式空域审批数据。 */
    val maxAltitudeMeters: Float? = if (type == ZoneType.RESTRICTED) 60f else null
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
            name = "示例禁飞区",
            centerLatitude = 39.908823,
            centerLongitude = 116.397470,
            radiusMeters = 1000.0,
            type = ZoneType.NO_FLY
        ),
        FlightZone(
            id = "beijing-restricted",
            name = "示例限高区（60 米）",
            centerLatitude = 39.923540,
            centerLongitude = 116.408820,
            radiusMeters = 1500.0,
            type = ZoneType.RESTRICTED
        ),
        FlightZone(
            id = "beijing-safe",
            name = "示例参考区（不代表飞行许可）",
            centerLatitude = 39.895780,
            centerLongitude = 116.418760,
            radiusMeters = 2000.0,
            type = ZoneType.SAFE
        )
    )
}
