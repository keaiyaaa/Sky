package com.example.msdksample.auth

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.example.msdksample.HomeActivity

class DemoLoginActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val input = EditText(this).apply {
            hint = "演示用户名"
            setSingleLine(true)
        }
        val login = Button(this).apply { text = "进入演示" }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(32, 24, 32, 24)
            addView(TextView(this@DemoLoginActivity).apply {
                text = "天枢巡检演示登录\n此页面不连接账号服务，也不验证真实身份。"
                textSize = 20f
                gravity = Gravity.CENTER
            })
            addView(input)
            addView(login)
        }
        setContentView(content)
        login.setOnClickListener {
            if (!DemoSession.signIn(input.text.toString())) {
                Toast.makeText(this, "请输入演示用户名", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startActivity(Intent(this, HomeActivity::class.java))
            finish()
        }
    }
}
