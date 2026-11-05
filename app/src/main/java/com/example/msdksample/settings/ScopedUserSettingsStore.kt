package com.example.msdksample.settings

import android.content.Context
import com.example.msdksample.data.remote.BackendSessionRegistry
import com.example.msdksample.data.remote.BackendSessions
import java.security.MessageDigest

/** A page owns one session's draft for its entire lifetime. Legacy data is never adopted implicitly. */
class ScopedUserSettingsStore(
    private val context: Context,
    private val sessions: BackendSessionRegistry = BackendSessions.registry
) {
    private val lease = sessions.current()
    val userId: String? = lease?.userId
    private val preferences = if (userId == null) UserSettingsPreferences(context) else {
        val key = MessageDigest.getInstance("SHA-256").digest(userId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        UserSettingsPreferences(context,
            context.getSharedPreferences("tianshu_settings_user_$key", Context.MODE_PRIVATE),
            context.getSharedPreferences("tianshu_settings_no_legacy", Context.MODE_PRIVATE))
    }

    fun isCurrentOwner(): Boolean = lease?.let(sessions::isCurrent) ?: (sessions.current() == null)
    fun requireCurrentOwner() = check(isCurrentOwner()) { "账号已变更，请重新打开设置页面" }
    fun load(): SettingsLoadResult { requireCurrentOwner(); return preferences.load() }
    fun save(settings: UserSettings, sensitivityReviewed: Boolean = false) {
        requireCurrentOwner(); preferences.save(settings, sensitivityReviewed)
    }
    fun importLocalDraft() {
        requireCurrentOwner()
        check(userId != null) { "请先登录真实账户" }
        val legacy = UserSettingsPreferences(context).load()
        requireCurrentOwner()
        preferences.importDraft(legacy)
    }
    fun useCloudSettings(settings: UserSettings) {
        requireCurrentOwner()
        check(userId != null)
        preferences.importDraft(SettingsLoadResult(settings, false, emptyList()))
    }
}
