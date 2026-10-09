package com.example.msdksample.ui.settings

import android.content.Intent
import com.example.msdksample.R
import com.example.msdksample.base.BaseActivity
import com.example.msdksample.databinding.ActivitySettingsBinding

class SettingsActivity : BaseActivity<ActivitySettingsBinding>() {

    override fun getViewBinding() = ActivitySettingsBinding.inflate(layoutInflater)

    override fun initViews() {
        // 返回按钮
        binding.ivBack.setOnClickListener {
            finish()
        }

        // 菜单点击事件
        binding.menuAccount.setOnClickListener {
            selectMenu(0)
            showFragment()
        }
        binding.menuFlight.setOnClickListener {
            selectMenu(1)
            startActivity(Intent(this, FlightSettingsActivity::class.java))
        }
        binding.menuVideo.setOnClickListener {
            selectMenu(2)
            startActivity(Intent(this, VideoSettingsActivity::class.java))
        }
        binding.menuAbout.setOnClickListener {
            selectMenu(3)
            startActivity(Intent(this, AboutActivity::class.java))
        }

        // 默认显示账户信息
        showFragment()
    }

    private fun selectMenu(index: Int) {
        // 重置所有菜单样式
        val menus = listOf(
            binding.menuAccount,
            binding.menuFlight,
            binding.menuVideo,
            binding.menuAbout
        )
        menus.forEach { menu ->
            menu.setTextColor(getColor(R.color.gray_600))
            menu.background = null
        }

        // 高亮选中菜单
        val selectedMenu = menus[index]
        selectedMenu.setTextColor(getColor(R.color.primary))
        selectedMenu.background = getDrawable(R.drawable.bg_menu_active)
    }

    private fun showFragment() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.content_frame, AccountFragment())
            .commit()
    }
}