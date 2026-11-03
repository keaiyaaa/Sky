package com.example.msdksample.ui.device

import android.content.pm.ApplicationInfo
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.example.msdksample.data.local.LocalDataStore
import com.example.msdksample.data.local.SavedDevice
import com.example.msdksample.drone.DroneManager
import com.example.msdksample.drone.state.*
import com.example.msdksample.ui.local.LocalToolsActivity
import com.example.msdksample.ui.local.localProfileOwner

class DeviceManagementActivity : LocalToolsActivity() {
    private val manager get() = DroneManager.getInstance()
    private val store by lazy { LocalDataStore.getInstance(this) }
    private lateinit var status: TextView
    private lateinit var current: LinearLayout
    private lateinit var saved: LinearLayout
    private var scanRevision = 0
    @Volatile private var active = false
    @Volatile private var pageEpoch = 0L
    private var subscription: DeviceStateSubscription? = null
    private var lastConnectionKey: List<Any?>? = null

    override fun initViews() {
        setupPage("设备管理", "当前连接由 SDK 或本地演示确认。本机设备列表用于演示保存与解绑，尚未建立云端账户绑定。")
        status = text("")
        actions("刷新已发现设备" to { scan() }, "等待连接／重连" to {
            val epoch = pageEpoch
            val generation = manager.deviceStateProvider.currentSnapshot().generation
            if (manager.isSimulatorEnabled()) connect() else manager.initSDK(applicationContext) { success, message ->
                runOnUiThread {
                    if (!active || epoch != pageEpoch) return@runOnUiThread
                    if (manager.deviceStateProvider.currentSnapshot().generation != generation) {
                        toast("连接会话已变化，请重新操作")
                    } else if (success) connect() else toast(message ?: "SDK 未就绪")
                }
            }
        }, "取消等待" to { manager.cancelConnectionWait() })
        if (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            actions("开启本地演示" to { manager.enableSimulator { _, message -> toast(message.orEmpty()) } },
                "关闭本地演示" to { manager.disableSimulator { _, message -> toast(message.orEmpty()) } },
                "演示异常场景" to { showScenarios() })
        }
        current = section("当前连接设备")
        saved = section("本机设备列表")
        refresh()
    }

    private fun connect() { runOnUiThread {
        val epoch = pageEpoch
        if (active) manager.connectDevice { _, message -> runOnUiThread {
            if (active && epoch == pageEpoch) toast(message.orEmpty())
        } }
    } }

    override fun onStart() {
        super.onStart(); active = true
        val epoch = ++pageEpoch
        subscription = manager.deviceStateProvider.subscribe { snapshot -> runOnUiThread {
            if (!active || epoch != pageEpoch) return@runOnUiThread
            val key = listOf(snapshot.generation, snapshot.mode, snapshot.linkState, snapshot.sdkRegistration,
                snapshot.device, snapshot.readiness, snapshot.errorCode, snapshot.userMessage)
            if (key != lastConnectionKey) { lastConnectionKey = key; refresh(snapshot) }
        } }
        if (intent.getBooleanExtra("scan_on_open", false)) { intent.removeExtra("scan_on_open"); scan() }
    }
    override fun onStop() {
        active = false; pageEpoch++; scanRevision++; subscription?.cancel(); subscription = null
        lastConnectionKey = null; super.onStop()
    }

    private fun refresh(snapshot: DeviceStateSnapshot = manager.deviceStateProvider.currentSnapshot()) {
        status.text = DeviceStatePresentation.from(snapshot).details
        current.removeAllViews()
        val device = snapshot.device
        if (device == null || snapshot.linkState != LinkState.CONNECTED) text("尚无已连接设备。无设备时可在 Debug 版使用本地演示。", current)
        else {
            text("${device.productType ?: "型号未知"}\n序列号：${device.serialNumber ?: "读取中"}\n固件：${device.firmwareVersion ?: "暂无数据"}", current)
            val isDemo = snapshot.mode == DeviceMode.LOCAL_DEMO
            val schemaErrors = DroneIdentityFields.from(snapshot)?.validationErrors().orEmpty()
            if (schemaErrors.isNotEmpty()) text(schemaErrors.joinToString("\n"), current)
            button(if (isDemo) "保存演示设备到本机" else "保存设备到本机", current) {
                if (manager.deviceStateProvider.currentSnapshot().generation != snapshot.generation) {
                    toast("设备会话已变化，请刷新后保存")
                } else if (schemaErrors.isNotEmpty()) toast("设备字段尚未满足数据库要求")
                else saveDevice(DroneManager.DroneDevice(device.productId, device.productType.orEmpty(),
                    device.firmwareVersion.orEmpty(), device.serialNumber.orEmpty()), isDemo)
            }
        }
        saved.removeAllViews()
        val owner = localProfileOwner(this)
        val devices = store.devices(owner)
        if (devices.isEmpty()) text("暂无本机设备记录", saved)
        devices.forEach { item ->
            text("${item.name}${if (item.demo) "（演示）" else ""}\nSN：${item.serial}\n固件：${item.firmware.ifBlank { "暂无数据" }}", saved)
            button("移除 ${item.name}", saved) {
                AlertDialog.Builder(this).setTitle("移除本机记录")
                    .setMessage("移除 ${item.name} 的本机记录，不影响当前设备连接。")
                    .setNegativeButton("取消", null).setPositiveButton("移除") { _, _ ->
                        runCatching { check(store.unbindDevice(owner, item.key)) }
                            .onSuccess { toast("本机设备记录已移除"); refresh() }
                            .onFailure { toast("移除失败，请重试") }
                    }.show()
            }
        }
    }

    private fun saveDevice(device: DroneManager.DroneDevice, demo: Boolean) {
        if (device.serialNumber.isBlank()) { toast("设备序列号尚未获取，暂不能保存"); return }
        val owner = localProfileOwner(this)
        val item = SavedDevice("${if (demo) "demo" else "dji"}:${device.serialNumber}",
            device.productType, device.serialNumber, device.firmwareVersion, demo)
        if (store.devices(owner).any { it.key == item.key }) { toast("该设备已在本机列表中"); return }
        runCatching { check(store.bindDevice(owner, item)) }
            .onSuccess { toast("设备已保存到本机"); refresh() }
            .onFailure { toast("保存失败，请重试") }
    }

    private fun scan() {
        val request = ++scanRevision
        val generation = manager.deviceStateProvider.currentSnapshot().generation
        status.text = "正在查询 SDK 已发现的设备…"
        manager.searchDevices { devices, error -> runOnUiThread {
            if (!active || request != scanRevision || generation != manager.deviceStateProvider.currentSnapshot().generation) return@runOnUiThread
            refresh()
            status.append("\n" + (error ?: "发现 ${devices.size} 台设备"))
        } }
    }

    private fun showScenarios() {
        if (!manager.isSimulatorEnabled()) { toast("请先开启本地演示"); return }
        val scenarios = DroneManager.DemoScenario.values()
        val labels = arrayOf("恢复正常／重连成功", "连接中", "连接失败", "遥测缺值", "遥测异常值", "设置同步失败", "未连接")
        AlertDialog.Builder(this).setTitle("选择本地演示场景").setItems(labels) { _, index ->
            manager.setDemoScenario(scenarios[index]) { _, message -> toast(message.orEmpty()) }
        }.setNegativeButton("取消", null).show()
    }
}
