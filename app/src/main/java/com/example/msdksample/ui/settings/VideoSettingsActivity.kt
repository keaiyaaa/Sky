package com.example.msdksample.ui.settings

import com.example.msdksample.R
import com.example.msdksample.base.BaseActivity
import com.example.msdksample.databinding.ActivityVideoSettingsBinding
import com.example.msdksample.drone.DroneManager
import com.example.msdksample.utils.PreferenceManager

class VideoSettingsActivity : BaseActivity<ActivityVideoSettingsBinding>() {

    private lateinit var prefs: PreferenceManager
    private lateinit var sync: SettingsSyncController

    override fun getViewBinding() = ActivityVideoSettingsBinding.inflate(layoutInflater)

    override fun initViews() {
        prefs = PreferenceManager.getInstance(this)
        sync = SettingsSyncController(this) { text, busy ->
            binding.tvSyncStatus.text = text
            binding.btnSave.isEnabled = !busy
        }

        // 设置工具栏
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        // 加载保存的设置
        loadSettings()

        // 保存按钮
        binding.btnSave.setOnClickListener {
            saveSettings()
        }
    }

    private fun loadSettings() {
        // 视频画质
        val videoQuality = prefs.getVideoQuality()
        when (videoQuality) {
            "smooth" -> binding.rbVideoSmooth.isChecked = true
            "hd" -> binding.rbVideoHd.isChecked = true
            "ultra" -> binding.rbVideoUltra.isChecked = true
            "4k" -> binding.rbVideo4k.isChecked = true
        }

        // 图片画质
        val imageQuality = prefs.getImageQuality()
        when (imageQuality) {
            "standard" -> binding.rbImageStandard.isChecked = true
            "hd" -> binding.rbImageHd.isChecked = true
            "ultra" -> binding.rbImageUltra.isChecked = true
        }

        // 开关
        binding.switchAutoSync.isChecked = prefs.isAutoSyncEnabled()
        binding.switchLowBattery.isChecked = prefs.isLowBatteryReturnEnabled()
    }

    private fun saveSettings() {
        // 视频画质
        val videoQuality = when (binding.rgVideoQuality.checkedRadioButtonId) {
            R.id.rb_video_smooth -> "smooth"
            R.id.rb_video_hd -> "hd"
            R.id.rb_video_ultra -> "ultra"
            else -> "4k"
        }
        prefs.setVideoQuality(videoQuality)

        // 图片画质
        val imageQuality = when (binding.rgImageQuality.checkedRadioButtonId) {
            R.id.rb_image_standard -> "standard"
            R.id.rb_image_hd -> "hd"
            else -> "ultra"
        }
        prefs.setImageQuality(imageQuality)

        // 开关
        prefs.setAutoSyncEnabled(binding.switchAutoSync.isChecked)
        prefs.setLowBatteryReturnEnabled(binding.switchLowBattery.isChecked)

        // 更新视频预览样式
        updateVideoPreview(videoQuality)

        // 同步到无人机
        val droneManager = DroneManager.getInstance()
        val autoSync = binding.switchAutoSync.isChecked
        val lowBattery = binding.switchLowBattery.isChecked
        val videoLabels = mapOf("smooth" to "流畅", "hd" to "高清", "ultra" to "超清", "4k" to "4K")
        val imageLabels = mapOf("standard" to "标准", "hd" to "高清", "ultra" to "超清")
        sync.start(listOf(
            SettingOperation("视频画质", videoLabels.getValue(videoQuality)) { droneManager.setVideoQuality(videoQuality, it) },
            SettingOperation("图片画质", imageLabels.getValue(imageQuality)) { droneManager.setImageQuality(imageQuality, it) },
            SettingOperation("自动同步", if (autoSync) "开启" else "关闭") { droneManager.setAutoSyncEnabled(autoSync, it) },
            SettingOperation("低电量返航", if (lowBattery) "开启" else "关闭") { droneManager.setLowBatteryReturnEnabled(lowBattery, it) }
        ))
    }

    private fun updateVideoPreview(quality: String) {
        val previewContainer = binding.root.findViewById<android.view.View>(R.id.video_preview_container)
        when (quality) {
            "smooth" -> previewContainer?.setBackgroundResource(R.drawable.bg_card_gray)
            "hd", "ultra" -> previewContainer?.setBackgroundResource(R.drawable.bg_card_white)
            else -> previewContainer?.setBackgroundResource(R.drawable.bg_primary_rounded)
        }
    }

    override fun onDestroy() { sync.detach(); super.onDestroy() }
}
