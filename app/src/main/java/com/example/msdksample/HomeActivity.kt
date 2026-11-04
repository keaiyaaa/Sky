package com.example.msdksample

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import android.widget.VideoView
import androidx.core.view.GravityCompat
import androidx.activity.addCallback
import com.google.android.material.navigation.NavigationView
import com.example.msdksample.aerial.Aerial_photography
import com.example.msdksample.auth.DemoSession
import com.example.msdksample.asuka.MainActivity
import com.example.msdksample.base.BaseActivity
import com.example.msdksample.databinding.ActivityHomeBinding
import com.example.msdksample.databinding.DialogSdkTestBinding
import com.example.msdksample.drone.DroneManager
import com.example.msdksample.drone.TelemetryDisplay
import com.example.msdksample.drone.TelemetryUiObserver
import com.example.msdksample.drone.state.DeviceStateSnapshot
import com.example.msdksample.drone.state.DeviceStateSubscription
import com.example.msdksample.drone.state.DeviceStatePresentation
import com.example.msdksample.drone.state.Readiness
import com.example.msdksample.data.local.LocalDataStore
import com.example.msdksample.ui.profile.ProfileActivity
import com.example.msdksample.ui.device.DeviceManagementActivity
import com.example.msdksample.ui.logs.FlightLogActivity
import com.example.msdksample.ui.login.LoginActivity
import com.example.msdksample.ui.local.localProfileOwner
import com.example.msdksample.ui.local.maskedPhone
import com.example.msdksample.utils.PreferenceManager
import com.example.msdksample.kami.RoutePlanningActivity
import com.example.msdksample.ui.settings.AboutActivity
import com.example.msdksample.ui.settings.SettingsActivity
import com.example.msdksample.asuka.VipActivity
import java.io.File
import kotlin.math.min
import java.util.Locale

class HomeActivity : BaseActivity<ActivityHomeBinding>(), NavigationView.OnNavigationItemSelectedListener {

    /** 隐藏测试入口：连点设备卡计数 / 时间戳 / 测试面板 */
    private var deviceTapCount = 0
    private var lastDeviceTapTime = 0L
    private var sdkTestDialog: AlertDialog? = null
    private var sdkScenarioDialog: AlertDialog? = null
    private var dialogTestBinding: DialogSdkTestBinding? = null

    /** 首页航拍演示视频：存在 test.mp4 时替代静态预览图（can.jpg）播放 */
    private var homeDemoVideoStarted = false
    @Volatile private var pageActive = false
    @Volatile private var pageEpoch = 0L
    private var stateSubscription: DeviceStateSubscription? = null

    override fun getViewBinding() = ActivityHomeBinding.inflate(layoutInflater)

    override fun initViews() {
        binding.navView.setNavigationItemSelectedListener(this)
        binding.btnNavigation.setOnClickListener { binding.drawerLayout.openDrawer(GravityCompat.START) }
        onBackPressedDispatcher.addCallback(this) {
            if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) binding.drawerLayout.closeDrawers()
            else { isEnabled = false; onBackPressedDispatcher.onBackPressed() }
        }

        // ── 右侧快捷入口 ──────────────────────────────────────────

        // 「开始飞行」→ 航拍飞行控制页面（来自 DJI-FLY-aerial）
        binding.cardStartFlight.setOnClickListener {
            startActivity(Intent(this, Aerial_photography::class.java))
        }

        // 「规划航线」→ 航线规划页面（来自 DJI-FLY-kami）
        binding.cardRoutePlan.setOnClickListener {
            startActivity(Intent(this, RoutePlanningActivity::class.java))
        }

        // 左侧栏「任务中心」入口 → 巡检模块主页（来自 DJI-FLY-asuka912）
        binding.llMissionEntry.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        // 顶部 VIP 按钮
        binding.btnVip.setOnClickListener {
            startActivity(Intent(this, VipActivity::class.java))
        }

        // 顶部设置按钮
        binding.btnMenu.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        // ── 隐藏测试入口：连点设备状态卡片 5 次弹出 SDK 测试面板 ──
        setupHiddenTestEntry()

        // 顶部设备切换
        binding.tvSwitchDevice.setOnClickListener {
            showDeviceSwitchDialog()
        }

        // 中间视频预览 → 航拍飞行控制页面
        binding.cardVideo.setOnClickListener {
            startActivity(Intent(this, Aerial_photography::class.java))
        }
        binding.btnFullscreen.setOnClickListener { startActivity(Intent(this, Aerial_photography::class.java)) }

        // 定位标签 → 航线规划页面
        binding.llLocation.setOnClickListener {
            startActivity(Intent(this, RoutePlanningActivity::class.java))
        }

        renderTelemetry(TelemetryDisplay.from(DroneManager.getInstance().deviceStateProvider.currentSnapshot(), SystemClock.elapsedRealtime()))
        binding.tvProgress.text = "--"
        binding.tvCompletedWaypoints.text = "--"
        binding.tvDefectCount.text = "--"
        binding.tvStorage.text = "暂无设备数据"

        // 首页视频预览：尝试播放 test.mp4 替代 can.jpg 静态图；失败静默保留静态图
        initHomeDemoVideo()
    }

    private fun renderTelemetry(sample: TelemetryDisplay) {
        fun value(number: Float?) = number?.let { String.format(Locale.CHINA, "%.1f", it) } ?: "--"
        binding.tvOsdHeight.text = "H:${value(sample.altitudeMeters)}m"
        binding.tvOsdDistance.text = "D:${value(sample.distanceMeters)}m"
        binding.tvOsdSpeed.text = "SPD:${value(sample.speedMetersPerSecond)}m/s"
        binding.tvBattery.text = sample.batteryPercent?.let { "$it%" } ?: "--%"
        binding.tvTelemetrySource.text = "${sample.sourceLabel} · 信号${sample.signalPercent?.let { "$it%" } ?: "--%"}"
    }

    // ── 左侧抽屉菜单 ──────────────────────────────────────────────

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.nav_home -> binding.drawerLayout.closeDrawers()
            R.id.nav_vip -> {
                // VIP 会员页面已就绪
                startActivity(Intent(this, VipActivity::class.java))
            }
            R.id.nav_flight_log -> {
                startActivity(Intent(this, FlightLogActivity::class.java))
            }
            R.id.nav_profile -> {
                startActivity(Intent(this, ProfileActivity::class.java))
            }
            R.id.nav_about -> startActivity(Intent(this, AboutActivity::class.java))
            R.id.nav_logout -> if (PreferenceManager.getInstance(this).isLoggedIn()) showLogoutDialog()
                else startActivity(Intent(this, LoginActivity::class.java))
        }
        binding.drawerLayout.closeDrawers()
        return true
    }

    private fun showLogoutDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("退出登录")
            .setMessage("确定要退出登录吗？")
            .setPositiveButton("确定") { _, _ ->
                DemoSession.signOut()
                PreferenceManager.getInstance(this).logout()
                refreshProfileHeader()
                Toast.makeText(this, "已退出本机会话", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDeviceSwitchDialog() {
        startActivity(Intent(this, DeviceManagementActivity::class.java))
    }

    // ── 隐藏测试入口：连点设备状态卡片 5 次弹出 SDK 测试面板 ──────

    private fun setupHiddenTestEntry() {
        // 设备状态卡片作为隐藏入口
        binding.llDeviceCard.setOnClickListener { handleDeviceCardTap() }
        binding.tvDeviceCardTitle.setOnClickListener { handleDeviceCardTap() }
        updateSdkStatusText()
    }

    /** 设备卡片点击计数：800ms 内连续点击 5 次触发测试面板 */
    private fun handleDeviceCardTap() {
        if (applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val now = SystemClock.elapsedRealtime()
        deviceTapCount = if (now - lastDeviceTapTime <= 800) deviceTapCount + 1 else 1
        lastDeviceTapTime = now
        if (deviceTapCount >= 5) {
            deviceTapCount = 0
            showSdkTestDialog()
        }
    }

    /** 弹出 SDK 测试面板（Dialog，不改变主页面布局） */
    private fun showSdkTestDialog() {
        if (sdkTestDialog?.isShowing == true) return
        val dialogBinding = DialogSdkTestBinding.inflate(layoutInflater)
        dialogTestBinding = dialogBinding
        val droneManager = DroneManager.getInstance()
        fun showResult(success: Boolean, message: String?) {
            runOnUiThread {
                if (!pageActive || dialogTestBinding !== dialogBinding || isDestroyed || isFinishing) return@runOnUiThread
                Toast.makeText(this, message ?: if (success) "操作已确认" else "操作失败", Toast.LENGTH_SHORT).show()
                updateSdkStatusText()
            }
        }

        dialogBinding.btnTestInit.setOnClickListener {
            droneManager.initSDK(applicationContext, ::showResult)
        }

        dialogBinding.btnTestSimOn.setOnClickListener {
            droneManager.enableSimulator(callback = ::showResult)
        }

        dialogBinding.btnTestSimOff.setOnClickListener {
            droneManager.disableSimulator(::showResult)
        }
        dialogBinding.btnTestReconnect.setOnClickListener { droneManager.connectDevice(onResult = ::showResult) }
        dialogBinding.btnTestCancelWait.setOnClickListener { droneManager.cancelConnectionWait() }
        dialogBinding.btnTestScenario.setOnClickListener {
            val scenarios = arrayOf(DroneManager.DemoScenario.NORMAL, DroneManager.DemoScenario.CONNECTING,
                DroneManager.DemoScenario.CONNECTION_FAILED, DroneManager.DemoScenario.DISCONNECTED)
            sdkScenarioDialog?.dismiss()
            sdkScenarioDialog = AlertDialog.Builder(this).setTitle("本地演示连接场景")
                .setItems(arrayOf("正常／重连成功", "连接中", "连接失败", "未连接")) { _, index ->
                    droneManager.setDemoScenario(scenarios[index], ::showResult)
                }.show()
        }

        dialogBinding.btnTestClose.setOnClickListener {
            sdkTestDialog?.dismiss()
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()
        dialog.setOnDismissListener {
            sdkScenarioDialog?.dismiss()
            sdkScenarioDialog = null
            dialogTestBinding = null
            sdkTestDialog = null
            if (!isDestroyed && !isFinishing) updateSdkStatusText()
        }
        sdkTestDialog = dialog
        dialog.show()
        val metrics = resources.displayMetrics
        dialog.window?.setLayout(min((420 * metrics.density).toInt(), metrics.widthPixels - (32 * metrics.density).toInt()),
            (metrics.heightPixels * 0.85f).toInt())
        updateSdkStatusText()
    }

    /** 刷新首页设备卡 + 测试面板（若已打开）状态显示 */
    private fun updateSdkStatusText(state: DeviceStateSnapshot = DroneManager.getInstance().deviceStateProvider.currentSnapshot()) {
        val display = DeviceStatePresentation.from(state)
        dialogTestBinding?.tvTestStatus?.text = display.details
        binding.tvDeviceName.text = display.title
        binding.tvDeviceStatus.text = display.status
        binding.tvDeviceStatus.setTextColor(if (state.readiness == Readiness.READY) 0xFF2E7D32.toInt() else 0xFF9E9E9E.toInt())
    }

    override fun onPause() {
        pauseHomeDemoVideo()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        refreshProfileHeader()
        resumeHomeDemoVideo()
    }

    override fun onStart() {
        super.onStart()
        pageActive = true
        val epoch = ++pageEpoch
        stateSubscription = TelemetryUiObserver(DroneManager.getInstance().deviceStateProvider) { state, display ->
            if (pageActive && epoch == pageEpoch && !isDestroyed) {
                updateSdkStatusText(state)
                renderTelemetry(display)
            }
        }
    }

    override fun onStop() {
        pageActive = false
        pageEpoch++
        stateSubscription?.cancel()
        stateSubscription = null
        sdkTestDialog?.dismiss()
        deviceTapCount = 0
        super.onStop()
    }

    private fun refreshProfileHeader() {
        val prefs = PreferenceManager.getInstance(this)
        val profile = LocalDataStore.getInstance(this).profile(localProfileOwner(this))
        val header = binding.navView.getHeaderView(0)
        header.findViewById<android.widget.TextView>(R.id.tv_username).text = profile?.nickname
            ?: if (prefs.isLoggedIn()) prefs.getUsername() else "未登录"
        header.findViewById<android.widget.TextView>(R.id.tv_user_phone).text =
            if (prefs.isLoggedIn()) maskedPhone(prefs.getUserPhone()) else "本机演示资料"
        binding.navView.menu.findItem(R.id.nav_logout).title = if (prefs.isLoggedIn()) "退出登录" else "登录"
    }

    override fun onDestroy() {
        releaseHomeDemoVideo()
        stateSubscription?.cancel()
        sdkTestDialog?.dismiss()
        sdkTestDialog = null
        dialogTestBinding = null
        super.onDestroy()
    }

    // ── 首页航拍演示视频（test.mp4，替代 can.jpg 静态预览图）───────

    private fun initHomeDemoVideo() {
        val file = File(getExternalFilesDir(null), "test.mp4")
        if (!file.exists()) {
            Log.w("HomeActivity", "演示视频不存在: ${file.absolutePath}，保留静态预览图")
            return
        }
        val vv = binding.videoPreviewVideo
        vv.visibility = View.VISIBLE
        binding.videoPreview.visibility = View.GONE

        vv.setOnPreparedListener { mp ->
            mp.isLooping = true
            vv.post {
                val container = binding.videoPreview.parent as? ViewGroup ?: return@post
                val cw = container.width
                val ch = container.height
                if (cw <= 0 || ch <= 0 || mp.videoWidth <= 0 || mp.videoHeight <= 0) return@post
                // 等比缩放 + 居中，不裁剪不变形
                val scale = min(cw.toFloat() / mp.videoWidth, ch.toFloat() / mp.videoHeight)
                vv.layoutParams = FrameLayout.LayoutParams(
                    (mp.videoWidth * scale).toInt().coerceAtLeast(1),
                    (mp.videoHeight * scale).toInt().coerceAtLeast(1),
                    Gravity.CENTER
                )
            }
            mp.start()
            homeDemoVideoStarted = true
            Log.i("HomeActivity", "首页演示视频已开始播放")
        }
        vv.setOnErrorListener { _, _, _ ->
            Log.w("HomeActivity", "首页演示视频播放失败，恢复静态预览图")
            restoreHomeStaticPreview()
            true
        }
        try {
            vv.setVideoURI(Uri.fromFile(file))
        } catch (t: Throwable) {
            Log.w("HomeActivity", "加载首页演示视频异常: ${t.message}")
            restoreHomeStaticPreview()
        }
    }

    /** 播放失败/降级：恢复原 can.jpg 静态预览图 */
    private fun restoreHomeStaticPreview() {
        homeDemoVideoStarted = false
        binding.videoPreviewVideo.visibility = View.GONE
        binding.videoPreview.visibility = View.VISIBLE
    }

    private fun pauseHomeDemoVideo() {
        if (homeDemoVideoStarted && binding.videoPreviewVideo.isPlaying) {
            binding.videoPreviewVideo.pause()
        }
    }

    private fun resumeHomeDemoVideo() {
        if (homeDemoVideoStarted && !binding.videoPreviewVideo.isPlaying) {
            binding.videoPreviewVideo.start()
        }
    }

    private fun releaseHomeDemoVideo() {
        homeDemoVideoStarted = false
        // 在 super.onDestroy()（将 binding 置空）之前调用，binding 必然有效
        binding.videoPreviewVideo.stopPlayback()
    }
}
