package com.example.msdksample.drone

/** A missing value stays missing; connection alone is not evidence of telemetry. */
data class FlightTelemetry(
    val source: Source = Source.OFFLINE,
    val altitudeMeters: Float? = null,
    val distanceMeters: Float? = null,
    val speedMetersPerSecond: Float? = null,
    val batteryPercent: Int? = null,
    val signalPercent: Int? = null,
    val updatedAtMillis: Long? = null
) {
    enum class Source { OFFLINE, LOCAL_DEMO, DJI }

    fun validated(): FlightTelemetry {
        if (source == Source.OFFLINE) return FlightTelemetry()
        return copy(
            altitudeMeters = altitudeMeters?.takeIf { it.isFinite() },
            distanceMeters = distanceMeters?.takeIf { it.isFinite() && it >= 0 },
            speedMetersPerSecond = speedMetersPerSecond?.takeIf { it.isFinite() && it >= 0 },
            batteryPercent = batteryPercent?.takeIf { it in 0..100 },
            signalPercent = signalPercent?.takeIf { it in 0..100 }
        )
    }

    fun displayAt(nowMillis: Long): FlightTelemetry =
        if (updatedAtMillis != null && nowMillis - updatedAtMillis > STALE_AFTER_MILLIS) {
            FlightTelemetry(source = source)
        } else validated()

    val sourceLabel: String get() = when (source) {
        Source.OFFLINE -> "未连接 · 无飞行数据"
        Source.LOCAL_DEMO -> "本地演示 · 非飞机数据"
        Source.DJI -> if (updatedAtMillis == null) "真机已连接 · 遥测待接入" else "飞机遥测"
    }

    companion object { const val STALE_AFTER_MILLIS = 5_000L }
}
