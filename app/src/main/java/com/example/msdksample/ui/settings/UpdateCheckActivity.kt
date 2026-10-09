package com.example.msdksample.ui.settings

import com.example.msdksample.base.BaseActivity
import com.example.msdksample.databinding.ActivityUpdateCheckBinding

/**
 * 检查更新页面
 * TODO: 提供具体实现
 */
class UpdateCheckActivity : BaseActivity<ActivityUpdateCheckBinding>() {

    override fun getViewBinding() = ActivityUpdateCheckBinding.inflate(layoutInflater)

    override fun initViews() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }
}
