package com.example.msdksample

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.Toast
import android.widget.VideoView
import androidx.lifecycle.lifecycleScope
import com.google.android.material.navigation.NavigationView
import com.example.msdksample.aerial.Aerial_photography
import com.example.msdksample.asuka.MainActivity
import com.example.msdksample.base.BaseActivity
import com.example.msdksample.databinding.ActivityHomeBinding
import com.example.msdksample.databinding.DialogSdkTestBinding
import com.example.msdksample.drone.DroneManager
import com.example.msdksample.kami.RoutePlanningActivity
import com.example.msdksample.ui.settings.AboutActivity
import com.example.msdksample.ui.settings.SettingsActivity
import com.example.msdksample.asuka.VipActivity
import java.io.File
import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HomeActivity : BaseActivity<ActivityHomeBinding>(), NavigationView.OnNavigationItemSelectedListener {

    /** 隐藏测试入口：连点设备卡计数 / 时间戳 / 测试面板 */
    private var deviceTapCount = 0
    private var lastDeviceTapTime = 0L
    private var sdkTestDialog: AlertDialog? = null
    private var dialogTestBinding: DialogSdkTestBinding? = null

    /** 首页航拍演示视频：存在 test.mp4 时替代静态预览图（can.jpg）播放 */
    private var homeDemoVideoStarted = false

    /** DJI SDK / 无人机连接状态监听（回调可能来自非主线程，统一切回主线程刷新） */
    private val sdkStateListener = object : DroneManager.ConnectionStateListener {
        override fun onSdkStateChanged(state: DroneManager.SdkState, message: String?) {
            runOnUiThread { updateSdkStatusText() }
        }

        override fun onConnectionChanged(connected: Boolean, device: DroneManager.DroneDevice?) {
            runOnUiThread { updateSdkStatusText() }
        }

        override fun onDeviceInfoUpdated(device: DroneManager.DroneDevice?) {
            runOnUiThread { updateSdkStatusText() }
        }

        override fun onSimulatorTelemetry(batteryPercent: Int, signalPercent: Int) {
            // 模拟器模式下实时刷新首页电量/信号显示
            runOnUiThread {
                binding.tvBattery.text = "${batteryPercent}%"
                updateSdkStatusText()
            }
        }
    }

    override fun getViewBinding() = ActivityHomeBinding.inflate(layoutInflater)

    override fun initViews() {
        binding.navView.setNavigationItemSelectedListener(this)

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

        // 定位标签 → 航线规划页面
        binding.llLocation.setOnClickListener {
            startActivity(Intent(this, RoutePlanningActivity::class.java))
        }

        // 启动实时数据（优先 DJI 遥测，未连接时使用模拟数据）
        startTelemetryUpdate()

        // 首页视频预览：尝试播放 test.mp4 替代 can.jpg 静态图；失败静默保留静态图
        initHomeDemoVideo()
    }

    private fun startTelemetryUpdate() {
        val droneManager = DroneManager.getInstance()

        if (droneManager.isConnected()) {
            // 连接无人机 → 通过大疆 SDK 实时获取遥测数据
            droneManager.startTelemetry(
                onAltitude = { altitude ->
                    runOnUiThread { binding.tvOsdHeight.text = "H:${altitude.toInt()}m" }
                },
                onDistance = { distance ->
                    runOnUiThread { binding.tvOsdDistance.text = "D:${distance.toInt()}m" }
                },
                onSpeed = { speed ->
                    runOnUiThread { binding.tvOsdSpeed.text = "SPD:${speed.toInt()}m/s" }
                }
            )
            return
        }

        // 未连接模拟数据
        lifecycleScope.launch {
            var height = 124
            var distance = 842
            var speed = 8
            while (true) {
                delay(2000)
                height += (Math.random() * 10 - 5).toInt()
                distance += (Math.random() * 20 - 10).toInt()
                speed += (Math.random() * 4 - 2).toInt()

                binding.tvOsdHeight.text = "H:${height}m"
                binding.tvOsdDistance.text = "D:${distance}m"
                binding.tvOsdSpeed.text = "SPD:${speed}m/s"
            }
        }
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
                // TODO: 飞行日志页面尚未实现，等待后续开发
                Toast.makeText(this, "飞行日志开发中，敬请期待", Toast.LENGTH_SHORT).show()
            }
            R.id.nav_profile -> {
                // TODO: 个人中心页面尚未实现，等待后续开发
                Toast.makeText(this, "个人中心开发中，敬请期待", Toast.LENGTH_SHORT).show()
            }
            R.id.nav_about -> startActivity(Intent(this, AboutActivity::class.java))
            R.id.nav_logout -> showLogoutDialog()
        }
        binding.drawerLayout.closeDrawers()
        return true
    }

    private fun showLogoutDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("退出登录")
            .setMessage("确定要退出登录吗？")
            .setPositiveButton("确定") { _, _ ->
                finishAffinity()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showDeviceSwitchDialog() {
        val devices = arrayOf("Mavic 4 Pro v2", "Mavic 3 Pro", "Mini 4 Pro", "Air 3S")
        val checkedItem = 0
        AlertDialog.Builder(this)
            .setTitle("切换设备")
            .setSingleChoiceItems(devices, checkedItem) { dialog, which ->
                binding.tvDeviceName.text = devices[which]
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    // ── 隐藏测试入口：连点设备状态卡片 5 次弹出 SDK 测试面板 ──────

    private fun setupHiddenTestEntry() {
        val droneManager = DroneManager.getInstance()
        // 订阅状态变化（Activity 销毁前移除），驱动测试面板与主页状态刷新
        droneManager.addStateListener(sdkStateListener)

        // 设备状态卡片作为隐藏入口
        binding.llDeviceCard.setOnClickListener { handleDeviceCardTap() }
        updateSdkStatusText()
    }

    /** 设备卡片点击计数：800ms 内连续点击 5 次触发测试面板 */
    private fun handleDeviceCardTap() {
        val now = System.currentTimeMillis()
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

        dialogBinding.btnTestInit.setOnClickListener {
            droneManager.initSDK(applicationContext) { success, message ->
                runOnUiThread {
                    Toast.makeText(this, message ?: (if (success) "SDK 就绪" else "失败"), Toast.LENGTH_SHORT).show()
                    updateSdkStatusText()
                }
            }
        }

        dialogBinding.btnTestSimOn.setOnClickListener {
            droneManager.enableSimulator { _, msg ->
                runOnUiThread {
                    Toast.makeText(this, msg ?: "", Toast.LENGTH_SHORT).show()
                    updateSdkStatusText()
                }
            }
        }

        dialogBinding.btnTestSimOff.setOnClickListener {
            droneManager.disableSimulator { _, msg ->
                runOnUiThread {
                    Toast.makeText(this, msg ?: "", Toast.LENGTH_SHORT).show()
                    updateSdkStatusText()
                }
            }
        }

        dialogBinding.btnTestClose.setOnClickListener {
            sdkTestDialog?.dismiss()
        }

        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .setCancelable(true)
            .create()
        dialog.setOnDismissListener {
            dialogTestBinding = null
            sdkTestDialog = null
            updateSdkStatusText()
        }
        sdkTestDialog = dialog
        dialog.show()
        updateSdkStatusText()
    }

    /** 构建测试面板状态文本（SDK/注册/模式/连接/设备/电量信号） */
    private fun buildTestStatusText(): String {
        val dm = DroneManager.getInstance()
        val state = dm.getSdkState()
        val sb = StringBuilder()
        sb.append("SDK: ${state.name}")
        sb.append('\n').append("注册: ${if (dm.isRegistered()) "成功" else "未完成/失败"}")
        sb.append('\n').append("模式: ${if (dm.isSimulatorEnabled()) "模拟器" else "真机"}")

        if (dm.isSimulatorEnabled()) {
            sb.append('\n').append("连接: 已连接 (Simulator)")
            sb.append('\n').append("电量: ${dm.getBatteryPercent()}%  信号: ${dm.getSignalStrength()}%")
        } else {
            sb.append('\n').append("连接: ${if (dm.isConnected()) "已连接" else "未连接"}")
            val device = dm.getConnectedDevice()
            if (device != null) {
                sb.append('\n').append("设备: ${device.summary()}")
            }
        }
        return sb.toString()
    }

    /** 刷新首页设备卡 + 测试面板（若已打开）状态显示 */
    private fun updateSdkStatusText() {
        val dm = DroneManager.getInstance()
        val state = dm.getSdkState()

        // 测试面板（Dialog）实时刷新
        dialogTestBinding?.tvTestStatus?.text = buildTestStatusText()

        // 首页左侧设备状态卡
        if (dm.isSimulatorEnabled()) {
            binding.tvDeviceName.text = "Simulator"
            binding.tvDeviceStatus.text = "模拟器运行中"
            binding.tvDeviceStatus.setTextColor(0xFF2E7D32.toInt())
            binding.tvBattery.text = "${dm.getBatteryPercent()}%"
            return
        }
        val device = dm.getConnectedDevice()
        if (device != null) {
            binding.tvDeviceName.text = device.productType.ifBlank { "已连接设备" }
            binding.tvDeviceStatus.text = "设备在线 · SDK 正常"
            binding.tvDeviceStatus.setTextColor(0xFF2E7D32.toInt())
        } else if (dm.isConnected()) {
            binding.tvDeviceName.text = "已连接设备"
            binding.tvDeviceStatus.text = "设备在线 · 等待型号信息"
        } else {
            binding.tvDeviceName.text = if (state == DroneManager.SdkState.REGISTERED) "未连接设备" else "设备未就绪"
            binding.tvDeviceStatus.text = when (state) {
                DroneManager.SdkState.REGISTERED -> "SDK 正常 · 等待连接无人机"
                DroneManager.SdkState.REGISTER_FAILED -> "SDK 注册失败，请检查网络/API Key"
                DroneManager.SdkState.NOT_INITIALIZED -> "SDK 未初始化"
                else -> "SDK 初始化中..."
            }
            binding.tvDeviceStatus.setTextColor(0xFF9E9E9E.toInt())
        }
    }

    override fun onPause() {
        pauseHomeDemoVideo()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        resumeHomeDemoVideo()
    }

    override fun onDestroy() {
        releaseHomeDemoVideo()
        DroneManager.getInstance().removeStateListener(sdkStateListener)
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