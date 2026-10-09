package com.example.msdksample.utils

import android.content.Context
import android.content.SharedPreferences

class PreferenceManager private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREF_NAME = "tianshu_prefs"

        // 飞控参数
        const val KEY_RETURN_ALTITUDE = "return_altitude"
        const val KEY_FAILSAFE_ACTION = "failsafe_action"
        const val KEY_BEGINNER_MODE = "beginner_mode"
        const val KEY_OBSTACLE_ACTION = "obstacle_action"
        const val KEY_CONTROL_SENSITIVITY = "control_sensitivity"

        // 图传设置
        const val KEY_VIDEO_QUALITY = "video_quality"
        const val KEY_IMAGE_QUALITY = "image_quality"
        const val KEY_AUTO_SYNC = "auto_sync"
        const val KEY_LOW_BATTERY_RETURN = "low_battery_return"
        const val KEY_CURRENT_DEVICE_ID = "current_device_id"

        @Volatile
        private var instance: PreferenceManager? = null

        fun getInstance(context: Context): PreferenceManager {
            return instance ?: synchronized(this) {
                instance ?: PreferenceManager(context.applicationContext).also { instance = it }
            }
        }
    }

    // 飞控参数
    fun getReturnAltitude(): Int = prefs.getInt(KEY_RETURN_ALTITUDE, 120)
    fun setReturnAltitude(value: Int) = prefs.edit().putInt(KEY_RETURN_ALTITUDE, value).apply()

    fun getFailsafeAction(): String = prefs.getString(KEY_FAILSAFE_ACTION, "rth") ?: "rth"
    fun setFailsafeAction(value: String) = prefs.edit().putString(KEY_FAILSAFE_ACTION, value).apply()

    fun isBeginnerMode(): Boolean = prefs.getBoolean(KEY_BEGINNER_MODE, false)
    fun setBeginnerMode(enabled: Boolean) = prefs.edit().putBoolean(KEY_BEGINNER_MODE, enabled).apply()

    fun getObstacleAction(): String = prefs.getString(KEY_OBSTACLE_ACTION, "brake") ?: "brake"
    fun setObstacleAction(value: String) = prefs.edit().putString(KEY_OBSTACLE_ACTION, value).apply()

    fun getControlSensitivity(): Int = prefs.getInt(KEY_CONTROL_SENSITIVITY, 50)
    fun setControlSensitivity(value: Int) = prefs.edit().putInt(KEY_CONTROL_SENSITIVITY, value).apply()

    // 图传参数
    fun getVideoQuality(): String = prefs.getString(KEY_VIDEO_QUALITY, "hd") ?: "hd"
    fun setVideoQuality(value: String) = prefs.edit().putString(KEY_VIDEO_QUALITY, value).apply()

    fun getImageQuality(): String = prefs.getString(KEY_IMAGE_QUALITY, "standard") ?: "standard"
    fun setImageQuality(value: String) = prefs.edit().putString(KEY_IMAGE_QUALITY, value).apply()

    fun isAutoSyncEnabled(): Boolean = prefs.getBoolean(KEY_AUTO_SYNC, true)
    fun setAutoSyncEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_AUTO_SYNC, enabled).apply()

    fun isLowBatteryReturnEnabled(): Boolean = prefs.getBoolean(KEY_LOW_BATTERY_RETURN, true)
    fun setLowBatteryReturnEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_LOW_BATTERY_RETURN, enabled).apply()

    // 设备管理
    fun getCurrentDeviceId(): Long = prefs.getLong(KEY_CURRENT_DEVICE_ID, -1L)
    fun setCurrentDeviceId(deviceId: Long) = prefs.edit().putLong(KEY_CURRENT_DEVICE_ID, deviceId).apply()
}