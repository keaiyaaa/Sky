package com.example.msdksample.ui.settings

import android.view.View
import com.example.msdksample.base.BaseActivity
import com.example.msdksample.databinding.ActivityVideoSettingsBinding
import com.example.msdksample.settings.*

class VideoSettingsActivity : BaseActivity<ActivityVideoSettingsBinding>() {
    private lateinit var preferences: UserSettingsPreferences
    private lateinit var sync: SettingsSyncController
    override fun getViewBinding() = ActivityVideoSettingsBinding.inflate(layoutInflater)

    override fun initViews() {
        preferences = UserSettingsPreferences(this)
        sync = SettingsSyncController(this, onChanged = { text, busy ->
            binding.tvSyncStatus.text = text
            binding.btnSave.isEnabled = !busy
        })
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        // Only the database defaults have an agreed encoding. Hidden choices are not silently mapped.
        listOf(binding.rbVideoSmooth, binding.rbVideoUltra, binding.rbVideo4k,
            binding.rbImageStandard, binding.rbImageUltra).forEach { it.visibility = View.GONE }
        binding.rbVideoHd.text = "1080p"
        binding.rbImageHd.text = "高画质（high）"
        binding.rbVideoHd.isChecked = true
        binding.rbImageHd.isChecked = true
        runCatching {
            val loaded = preferences.load()
            binding.switchAutoSync.isChecked = loaded.settings.auto_sync_records == "1"
            binding.switchLowBattery.isChecked = loaded.settings.low_battery_return == "1"
            binding.tvSyncStatus.text = "本地设置；当前仅开放1080p和high。设备参数尚未确认，巡查记录云端同步未接入。\n" + loaded.notes.joinToString("\n")
        }.onFailure {
            binding.btnSave.isEnabled = false
            binding.tvSyncStatus.text = "无法读取本地设置：${it.message}"
            return
        }
        binding.btnSave.setOnClickListener {
            var localSaved = false
            runCatching {
                val updated = preferences.load().settings.copy(video_quality = "1080p", image_quality = "high",
                    auto_sync_records = if (binding.switchAutoSync.isChecked) "1" else "0",
                    low_battery_return = if (binding.switchLowBattery.isChecked) "1" else "0")
                preferences.save(updated)
                localSaved = true
                sync.start(updated.values().filter { it.field in setOf(SettingField.VIDEO, SettingField.IMAGE,
                    SettingField.AUTO_SYNC_RECORDS, SettingField.LOW_BATTERY) })
            }.onFailure { binding.tvSyncStatus.text = if (localSaved) "本地已保存 · 同步未完成：${it.message}" else "本地未保存：${it.message}" }
        }
    }

    override fun onDestroy() { sync.detach(); super.onDestroy() }
}
