package com.example.msdksample.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.widget.SwitchCompat
import com.example.msdksample.R
import com.example.msdksample.base.BaseActivity
import com.example.msdksample.databinding.ActivityFlightSettingsBinding
import com.example.msdksample.drone.DroneManager
import com.example.msdksample.utils.PreferenceManager

class FlightSettingsActivity : BaseActivity<ActivityFlightSettingsBinding>() {

    private lateinit var prefs: PreferenceManager

    override fun getViewBinding() = ActivityFlightSettingsBinding.inflate(layoutInflater)

    override fun initViews() {
        prefs = PreferenceManager.getInstance(this)

        // 设置工具栏
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        // 加载保存的设置
        loadSettings()

        // 监听器
        setupListeners()
    }

    private fun loadSettings() {
        // 返航高度
        val altitude = prefs.getReturnAltitude()
        binding.sbAltitude.progress = altitude - 20 // 20-400, 范围380
        binding.tvAltitudeValue.text = "${altitude}m"

        // 失控行为
        val failsafeAction = prefs.getFailsafeAction()
        when (failsafeAction) {
            "rth" -> binding.rbFailsafeRth.isChecked = true
            "hover" -> binding.rbFailsafeHover.isChecked = true
            "land" -> binding.rbFailsafeLand.isChecked = true
        }

        // 新手模式
        val beginnerMode = prefs.isBeginnerMode()
        binding.switchBeginner.isChecked = beginnerMode
        binding.tvBeginnerWarning.visibility = if (beginnerMode) View.VISIBLE else View.GONE

        // 避障行为
        val obstacleAction = prefs.getObstacleAction()
        when (obstacleAction) {
            "brake" -> binding.rbObstacleBrake.isChecked = true
            "bypass" -> binding.rbObstacleBypass.isChecked = true
        }

        // 灵敏度
        val sensitivity = prefs.getControlSensitivity()
        binding.sbSensitivity.progress = sensitivity
        binding.tvSensitivityValue.text = "${sensitivity}%"
    }

    private fun setupListeners() {
        // 返航高度
        binding.sbAltitude.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val altitude = progress + 20
                binding.tvAltitudeValue.text = "${altitude}m"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 新手模式
        binding.switchBeginner.setOnCheckedChangeListener { _, isChecked ->
            binding.tvBeginnerWarning.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (isChecked && binding.sbAltitude.progress > 10) {
                // 新手模式下限制返航高度最大30米
                binding.sbAltitude.progress = 10
                binding.tvAltitudeValue.text = "30m"
                Toast.makeText(this, "新手模式下返航高度限制为30米", Toast.LENGTH_SHORT).show()
            }
        }

        // 保存按钮
        binding.btnSave.setOnClickListener {
            saveSettingsAndSyncToDrone()
        }
    }

    private fun saveSettingsAndSyncToDrone() {
        // 1. 保存到本地 SharedPreferences
        val altitude = binding.sbAltitude.progress + 20
        prefs.setReturnAltitude(altitude)

        val failsafeAction = when (binding.rgFailsafe.checkedRadioButtonId) {
            R.id.rb_failsafe_rth -> "rth"
            R.id.rb_failsafe_hover -> "hover"
            else -> "land"
        }
        prefs.setFailsafeAction(failsafeAction)

        val beginnerMode = binding.switchBeginner.isChecked
        prefs.setBeginnerMode(beginnerMode)

        val obstacleAction = when (binding.rgObstacle.checkedRadioButtonId) {
            R.id.rb_obstacle_brake -> "brake"
            else -> "bypass"
        }
        prefs.setObstacleAction(obstacleAction)

        val sensitivity = binding.sbSensitivity.progress
        prefs.setControlSensitivity(sensitivity)

        // 2. 同步到无人机
        val droneManager = DroneManager.getInstance()
        if (droneManager.isConnected()) {
            droneManager.setReturnAltitude(altitude) { success, error ->
                if (!success) showToast("返航高度同步失败: $error")
            }
            droneManager.setFailsafeAction(failsafeAction) { success, error ->
                if (!success) showToast("失控行为同步失败: $error")
            }
            droneManager.setBeginnerMode(beginnerMode) { success, error ->
                if (!success) showToast("新手模式同步失败: $error")
            }
            droneManager.setObstacleAction(obstacleAction) { success, error ->
                if (!success) showToast("避障行为同步失败: $error")
            }
            droneManager.setControlSensitivity(sensitivity) { success, error ->
                if (!success) showToast("灵敏度同步失败: $error")
            }
            showToast("设置已保存并同步到无人机")
        } else {
            showToast("无人机未连接，设置已保存，连接后将自动同步")
        }
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}