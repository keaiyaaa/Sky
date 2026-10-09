package com.example.msdksample.ui.settings

import android.content.Intent
import com.example.msdksample.R
import com.example.msdksample.base.BaseActivity
import com.example.msdksample.databinding.ActivityAboutBinding

class AboutActivity : BaseActivity<ActivityAboutBinding>() {

    override fun getViewBinding() = ActivityAboutBinding.inflate(layoutInflater)

    override fun initViews() {
        // 设置工具栏
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }

        // 用户手册
        binding.btnUserManual.setOnClickListener {
            startActivity(Intent(this, UserManualActivity::class.java))
        }

        // 检查更新
        binding.btnCheckUpdate.setOnClickListener {
            startActivity(Intent(this, UpdateCheckActivity::class.java))
        }
    }
}