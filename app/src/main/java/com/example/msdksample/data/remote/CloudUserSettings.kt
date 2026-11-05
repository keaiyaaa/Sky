package com.example.msdksample.data.remote

import com.example.msdksample.settings.UserSettings
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException

data class AccountSettingsValues(
    val autoSyncRecords: String, val lowBatteryReturn: String, val returnAltitudeM: String?,
    val failsafeAction: String, val obstacleAction: String, val controlSensitivity: Int,
    val videoQuality: String, val imageQuality: String, val beginnerMode: String
) {
    fun local() = UserSettings(autoSyncRecords, lowBatteryReturn, returnAltitudeM?.toBigDecimal(),
        failsafeAction, obstacleAction, controlSensitivity, videoQuality, imageQuality, beginnerMode).normalized()
    companion object {
        fun fromLocal(settings: UserSettings): AccountSettingsValues {
            require(settings.validationErrors().isEmpty()) { settings.validationErrors().joinToString("\n") }
            return settings.normalized().let { AccountSettingsValues(it.auto_sync_records, it.low_battery_return,
                it.return_altitude_m?.toPlainString(), it.failsafe_action, it.obstacle_action,
                it.control_sensitivity, it.video_quality, it.image_quality, it.beginner_mode) }
        }
    }
}
data class CloudUserSettings(val id: String?, val clientId: String, val userId: String,
    val settings: AccountSettingsValues, val version: Long, val updatedAt: String?,
    val cloudSaved: Boolean, val deviceApplied: Boolean?)
data class SettingsUpdate(val settings: AccountSettingsValues, val expectedVersion: Long, val idempotencyKey: String)

/** Strict wire types keep a null height explicit and prevent Gson's string/number coercion. */
object CloudSettingsCodec {
    val json = GsonBuilder().serializeNulls().create()
    fun request(settings: UserSettings, version: Long, key: String): String {
        require(version >= 0 && key.isNotBlank())
        return json.toJson(SettingsUpdate(AccountSettingsValues.fromLocal(settings), version, key))
    }
    fun decode(body: String, userId: String): CloudUserSettings = try {
        val root = JsonParser.parseString(body).asJsonObject
        string(root, "userId"); string(root, "clientId")
        val version = root["version"]
        require(version?.isJsonPrimitive == true && version.asJsonPrimitive.isNumber &&
            version.asString.matches(Regex("[0-9]+")) && version.asString.toLongOrNull() != null)
        nullableString(root, "id"); nullableString(root, "updatedAt")
        val saved = root["cloudSaved"]
        require(saved?.isJsonPrimitive == true && saved.asJsonPrimitive.isBoolean)
        // This API does not apply aircraft parameters; its state must remain explicitly unknown.
        require(root.has("deviceApplied") && root["deviceApplied"].isJsonNull)
        val values = root.getAsJsonObject("settings")
        listOf("autoSyncRecords", "lowBatteryReturn", "failsafeAction", "obstacleAction", "videoQuality",
            "imageQuality", "beginnerMode").forEach { string(values, it) }
        nullableString(values, "returnAltitudeM")
        val sensitivity = values["controlSensitivity"]
        require(sensitivity?.isJsonPrimitive == true && sensitivity.asJsonPrimitive.isNumber &&
            sensitivity.asString.matches(Regex("(?:[1-9]|10)")))
        val result = json.fromJson(root, CloudUserSettings::class.java)
        require(result.userId == userId && result.clientId == "user-settings")
        require(result.settings.local().validationErrors().isEmpty())
        result.settings.returnAltitudeM?.let { require(it.matches(Regex("[0-9]+\\.[0-9]{2}"))) }
        if (result.version == 0L) {
            require(!result.cloudSaved && result.id == null && result.updatedAt == null)
            require(result.settings == AccountSettingsValues.fromLocal(UserSettings()))
        } else {
            require(result.cloudSaved && result.id?.matches(Regex("[1-9][0-9]*")) == true && result.updatedAt != null)
            com.example.msdksample.data.logs.FlightLogPolicy.parseUtc(result.updatedAt)
        }
        result
    } catch (error: RuntimeException) { throw IOException("账户设置响应无效，尚未确认保存", error) }

    fun latest(current: CloudUserSettings?, incoming: CloudUserSettings): CloudUserSettings {
        require(current == null || current.userId == incoming.userId)
        if (current == null || incoming.version > current.version) return incoming
        if (incoming.version == current.version) require(incoming == current) { "同一版本的账户设置内容不一致" }
        return current
    }
    private fun string(root: JsonObject, field: String) {
        val value = root[field]
        require(value?.isJsonPrimitive == true && value.asJsonPrimitive.isString)
    }
    private fun nullableString(root: JsonObject, field: String) {
        require(root.has(field))
        if (!root[field].isJsonNull) string(root, field)
    }
}
