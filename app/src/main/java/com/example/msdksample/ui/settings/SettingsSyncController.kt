package com.example.msdksample.ui.settings

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.msdksample.data.local.LocalDataStore
import com.example.msdksample.drone.DroneManager
import com.example.msdksample.drone.state.*
import com.example.msdksample.settings.*

class SettingsSyncController(
    context: Context,
    private var onChanged: ((String, Boolean) -> Unit)?,
    private val provider: DeviceStateProvider = DroneManager.getInstance().deviceStateProvider,
    private val apply: (SettingValue, Long, (Boolean, String?) -> Unit) -> Unit = { value, generation, reply ->
        DroneManager.getInstance().applyUserSetting(value, generation, reply)
    },
    private val timeoutMillis: Long = 15_000
) {
    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private var busy = false
    private var requestId = 0L
    private var subscription: DeviceStateSubscription? = null

    fun start(values: List<SettingValue>) {
        if (Looper.myLooper() != Looper.getMainLooper()) { handler.post { start(values) }; return }
        if (busy || onChanged == null) return
        require(values.map { it.field }.distinct().size == values.size) { "设置字段不可重复" }
        require(values.all { it.field.validate(it.value) == null }) {
            values.mapNotNull { it.field.validate(it.value) }.joinToString("\n")
        }
        val state = provider.currentSnapshot()
        val generation = state.generation
        val request = ++requestId
        val deviceValues = values.filter { !it.field.cloudOnly && it.value != null }
        val demo = state.mode == DeviceMode.LOCAL_DEMO
        fun finish(replies: Map<String, String?>) {
            if (request != requestId || onChanged == null) return
            busy = false
            subscription?.cancel(); subscription = null
            handler.removeCallbacksAndMessages(null)
            val finalReplies = if (provider.currentSnapshot().generation == generation) replies else
                deviceValues.associate { it.field.column to "连接会话已变化，请重新同步" }
            val failures = finalReplies.values.count { it != null }
            val rejection = SettingsDispatchPolicy.rejection(state, generation)
            val title = when {
                deviceValues.isEmpty() || rejection != null -> "本地已保存 · 未同步"
                failures == 0 && demo -> "本地已保存 · 演示同步成功（未下发飞机，未做设备读回）"
                else -> "本地已保存 · 演示同步失败 $failures/${deviceValues.size} 项"
            }
            val details = values.joinToString("\n") { item ->
                val result = when {
                    item.field.cloudOnly -> "仅保存本机，云端未接入"
                    item.value == null -> "未设置，未下发"
                    finalReplies[item.field.column] != null -> "未确认（${finalReplies[item.field.column]}）"
                    else -> "演示确认=${item.display}，未做设备读回"
                }
                "${item.field.label}：本地=${item.display}；$result"
            }
            runCatching { LocalDataStore.getInstance(appContext).record("设置", "保存并同步设置",
                if (rejection != null || deviceValues.isEmpty()) "未同步" else if (failures == 0) "演示成功" else "失败",
                if (demo) "本地演示" else "设备连接", "$title\n$details") }
                .onFailure { Log.e("SettingsSync", "保存同步日志失败", it) }
            onChanged?.invoke("$title\n$details", false)
        }
        if (deviceValues.isEmpty()) { finish(emptyMap()); return }
        val rejected = SettingsDispatchPolicy.rejection(state, generation)
        if (rejected != null) { finish(deviceValues.associate { it.field.column to rejected }); return }
        busy = true
        onChanged?.invoke("本地已保存 · 等待演示回调（未下发飞机）", true)
        val batch = SyncBatch(deviceValues.map { it.field.column }) { replies -> handler.post { finish(replies) } }
        subscription = provider.subscribe { next ->
            if (next.generation != generation) handler.post {
                if (request == requestId && busy) batch.invalidate("连接会话已变化，请重新同步")
            }
        }
        handler.postDelayed({ if (request == requestId && busy) batch.timeout() }, timeoutMillis)
        deviceValues.forEach { item ->
            val error = SettingsDispatchPolicy.rejection(provider.currentSnapshot(), generation) ?: item.field.validate(item.value)
            if (error != null) batch.reply(item.field.column, false, error)
            else try {
                apply(item, generation) { success, message -> handler.post {
                    if (request != requestId || !busy) return@post
                    if (provider.currentSnapshot().generation != generation) batch.invalidate("连接会话已变化，请重新同步")
                    else batch.reply(item.field.column, success, message)
                } }
            } catch (error: Exception) { batch.reply(item.field.column, false, error.message ?: "参数接口不可用") }
        }
    }

    fun detach() {
        onChanged = null
        requestId++
        busy = false
        subscription?.cancel(); subscription = null
        handler.removeCallbacksAndMessages(null)
    }
}
