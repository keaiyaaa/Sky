package com.example.msdksample.ui.settings

import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.msdksample.R
import com.example.msdksample.data.remote.*
import com.example.msdksample.settings.ScopedUserSettingsStore
import com.example.msdksample.settings.UserSettings
import kotlinx.coroutines.*
import java.util.UUID

/** Shared account panel. It saves drafts/cloud values only; aircraft application remains a separate action. */
class AccountSettingsController(
    private val activity: AppCompatActivity,
    private val preferences: ScopedUserSettingsStore,
    private val draftStamp: () -> String,
    private val saveDraft: () -> UserSettings,
    private val reloadDraft: () -> Unit,
    private val apiFactory: () -> BackendAccountApi = { BackendAccountRuntime.api(activity.applicationContext) }
) : DefaultLifecycleObserver {
    private val store = AccountCloudStore.getInstance(activity)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val status = activity.findViewById<TextView>(R.id.tv_account_settings_status)
    private val read = activity.findViewById<Button>(R.id.btn_account_settings_read)
    private val save = activity.findViewById<Button>(R.id.btn_account_settings_save)
    private val retry = activity.findViewById<Button>(R.id.btn_account_settings_retry)
    private val discard = activity.findViewById<Button>(R.id.btn_account_settings_discard)
    private val import = activity.findViewById<Button>(R.id.btn_account_settings_import)
    private var snapshot: CloudUserSettings? = preferences.userId?.let { owner ->
        store.snapshot(owner, "settings")?.let { runCatching { CloudSettingsCodec.decode(it, owner) }.getOrNull() }
    }
    private var busy = false
    private var epoch = 0L
    private var dialog: AlertDialog? = null

    init {
        activity.lifecycle.addObserver(this)
        status.text = if (preferences.userId == null) "账户未登录；当前为本机草稿。旧草稿不会自动上传。"
            else "当前为该账号的本机草稿；${cloudState()}\n账户保存不代表飞机已应用。"
        read.setOnClickListener {
            if (guard()) confirm("读取账户设置", "这会替换当前页面和该账号的本机草稿；待确认保存会继续保留。") {
                val before = draftStamp()
                request { api, lease ->
                    val result = api.settings(lease)
                    remember(result)
                    if (draftStamp() == before) {
                        preferences.useCloudSettings(snapshot!!.settings.local()); reloadDraft()
                        status.text = "${cloudState()}；已读取到本机草稿。飞机应用状态未知。"
                    } else status.text = "${cloudState()}；页面内容已变化，保留当前草稿。飞机应用状态未知。"
                }
            }
        }
        save.setOnClickListener {
            if (!guard()) return@setOnClickListener
            runCatching {
                check(pending() == null) { "存在待确认保存，请先重试或放弃；当前草稿不会替换原请求。" }
                val draft = saveDraft()
                check(!preferences.load().sensitivityNeedsReview) { "请先到飞行设置确认1–10档灵敏度；本机草稿已保留。" }
                request { api, lease ->
                    val current = snapshot ?: api.settings(lease).also(::remember)
                    val write = store.pending(lease.userId, "settings", "PUT", "settings") {
                        CloudSettingsCodec.request(draft, current.version, UUID.randomUUID().toString())
                    }
                    send(api, lease, write)
                }
            }.onFailure { status.text = it.message }
        }
        retry.setOnClickListener {
            if (!guard()) return@setOnClickListener
            val write = pending() ?: return@setOnClickListener
            request { api, lease -> send(api, lease, write) }
        }
        discard.setOnClickListener {
            if (guard()) confirm("放弃待确认保存", "只移除本机重试记录，已到达服务器的保存不会撤销。本机草稿保留；下次保存前请读取账户确认最新内容。") {
                pending()?.let(store::confirm)
                snapshot = null
                preferences.userId?.let { store.deleteSnapshot(it, "settings") }
                status.text = "已放弃本机重试记录；云端结果仍需读取确认。本机草稿已保留。"
                buttons()
            }
        }
        import.setOnClickListener {
            if (guard()) confirm("导入旧本机草稿", "这会替换该账号的本机草稿，旧草稿和备份保留。导入后需另行点击保存到账户；不会向飞机下发。") {
                runCatching { preferences.importLocalDraft(); reloadDraft() }
                    .onSuccess { status.text = "已导入本机草稿，尚未保存到账户；旧灵敏度如需复核，请在飞行设置确认。" }
                    .onFailure { status.text = it.message }
            }
        }
        buttons()
    }

    private fun cloudState(): String = snapshot?.let {
        if (it.cloudSaved) "最近确认账户已保存（版本${it.version}）" else "账户尚未保存，云端返回默认值"
    } ?: "账户状态尚未读取"
    private fun pending(): PendingDeviceWrite? = preferences.userId?.let { owner -> store.pending(owner).firstOrNull { it.key == "settings" } }
    private fun buttons() {
        val available = preferences.userId != null && preferences.isCurrentOwner() && !busy
        listOf(read, save, import).forEach { it.isEnabled = available }
        val hasPending = pending() != null
        retry.isEnabled = available && hasPending
        discard.isEnabled = available && hasPending
    }
    private fun guard(): Boolean {
        if (!preferences.isCurrentOwner()) {
            Toast.makeText(activity, "账号已变更，请重新打开设置页面", Toast.LENGTH_LONG).show()
            activity.finish(); return false
        }
        if (preferences.userId == null) { status.text = "请先完成云端登录；本机草稿仍可保存。"; return false }
        return !busy
    }
    private fun confirm(title: String, message: String, action: () -> Unit) {
        dialog?.dismiss()
        dialog = AlertDialog.Builder(activity).setTitle(title).setMessage(message)
            .setNegativeButton("取消", null).setPositiveButton("继续") { _, _ -> if (guard()) action() }.show()
    }
    private fun remember(value: CloudUserSettings) {
        preferences.requireCurrentOwner()
        snapshot = CloudSettingsCodec.latest(snapshot, value)
        store.saveSnapshot(value.userId, "settings", CloudSettingsCodec.json.toJson(snapshot))
    }
    private suspend fun send(api: BackendAccountApi, lease: BackendSessionRegistry.Lease, write: PendingDeviceWrite) {
        check(write.userId == lease.userId && write.method == "PUT" && write.path == "settings")
        val response = api.writeSettings(lease, write.body)
        remember(response)
        store.confirm(write)
        status.text = if (snapshot!!.version > response.version)
            "已确认原保存（版本${response.version}）；最近账户版本为${snapshot!!.version}，请读取最新内容。本机草稿保留，飞机应用状态未知。"
        else "账户已保存（版本${response.version}）。本机草稿保留；飞机应用状态未知，请查看设备同步结果。"
    }
    private fun request(action: suspend (BackendAccountApi, BackendSessionRegistry.Lease) -> Unit) {
        if (!guard()) return
        val lease = BackendSessions.registry.current() ?: return
        busy = true; val requestEpoch = ++epoch; buttons()
        status.text = "正在处理账户设置…"
        scope.launch {
            try {
                action(apiFactory(), lease)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                if (requestEpoch == epoch) status.text = when (error) {
                    is AccountApiFailure, is IllegalStateException -> error.message
                    else -> "网络或响应异常，尚未确认账户保存；本机草稿和待确认请求已保留。"
                }
            } finally { if (requestEpoch == epoch) { busy = false; buttons() } }
        }
    }
    override fun onStart(owner: LifecycleOwner) {
        if (!preferences.isCurrentOwner()) { activity.finish(); return }
        // Another settings page may have saved/discarded while this page was stopped.
        snapshot = preferences.userId?.let { id -> store.snapshot(id, "settings")?.let {
            runCatching { CloudSettingsCodec.decode(it, id) }.getOrNull()
        } }
        buttons()
        if (preferences.userId != null) status.text = "当前为该账号的本机草稿；${cloudState()}\n" +
            (if (pending() != null) "有待确认保存，请重试或放弃。" else "") + "账户保存不代表飞机已应用。"
    }
    override fun onStop(owner: LifecycleOwner) {
        val wasBusy = busy
        ++epoch; scope.coroutineContext.cancelChildren(); busy = false
        if (wasBusy) status.text = "账户请求已停止，保存结果尚未确认；本机草稿及待确认请求保留。"
        dialog?.dismiss(); dialog = null; buttons()
    }
    override fun onDestroy(owner: LifecycleOwner) { scope.cancel() }
}
