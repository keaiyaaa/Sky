package com.example.msdksample.ui.route

import com.example.msdksample.base.BaseActivity
import com.example.msdksample.databinding.ActivityRoutePlanBinding

/**
 * 航线规划页面
 * TODO: 等待组员2提供具体实现
 */
class RoutePlanActivity : BaseActivity<ActivityRoutePlanBinding>() {

    override fun getViewBinding() = ActivityRoutePlanBinding.inflate(layoutInflater)

    override fun initViews() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }
}
