package com.example.msdksample.ui.login

import android.os.Bundle
import android.os.CountDownTimer
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.msdksample.R
import com.example.msdksample.databinding.ActivityRegisterBinding
import com.example.msdksample.utils.PreferenceManager

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var prefs: PreferenceManager
    private var sentCode: String? = null
    private var codeTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PreferenceManager.getInstance(this)

        binding.btnGetCode.setOnClickListener { sendCode() }
        binding.btnRegister.setOnClickListener { doRegister() }
        binding.tvGoLogin.setOnClickListener { finish() }
        binding.tvBackSettings.setOnClickListener { finish() }
    }

    private fun sendCode() {
        val phone = binding.etPhone.text.toString().trim()
        if (!isValidPhone(phone)) {
            Toast.makeText(this, "请输入正确的11位手机号", Toast.LENGTH_SHORT).show()
            return
        }
        if (phone == prefs.getRegisteredPhone()) {
            Toast.makeText(this, "该手机号已注册，请直接登录", Toast.LENGTH_SHORT).show()
            return
        }
        sentCode = (100000..999999).random().toString()
        Toast.makeText(this, "验证码已发送：$sentCode（演示环境）", Toast.LENGTH_LONG).show()
        startCodeCountdown()
    }

    private fun startCodeCountdown() {
        codeTimer?.cancel()
        binding.btnGetCode.isEnabled = false
        binding.btnGetCode.alpha = 0.5f
        codeTimer = object : CountDownTimer(60_000, 1_000) {
            override fun onTick(millisUntilFinished: Long) {
                binding.btnGetCode.text = getString(R.string.code_countdown, millisUntilFinished / 1000)
            }

            override fun onFinish() {
                binding.btnGetCode.isEnabled = true
                binding.btnGetCode.alpha = 1f
                binding.btnGetCode.text = getString(R.string.get_code)
            }
        }.start()
    }

    private fun doRegister() {
        val username = binding.etUsername.text.toString().trim()
        val phone = binding.etPhone.text.toString().trim()
        val code = binding.etCode.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val confirmPassword = binding.etConfirmPassword.text.toString()

        if (username.isEmpty()) {
            Toast.makeText(this, "请输入用户名", Toast.LENGTH_SHORT).show()
            return
        }
        if (!isValidPhone(phone)) {
            Toast.makeText(this, "请输入正确的11位手机号", Toast.LENGTH_SHORT).show()
            return
        }
        if (phone == prefs.getRegisteredPhone()) {
            Toast.makeText(this, "该手机号已注册，请直接登录", Toast.LENGTH_SHORT).show()
            return
        }
        if (sentCode == null) {
            Toast.makeText(this, "请先获取验证码", Toast.LENGTH_SHORT).show()
            return
        }
        if (code != sentCode) {
            Toast.makeText(this, "验证码错误", Toast.LENGTH_SHORT).show()
            return
        }
        if (password.length < 6 || password.length > 20) {
            Toast.makeText(this, "密码长度为6-20位", Toast.LENGTH_SHORT).show()
            return
        }
        if (password != confirmPassword) {
            Toast.makeText(this, "两次输入的密码不一致", Toast.LENGTH_SHORT).show()
            return
        }
        if (!binding.cbAgreement.isChecked) {
            Toast.makeText(this, "请先阅读并同意用户协议和隐私政策", Toast.LENGTH_SHORT).show()
            return
        }

        // 本地演示注册：保存账号信息
        prefs.setRegisteredUsername(username)
        prefs.setRegisteredPhone(phone)
        prefs.setRegisteredPassword(password)

        Toast.makeText(this, "注册成功，请登录", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun isValidPhone(phone: String): Boolean = phone.matches(Regex("^1[3-9]\\d{9}$"))

    override fun onDestroy() {
        codeTimer?.cancel()
        super.onDestroy()
    }
}
