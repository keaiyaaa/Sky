package com.example.msdksample.settings

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject

/** Local v2 preferences; no backend identity or cloud synchronization is implied. */
class UserSettingsPreferences(
    context: Context,
    private val storage: SharedPreferences = context.getSharedPreferences("tianshu_user_settings_v2", Context.MODE_PRIVATE),
    private val legacy: SharedPreferences = context.getSharedPreferences("tianshu_prefs", Context.MODE_PRIVATE)
) {
    @Synchronized fun load(): SettingsLoadResult {
        val version = storage.getInt("schema_version", 0)
        require(version == 0 || version == 2) { "本地设置版本不受支持，已保留原数据" }
        if (version == 0) {
            val old = legacy.all.filterKeys { it in UserSettingsMigration.legacyKeys }
            val migrated = UserSettingsMigration.fromLegacy(old)
            val editor = storage.edit().putInt("schema_version", 2)
                .putString("legacy_backup", JSONObject(old).toString())
                .putBoolean("sensitivity_needs_review", migrated.sensitivityNeedsReview)
                .putString("migration_notes", migrated.notes.joinToString("\n"))
            write(editor, migrated.settings)
            check(editor.commit()) { "本地设置迁移保存失败，原设置未修改" }
        }
        val defaults = UserSettings()
        val rawHeight = storage.getString("return_altitude_m", null)
        val height = rawHeight?.let { it.toBigDecimalOrNull() ?: error("返航高度存储格式异常，请检查本地数据") }
        return SettingsLoadResult(UserSettings(
            storage.getString("auto_sync_records", defaults.auto_sync_records)!!,
            storage.getString("low_battery_return", defaults.low_battery_return)!!,
            height,
            storage.getString("failsafe_action", defaults.failsafe_action)!!,
            storage.getString("obstacle_action", defaults.obstacle_action)!!,
            storage.getInt("control_sensitivity", defaults.control_sensitivity),
            storage.getString("video_quality", defaults.video_quality)!!,
            storage.getString("image_quality", defaults.image_quality)!!,
            storage.getString("beginner_mode", defaults.beginner_mode)!!),
            storage.getBoolean("sensitivity_needs_review", false),
            storage.getString("migration_notes", "").orEmpty().split('\n').filter(String::isNotBlank))
    }

    @Synchronized fun save(settings: UserSettings, sensitivityReviewed: Boolean = false) {
        val current = load()
        require(settings.validationErrors().isEmpty()) { settings.validationErrors().joinToString("\n") }
        // A video-only save may preserve an unreviewed level; the flight page must explicitly confirm it.
        require(!current.sensitivityNeedsReview || sensitivityReviewed || settings.control_sensitivity == current.settings.control_sensitivity)
        val editor = storage.edit()
        write(editor, settings.normalized())
        if (sensitivityReviewed) editor.putBoolean("sensitivity_needs_review", false)
        check(editor.commit()) { "本地设置保存失败，未尝试同步" }
    }

    /** Explicit local import; retains review metadata and never copies a credential or uploads. */
    @Synchronized fun importDraft(draft: SettingsLoadResult) {
        load()
        require(draft.settings.validationErrors().isEmpty()) { draft.settings.validationErrors().joinToString("\n") }
        val editor = storage.edit().putBoolean("sensitivity_needs_review", draft.sensitivityNeedsReview)
            .putString("migration_notes", draft.notes.joinToString("\n"))
        write(editor, draft.settings.normalized())
        check(editor.commit()) { "导入本机草稿失败" }
    }

    private fun write(editor: SharedPreferences.Editor, settings: UserSettings) {
        settings.values().forEach { item ->
            if (item.field == SettingField.SENSITIVITY) editor.putInt(item.field.column, settings.control_sensitivity)
            else if (item.value == null) editor.remove(item.field.column)
            else editor.putString(item.field.column, item.value)
        }
    }
}
