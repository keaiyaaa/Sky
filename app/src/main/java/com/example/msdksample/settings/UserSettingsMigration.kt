package com.example.msdksample.settings

data class SettingsLoadResult(val settings: UserSettings, val sensitivityNeedsReview: Boolean, val notes: List<String>)

object UserSettingsMigration {
    val legacyKeys = setOf("return_altitude", "failsafe_action", "beginner_mode", "obstacle_action",
        "control_sensitivity", "video_quality", "image_quality", "auto_sync", "low_battery_return")

    /** Legacy percentages have no approved level conversion. Preserve a backup; require user selection. */
    fun fromLegacy(legacy: Map<String, *>): SettingsLoadResult {
        val notes = mutableListOf<String>()
        val sensitivityNeedsReview = legacy.containsKey("control_sensitivity")
        if (sensitivityNeedsReview) notes += "旧灵敏度百分比已备份，请重新选择并确认1–10档；未自动换算。"
        if (legacy.containsKey("video_quality") || legacy.containsKey("image_quality"))
            notes += "旧画质选项已备份；当前仅开放已确认的1080p和high。"
        if (legacy.containsKey("auto_sync"))
            notes += "旧照片同步开关未转换为巡查记录同步；新字段按默认开启保存，后端尚未接入。"
        val height = if (!legacy.containsKey("return_altitude")) UserSettings().return_altitude_m
            else legacy["return_altitude"]?.toString()?.toBigDecimalOrNull()
        if (legacy.containsKey("return_altitude") && height == null) notes += "旧返航高度无法读取，请重新设置；旧值已备份。"
        return SettingsLoadResult(UserSettings(
            return_altitude_m = height,
            failsafe_action = if (!legacy.containsKey("failsafe_action")) "0" else
                mapOf("rth" to "0", "hover" to "1", "land" to "2")[legacy["failsafe_action"]] ?: "?",
            obstacle_action = if (!legacy.containsKey("obstacle_action")) "0" else
                mapOf("bypass" to "0", "brake" to "1")[legacy["obstacle_action"]] ?: "?",
            beginner_mode = if (legacy["beginner_mode"] == true) "1" else "0",
            low_battery_return = if (legacy["low_battery_return"] == false) "0" else "1"
        ), sensitivityNeedsReview, notes)
    }
}
