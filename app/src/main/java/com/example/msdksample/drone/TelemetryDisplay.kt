package com.example.msdksample.drone

import com.example.msdksample.drone.state.*

/** UI projection only. Its 5-second lifetime is not an aircraft action policy. */
data class TelemetryDisplay(
    val generation: Long,
    val version: Long,
    val altitudeMeters: Float?,
    val distanceMeters: Float?,
    val speedMetersPerSecond: Float?,
    val batteryPercent: Int?,
    val signalPercent: Int?,
    val sourceLabel: String,
    val unavailableReasons: Map<TelemetryField, String>,
    val nextRefreshAtMonotonicMs: Long?
) {
    companion object {
        fun from(state: DeviceStateSnapshot, nowMonotonicMs: Long): TelemetryDisplay {
            val expected = when (state.mode) {
                DeviceMode.DEVICE -> TelemetrySource.DEVICE
                DeviceMode.DJI_SIMULATOR -> TelemetrySource.DJI_SIMULATOR
                DeviceMode.LOCAL_DEMO -> TelemetrySource.LOCAL_DEMO
                DeviceMode.OFFLINE -> null
            }
            val frame = state.telemetry
            val reasons = linkedMapOf<TelemetryField, String>()
            val usable = linkedMapOf<TelemetryField, Any>()
            val expiries = mutableListOf<Long>()
            for (field in TelemetryField.values()) {
                val item = frame?.field(field)
                val observed = item?.observedAtMonotonicMs
                val number = (item?.value as? Number)?.toDouble()
                val rangeValid = when (field) {
                    TelemetryField.LATITUDE -> number != null && number in -90.0..90.0
                    TelemetryField.LONGITUDE -> number != null && number in -180.0..180.0
                    TelemetryField.HEADING -> number != null && number >= 0 && number < 360
                    TelemetryField.BATTERY, TelemetryField.SIGNAL -> number != null && number in 0.0..100.0
                    TelemetryField.ALTITUDE -> number != null && !frame?.altitudeReference.isNullOrBlank()
                    TelemetryField.DISTANCE -> number != null && number >= 0 && !frame?.distanceReference.isNullOrBlank()
                    TelemetryField.HORIZONTAL_SPEED -> number != null && number >= 0
                    TelemetryField.FLYING -> item?.value is Boolean
                }
                val reason = when {
                    expected == null || state.linkState != LinkState.CONNECTED -> "NOT_CONNECTED"
                    frame == null -> "NOT_OBSERVED"
                    frame.generation != state.generation -> "STALE_GENERATION"
                    item == null || !item.valid || item.value == null -> item?.unavailableReason ?: "NOT_OBSERVED"
                    item.source != expected -> "SOURCE_MISMATCH"
                    observed == null || item.observedAtEpochMs == null -> "OBSERVATION_TIME_UNKNOWN"
                    observed < 0 || observed > nowMonotonicMs || observed > frame.receivedAtMonotonicMs -> "OBSERVATION_TIME_INVALID"
                    frame.receivedAtMonotonicMs > nowMonotonicMs -> "OBSERVATION_TIME_INVALID"
                    number != null && !number.isFinite() -> "INVALID_VALUE"
                    !rangeValid -> "VALUE_OR_REFERENCE_INVALID"
                    nowMonotonicMs - observed > FlightTelemetry.STALE_AFTER_MILLIS -> "DISPLAY_STALE"
                    else -> null
                }
                if (reason != null) reasons[field] = reason
                else {
                    usable[field] = item!!.value!!
                    expiries += observed!! + FlightTelemetry.STALE_AFTER_MILLIS + 1
                }
            }
            fun float(field: TelemetryField): Float? = (usable[field] as? Number)?.toFloat()?.takeIf(Float::isFinite)
            val source = when (state.mode) {
                DeviceMode.DEVICE -> "DJI 设备"
                DeviceMode.DJI_SIMULATOR -> "DJI SDK 模拟器 · 非真实飞行数据"
                DeviceMode.LOCAL_DEMO -> "本地演示 · 非飞机数据"
                DeviceMode.OFFLINE -> "离线 · 无飞行数据"
            }
            val status = when (state.linkState) {
                LinkState.ERROR -> "连接失败"
                LinkState.CONNECTING -> "连接中"
                LinkState.DISCONNECTING -> "断开中"
                LinkState.DISCONNECTED -> "未连接"
                LinkState.CONNECTED -> if (usable.isEmpty()) "遥测待接入或不可用" else null
            }
            return TelemetryDisplay(state.generation, state.version, float(TelemetryField.ALTITUDE),
                float(TelemetryField.DISTANCE), float(TelemetryField.HORIZONTAL_SPEED),
                usable[TelemetryField.BATTERY] as? Int, usable[TelemetryField.SIGNAL] as? Int,
                listOfNotNull(source, status).joinToString(" · "), reasons, expiries.minOrNull())
        }
    }
}
