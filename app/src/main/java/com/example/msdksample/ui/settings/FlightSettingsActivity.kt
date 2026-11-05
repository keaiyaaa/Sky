package com.example.msdksample.ui.settings

import android.view.View
import android.widget.SeekBar
import android.widget.Toast
import androidx.core.widget.doAfterTextChanged
import com.example.msdksample.R
import com.example.msdksample.base.BaseActivity
import com.example.msdksample.databinding.ActivityFlightSettingsBinding
import com.example.msdksample.settings.*
import java.math.BigDecimal

class FlightSettingsActivity : BaseActivity<ActivityFlightSettingsBinding>() {
    private lateinit var preferences: ScopedUserSettingsStore
    private lateinit var sync: SettingsSyncController
    private var needsSensitivityReview = false
    override fun getViewBinding() = ActivityFlightSettingsBinding.inflate(layoutInflater)

    override fun initViews() {
        preferences = ScopedUserSettingsStore(this)
        sync = SettingsSyncController(this, onChanged = { text, busy ->
            binding.tvSyncStatus.text = text
            binding.btnSave.isEnabled = !busy
        })
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
        runCatching { loadSettings() }.onFailure {
            binding.tvSyncStatus.text = "无法读取本地设置：${it.message}"
            binding.btnSave.isEnabled = false
            return
        }
        binding.etReturnAltitude.doAfterTextChanged {
            val text = it.toString().trim()
            binding.tvAltitudeValue.text = if (text.isEmpty()) "未设置" else "${text}m"
        }
        binding.sbAltitude.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                val meters = (progress + 20).coerceAtMost(if (binding.switchBeginner.isChecked) 30 else 400)
                binding.etReturnAltitude.setText("$meters.00")
                if (meters != progress + 20) binding.sbAltitude.progress = meters - 20
            }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })
        binding.sbSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                binding.tvSensitivityValue.text = "${progress + 1} 档（1–10）"
                if (fromUser) binding.cbSensitivityConfirm.isChecked = true
            }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })
        binding.switchBeginner.setOnCheckedChangeListener { _, enabled ->
            binding.tvBeginnerWarning.visibility = if (enabled) View.VISIBLE else View.GONE
            val altitude = binding.etReturnAltitude.text.toString().toBigDecimalOrNull()
            if (enabled && altitude != null && altitude > BigDecimal("30")) {
                binding.etReturnAltitude.setText("30.00")
                binding.sbAltitude.progress = 10
                Toast.makeText(this, "仅调整本地返航高度选项为30米，尚未确认飞机限制", Toast.LENGTH_LONG).show()
            }
        }
        binding.btnSave.setOnClickListener { saveSettings() }
        AccountSettingsController(this, preferences,
            draftStamp = { listOf(binding.etReturnAltitude.text.toString(), binding.rgFailsafe.checkedRadioButtonId,
                binding.rgObstacle.checkedRadioButtonId, binding.switchBeginner.isChecked,
                binding.sbSensitivity.progress, binding.cbSensitivityConfirm.isChecked).toString() },
            saveDraft = { persistDraft() }, reloadDraft = { loadSettings() })
    }

    private fun loadSettings() {
        val loaded = preferences.load()
        val values = loaded.settings
        needsSensitivityReview = loaded.sensitivityNeedsReview
        binding.tvSettingsMigration.text = loaded.notes.joinToString("\n")
        binding.tvSettingsMigration.visibility = if (loaded.notes.isEmpty()) View.GONE else View.VISIBLE
        binding.cbSensitivityConfirm.visibility = if (needsSensitivityReview) View.VISIBLE else View.GONE
        binding.cbSensitivityConfirm.isChecked = !needsSensitivityReview
        binding.etReturnAltitude.setText(values.return_altitude_m?.toPlainString().orEmpty())
        binding.tvAltitudeValue.text = values.return_altitude_m?.let { "${it.toPlainString()}m" } ?: "未设置"
        binding.sbAltitude.progress = ((values.return_altitude_m?.toInt() ?: 20) - 20).coerceIn(0, 380)
        when (values.failsafe_action) {
            "0" -> binding.rbFailsafeRth.isChecked = true
            "1" -> binding.rbFailsafeHover.isChecked = true
            "2" -> binding.rbFailsafeLand.isChecked = true
            else -> binding.rgFailsafe.clearCheck()
        }
        when (values.obstacle_action) {
            "0" -> binding.rbObstacleBypass.isChecked = true
            "1" -> binding.rbObstacleBrake.isChecked = true
            else -> binding.rgObstacle.clearCheck()
        }
        binding.switchBeginner.isChecked = values.beginner_mode == "1"
        binding.tvBeginnerWarning.visibility = if (binding.switchBeginner.isChecked) View.VISIBLE else View.GONE
        binding.sbSensitivity.progress = (values.control_sensitivity - 1).coerceIn(0, 9)
        binding.tvSensitivityValue.text = "${binding.sbSensitivity.progress + 1} 档（1–10）"
    }

    private fun persistDraft(): UserSettings {
        preferences.requireCurrentOwner()
        val heightText = binding.etReturnAltitude.text.toString().trim()
        val height = if (heightText.isBlank()) null else heightText.toBigDecimalOrNull()
        if (heightText.isNotBlank() && (height == null || SettingField.RETURN_ALTITUDE.validate(heightText) != null)) {
            binding.etReturnAltitude.error = "请输入20–400米，最多两位小数；留空表示未设置"
            binding.tvSyncStatus.text = "本地未保存：请修正返航高度，未尝试同步。"
            binding.etReturnAltitude.requestFocus()
            error("请修正返航高度，尚未保存")
        }
        if (needsSensitivityReview && !binding.cbSensitivityConfirm.isChecked) {
            binding.tvSyncStatus.text = "旧灵敏度百分比已备份，请重新选择或确认1–10档后保存。"
            error("旧灵敏度百分比已备份，请重新选择或确认1–10档后保存。")
        }
        val current = preferences.load().settings
        val updated = current.copy(return_altitude_m = height,
            failsafe_action = when (binding.rgFailsafe.checkedRadioButtonId) {
                R.id.rb_failsafe_rth -> "0"; R.id.rb_failsafe_hover -> "1"; R.id.rb_failsafe_land -> "2"; else -> ""
            }, obstacle_action = when (binding.rgObstacle.checkedRadioButtonId) {
                R.id.rb_obstacle_bypass -> "0"; R.id.rb_obstacle_brake -> "1"; else -> ""
            }, beginner_mode = if (binding.switchBeginner.isChecked) "1" else "0",
            control_sensitivity = binding.sbSensitivity.progress + 1)
        require(updated.validationErrors().isEmpty()) { updated.validationErrors().joinToString("\n") }
        preferences.save(updated, sensitivityReviewed = true)
        needsSensitivityReview = false
        binding.cbSensitivityConfirm.visibility = View.GONE
        binding.tvSettingsMigration.text = "旧设置备份仍保留；当前使用数据库v2字段。"
        return updated.normalized()
    }

    private fun saveSettings() {
        var localSaved = false
        runCatching {
            val updated = persistDraft()
            localSaved = true
            sync.start(updated.normalized().values().filter { it.field in setOf(SettingField.RETURN_ALTITUDE,
                SettingField.FAILSAFE, SettingField.BEGINNER, SettingField.OBSTACLE, SettingField.SENSITIVITY) })
        }.onFailure { binding.tvSyncStatus.text = if (localSaved) "本地已保存 · 同步未完成：${it.message}" else "本地未保存：${it.message}" }
    }

    override fun onStart() {
        super.onStart()
        if (::preferences.isInitialized && !preferences.isCurrentOwner()) finish()
    }
    override fun onDestroy() { sync.detach(); super.onDestroy() }
}
