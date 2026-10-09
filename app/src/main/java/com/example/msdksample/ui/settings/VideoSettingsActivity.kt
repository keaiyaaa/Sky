package com.example.msdksample.ui.settings

import android.widget.RadioGroup
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import com.example.msdksample.R
import com.example.msdksample.base.BaseActivity
import com.example.msdksample.databinding.ActivityVideoSettingsBinding
import com.example.msdksample.drone.DroneManager
import com.example.msdksample.utils.PreferenceManager

class VideoSettingsActivity : BaseActivity<ActivityVideoSettingsBinding>() {

    private lateinit var prefs: PreferenceManager

    override fun getViewBinding() = ActivityVideoSettingsBinding.inflate(layoutInflater)

    override fun initViews() {
        prefs = PreferenceManager.getInstance(this)

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
        if (droneManager.isConnected()) {
            droneManager.setVideoQuality(videoQuality) { success, error ->
                if (!success) Toast.makeText(this@VideoSettingsActivity, "视频画质同步失败: $error", Toast.LENGTH_SHORT).show()
            }
            droneManager.setImageQuality(imageQuality) { success, error ->
                if (!success) Toast.makeText(this@VideoSettingsActivity, "图片画质同步失败: $error", Toast.LENGTH_SHORT).show()
            }
            droneManager.setAutoSyncEnabled(binding.switchAutoSync.isChecked) { success, error ->
                if (!success) Toast.makeText(this@VideoSettingsActivity, "自动同步设置失败: $error", Toast.LENGTH_SHORT).show()
            }
            droneManager.setLowBatteryReturnEnabled(binding.switchLowBattery.isChecked) { success, error ->
                if (!success) Toast.makeText(this@VideoSettingsActivity, "低电量返航同步失败: $error", Toast.LENGTH_SHORT).show()
            }
            Toast.makeText(this, "设置已保存并同步到无人机", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "设置已保存（无人机未连接）", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateVideoPreview(quality: String) {
        val previewContainer = binding.root.findViewById<android.view.View>(R.id.video_preview_container)
        when (quality) {
            "smooth" -> previewContainer?.setBackgroundResource(R.drawable.bg_card_gray)
            "hd", "ultra" -> previewContainer?.setBackgroundResource(R.drawable.bg_card_white)
            else -> previewContainer?.setBackgroundResource(R.drawable.bg_primary_rounded)
        }
    }
}