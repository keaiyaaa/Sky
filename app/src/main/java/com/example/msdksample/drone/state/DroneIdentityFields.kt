package com.example.msdksample.drone.state

/** Partial observed identity matching database v2 drones columns, NOT a bind/upload request.
 * Database id and user_id must come from the backend; productId is only a DJI runtime identifier.
 * Unknown serial_no stays null and fails validation rather than inventing a database identity.
 */
data class DroneIdentityFields(
    val model: String?,
    val serial_no: String?,
    val firmware_version: String?
) {
    fun validationErrors(): List<String> = buildList {
        if (serial_no.isNullOrBlank()) add("serial_no：尚未读取设备序列号")
        if (serial_no != null && serial_no.codePointCount(0, serial_no.length) > 128) add("serial_no：超过128字")
        if (model != null && model.codePointCount(0, model.length) > 64) add("model：超过64字")
        if (firmware_version != null && firmware_version.codePointCount(0, firmware_version.length) > 64) add("firmware_version：超过64字")
    }

    companion object {
        fun from(state: DeviceStateSnapshot): DroneIdentityFields? {
            if (state.mode != DeviceMode.DEVICE || state.linkState != LinkState.CONNECTED) return null
            val identity = state.device ?: return null
            return DroneIdentityFields(identity.productType?.takeIf { it.isNotBlank() && it != "未知" && !it.equals("UNKNOWN", true) },
                identity.serialNumber?.takeIf(String::isNotBlank), identity.firmwareVersion?.takeIf(String::isNotBlank))
        }
    }
}
