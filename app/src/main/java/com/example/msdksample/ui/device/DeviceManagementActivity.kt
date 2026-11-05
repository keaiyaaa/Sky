package com.example.msdksample.ui.device

import android.content.pm.ApplicationInfo
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.example.msdksample.data.local.LocalDataStore
import com.example.msdksample.data.local.SavedDevice
import com.example.msdksample.drone.DroneManager
import com.example.msdksample.drone.state.*
import com.example.msdksample.ui.local.AccountCloudActivity
import com.example.msdksample.ui.local.localProfileOwner
import com.example.msdksample.data.remote.*
import com.google.gson.Gson
import java.util.UUID

class DeviceManagementActivity : AccountCloudActivity() {
    private val manager get() = DroneManager.getInstance()
    private val store by lazy { LocalDataStore.getInstance(this) }
    private lateinit var status: TextView
    private lateinit var current: LinearLayout
    private lateinit var saved: LinearLayout
    private lateinit var accountDevices: LinearLayout
    private lateinit var pendingWrites: LinearLayout
    private lateinit var accountPageLabel: TextView
    private var accountPage = 0
    private var accountTotal = 0L
    private var accountItems: List<CloudDevice> = emptyList()
    private val json = Gson()
    private var scanRevision = 0
    @Volatile private var active = false
    @Volatile private var pageEpoch = 0L
    private var subscription: DeviceStateSubscription? = null
    private var lastConnectionKey: List<Any?>? = null

    override fun initViews() {
        setupPage("设备管理", "当前连接、本机记录与账户记录分别显示。保存或移除账户记录不改变 DJI 连接，也不证明物理设备所有权。")
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
        addCloudStatus()
        actions("刷新账户设备" to { loadAccountPage(0) }, "上一页" to {
            if (accountPage > 0) loadAccountPage(accountPage - 1)
        }, "下一页" to { if ((accountPage + 1L) * 20 < accountTotal) loadAccountPage(accountPage + 1) })
        accountPageLabel = text("账户设备尚未读取")
        accountDevices = section("账户设备记录")
        pendingWrites = section("待处理账户操作")
        BackendSessions.registry.current()?.let { lease ->
            runCatching { json.fromJson(cloudStore.snapshot(lease.userId, "devices:0"), DevicePage::class.java) }
                .getOrNull()?.let { page -> accountItems = page.items; accountTotal = page.total
                    accountPageLabel.text = "本机缓存，第1页；请刷新确认最新状态" }
        }
        renderAccountDevices()
        renderPending()
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
            val isDemo = snapshot.mode != DeviceMode.DEVICE
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
                        if (localProfileOwner(this) != owner) {
                            toast("登录身份已变化，请重新打开设备页面"); finish(); return@setPositiveButton
                        }
                        runCatching { check(store.unbindDevice(owner, item.key)) }
                            .onSuccess { toast("本机设备记录已移除"); refresh() }
                            .onFailure { toast("移除失败，请重试") }
                    }.show()
            }
            if (!item.demo) button("将 ${item.name} 保存到账户", saved) {
                if (localProfileOwner(this) != owner) { toast("登录身份已变化，请重新打开页面"); return@button }
                queueCreate(item)
            }
        }
    }

    private fun saveDevice(device: DroneManager.DroneDevice, demo: Boolean) {
        if (!samePageIdentity()) return
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

    private fun loadAccountPage(page: Int) = cloud({ api, lease -> api.devices(lease, page) }) { result, lease ->
        accountPage = result.page; accountTotal = result.total; accountItems = result.items
        cloudStore.saveSnapshot(lease.userId, "devices:$page", json.toJson(result))
        accountPageLabel.text = "第${accountPage + 1}页，共${accountTotal}条账户记录"
        cloudStatus.text = "账户设备已刷新"
        renderAccountDevices(); renderPending()
    }

    private fun queueCreate(item: SavedDevice) {
        if (item.demo) { toast("演示设备不上传"); return }
        if (!AccountFieldRules.validText(item.name, 64, true) || !AccountFieldRules.validText(item.serial, 128, true) ||
            !AccountFieldRules.validText(item.firmware, 64)) { toast("设备字段不符合账户保存要求"); return }
        val lease = accountLease() ?: return
        if (cloudStore.pending(lease.userId).any { it.key == "create:${item.key}" }) {
            cloudStatus.text = "该设备有未确认的保存请求，请在待处理列表重试或放弃。"; renderPending(); return
        }
        val pending = runCatching { cloudStore.pending(lease.userId, "create:${item.key}", "POST", "devices") {
            json.toJson(DeviceCreate(UUID.randomUUID().toString(), item.name.trim(), item.name.trim(),
                item.serial.trim(), item.firmware.trim(), idempotencyKey = UUID.randomUUID().toString()))
        } }.getOrElse { toast("待处理请求保存失败，暂未发送"); return }
        send(pending)
    }

    private fun renderAccountDevices() {
        accountDevices.removeAllViews()
        if (accountItems.isEmpty()) text("暂无已读取的账户设备记录", accountDevices)
        accountItems.forEach { item ->
            text("${item.droneName}\n型号：${item.model.ifBlank { "未知" }}\nSN：${item.serialNo}\n固件：${item.firmwareVersion.ifBlank { "未知" }}\n记录版本：${item.version}", accountDevices)
            button("修改 ${item.droneName} 的名称", accountDevices) {
                val input = EditText(this).apply { setText(item.droneName); setSingleLine(true) }
                AlertDialog.Builder(this).setTitle("修改账户设备名称").setView(input)
                    .setNegativeButton("取消", null).setPositiveButton("保存") { _, _ ->
                        val name = input.text.toString().trim()
                        if (!AccountFieldRules.validText(name, 64, true)) { toast("名称须为1至64字"); return@setPositiveButton }
                        queueMutation(item, "PUT") { json.toJson(DeviceUpdate(name, item.model, item.firmwareVersion,
                            item.version, UUID.randomUUID().toString())) }
                    }.show()
            }
            button("移除账户记录 ${item.droneName}", accountDevices) {
                AlertDialog.Builder(this).setTitle("移除账户记录")
                    .setMessage("只移除当前账号保存的记录，不影响本机记录或 DJI 连接。")
                    .setNegativeButton("取消", null).setPositiveButton("移除") { _, _ ->
                        queueMutation(item, "DELETE") { json.toJson(DeviceDelete(item.version, UUID.randomUUID().toString())) }
                    }.show()
            }
        }
    }

    private fun queueMutation(item: CloudDevice, method: String, body: () -> String) {
        val lease = accountLease() ?: return
        if (cloudStore.pending(lease.userId).any { it.key == "device:${item.id}" }) {
            cloudStatus.text = "该设备有未确认操作，请在待处理列表重试或放弃。"; renderPending(); return
        }
        val pending = runCatching { cloudStore.pending(lease.userId, "device:${item.id}", method, "devices/${item.id}", body) }
            .getOrElse { toast(it.message ?: "待处理请求保存失败，暂未发送"); return }
        send(pending)
    }

    private fun send(write: PendingDeviceWrite) {
        renderPending()
        cloud({ api, lease ->
            check(lease.userId == write.userId) { "登录身份已变化，请重新打开页面" }
            api.writeDevice(lease, write.method, write.path, write.body)
        }) { result, _ ->
            cloudStore.confirm(write)
            if (result.deleted) accountTotal = (accountTotal - 1).coerceAtLeast(0)
            // Update only this known record; a new item requires a fresh server page to preserve sorting.
            accountItems = accountItems.map { if (it.id == result.id) result else it }.filterNot { it.deleted }
            BackendSessions.registry.current()?.let { lease ->
                cloudStore.saveSnapshot(lease.userId, "devices:$accountPage", json.toJson(
                    DevicePage(accountItems, accountPage, 20, accountTotal)))
            }
            cloudStatus.text = if (result.deleted) "账户记录已移除；本机连接未改变" else "设备记录已保存到账户；可刷新查看完整列表"
            accountPageLabel.text = "第${accountPage + 1}页；列表已变化，请刷新核对"
            renderAccountDevices(); renderPending()
        }
    }

    private fun renderPending() {
        pendingWrites.removeAllViews()
        val lease = BackendSessions.registry.current() ?: return
        val pending = cloudStore.pending(lease.userId)
        if (pending.isEmpty()) text("没有待处理操作", pendingWrites)
        pending.forEach { write ->
            val label = when (write.method) { "POST" -> "保存设备"; "PUT" -> "修改设备"; else -> "移除设备" }
            val details = runCatching { json.fromJson(write.body, com.google.gson.JsonObject::class.java) }.getOrNull()
            val name = details?.get("droneName")?.asString
            val serial = details?.get("serialNo")?.asString
            text("$label${name?.let { "：$it" }.orEmpty()}${serial?.let { "\nSN：$it" }.orEmpty()}\n操作未确认时应先重试。版本冲突请刷新，再放弃此项后重新编辑。", pendingWrites)
            button("重试$label", pendingWrites) { send(write) }
            button("放弃这次$label", pendingWrites) {
                AlertDialog.Builder(this).setTitle("放弃待处理操作")
                    .setMessage("操作可能已在服务端完成。放弃后请刷新账户列表核对；此操作不会撤销服务端结果。")
                    .setNegativeButton("保留", null).setPositiveButton("放弃") { _, _ ->
                        if (BackendSessions.registry.current()?.userId == write.userId) {
                            cloudStore.confirm(write); renderPending()
                        }
                    }.show()
            }
        }
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
