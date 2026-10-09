package com.example.msdksample.ui.mission

import com.example.msdksample.base.BaseActivity
import com.example.msdksample.databinding.ActivityMissionCenterBinding

/**
 * 任务中心页面
 * TODO: 等待组员3提供具体实现
 */
class MissionCenterActivity : BaseActivity<ActivityMissionCenterBinding>() {

    override fun getViewBinding() = ActivityMissionCenterBinding.inflate(layoutInflater)

    override fun initViews() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        binding.toolbar.setNavigationOnClickListener { finish() }
    }
}
