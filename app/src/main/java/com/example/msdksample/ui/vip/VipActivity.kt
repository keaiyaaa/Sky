package com.example.msdksample.ui.vip

import com.example.msdksample.base.BaseActivity
import com.example.msdksample.databinding.ActivityVipBinding

/**
 * VIP会员页面
 * TODO: 等待组员3提供具体实现
 */
class VipActivity : BaseActivity<ActivityVipBinding>() {

    override fun getViewBinding() = ActivityVipBinding.inflate(layoutInflater)

    override fun initViews() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }
}
