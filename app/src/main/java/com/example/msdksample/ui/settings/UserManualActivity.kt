package com.example.msdksample.ui.settings

import com.example.msdksample.base.BaseActivity
import com.example.msdksample.databinding.ActivityUserManualBinding

/**
 * 用户手册页面
 * TODO: 提供具体实现
 */
class UserManualActivity : BaseActivity<ActivityUserManualBinding>() {

    override fun getViewBinding() = ActivityUserManualBinding.inflate(layoutInflater)

    override fun initViews() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }
}
