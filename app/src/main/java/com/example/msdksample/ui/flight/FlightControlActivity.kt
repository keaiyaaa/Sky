package com.example.msdksample.ui.flight

import com.example.msdksample.base.BaseActivity
import com.example.msdksample.databinding.ActivityFlightControlBinding

/**
 * 飞行控制页面
 * TODO: 等待负责人提供具体实现
 */
class FlightControlActivity : BaseActivity<ActivityFlightControlBinding>() {

    override fun getViewBinding() = ActivityFlightControlBinding.inflate(layoutInflater)

    override fun initViews() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }
}
