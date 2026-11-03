package com.example.msdksample.ui.settings

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.msdksample.data.local.LocalDataStore
import com.example.msdksample.drone.DroneManager

data class SettingOperation(val label: String, val value: String, val apply: ((Boolean, String?) -> Unit) -> Unit)

class SettingsSyncController(context: Context, private var onChanged: ((String, Boolean) -> Unit)?) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var busy = false

    fun start(operations: List<SettingOperation>) {
        if (busy) return
        val manager = DroneManager.getInstance()
        val demo = manager.isSimulatorEnabled()
        if (!manager.isConnected()) {
            onChanged?.invoke("本地已保存 · 未同步\n" + operations.joinToString("\n") { "${it.label}：本地=${it.value}；设备未确认" }, false)
            return
        }
        busy = true
        val revision = manager.getConnectionRevision()
        onChanged?.invoke("本地已保存 · ${if (demo) "等待演示回调" else "等待设备确认"}", true)
        var timeout: Runnable? = null
        val batch = SyncBatch(operations.map { it.label }) { replies ->
            handler.post {
                timeout?.let(handler::removeCallbacks)
                busy = false
                val finalReplies = if (manager.getConnectionRevision() == revision) replies else
                    replies.mapValues { "连接已变化，请重新同步" }
                val failures = finalReplies.values.count { it != null }
                val title = when {
                    demo && failures == 0 -> "本地已保存 · 演示同步成功（未下发飞机）"
                    failures == 0 -> "本地已保存 · 设备已确认（待真机验收）"
                    else -> "本地已保存 · ${if (demo) "演示" else "设备"}同步失败 $failures/${operations.size} 项"
                }
                val details = operations.joinToString("\n") { operation ->
                    val error = finalReplies[operation.label]
                    "${operation.label}：本地=${operation.value}；" +
                        if (error == null) "${if (demo) "演示" else "设备"}确认=${operation.value}" else "未确认（$error）"
                }
                runCatching { LocalDataStore.getInstance(appContext).record("设置", "保存并同步设置",
                    if (failures == 0) "成功" else "失败", if (demo) "本地演示" else "设备连接", details) }
                    .onFailure { Log.e("SettingsSync", "保存同步日志失败", it) }
                onChanged?.invoke("$title\n$details", false)
            }
        }
        timeout = Runnable { batch.timeout() }
        handler.postDelayed(timeout!!, 15_000)
        operations.forEach { operation ->
            try { operation.apply { success, error -> batch.reply(operation.label, success, error) } }
            catch (error: Throwable) { batch.reply(operation.label, false, error.message ?: "设备接口不可用") }
        }
    }

    fun detach() { onChanged = null }
}
