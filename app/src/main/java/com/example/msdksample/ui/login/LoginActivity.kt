package com.example.msdksample.ui.login

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.msdksample.R
import com.example.msdksample.databinding.ActivityLoginBinding
import com.example.msdksample.utils.PreferenceManager

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var prefs: PreferenceManager
    private var sentCode: String? = null
    private var codeTimer: CountDownTimer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = PreferenceManager.getInstance(this)

        setupTabs()
        setupPhoneLogin()
        setupThirdPartyLogin()
        setupLinks()
    }

    // ── 登录方式切换 ──
    private fun setupTabs() {
        binding.tabPhone.setOnClickListener { switchTab(0) }
        binding.tabWechat.setOnClickListener { switchTab(1) }
        binding.tabQq.setOnClickListener { switchTab(2) }
    }

    private fun switchTab(index: Int) {
        val tabs = listOf(binding.tabPhone, binding.tabWechat, binding.tabQq)
        tabs.forEachIndexed { i, tab ->
            if (i == index) {
                tab.setBackgroundResource(R.drawable.bg_tab_active)
                tab.setTextColor(ContextCompat.getColor(this, R.color.brand_green))
            } else {
                tab.background = null
                tab.setTextColor(ContextCompat.getColor(this, R.color.gray_600))
            }
        }
        binding.panelPhone.visibility = if (index == 0) android.view.View.VISIBLE else android.view.View.GONE
        binding.panelWechat.visibility = if (index == 1) android.view.View.VISIBLE else android.view.View.GONE
        binding.panelQq.visibility = if (index == 2) android.view.View.VISIBLE else android.view.View.GONE
    }

    // ── 手机号验证码登录 ──
    private fun setupPhoneLogin() {
        binding.btnGetCode.setOnClickListener {
            val phone = binding.etPhone.text.toString().trim()
            if (!isValidPhone(phone)) {
                Toast.makeText(this, "请输入正确的11位手机号", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            sentCode = (100000..999999).random().toString()
            Toast.makeText(this, "验证码已发送：$sentCode（演示环境）", Toast.LENGTH_LONG).show()
            startCodeCountdown()
        }

        binding.btnLogin.setOnClickListener { doLogin() }

        binding.tvForgotPassword.setOnClickListener {
            Toast.makeText(this, "请联系管理员重置密码", Toast.LENGTH_SHORT).show()
        }
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

    private fun doLogin() {
        val phone = binding.etPhone.text.toString().trim()
        val code = binding.etCode.text.toString().trim()

        if (!isValidPhone(phone)) {
            Toast.makeText(this, "请输入正确的11位手机号", Toast.LENGTH_SHORT).show()
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

        // 本地演示登录：保存会话
        val username = if (phone == prefs.getRegisteredPhone() && prefs.getRegisteredUsername().isNotEmpty()) {
            prefs.getRegisteredUsername()
        } else {
            "用户${phone.takeLast(4)}"
        }
        prefs.setUsername(username)
        prefs.setUserPhone(phone)
        prefs.setLoginTime(System.currentTimeMillis())
        prefs.setAutoLoginEnabled(binding.cbAutoLogin.isChecked)
        prefs.setLoggedIn(true)

        Toast.makeText(this, "登录成功，欢迎 $username", Toast.LENGTH_SHORT).show()
        finish()
    }

    // ── 第三方登录 ──
    private fun setupThirdPartyLogin() {
        binding.btnConfirmWechat.setOnClickListener {
            Toast.makeText(this, "微信扫码登录需接入微信开放平台，当前请使用手机号登录", Toast.LENGTH_LONG).show()
        }
        binding.btnConfirmQq.setOnClickListener {
            Toast.makeText(this, "QQ扫码登录需接入QQ互联，当前请使用手机号登录", Toast.LENGTH_LONG).show()
        }
        binding.btnSocialWechat.setOnClickListener {
            Toast.makeText(this, "微信登录功能开发中", Toast.LENGTH_SHORT).show()
        }
        binding.btnSocialQq.setOnClickListener {
            Toast.makeText(this, "QQ登录功能开发中", Toast.LENGTH_SHORT).show()
        }
        binding.btnSocialApple.setOnClickListener {
            Toast.makeText(this, "Apple登录功能开发中", Toast.LENGTH_SHORT).show()
        }
    }

    // ── 页面跳转链接 ──
    private fun setupLinks() {
        binding.tvGoRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
        binding.tvBackSettings.setOnClickListener { finish() }
    }

    private fun isValidPhone(phone: String): Boolean = phone.matches(Regex("^1[3-9]\\d{9}$"))

    override fun onDestroy() {
        codeTimer?.cancel()
        super.onDestroy()
    }
}
