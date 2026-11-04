package com.example.msdksample.settings

import java.math.BigDecimal
import java.math.RoundingMode

/** Database v2 user_settings business fields only; backend id/user_id are intentionally absent. */
data class UserSettings(
    val auto_sync_records: String = "1",
    val low_battery_return: String = "1",
    val return_altitude_m: BigDecimal? = BigDecimal("100.00"),
    val failsafe_action: String = "0",
    val obstacle_action: String = "0",
    val control_sensitivity: Int = 5,
    val video_quality: String = "1080p",
    val image_quality: String = "high",
    val beginner_mode: String = "0"
) {
    fun values(): List<SettingValue> = listOf(
        SettingValue(SettingField.RETURN_ALTITUDE, return_altitude_m?.toPlainString()),
        SettingValue(SettingField.FAILSAFE, failsafe_action), SettingValue(SettingField.BEGINNER, beginner_mode),
        SettingValue(SettingField.OBSTACLE, obstacle_action), SettingValue(SettingField.SENSITIVITY, control_sensitivity.toString()),
        SettingValue(SettingField.VIDEO, video_quality), SettingValue(SettingField.IMAGE, image_quality),
        SettingValue(SettingField.AUTO_SYNC_RECORDS, auto_sync_records), SettingValue(SettingField.LOW_BATTERY, low_battery_return))

    fun validationErrors(): List<String> = values().mapNotNull { it.field.validate(it.value) } +
        if (beginner_mode == "1" && return_altitude_m != null && return_altitude_m > BigDecimal("30"))
            listOf("新手模式的本地返航高度选项不得超过30米") else emptyList()

    fun normalized(): UserSettings = copy(return_altitude_m = return_altitude_m?.setScale(2, RoundingMode.UNNECESSARY))
}

enum class SettingField(val column: String, val label: String, val cloudOnly: Boolean = false) {
    RETURN_ALTITUDE("return_altitude_m", "返航高度"), FAILSAFE("failsafe_action", "失控行为"),
    BEGINNER("beginner_mode", "新手模式"), OBSTACLE("obstacle_action", "避障行为"),
    SENSITIVITY("control_sensitivity", "灵敏度"), VIDEO("video_quality", "视频画质"),
    IMAGE("image_quality", "图片画质"), AUTO_SYNC_RECORDS("auto_sync_records", "自动同步巡查记录", true),
    LOW_BATTERY("low_battery_return", "低电量返航");

    fun validate(value: String?): String? {
        val valid = when (this) {
            RETURN_ALTITUDE -> if (value == null) true else value.matches(Regex("[0-9]+(\\.[0-9]{1,2})?")) && (value.toBigDecimalOrNull()?.let {
                it >= BigDecimal("20") && it <= BigDecimal("400") && it.stripTrailingZeros().scale() <= 2
            } ?: false)
            FAILSAFE -> value in setOf("0", "1", "2")
            BEGINNER, OBSTACLE, AUTO_SYNC_RECORDS, LOW_BATTERY -> value in setOf("0", "1")
            SENSITIVITY -> value?.toIntOrNull() in 1..10
            VIDEO -> value == "1080p"
            IMAGE -> value == "high"
        }
        return if (valid) null else "$label：值不符合已确认的字段范围"
    }
}

data class SettingValue(val field: SettingField, val value: String?) {
    val display: String get() = when (this.field) {
        SettingField.RETURN_ALTITUDE -> value?.let { "${it}m" } ?: "未设置"
        SettingField.FAILSAFE -> mapOf("0" to "返航", "1" to "悬停", "2" to "原地降落")[value] ?: "未选择"
        SettingField.OBSTACLE -> mapOf("0" to "绕行", "1" to "刹停")[value] ?: "未选择"
        SettingField.BEGINNER, SettingField.LOW_BATTERY, SettingField.AUTO_SYNC_RECORDS -> if (value == "1") "开启" else "关闭"
        SettingField.SENSITIVITY -> "$value 档（1–10）"
        else -> value ?: "未设置"
    }
}
